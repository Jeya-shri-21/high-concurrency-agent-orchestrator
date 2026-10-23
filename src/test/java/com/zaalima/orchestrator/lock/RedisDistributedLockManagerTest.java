package com.zaalima.orchestrator.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisDistributedLockManager Unit Tests")
class RedisDistributedLockManagerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisDistributedLockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new RedisDistributedLockManager(redisTemplate, "test-node-01");
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Null or blank lockKey throws exception")
        void testInvalidLockKey() {
            assertThrows(NullPointerException.class, () ->
                    lockManager.tryAcquire(null, Duration.ofSeconds(10), Duration.ofSeconds(1)));

            assertThrows(IllegalArgumentException.class, () ->
                    lockManager.tryAcquire("   ", Duration.ofSeconds(10), Duration.ofSeconds(1)));
        }

        @Test
        @DisplayName("Non-positive leaseDuration throws exception")
        void testInvalidLeaseDuration() {
            assertThrows(NullPointerException.class, () ->
                    lockManager.tryAcquire("lock:task:1", null, Duration.ofSeconds(1)));

            assertThrows(IllegalArgumentException.class, () ->
                    lockManager.tryAcquire("lock:task:1", Duration.ZERO, Duration.ofSeconds(1)));

            assertThrows(IllegalArgumentException.class, () ->
                    lockManager.tryAcquire("lock:task:1", Duration.ofSeconds(-5), Duration.ofSeconds(1)));
        }

        @Test
        @DisplayName("Negative acquireTimeout throws exception")
        void testInvalidAcquireTimeout() {
            assertThrows(NullPointerException.class, () ->
                    lockManager.tryAcquire("lock:task:1", Duration.ofSeconds(10), null));

            assertThrows(IllegalArgumentException.class, () ->
                    lockManager.tryAcquire("lock:task:1", Duration.ofSeconds(10), Duration.ofSeconds(-1)));
        }
    }

    @Nested
    @DisplayName("Acquisition Semantics Tests")
    class AcquisitionTests {

        @Test
        @DisplayName("Successful atomic acquisition returns valid DistributedLock handle")
        void testSuccessfulAcquisition() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.TRUE);

            Optional<DistributedLock> lockOpt = lockManager.tryAcquire("lock:task:100", Duration.ofSeconds(10), Duration.ofSeconds(1));

            assertTrue(lockOpt.isPresent(), "Lock should be acquired successfully");
            DistributedLock lock = lockOpt.get();
            assertEquals("lock:task:100", lock.getLockKey());
            assertNotNull(lock.getToken());
            assertTrue(lock.getToken().ownerToken().startsWith("test-node-01:"), "Token should contain instanceId prefix");
            assertFalse(lock.getToken().isExpired());

            verify(valueOperations, times(1)).setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10)));
        }

        @Test
        @DisplayName("Immediate failure when lock is held and acquireTimeout is zero")
        void testImmediateFailureWhenHeld() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.FALSE);

            Optional<DistributedLock> lockOpt = lockManager.tryAcquire("lock:task:100", Duration.ofSeconds(10), Duration.ZERO);

            assertTrue(lockOpt.isEmpty(), "Should fail immediately without retrying");
            verify(valueOperations, times(1)).setIfAbsent(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Retry succeeds before timeout expires")
        void testRetrySucceedsBeforeTimeout() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.FALSE)
                    .thenReturn(Boolean.TRUE);

            Optional<DistributedLock> lockOpt = lockManager.tryAcquire("lock:task:100", Duration.ofSeconds(10), Duration.ofMillis(400));

            assertTrue(lockOpt.isPresent(), "Lock should eventually be acquired upon retry");
            verify(valueOperations, times(2)).setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10)));
        }

        @Test
        @DisplayName("Acquisition times out after bounded retries")
        void testAcquisitionTimeout() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.FALSE);

            Optional<DistributedLock> lockOpt = lockManager.tryAcquire("lock:task:100", Duration.ofSeconds(10), Duration.ofMillis(120));

            assertTrue(lockOpt.isEmpty(), "Lock acquisition should time out and return empty");
            verify(valueOperations, atLeast(2)).setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10)));
        }

        @Test
        @DisplayName("Preserves interruption status when thread is interrupted during retry backoff")
        void testInterruptionHandling() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.FALSE);

            Thread.currentThread().interrupt();

            Optional<DistributedLock> lockOpt = lockManager.tryAcquire("lock:task:100", Duration.ofSeconds(10), Duration.ofSeconds(5));

            assertTrue(lockOpt.isEmpty());
            assertTrue(Thread.currentThread().isInterrupted(), "Thread interrupt status must be preserved");
            // Clear interrupt status for subsequent tests
            Thread.interrupted();
        }

        @Test
        @DisplayName("Fail-closed on Redis infrastructure exception during acquisition")
        void testFailClosedOnRedisException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:100"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenThrow(new QueryTimeoutException("Redis connection timed out"));

            LockAcquisitionException ex = assertThrows(LockAcquisitionException.class, () ->
                    lockManager.tryAcquire("lock:task:100", Duration.ofSeconds(10), Duration.ofSeconds(1)));

            assertTrue(ex.getMessage().contains("Redis unavailable while acquiring lock"));
            assertInstanceOf(QueryTimeoutException.class, ex.getCause());
        }
    }

    @Nested
    @DisplayName("Release Semantics Tests")
    class ReleaseTests {

        @Test
        @DisplayName("Atomic release via Lua script succeeds when owner token matches")
        @SuppressWarnings("unchecked")
        void testSuccessfulRelease() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:200"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.TRUE);

            DistributedLock lock = lockManager.tryAcquire("lock:task:200", Duration.ofSeconds(10), Duration.ZERO).orElseThrow();

            when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("lock:task:200")), eq(lock.getToken().ownerToken())))
                    .thenReturn(1L);

            boolean released = lockManager.release(lock);

            assertTrue(released, "Release should succeed when Lua script returns 1");
        }

        @Test
        @DisplayName("Release returns false when key is already expired or held by another owner")
        @SuppressWarnings("unchecked")
        void testReleaseFailsWhenOwnerMismatch() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:200"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.TRUE);

            DistributedLock lock = lockManager.tryAcquire("lock:task:200", Duration.ofSeconds(10), Duration.ZERO).orElseThrow();

            when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("lock:task:200")), eq(lock.getToken().ownerToken())))
                    .thenReturn(0L);

            boolean released = lockManager.release(lock);

            assertFalse(released, "Release must return false when Lua script returns 0 (expired or wrong owner)");
        }

        @Test
        @DisplayName("Release handles Redis infrastructure error safely and returns false")
        @SuppressWarnings("unchecked")
        void testReleaseHandlesRedisError() {
            LockToken token = new LockToken("lock:task:200", "test-token", Instant.now(), Instant.now().plusSeconds(10));
            DistributedLock mockLock = mock(DistributedLock.class);
            when(mockLock.getToken()).thenReturn(token);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenThrow(new RedisSystemException("Connection dropped", new RuntimeException()));

            boolean released = lockManager.release(mockLock);

            assertFalse(released, "Release should return false when Redis infrastructure fails");
        }

        @Test
        @DisplayName("Release with null lock or null token returns false")
        void testReleaseNull() {
            assertFalse(lockManager.release(null));

            DistributedLock lockWithNullToken = mock(DistributedLock.class);
            when(lockWithNullToken.getToken()).thenReturn(null);
            assertFalse(lockManager.release(lockWithNullToken));
        }

        @Test
        @DisplayName("Lock handle close() invokes release idempotently")
        @SuppressWarnings("unchecked")
        void testLockHandleCloseIdempotent() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:task:300"), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.TRUE);

            DistributedLock lock = lockManager.tryAcquire("lock:task:300", Duration.ofSeconds(10), Duration.ZERO).orElseThrow();

            when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("lock:task:300")), eq(lock.getToken().ownerToken())))
                    .thenReturn(1L);

            lock.close();
            lock.close(); // Second close should be no-op

            verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyString());
        }
    }

    @Nested
    @DisplayName("isLocked Tests")
    class IsLockedTests {

        @Test
        @DisplayName("isLocked returns true when Redis key exists")
        void testIsLockedTrue() {
            when(redisTemplate.hasKey("lock:task:400")).thenReturn(Boolean.TRUE);

            assertTrue(lockManager.isLocked("lock:task:400"));
        }

        @Test
        @DisplayName("isLocked returns false when Redis key does not exist")
        void testIsLockedFalse() {
            when(redisTemplate.hasKey("lock:task:400")).thenReturn(Boolean.FALSE);

            assertFalse(lockManager.isLocked("lock:task:400"));
        }

        @Test
        @DisplayName("isLocked returns false for null or blank key")
        void testIsLockedNullOrBlank() {
            assertFalse(lockManager.isLocked(null));
            assertFalse(lockManager.isLocked("   "));
        }

        @Test
        @DisplayName("isLocked throws LockAcquisitionException on Redis failure (fail-closed)")
        void testIsLockedThrowsOnRedisError() {
            when(redisTemplate.hasKey("lock:task:400"))
                    .thenThrow(new QueryTimeoutException("Timeout"));

            assertThrows(LockAcquisitionException.class, () -> lockManager.isLocked("lock:task:400"));
        }
    }
}
