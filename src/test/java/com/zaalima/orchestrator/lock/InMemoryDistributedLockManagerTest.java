package com.zaalima.orchestrator.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryDistributedLockManager Tests")
class InMemoryDistributedLockManagerTest {

    private InMemoryDistributedLockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new InMemoryDistributedLockManager("test-node-1");
    }

    @Test
    @DisplayName("Should successfully acquire and release lock")
    void testAcquireAndRelease() {
        String key = "lock:task:100";
        assertFalse(lockManager.isLocked(key));

        Optional<DistributedLock> lockOpt = lockManager.tryAcquire(key, Duration.ofSeconds(5), Duration.ZERO);
        assertTrue(lockOpt.isPresent());
        DistributedLock lock = lockOpt.get();

        assertEquals(key, lock.getLockKey());
        assertNotNull(lock.getToken().ownerToken());
        assertTrue(lock.isValid());
        assertTrue(lockManager.isLocked(key));

        boolean released = lockManager.release(lock);
        assertTrue(released);
        assertFalse(lock.isValid());
        assertFalse(lockManager.isLocked(key));
    }

    @Test
    @DisplayName("Should release lock automatically with try-with-resources")
    void testAutoCloseableRelease() {
        String key = "lock:task:auto-close";

        try (DistributedLock lock = lockManager.tryAcquire(key, Duration.ofSeconds(10), Duration.ZERO).orElseThrow()) {
            assertTrue(lockManager.isLocked(key));
            assertTrue(lock.isValid());
        }

        assertFalse(lockManager.isLocked(key));
    }

    @Test
    @DisplayName("Should fail fast when lock is already held and acquireTimeout is ZERO")
    void testAcquireConflictNoWait() {
        String key = "lock:task:conflict";

        Optional<DistributedLock> firstLock = lockManager.tryAcquire(key, Duration.ofSeconds(10), Duration.ZERO);
        assertTrue(firstLock.isPresent());

        Optional<DistributedLock> secondLock = lockManager.tryAcquire(key, Duration.ofSeconds(10), Duration.ZERO);
        assertTrue(secondLock.isEmpty());

        firstLock.get().close();
        assertFalse(lockManager.isLocked(key));
    }

    @Test
    @DisplayName("Should wait and acquire lock once previous holder releases before timeout")
    void testWaitAndAcquireSuccess() throws Exception {
        String key = "lock:task:wait-acquire";

        DistributedLock firstLock = lockManager.tryAcquire(key, Duration.ofSeconds(10), Duration.ZERO).orElseThrow();

        // Release first lock after 100ms in background virtual thread
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            firstLock.close();
        }, Executors.newVirtualThreadPerTaskExecutor());

        Optional<DistributedLock> secondLock = lockManager.tryAcquire(key, Duration.ofSeconds(10), Duration.ofMillis(800));
        assertTrue(secondLock.isPresent());
        secondLock.get().close();
    }

    @Test
    @DisplayName("Should time out when lock is held longer than acquireTimeout")
    void testAcquireTimeoutFailure() {
        String key = "lock:task:timeout-fail";

        DistributedLock firstLock = lockManager.tryAcquire(key, Duration.ofSeconds(10), Duration.ZERO).orElseThrow();

        long startTime = System.currentTimeMillis();
        Optional<DistributedLock> secondLock = lockManager.tryAcquire(key, Duration.ofSeconds(10), Duration.ofMillis(150));
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(secondLock.isEmpty());
        assertTrue(duration >= 140, "Should have waited for close to the requested timeout duration");
        firstLock.close();
    }

    @Test
    @DisplayName("Ownership safety: Expired lock holder cannot delete another owner's lock")
    void testOwnershipSafeRelease() throws Exception {
        String key = "lock:task:owner-safety";

        // Acquire with very short lease (50 ms)
        DistributedLock expiredLock = lockManager.tryAcquire(key, Duration.ofMillis(50), Duration.ZERO).orElseThrow();

        // Wait for lease to expire
        Thread.sleep(80);

        // Second owner acquires the lock on the now-expired key
        DistributedLock newOwnerLock = lockManager.tryAcquire(key, Duration.ofSeconds(5), Duration.ZERO).orElseThrow();
        assertNotEquals(expiredLock.getToken().ownerToken(), newOwnerLock.getToken().ownerToken());

        // First owner attempts to release after lease expiration
        boolean releasedByOldOwner = lockManager.release(expiredLock);
        assertFalse(releasedByOldOwner, "Old owner release must return false");

        // Verify that the new owner's lock is STILL actively held and valid
        assertTrue(newOwnerLock.isValid(), "New owner's lock must remain valid and intact");
        assertTrue(lockManager.isLocked(key));

        // Clean up new owner lock
        newOwnerLock.close();
        assertFalse(lockManager.isLocked(key));
    }

    @Test
    @DisplayName("Should reject invalid input arguments")
    void testInputValidation() {
        assertThrows(NullPointerException.class, () -> lockManager.tryAcquire(null, Duration.ofSeconds(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> lockManager.tryAcquire("  ", Duration.ofSeconds(1), Duration.ZERO));
        assertThrows(NullPointerException.class, () -> lockManager.tryAcquire("key", null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> lockManager.tryAcquire("key", Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> lockManager.tryAcquire("key", Duration.ofSeconds(-1), Duration.ZERO));
        assertThrows(NullPointerException.class, () -> lockManager.tryAcquire("key", Duration.ofSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> lockManager.tryAcquire("key", Duration.ofSeconds(1), Duration.ofMillis(-5)));

        assertFalse(lockManager.release(null));
        assertFalse(lockManager.isLocked(null));
    }

    @Test
    @DisplayName("Concurrency: Multiple virtual threads contending for same lock maintain mutual exclusion")
    void testConcurrentMutualExclusion() throws Exception {
        String key = "lock:task:concurrent-test";
        int threadCount = 40;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger activeHolders = new AtomicInteger(0);
        AtomicInteger maxSimultaneousHolders = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicBoolean raceDetected = new AtomicBoolean(false);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        // Contend for the lock with 1500ms timeout and 200ms lease
                        Optional<DistributedLock> lockOpt = lockManager.tryAcquire(
                                key,
                                Duration.ofMillis(200),
                                Duration.ofMillis(1500)
                        );

                        if (lockOpt.isPresent()) {
                            try (DistributedLock lock = lockOpt.get()) {
                                int current = activeHolders.incrementAndGet();
                                maxSimultaneousHolders.updateAndGet(max -> Math.max(max, current));
                                if (current > 1) {
                                    raceDetected.set(true);
                                }
                                // Simulate small critical section
                                Thread.sleep(15);
                                successCount.incrementAndGet();
                            } finally {
                                activeHolders.decrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completedInTime = doneLatch.await(10, TimeUnit.SECONDS);

            assertTrue(completedInTime, "All virtual threads should complete within deadline");
            assertFalse(raceDetected.get(), "Mutual exclusion violation detected! More than 1 thread held lock");
            assertEquals(1, maxSimultaneousHolders.get(), "Maximum simultaneous lock holders must strictly be 1");
            assertTrue(successCount.get() > 0, "At least several threads must successfully acquire and execute");
            assertFalse(lockManager.isLocked(key), "Lock must be released after all threads finish");
        }
    }
}
