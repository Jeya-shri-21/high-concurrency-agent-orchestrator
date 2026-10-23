package com.zaalima.orchestrator.lock;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link RedisDistributedLockManager} against a real Redis server
 * running in a Testcontainers environment (redis:7-alpine).
 * <p>
 * If Docker is unavailable in the execution environment, these tests are cleanly skipped
 * using JUnit 5 assumptions.
 */
@DisplayName("RedisDistributedLockManager Integration Tests")
@TestMethodOrder(MethodOrderer.MethodName.class)
class RedisDistributedLockManagerIntegrationTest {

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static boolean dockerAvailable = false;

    private RedisDistributedLockManager manager1;
    private RedisDistributedLockManager manager2;

    @BeforeAll
    static void setUpAll() {
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }

        if (dockerAvailable) {
            redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redisContainer.start();

            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(
                    redisContainer.getHost(),
                    redisContainer.getMappedPort(6379)
            );
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(3))
                    .build();
            connectionFactory = new LettuceConnectionFactory(redisConfig, clientConfig);
            connectionFactory.afterPropertiesSet();

            redisTemplate = new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null && redisContainer.isRunning()) {
            redisContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(dockerAvailable, "Docker not available");
        manager1 = new RedisDistributedLockManager(redisTemplate, "orch-node-1");
        manager2 = new RedisDistributedLockManager(redisTemplate, "orch-node-2");
    }

    @Test
    @DisplayName("1. Successful acquisition of an unlocked key")
    void test1_SuccessfulAcquisitionOfUnlockedKey() {
        String key = "lock:task:test-1";
        Optional<DistributedLock> lockOpt = manager1.tryAcquire(key, Duration.ofSeconds(10), Duration.ofSeconds(1));

        assertTrue(lockOpt.isPresent(), "Lock should be acquired successfully");
        DistributedLock lock = lockOpt.get();
        assertEquals(key, lock.getLockKey());
        assertTrue(lock.isValid(), "Acquired lock should be valid");
        assertTrue(manager1.isLocked(key), "Redis should report key is locked");

        lock.close();
        assertFalse(manager1.isLocked(key), "Redis key should be cleared after close");
        assertFalse(lock.isValid(), "Lock handle should be invalid after close");
    }

    @Test
    @DisplayName("2. A second manager/instance cannot acquire the same key while its lease is active")
    void test2_SecondManagerCannotAcquireWhileLeaseActive() {
        String key = "lock:task:test-2";
        Optional<DistributedLock> lockOpt1 = manager1.tryAcquire(key, Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertTrue(lockOpt1.isPresent());

        // Manager 2 attempts to acquire the exact same key with zero timeout
        Optional<DistributedLock> lockOpt2 = manager2.tryAcquire(key, Duration.ofSeconds(10), Duration.ZERO);
        assertTrue(lockOpt2.isEmpty(), "Second manager should not acquire while lease is held by first manager");

        lockOpt1.get().close();

        // Manager 2 can now acquire the key
        Optional<DistributedLock> lockOpt2After = manager2.tryAcquire(key, Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertTrue(lockOpt2After.isPresent(), "Manager 2 should acquire once Manager 1 releases");
        lockOpt2After.get().close();
    }

    @Test
    @DisplayName("3. A different key can be acquired independently")
    void test3_DifferentKeyCanBeAcquiredIndependently() {
        String keyA = "lock:task:test-3a";
        String keyB = "lock:task:test-3b";

        Optional<DistributedLock> lockA = manager1.tryAcquire(keyA, Duration.ofSeconds(10), Duration.ofSeconds(1));
        Optional<DistributedLock> lockB = manager2.tryAcquire(keyB, Duration.ofSeconds(10), Duration.ofSeconds(1));

        assertTrue(lockA.isPresent());
        assertTrue(lockB.isPresent());
        assertTrue(manager1.isLocked(keyA));
        assertTrue(manager2.isLocked(keyB));

        lockA.get().close();
        assertFalse(manager1.isLocked(keyA));
        assertTrue(manager2.isLocked(keyB), "keyB should remain locked after keyA is closed");

        lockB.get().close();
        assertFalse(manager2.isLocked(keyB));
    }

    @Test
    @DisplayName("4. Only the correct owner can release a lock")
    void test4_OnlyCorrectOwnerCanReleaseLock() {
        String key = "lock:task:test-4";
        Optional<DistributedLock> lockOpt1 = manager1.tryAcquire(key, Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertTrue(lockOpt1.isPresent());

        // Create a counterfeit lock handle with a different owner token
        LockToken fakeToken = new LockToken(key, "orch-node-2:fake-thread:fake-uuid", Instant.now(), Instant.now().plusSeconds(10));
        DistributedLock spoofedLock = new DistributedLock() {
            @Override
            public String getLockKey() { return key; }
            @Override
            public LockToken getToken() { return fakeToken; }
            @Override
            public boolean isValid() { return false; }
            @Override
            public void close() { manager2.release(this); }
        };

        boolean spoofedRelease = manager2.release(spoofedLock);
        assertFalse(spoofedRelease, "Releasing with a non-matching owner token must return false");
        assertTrue(manager1.isLocked(key), "Lock must remain active in Redis despite counterfeit release attempt");

        // Legitimate owner releases
        boolean realRelease = manager1.release(lockOpt1.get());
        assertTrue(realRelease, "Legitimate owner release must succeed");
        assertFalse(manager1.isLocked(key));
    }

    @Test
    @DisplayName("5. An expired owner cannot release a newer owner's lock")
    void test5_ExpiredOwnerCannotReleaseNewerOwnersLock() throws InterruptedException {
        String key = "lock:task:test-5";

        // Manager 1 acquires lock with short 250ms lease
        Optional<DistributedLock> m1LockOpt = manager1.tryAcquire(key, Duration.ofMillis(250), Duration.ofSeconds(1));
        assertTrue(m1LockOpt.isPresent());
        DistributedLock m1Lock = m1LockOpt.get();

        // Wait 350ms for Manager 1's lease to expire in Redis
        Thread.sleep(350);

        // Manager 2 acquires the now-expired key
        Optional<DistributedLock> m2LockOpt = manager2.tryAcquire(key, Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertTrue(m2LockOpt.isPresent(), "Manager 2 must be able to acquire after Manager 1's lease expired");
        DistributedLock m2Lock = m2LockOpt.get();

        // Stale Manager 1 now attempts to release its expired lock
        boolean m1ReleaseResult = manager1.release(m1Lock);
        assertFalse(m1ReleaseResult, "Stale owner must not succeed in releasing a reassigned lock");

        // Ensure Manager 2 still holds the lock in Redis!
        assertTrue(manager2.isLocked(key), "Redis lock must still be active for Manager 2");
        assertTrue(m2Lock.isValid(), "Manager 2's lock handle must still be valid");

        // Manager 2 cleans up cleanly
        m2Lock.close();
        assertFalse(manager2.isLocked(key));
    }

    @Test
    @DisplayName("6. Lease expiration allows a subsequent acquisition")
    void test6_LeaseExpirationAllowsSubsequentAcquisition() throws InterruptedException {
        String key = "lock:task:test-6";

        Optional<DistributedLock> m1LockOpt = manager1.tryAcquire(key, Duration.ofMillis(300), Duration.ofSeconds(1));
        assertTrue(m1LockOpt.isPresent());

        // Do not release. Wait for TTL to expire in Redis
        Thread.sleep(450);

        Optional<DistributedLock> m2LockOpt = manager2.tryAcquire(key, Duration.ofSeconds(5), Duration.ofSeconds(1));
        assertTrue(m2LockOpt.isPresent(), "Manager 2 must successfully acquire lock after previous lease expired");

        m2LockOpt.get().close();
    }

    @Test
    @DisplayName("7. Acquisition times out when another owner holds the lock")
    void test7_AcquisitionTimesOutWhenAnotherOwnerHoldsLock() {
        String key = "lock:task:test-7";

        Optional<DistributedLock> m1LockOpt = manager1.tryAcquire(key, Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertTrue(m1LockOpt.isPresent());

        long start = System.currentTimeMillis();
        Optional<DistributedLock> m2LockOpt = manager2.tryAcquire(key, Duration.ofSeconds(10), Duration.ofMillis(300));
        long duration = System.currentTimeMillis() - start;

        assertTrue(m2LockOpt.isEmpty(), "Acquisition should time out when key is continuously held");
        assertTrue(duration >= 250, "Should wait approximately the acquireTimeout duration");

        m1LockOpt.get().close();
    }

    @Test
    @DisplayName("8. Redis connection failure follows fail-closed semantics")
    void test8_RedisConnectionFailureFollowsFailClosedSemantics() {
        // Connect to a closed port on localhost where no Redis server exists
        RedisStandaloneConfiguration badConfig = new RedisStandaloneConfiguration("127.0.0.1", 61999);
        LettuceClientConfiguration badClientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .build();
        LettuceConnectionFactory badFactory = new LettuceConnectionFactory(badConfig, badClientConfig);
        badFactory.afterPropertiesSet();

        StringRedisTemplate badTemplate = new StringRedisTemplate(badFactory);
        badTemplate.afterPropertiesSet();

        RedisDistributedLockManager badManager = new RedisDistributedLockManager(badTemplate, "failing-node");

        try {
            assertThrows(LockAcquisitionException.class, () ->
                    badManager.tryAcquire("lock:task:test-8", Duration.ofSeconds(5), Duration.ofMillis(100)));
        } finally {
            badFactory.destroy();
        }
    }

    @Test
    @DisplayName("9. Concurrent contenders across separate manager instances cannot simultaneously acquire the same key")
    void test9_ConcurrentContendersAcrossSeparateManagersCannotSimultaneouslyAcquire() throws Exception {
        String key = "lock:task:contention-test";
        int nodesCount = 4;
        int threadsPerNode = 5;
        int totalThreads = nodesCount * threadsPerNode;

        List<RedisDistributedLockManager> managers = new ArrayList<>();
        for (int i = 0; i < nodesCount; i++) {
            managers.add(new RedisDistributedLockManager(redisTemplate, "cluster-node-" + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CyclicBarrier barrier = new CyclicBarrier(totalThreads);
        AtomicInteger activeHolders = new AtomicInteger(0);
        AtomicInteger maxConcurrentHolders = new AtomicInteger(0);
        AtomicInteger successfulAcquisitions = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            final RedisDistributedLockManager nodeManager = managers.get(i % nodesCount);
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);

                    Optional<DistributedLock> lockOpt = nodeManager.tryAcquire(
                            key,
                            Duration.ofSeconds(2),
                            Duration.ofMillis(800)
                    );

                    if (lockOpt.isPresent()) {
                        try (DistributedLock lock = lockOpt.get()) {
                            int current = activeHolders.incrementAndGet();
                            maxConcurrentHolders.accumulateAndGet(current, Math::max);
                            successfulAcquisitions.incrementAndGet();

                            // Simulate critical section work
                            Thread.sleep(40);

                            activeHolders.decrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, maxConcurrentHolders.get(),
                "At no point should more than 1 contender hold the distributed lock simultaneously!");
        assertTrue(successfulAcquisitions.get() >= 1,
                "At least one contender should have successfully acquired the lock");
    }
}
