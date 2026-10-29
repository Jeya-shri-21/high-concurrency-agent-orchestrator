package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.resilience.config.ResilienceProperties;
import com.zaalima.orchestrator.resilience.exception.BulkheadFullException;
import com.zaalima.orchestrator.resilience.exception.CircuitBreakerOpenException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiTimeoutException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiValidationException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Resilience Pipeline Integration Tests")
class ResilienceIntegrationTest {

    private ResilienceProperties properties;
    private ResilienceManager resilienceManager;
    private ResilientOperationExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new ResilienceProperties();
        properties.getBulkhead().setMaxConcurrentCalls(2);
        properties.getBulkhead().setMaxWaitDurationMs(0L);

        properties.getCircuitBreaker().setSlidingWindowSize(4);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(4);
        properties.getCircuitBreaker().setFailureRateThreshold(50.0f);

        properties.getTimeout().setOperationTimeoutMs(100L);

        resilienceManager = new ResilienceManager(properties);
        executor = new ResilientOperationExecutor(resilienceManager);
    }

    @Test
    @DisplayName("End-to-end pipeline execution succeeds under normal conditions")
    void shouldExecuteThroughFullPipelineSuccessfully() throws Exception {
        String instance = "integration-success";

        String result = executor.executeWithTimeLimiter(instance, () -> "pipeline-ok");

        assertEquals("pipeline-ok", result);
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);
        Bulkhead bh = resilienceManager.getBulkhead(instance);

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(2, bh.getMetrics().getAvailableConcurrentCalls());
        assertEquals(1, cb.getMetrics().getNumberOfSuccessfulCalls());
    }

    @Test
    @DisplayName("Bulkhead rejection does NOT increment CircuitBreaker failure count")
    void bulkheadRejectionShouldNotCountAsCircuitBreakerFailure() throws Exception {
        String instance = "integration-bulkhead-not-cb";
        Bulkhead bh = resilienceManager.getBulkhead(instance);
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        CountDownLatch activeCallsLatch = new CountDownLatch(2);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        ExecutorService testPool = Executors.newFixedThreadPool(3);

        try {
            // Fill 2 permits
            for (int i = 0; i < 2; i++) {
                testPool.submit(() -> {
                    try {
                        executor.execute(instance, () -> {
                            activeCallsLatch.countDown();
                            releaseLatch.await(5, TimeUnit.SECONDS);
                            return "running";
                        });
                    } catch (Exception ignored) {}
                });
            }

            assertTrue(activeCallsLatch.await(3, TimeUnit.SECONDS));

            // 3rd call rejected by Bulkhead
            assertThrows(BulkheadFullException.class, () ->
                    executor.execute(instance, () -> "overflow")
            );

            // Circuit Breaker must NOT have recorded a failure!
            assertEquals(0, cb.getMetrics().getNumberOfFailedCalls(),
                    "Bulkhead rejection is local capacity saturation and must not count as remote failure in CB");
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

        } finally {
            releaseLatch.countDown();
            testPool.shutdown();
            assertTrue(testPool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("Open CircuitBreaker fast-fails and releases Bulkhead permit immediately")
    void openCircuitBreakerShouldFastFailWithoutConsumingBulkheadCapacity() {
        String instance = "integration-cb-open-bh-release";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);
        Bulkhead bh = resilienceManager.getBulkhead(instance);

        cb.transitionToOpenState();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertEquals(2, bh.getMetrics().getAvailableConcurrentCalls());

        // Call should be rejected by CircuitBreaker
        assertThrows(CircuitBreakerOpenException.class, () ->
                executor.execute(instance, () -> "should-not-run")
        );

        // Bulkhead permit must remain 2 (not leaked or held)
        assertEquals(2, bh.getMetrics().getAvailableConcurrentCalls(),
                "Bulkhead permit must be completely free after fast-fail from open circuit");
    }

    @Test
    @DisplayName("Repeated timeouts trip CircuitBreaker to OPEN")
    void timeoutsShouldContributeToOpeningCircuitBreaker() {
        String instance = "integration-timeout-cb-open";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        // 2 timeouts + 2 successes out of 4 calls = 50% failure rate -> OPEN
        for (int i = 0; i < 2; i++) {
            assertThrows(ExternalApiTimeoutException.class, () ->
                    executor.executeWithTimeLimiter(instance, () -> {
                        Thread.sleep(250);
                        return "slow";
                    })
            );
        }

        for (int i = 0; i < 2; i++) {
            assertDoesNotThrow(() ->
                    executor.executeWithTimeLimiter(instance, () -> "fast")
            );
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
                "Timeouts are remote unresponsiveness and must trip the Circuit Breaker");
    }

    @Test
    @DisplayName("5xx errors trip CircuitBreaker while 4xx errors are ignored")
    void downstream5xxErrorsShouldOpenCircuitWhile4xxShouldNot() {
        String instance = "integration-5xx-vs-4xx";
        CircuitBreaker cb = resilienceManager.getCircuitBreaker(instance);

        // 4 client validation errors (4xx)
        for (int i = 0; i < 4; i++) {
            assertThrows(ExternalApiValidationException.class, () ->
                    executor.execute(instance, () -> {
                        throw new ExternalApiValidationException(400, "Bad Request");
                    })
            );
        }
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "4xx must not trip CB");

        // Now 2 server errors (5xx) and 2 successes
        for (int i = 0; i < 2; i++) {
            assertThrows(ExternalApiException.class, () ->
                    executor.execute(instance, () -> {
                        throw new ExternalApiException(500, "Server Error");
                    })
            );
        }
        for (int i = 0; i < 2; i++) {
            assertDoesNotThrow(() ->
                    executor.execute(instance, () -> "ok")
            );
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState(), "5xx server errors must trip CB");
    }
}
