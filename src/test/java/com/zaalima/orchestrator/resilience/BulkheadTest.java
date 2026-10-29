package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.resilience.config.ResilienceProperties;
import com.zaalima.orchestrator.resilience.exception.BulkheadFullException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiException;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bulkhead Concurrency Isolation Tests")
class BulkheadTest {

    private ResilienceProperties properties;
    private ResilienceManager resilienceManager;
    private ResilientOperationExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new ResilienceProperties();
        // Configure Bulkhead with limit of 3 concurrent calls and 0ms wait duration for deterministic fail-fast
        properties.getBulkhead().setMaxConcurrentCalls(3);
        properties.getBulkhead().setMaxWaitDurationMs(0L);

        resilienceManager = new ResilienceManager(properties);
        executor = new ResilientOperationExecutor(resilienceManager);
    }

    @Test
    @DisplayName("Calls within concurrency limit succeed")
    void shouldAllowCallsWithinConcurrencyLimit() throws Exception {
        String instance = "bulkhead-allow";
        Bulkhead bh = resilienceManager.getBulkhead(instance);

        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls());

        String result = executor.execute(instance, () -> "call-ok");

        assertEquals("call-ok", result);
        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls(), "Permit must be returned after execution");
    }

    @Test
    @DisplayName("Calls exceeding concurrency limit are rejected immediately with BulkheadFullException")
    void shouldRejectCallsExceedingConcurrencyLimit() throws Exception {
        String instance = "bulkhead-reject";
        Bulkhead bh = resilienceManager.getBulkhead(instance);

        CountDownLatch activeCallsLatch = new CountDownLatch(3);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        ExecutorService testPool = Executors.newFixedThreadPool(4);

        try {
            // Occupy all 3 permits
            for (int i = 0; i < 3; i++) {
                testPool.submit(() -> {
                    try {
                        executor.execute(instance, () -> {
                            activeCallsLatch.countDown();
                            releaseLatch.await(5, TimeUnit.SECONDS);
                            return "done";
                        });
                    } catch (Exception ignored) {
                    }
                });
            }

            // Wait until all 3 calls hold permits
            assertTrue(activeCallsLatch.await(3, TimeUnit.SECONDS));
            assertEquals(0, bh.getMetrics().getAvailableConcurrentCalls());

            // 4th call must be rejected immediately by Bulkhead
            BulkheadFullException ex = assertThrows(BulkheadFullException.class, () ->
                    executor.execute(instance, () -> "overflow-call")
            );

            assertTrue(ex.getMessage().contains("is saturated"));

        } finally {
            releaseLatch.countDown();
            testPool.shutdown();
            assertTrue(testPool.awaitTermination(3, TimeUnit.SECONDS));
        }

        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls(),
                "All permits must be released after concurrent tasks finish");
    }

    @Test
    @DisplayName("Bulkhead permits are cleanly released after exceptions")
    void shouldReleasePermitsAfterException() {
        String instance = "bulkhead-exception";
        Bulkhead bh = resilienceManager.getBulkhead(instance);

        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls());

        assertThrows(ExternalApiException.class, () ->
                executor.execute(instance, () -> {
                    throw new ExternalApiException(500, "Downstream failure");
                })
        );

        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls(),
                "Permit must NOT be leaked when downstream call throws an exception");
    }

    @Test
    @DisplayName("Bulkhead permits are cleanly released after operation timeout")
    void shouldReleasePermitsAfterTimeout() {
        String instance = "bulkhead-timeout";
        properties.getTimeout().setOperationTimeoutMs(50L);
        resilienceManager = new ResilienceManager(properties);
        executor = new ResilientOperationExecutor(resilienceManager);

        Bulkhead bh = resilienceManager.getBulkhead(instance);
        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls());

        assertThrows(Exception.class, () ->
                executor.executeWithTimeLimiter(instance, () -> {
                    Thread.sleep(300);
                    return "too-slow";
                })
        );

        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls(),
                "Permit must be released when a call times out");
    }

    @Test
    @DisplayName("Subsequent calls succeed once in-flight call completes")
    void shouldAllowSubsequentCallsAfterCapacityFreesUp() throws Exception {
        String instance = "bulkhead-subsequent";
        Bulkhead bh = resilienceManager.getBulkhead(instance);

        CountDownLatch firstRunningLatch = new CountDownLatch(1);
        CountDownLatch finishFirstLatch = new CountDownLatch(1);

        Future<String> firstCall = CompletableFuture.supplyAsync(() -> {
            try {
                return executor.execute(instance, () -> {
                    firstRunningLatch.countDown();
                    finishFirstLatch.await(5, TimeUnit.SECONDS);
                    return "first";
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(firstRunningLatch.await(3, TimeUnit.SECONDS));
        assertEquals(2, bh.getMetrics().getAvailableConcurrentCalls());

        // Finish first call
        finishFirstLatch.countDown();
        assertEquals("first", firstCall.get(3, TimeUnit.SECONDS));

        // Now next call should execute smoothly with 3 permits available
        assertEquals(3, bh.getMetrics().getAvailableConcurrentCalls());
        String secondResult = executor.execute(instance, () -> "second");
        assertEquals("second", secondResult);
    }
}
