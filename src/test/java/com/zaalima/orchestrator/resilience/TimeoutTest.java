package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.resilience.config.ResilienceProperties;
import com.zaalima.orchestrator.resilience.exception.ExternalApiTimeoutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Timeout and Deadline Tests")
class TimeoutTest {

    private ResilienceProperties properties;
    private ResilienceManager resilienceManager;
    private ResilientOperationExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new ResilienceProperties();
        // Short timeout for deterministic tests
        properties.getTimeout().setOperationTimeoutMs(80L);
        properties.getTimeout().setRequestTimeoutMs(80L);
        properties.getTimeout().setConnectTimeoutMs(80L);

        resilienceManager = new ResilienceManager(properties);
        executor = new ResilientOperationExecutor(resilienceManager);
    }

    @Test
    @DisplayName("Fast operation finishes within timeout and returns result")
    void shouldSucceedWhenOperationCompletesWithinTimeout() throws Exception {
        String instance = "timeout-fast";

        String result = executor.executeWithTimeLimiter(instance, () -> {
            Thread.sleep(10);
            return "fast-response";
        });

        assertEquals("fast-response", result);
    }

    @Test
    @DisplayName("Delayed operation exceeding timeout throws ExternalApiTimeoutException")
    void shouldFailWithTimeoutExceptionWhenOperationExceedsDeadline() {
        String instance = "timeout-exceeded";

        long start = System.currentTimeMillis();

        ExternalApiTimeoutException ex = assertThrows(ExternalApiTimeoutException.class, () ->
                executor.executeWithTimeLimiter(instance, () -> {
                    Thread.sleep(500);
                    return "delayed";
                })
        );

        long elapsed = System.currentTimeMillis() - start;

        // Caller unblocks within reasonably bounded time (near 80ms, well before 500ms)
        assertTrue(elapsed < 400, "Caller must not block indefinitely; elapsed: " + elapsed + "ms");
        assertEquals(ExternalApiTimeoutException.TimeoutType.OPERATION, ex.getTimeoutType());
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("Timeout is recorded as a failure in Circuit Breaker")
    void shouldRecordTimeoutInCircuitBreaker() {
        String instance = "timeout-cb-record";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        assertEquals(0, cb.getMetrics().getNumberOfFailedCalls());

        assertThrows(ExternalApiTimeoutException.class, () ->
                executor.executeWithTimeLimiter(instance, () -> {
                    Thread.sleep(300);
                    return "slow";
                })
        );

        assertEquals(1, cb.getMetrics().getNumberOfFailedCalls(),
                "TimeLimiter timeout must be recorded as a failure in Circuit Breaker");
    }

    @Test
    @DisplayName("Thread interruption flag is propagated when operation is cancelled on timeout")
    void shouldPropagateCancellationOnTimeout() throws Exception {
        String instance = "timeout-cancel";
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);
        java.util.concurrent.CountDownLatch interruptLatch = new java.util.concurrent.CountDownLatch(1);

        assertThrows(ExternalApiTimeoutException.class, () ->
                executor.executeWithTimeLimiter(instance, () -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        wasInterrupted.set(true);
                        interruptLatch.countDown();
                        Thread.currentThread().interrupt();
                    }
                    return "cancelled";
                })
        );

        assertTrue(interruptLatch.await(3, java.util.concurrent.TimeUnit.SECONDS),
                "Underlying task should receive thread interrupt on timeout");
        assertTrue(wasInterrupted.get());
    }
}
