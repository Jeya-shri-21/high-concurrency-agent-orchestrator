package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.resilience.config.ResilienceProperties;
import com.zaalima.orchestrator.resilience.exception.CircuitBreakerOpenException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiValidationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Circuit Breaker Tests")
class CircuitBreakerTest {

    private ResilienceProperties properties;
    private ResilienceManager resilienceManager;
    private ResilientOperationExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new ResilienceProperties();
        // Short window size and thresholds for deterministic testing
        properties.getCircuitBreaker().setSlidingWindowSize(4);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(4);
        properties.getCircuitBreaker().setFailureRateThreshold(50.0f);
        properties.getCircuitBreaker().setPermittedNumberOfCallsInHalfOpenState(2);
        properties.getCircuitBreaker().setWaitDurationInOpenStateMs(50L);

        resilienceManager = new ResilienceManager(properties);
        executor = new ResilientOperationExecutor(resilienceManager);
    }

    @Test
    @DisplayName("Calls succeed when circuit is CLOSED")
    void shouldAllowCallsWhenCircuitIsClosed() throws Exception {
        CircuitBreaker cb = resilienceManager.getCircuitBreaker("test-cb-1");
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

        String result = executor.execute("test-cb-1", () -> "success-response");

        assertEquals("success-response", result);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(1, cb.getMetrics().getNumberOfSuccessfulCalls());
    }

    @Test
    @DisplayName("Repeated downstream failures trip circuit to OPEN")
    void shouldOpenCircuitWhenFailureRateExceedsThreshold() {
        String instance = "test-cb-open";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        // 2 failures and 2 successes out of 4 = 50% failure rate (meets 50% threshold)
        for (int i = 0; i < 2; i++) {
            assertThrows(ExternalApiException.class, () ->
                    executor.execute(instance, () -> {
                        throw new ExternalApiException(500, "Remote service 500 error");
                    })
            );
        }

        for (int i = 0; i < 2; i++) {
            assertDoesNotThrow(() ->
                    executor.execute(instance, () -> "ok")
            );
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertEquals(50.0f, cb.getMetrics().getFailureRate());
    }

    @Test
    @DisplayName("Calls are rejected fast with CircuitBreakerOpenException while OPEN without invoking downstream")
    void shouldRejectCallsQuicklyWhenCircuitIsOpen() {
        String instance = "test-cb-fast-reject";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        // Transition to OPEN
        cb.transitionToOpenState();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        AtomicInteger downstreamInvocations = new AtomicInteger(0);

        CircuitBreakerOpenException ex = assertThrows(CircuitBreakerOpenException.class, () ->
                executor.execute(instance, () -> {
                    downstreamInvocations.incrementAndGet();
                    return "should-never-be-reached";
                })
        );

        assertTrue(ex.getMessage().contains("is OPEN"));
        assertEquals(0, downstreamInvocations.get(), "Downstream service must NOT be called when circuit is OPEN");
        assertEquals(1, cb.getMetrics().getNumberOfNotPermittedCalls());
    }

    @Test
    @DisplayName("Circuit transitions to HALF_OPEN and permits configured recovery calls")
    void shouldPermitConfiguredRecoveryCallsInHalfOpen() {
        String instance = "test-cb-half-open";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

        AtomicInteger recoveryCalls = new AtomicInteger(0);

        // Permitted calls in half-open is configured to 2
        for (int i = 0; i < 2; i++) {
            assertDoesNotThrow(() ->
                    executor.execute(instance, () -> {
                        recoveryCalls.incrementAndGet();
                        return "recovery-ok";
                    })
            );
        }

        assertEquals(2, recoveryCalls.get());
    }

    @Test
    @DisplayName("Successful recovery in HALF_OPEN resets circuit to CLOSED")
    void shouldCloseCircuitAfterSuccessfulRecoveryInHalfOpen() {
        String instance = "test-cb-recovery-success";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();

        // 2 successful calls in half open
        for (int i = 0; i < 2; i++) {
            assertDoesNotThrow(() ->
                    executor.execute(instance, () -> "recovered")
            );
        }

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
                "Circuit breaker should return to CLOSED after successful recovery calls");
    }

    @Test
    @DisplayName("Failed recovery in HALF_OPEN transitions circuit back to OPEN")
    void shouldReopenCircuitIfRecoveryCallsFailInHalfOpen() {
        String instance = "test-cb-recovery-fail";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();

        // 2 failing calls in half open
        for (int i = 0; i < 2; i++) {
            assertThrows(ExternalApiException.class, () ->
                    executor.execute(instance, () -> {
                        throw new ExternalApiException(503, "Downstream still unavailable");
                    })
            );
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
                "Circuit breaker should re-open when recovery attempts fail");
    }

    @Test
    @DisplayName("Ignored exceptions (validation / 4xx) do not count as failures")
    void shouldIgnoreValidationExceptionsWithoutAffectingFailureRate() {
        String instance = "test-cb-ignored-exceptions";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        // 4 validation exceptions
        for (int i = 0; i < 4; i++) {
            assertThrows(ExternalApiValidationException.class, () ->
                    executor.execute(instance, () -> {
                        throw new ExternalApiValidationException(400, "Bad Request: invalid payload");
                    })
            );
        }

        // Circuit should remain CLOSED because 4xx are client errors, not remote failures
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getMetrics().getNumberOfFailedCalls());
    }

    @Test
    @DisplayName("Slow calls exceeding duration threshold are recorded towards slow call rate")
    void shouldRecordSlowCallsTowardThreshold() {
        String instance = "test-cb-slow-calls";
        properties.getCircuitBreaker().setSlowCallDurationThresholdMs(50L);
        properties.getCircuitBreaker().setSlowCallRateThreshold(50.0f);
        resilienceManager = new ResilienceManager(properties);
        executor = new ResilientOperationExecutor(resilienceManager);

        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        // 2 slow calls (100ms > 50ms) and 2 fast calls
        for (int i = 0; i < 2; i++) {
            assertDoesNotThrow(() ->
                    executor.execute(instance, () -> {
                        Thread.sleep(80);
                        return "slow";
                    })
            );
        }
        for (int i = 0; i < 2; i++) {
            assertDoesNotThrow(() ->
                    executor.execute(instance, () -> "fast")
            );
        }

        assertEquals(2, cb.getMetrics().getNumberOfSlowCalls());
        assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
                "Circuit should open when slow call rate threshold is breached");
    }
}
