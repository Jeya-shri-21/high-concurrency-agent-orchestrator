package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.resilience.exception.BulkheadFullException;
import com.zaalima.orchestrator.resilience.exception.CircuitBreakerOpenException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiTimeoutException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiValidationException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Executes operations protected by the Resilience4j pipeline.
 *
 * Decorator Order:
 * Operation Request
 *   --> Concurrency Admission Control (Bulkhead)
 *   --> Circuit State & Failure Tracking (CircuitBreaker)
 *   --> Overall Operation Deadline (TimeLimiter)
 *   --> External / Remote Network Operation (with connect/request timeouts)
 */
@Component
public class ResilientOperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResilientOperationExecutor.class);

    private final ResilienceManager resilienceManager;
    private final ExecutorService virtualExecutor;

    public ResilientOperationExecutor(ResilienceManager resilienceManager) {
        this.resilienceManager = Objects.requireNonNull(resilienceManager, "ResilienceManager must not be null");
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Executes a Callable protected by Bulkhead, CircuitBreaker, and TimeLimiter.
     *
     * @param instanceName name of the resilience instance
     * @param operation the remote operation to execute
     * @param <T> result type
     * @return result of the operation
     * @throws Exception if execution fails or is rejected
     */
    public <T> T executeWithTimeLimiter(String instanceName, Callable<T> operation) throws Exception {
        Bulkhead bulkhead = resilienceManager.getBulkhead(instanceName);
        CircuitBreaker circuitBreaker = resilienceManager.getCircuitBreaker(instanceName);
        TimeLimiter timeLimiter = resilienceManager.getTimeLimiter(instanceName);

        // 1. Bulkhead admission check
        if (!bulkhead.tryAcquirePermission()) {
            log.warn("Bulkhead '{}' rejected call: concurrency limit ({}) reached",
                    instanceName, bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
            throw new BulkheadFullException(instanceName,
                    "Bulkhead '" + instanceName + "' is saturated; concurrency limit (" +
                    bulkhead.getBulkheadConfig().getMaxConcurrentCalls() + ") reached");
        }

        // 2. Circuit Breaker admission check
        try {
            circuitBreaker.acquirePermission();
        } catch (CallNotPermittedException e) {
            bulkhead.onComplete(); // Always release permit if circuit breaker rejects!
            log.warn("CircuitBreaker '{}' rejected call: state is {}", instanceName, circuitBreaker.getState());
            throw new CircuitBreakerOpenException(instanceName,
                    "CircuitBreaker '" + instanceName + "' is " + circuitBreaker.getState() + "; call rejected", e);
        }

        long startTimestamp = circuitBreaker.getCurrentTimestamp();
        try {
            // 3. TimeLimiter bounded execution with cancellation and thread interrupt propagation
            CompletableFuture<T> future = new CompletableFuture<>();
            Future<?> runningTask = virtualExecutor.submit(() -> {
                try {
                    T res = operation.call();
                    future.complete(res);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });

            future.whenComplete((res, err) -> {
                if (future.isCancelled()) {
                    runningTask.cancel(true);
                }
            });

            T result = timeLimiter.executeFutureSupplier(() -> future);

            long duration = circuitBreaker.getCurrentTimestamp() - startTimestamp;
            circuitBreaker.onSuccess(duration, circuitBreaker.getTimestampUnit());
            return result;

        } catch (TimeoutException e) {
            long duration = circuitBreaker.getCurrentTimestamp() - startTimestamp;
            ExternalApiTimeoutException timeoutEx = new ExternalApiTimeoutException(
                    ExternalApiTimeoutException.TimeoutType.OPERATION,
                    timeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis(),
                    "Operation on '" + instanceName + "' timed out after " +
                    timeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis() + "ms", e);
            circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), timeoutEx);
            throw timeoutEx;

        } catch (Exception e) {
            long duration = circuitBreaker.getCurrentTimestamp() - startTimestamp;
            Throwable cause = extractRootCause(e);

            Throwable classifiedException = classifyException(cause);
            circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), classifiedException);

            if (classifiedException instanceof RuntimeException re) {
                throw re;
            } else if (classifiedException instanceof Exception ex) {
                throw ex;
            } else {
                throw new RuntimeException(classifiedException);
            }

        } finally {
            bulkhead.onComplete();
        }
    }

    /**
     * Executes a Callable directly protected by Bulkhead and CircuitBreaker,
     * relying on the underlying client's connection and read timeouts.
     */
    public <T> T execute(String instanceName, Callable<T> operation) throws Exception {
        Bulkhead bulkhead = resilienceManager.getBulkhead(instanceName);
        CircuitBreaker circuitBreaker = resilienceManager.getCircuitBreaker(instanceName);

        // 1. Bulkhead admission check
        if (!bulkhead.tryAcquirePermission()) {
            log.warn("Bulkhead '{}' rejected call: concurrency limit ({}) reached",
                    instanceName, bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
            throw new BulkheadFullException(instanceName,
                    "Bulkhead '" + instanceName + "' is saturated; concurrency limit (" +
                    bulkhead.getBulkheadConfig().getMaxConcurrentCalls() + ") reached");
        }

        // 2. Circuit Breaker admission check
        try {
            circuitBreaker.acquirePermission();
        } catch (CallNotPermittedException e) {
            bulkhead.onComplete();
            log.warn("CircuitBreaker '{}' rejected call: state is {}", instanceName, circuitBreaker.getState());
            throw new CircuitBreakerOpenException(instanceName,
                    "CircuitBreaker '" + instanceName + "' is " + circuitBreaker.getState() + "; call rejected", e);
        }

        long startTimestamp = circuitBreaker.getCurrentTimestamp();
        try {
            T result = operation.call();
            long duration = circuitBreaker.getCurrentTimestamp() - startTimestamp;
            circuitBreaker.onSuccess(duration, circuitBreaker.getTimestampUnit());
            return result;

        } catch (Exception e) {
            long duration = circuitBreaker.getCurrentTimestamp() - startTimestamp;
            Throwable cause = extractRootCause(e);
            Throwable classified = classifyException(cause);

            circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), classified);

            if (classified instanceof RuntimeException re) {
                throw re;
            } else if (classified instanceof Exception ex) {
                throw ex;
            } else {
                throw new RuntimeException(classified);
            }

        } finally {
            bulkhead.onComplete();
        }
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        return execute(ResilienceManager.DEFAULT_INSTANCE, operation);
    }

    public <T> T executeWithTimeLimiter(Callable<T> operation) throws Exception {
        return executeWithTimeLimiter(ResilienceManager.DEFAULT_INSTANCE, operation);
    }

    private Throwable extractRootCause(Throwable t) {
        if (t instanceof CompletionException || t instanceof ExecutionException) {
            if (t.getCause() != null) {
                return extractRootCause(t.getCause());
            }
        }
        return t;
    }

    private Throwable classifyException(Throwable t) {
        if (t instanceof HttpConnectTimeoutException) {
            return new ExternalApiTimeoutException(
                    ExternalApiTimeoutException.TimeoutType.CONNECT,
                    resilienceManager.getProperties().getTimeout().getConnectTimeoutMs(),
                    "Connection timed out: " + t.getMessage(), t);
        }
        if (t instanceof HttpTimeoutException) {
            return new ExternalApiTimeoutException(
                    ExternalApiTimeoutException.TimeoutType.REQUEST,
                    resilienceManager.getProperties().getTimeout().getRequestTimeoutMs(),
                    "HTTP request timed out: " + t.getMessage(), t);
        }
        if (t instanceof TimeoutException) {
            return new ExternalApiTimeoutException(
                    ExternalApiTimeoutException.TimeoutType.OPERATION,
                    resilienceManager.getProperties().getTimeout().getOperationTimeoutMs(),
                    "Operation timed out: " + t.getMessage(), t);
        }
        if (t instanceof ConnectException) {
            return new ExternalApiException(503, "Failed to connect to downstream service: " + t.getMessage(), t);
        }
        if (t instanceof IOException) {
            return new ExternalApiException(502, "Downstream I/O failure: " + t.getMessage(), t);
        }
        return t;
    }

    public void shutdown() {
        virtualExecutor.close();
    }
}
