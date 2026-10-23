package com.zaalima.orchestrator.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis-backed distributed implementation of {@link DistributedLockManager}.
 * <p>
 * This class coordinates mutual exclusion across multiple JVM instances of the
 * Zaalima Agent Orchestrator using a centralized Redis datastore.
 * </p>
 *
 * <h3>Acquisition Semantics</h3>
 * <ul>
 *   <li>Acquisition is strictly atomic via Redis {@code SET key ownerToken NX PX leaseMillis}.</li>
 *   <li>Never uses an {@code EXISTS}-then-{@code SET} check-then-set sequence, preventing race conditions.</li>
 *   <li>Each acquisition attempt generates a cryptographically unique owner token
 *       ({@code instanceId:threadName:UUID}) to prevent cross-node or cross-thread collisions.</li>
 *   <li>Bounded retry loop with truncated exponential backoff and full jitter respects {@code acquireTimeout}.</li>
 *   <li>Fail-closed: infrastructure failures (network disconnect, Redis down) throw
 *       {@link LockAcquisitionException} rather than proceeding uncoordinated.</li>
 * </ul>
 *
 * <h3>Ownership-Safe Release Semantics</h3>
 * <ul>
 *   <li>Release is executed atomically via an evaluation Lua script:
 *     <pre>{@code
 *       if redis.call("get", KEYS[1]) == ARGV[1] then
 *           return redis.call("del", KEYS[1])
 *       else
 *           return 0
 *       end
 *     }</pre>
 *   </li>
 *   <li>Only the owner matching the stored token can release the lock. An expired lock handle cannot delete
 *       a newer owner's lock.</li>
 * </ul>
 *
 * <h3>Lease Expiration and Safety Semantics</h3>
 * <ul>
 *   <li>Every lock has a finite lease duration (TTL). If an instance crashes or is partitioned, the key
 *       expires automatically in Redis to prevent permanent deadlocks.</li>
 *   <li><strong>CRITICAL LIMITATION:</strong> In this Day 2 implementation, no automatic background renewal
 *       (watchdog) is enabled. If a task executes longer than {@code leaseDuration}, the lease will expire
 *       while work is still running. Downstream consumers must either size the lease appropriately, implement
 *       periodic validity checks via {@link DistributedLock#isValid()}, or utilize fencing tokens.</li>
 * </ul>
 */
@Component("redisDistributedLockManager")
public class RedisDistributedLockManager implements DistributedLockManager {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockManager.class);

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    @Autowired
    public RedisDistributedLockManager(StringRedisTemplate redisTemplate) {
        this(redisTemplate, "orch-node-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public RedisDistributedLockManager(StringRedisTemplate redisTemplate, String instanceId) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
    }

    @Override
    public Optional<DistributedLock> tryAcquire(String lockKey, Duration leaseDuration, Duration acquireTimeout) {
        validateInputs(lockKey, leaseDuration, acquireTimeout);

        String ownerToken = generateOwnerToken();
        Instant deadline = Instant.now().plus(acquireTimeout);
        long attempt = 0;

        while (true) {
            Instant attemptTime = Instant.now();
            boolean acquired;
            try {
                Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, ownerToken, leaseDuration);
                acquired = Boolean.TRUE.equals(result);
            } catch (DataAccessException e) {
                log.error("Redis infrastructure failure attempting to acquire lock for key: {}", lockKey, e);
                throw new LockAcquisitionException("Redis unavailable while acquiring lock for key: " + lockKey, e);
            }

            if (acquired) {
                LockToken token = new LockToken(lockKey, ownerToken, attemptTime, attemptTime.plus(leaseDuration));
                return Optional.of(new RedisDistributedLock(token));
            }

            Instant now = Instant.now();
            if (!now.isBefore(deadline)) {
                return Optional.empty();
            }

            long remainingMillis = Duration.between(now, deadline).toMillis();
            if (remainingMillis <= 0) {
                return Optional.empty();
            }

            attempt++;
            // Truncated exponential backoff with full jitter:
            // Initial backoff: 50ms, multiplier: 1.5, max backoff cap: 500ms
            long baseBackoff = Math.min(500L, (long) (50L * Math.pow(1.5, Math.min(attempt, 8))));
            long jitteredSleep = ThreadLocalRandom.current().nextLong(1, Math.max(2L, baseBackoff + 1));
            long sleepTime = Math.min(remainingMillis, jitteredSleep);

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Lock acquisition attempt interrupted for key: {}", lockKey);
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean release(DistributedLock lock) {
        if (lock == null || lock.getToken() == null) {
            return false;
        }

        LockToken token = lock.getToken();
        try {
            Long result = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    Collections.singletonList(token.lockKey()),
                    token.ownerToken()
            );

            boolean released = (result != null && result == 1L);
            if (!released) {
                log.warn("Failed to release lock for key: {}, owner: {}. Lock expired or reassigned.",
                        token.lockKey(), token.ownerToken());
            }
            return released;
        } catch (DataAccessException e) {
            log.error("Redis infrastructure failure releasing lock for key: {}, owner: {}",
                    token.lockKey(), token.ownerToken(), e);
            return false;
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        if (lockKey == null || lockKey.isBlank()) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(lockKey);
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException e) {
            log.error("Redis infrastructure failure checking lock status for key: {}", lockKey, e);
            throw new LockAcquisitionException("Redis unavailable while checking lock state for key: " + lockKey, e);
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    private void validateInputs(String lockKey, Duration leaseDuration, Duration acquireTimeout) {
        Objects.requireNonNull(lockKey, "lockKey must not be null");
        if (lockKey.isBlank()) {
            throw new IllegalArgumentException("lockKey must not be blank");
        }
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        Objects.requireNonNull(acquireTimeout, "acquireTimeout must not be null");
        if (acquireTimeout.isNegative()) {
            throw new IllegalArgumentException("acquireTimeout cannot be negative");
        }
    }

    private String generateOwnerToken() {
        return instanceId + ":" + Thread.currentThread().getName() + ":" + UUID.randomUUID();
    }

    /**
     * Internal implementation of {@link DistributedLock} bound to this Redis lock manager.
     */
    private final class RedisDistributedLock implements DistributedLock {

        private final LockToken token;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private RedisDistributedLock(LockToken token) {
            this.token = token;
        }

        @Override
        public String getLockKey() {
            return token.lockKey();
        }

        @Override
        public LockToken getToken() {
            return token;
        }

        @Override
        public boolean isValid() {
            if (closed.get() || token.isExpired()) {
                return false;
            }
            try {
                String currentOwner = redisTemplate.opsForValue().get(token.lockKey());
                return token.ownerToken().equals(currentOwner);
            } catch (DataAccessException e) {
                log.warn("Redis error checking validity of lock for key: {}", token.lockKey(), e);
                return false;
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(this);
            }
        }
    }
}
