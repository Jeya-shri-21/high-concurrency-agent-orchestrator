package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.resilience.config.ResilienceProperties;
import com.zaalima.orchestrator.resilience.exception.ExternalApiException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiTimeoutException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiValidationException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Central manager for Resilience4j registries and policies.
 * Configures CircuitBreaker, Bulkhead, and TimeLimiter with defaults and telemetry logging.
 */
@Component
public class ResilienceManager {

    private static final Logger log = LoggerFactory.getLogger(ResilienceManager.class);

    public static final String DEFAULT_INSTANCE = "external-agent";

    private final ResilienceProperties properties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    public ResilienceManager(ResilienceProperties properties) {
        this.properties = Objects.requireNonNull(properties, "ResilienceProperties must not be null");

        // 1. Configure Circuit Breaker Registry
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getCircuitBreaker().getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getCircuitBreaker().getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getCircuitBreaker().getFailureRateThreshold())
                .slowCallRateThreshold(properties.getCircuitBreaker().getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(properties.getCircuitBreaker().getSlowCallDurationThresholdMs()))
                .waitDurationInOpenState(Duration.ofMillis(properties.getCircuitBreaker().getWaitDurationInOpenStateMs()))
                .permittedNumberOfCallsInHalfOpenState(properties.getCircuitBreaker().getPermittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(properties.getCircuitBreaker().isAutomaticTransitionFromOpenToHalfOpenEnabled())
                .recordExceptions(
                        ExternalApiException.class,
                        ExternalApiTimeoutException.class,
                        IOException.class,
                        TimeoutException.class,
                        HttpTimeoutException.class,
                        ConnectException.class
                )
                .ignoreExceptions(
                        ExternalApiValidationException.class,
                        IllegalArgumentException.class
                )
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);

        // 2. Configure Bulkhead Registry (Semaphore-based for Virtual Thread compatibility)
        BulkheadConfig bhConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getMaxConcurrentCalls())
                .maxWaitDuration(Duration.ofMillis(properties.getBulkhead().getMaxWaitDurationMs()))
                .build();
        this.bulkheadRegistry = BulkheadRegistry.of(bhConfig);

        // 3. Configure TimeLimiter Registry
        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(properties.getTimeout().getOperationTimeoutMs()))
                .cancelRunningFuture(true)
                .build();
        this.timeLimiterRegistry = TimeLimiterRegistry.of(tlConfig);

        // Attach event listeners for default instance
        registerListeners(getCircuitBreaker(DEFAULT_INSTANCE));
        registerListeners(getBulkhead(DEFAULT_INSTANCE));
        registerListeners(getTimeLimiter(DEFAULT_INSTANCE));
    }

    public CircuitBreaker getCircuitBreaker(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        registerListeners(cb);
        return cb;
    }

    public Bulkhead getBulkhead(String name) {
        Bulkhead bh = bulkheadRegistry.bulkhead(name);
        registerListeners(bh);
        return bh;
    }

    public TimeLimiter getTimeLimiter(String name) {
        TimeLimiter tl = timeLimiterRegistry.timeLimiter(name);
        registerListeners(tl);
        return tl;
    }

    public CircuitBreaker getCircuitBreaker() {
        return getCircuitBreaker(DEFAULT_INSTANCE);
    }

    public Bulkhead getBulkhead() {
        return getBulkhead(DEFAULT_INSTANCE);
    }

    public TimeLimiter getTimeLimiter() {
        return getTimeLimiter(DEFAULT_INSTANCE);
    }

    public ResilienceProperties getProperties() {
        return properties;
    }

    public CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }

    public BulkheadRegistry getBulkheadRegistry() {
        return bulkheadRegistry;
    }

    public TimeLimiterRegistry getTimeLimiterRegistry() {
        return timeLimiterRegistry;
    }

    private void registerListeners(CircuitBreaker cb) {
        cb.getEventPublisher()
                .onStateTransition(event -> log.info("CircuitBreaker '{}' state transition: {} -> {}",
                        cb.getName(), event.getStateTransition().getFromState(), event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> log.warn("CircuitBreaker '{}' rejected call: Circuit is {}",
                        cb.getName(), cb.getState()))
                .onError(event -> log.warn("CircuitBreaker '{}' recorded error in {}ms: {}",
                        cb.getName(), event.getElapsedDuration().toMillis(), event.getThrowable().getMessage()))
                .onSlowCallRateExceeded(event -> log.warn("CircuitBreaker '{}' slow call rate exceeded: {}%",
                        cb.getName(), event.getSlowCallRate()))
                .onFailureRateExceeded(event -> log.warn("CircuitBreaker '{}' failure rate exceeded: {}%",
                        cb.getName(), event.getFailureRate()));
    }

    private void registerListeners(Bulkhead bh) {
        bh.getEventPublisher()
                .onCallRejected(event -> log.warn("Bulkhead '{}' rejected call: concurrency limit ({}) reached",
                        bh.getName(), bh.getBulkheadConfig().getMaxConcurrentCalls()))
                .onCallPermitted(event -> log.debug("Bulkhead '{}' call permitted (available: {})",
                        bh.getName(), bh.getMetrics().getAvailableConcurrentCalls()));
    }

    private void registerListeners(TimeLimiter tl) {
        tl.getEventPublisher()
                .onTimeout(event -> log.warn("TimeLimiter '{}' operation timed out after configured duration",
                        tl.getName()))
                .onError(event -> log.warn("TimeLimiter '{}' encountered error: {}",
                        tl.getName(), event.getThrowable().getMessage()));
    }
}
