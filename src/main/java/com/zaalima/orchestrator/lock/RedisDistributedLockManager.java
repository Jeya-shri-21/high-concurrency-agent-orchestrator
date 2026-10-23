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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
 * <h3>Atomic Lease Renewal & Watchdog Semantics</h3>
 * <ul>
 *   <li>Lease renewal is executed atomically via an owner-verifying evaluation Lua script:
 *     <pre>{@code
 *       if redis.call("get", KEYS[1]) == ARGV[1] then
 *           return redis.call("pexpire", KEYS[1], ARGV[2])
 *       else
 *           return 0
 *       end
 *     }</pre>
 *   </li>
 *   <li>A lock's TTL is extended only if the caller's owner token currently holds the active Redis key.
 *       Expired locks or locks reassigned to other nodes cannot be renewed.</li>
 *   <li>Optional auto-renewal (watchdog) schedules periodic renewal on a provided {@link ScheduledExecutorService}
 *       and automatically terminates when the lock is closed, ownership is lost, or renewal fails.</li>
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

    private static final RedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
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
    public boolean renew(DistributedLock lock, Duration extensionDuration) {
        if (lock == null || lock.getToken() == null) {
            return false;
        }
        Objects.requireNonNull(extensionDuration, "extensionDuration must not be null");
        if (extensionDuration.isNegative() || extensionDuration.isZero()) {
            throw new IllegalArgumentException("extensionDuration must be positive");
        }

        LockToken token = lock.getToken();
        try {
            Long result = redisTemplate.execute(
                    RENEW_SCRIPT,
                    Collections.singletonList(token.lockKey()),
                    token.ownerToken(),
                    String.valueOf(extensionDuration.toMillis())
            );

            boolean renewed = (result != null && result == 1L);
            if (!renewed) {
                log.warn("Failed to renew lock for key: {}, owner: {}. Lock expired or reassigned.",
                        token.lockKey(), token.ownerToken());
            }
            return renewed;
        } catch (DataAccessException e) {
            log.error("Redis infrastructure failure renewing lock for key: {}, owner: {}",
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

    /**
     * Attempts to acquire a lock and automatically schedule background renewal tasks.
     *
     * @param lockKey the resource identifier
     * @param leaseDuration lease duration
     * @param acquireTimeout timeout to acquire
     * @param scheduler executor for background renewal
     * @param renewalInterval interval between renewal attempts
     * @return acquired lock with auto-renewal active, or empty
     */
    public Optional<DistributedLock> tryAcquireWithAutoRenewal(
            String lockKey,
            Duration leaseDuration,
            Duration acquireTimeout,
            ScheduledExecutorService scheduler,
            Duration renewalInterval) {
        Optional<DistributedLock> lockOpt = tryAcquire(lockKey, leaseDuration, acquireTimeout);
        if (lockOpt.isPresent()) {
            DistributedLock lock = lockOpt.get();
            if (lock instanceof RedisDistributedLock redisLock) {
                redisLock.startAutoRenewal(scheduler, renewalInterval, leaseDuration);
            }
        }
        return lockOpt;
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
    public final class RedisDistributedLock implements DistributedLock {

        private volatile LockToken token;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicReference<ScheduledFuture<?>> renewalTask = new AtomicReference<>();

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
        public boolean renew(Duration extensionDuration) {
            if (closed.get()) {
                return false;
            }
            boolean renewed = RedisDistributedLockManager.this.renew(this, extensionDuration);
            if (renewed) {
                Instant now = Instant.now();
                this.token = new LockToken(
                        token.lockKey(),
                        token.ownerToken(),
                        token.acquiredAt(),
                        now.plus(extensionDuration)
                );
            }
            return renewed;
        }

        public synchronized boolean startAutoRenewal(ScheduledExecutorService scheduler, Duration interval, Duration extensionDuration) {
            Objects.requireNonNull(scheduler, "scheduler must not be null");
            Objects.requireNonNull(interval, "interval must not be null");
            Objects.requireNonNull(extensionDuration, "extensionDuration must not be null");
            if (closed.get() || !isValid()) {
                return false;
            }
            ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
                if (closed.get()) {
                    stopAutoRenewal();
                    return;
                }
                boolean renewed = renew(extensionDuration);
                if (!renewed) {
                    log.warn("Auto-renewal failed for key: {}. Stopping renewal task.", token.lockKey());
                    stopAutoRenewal();
                }
            }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);

            ScheduledFuture<?> prev = renewalTask.getAndSet(task);
            if (prev != null) {
                prev.cancel(false);
            }
            return true;
        }

        public synchronized void stopAutoRenewal() {
            ScheduledFuture<?> task = renewalTask.getAndSet(null);
            if (task != null) {
                task.cancel(false);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                stopAutoRenewal();
                release(this);
            }
        }
    }
}
