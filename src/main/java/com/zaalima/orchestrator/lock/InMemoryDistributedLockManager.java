package com.zaalima.orchestrator.lock;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory reference implementation of {@link DistributedLockManager}.
 * <p>
 * <strong>IMPORTANT ARCHITECTURAL NOTICE:</strong>
 * This class serves strictly as a <em>local test and development foundation</em> within a single JVM process.
 * It is <strong>NOT</strong> a distributed lock and does not coordinate state across multiple JVM instances.
 * Distributed production mutual exclusion requires the Redis-backed implementation designed in
 * {@code docs/week2-distributed-lock-design.md}.
 * <p>
 * This implementation avoids unsafe check-then-set race conditions by using atomic
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} and
 * {@link ConcurrentHashMap#computeIfPresent(Object, java.util.function.BiFunction)} operations,
 * mirroring the atomic semantics of Redis {@code SET NX PX} and the ownership-verifying Lua release script.
 */
@Component("inMemoryDistributedLockManager")
@Primary
public class InMemoryDistributedLockManager implements DistributedLockManager {

    private final String instanceId;
    private final Map<String, LockToken> locks = new ConcurrentHashMap<>();

    public InMemoryDistributedLockManager() {
        this("local-node-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public InMemoryDistributedLockManager(String instanceId) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
    }

    @Override
    public Optional<DistributedLock> tryAcquire(String lockKey, Duration leaseDuration, Duration acquireTimeout) {
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

        String ownerToken = generateOwnerToken();
        Instant deadline = Instant.now().plus(acquireTimeout);
        long attempt = 0;

        while (true) {
            LockToken acquiredToken = tryAcquireOnce(lockKey, ownerToken, leaseDuration);
            if (acquiredToken != null) {
                return Optional.of(new LocalDistributedLock(acquiredToken));
            }

            Instant now = Instant.now();
            if (!now.isBefore(deadline)) {
                return Optional.empty();
            }

            long remainingMillis = Duration.between(now, deadline).toMillis();
            if (remainingMillis <= 0) {
                return Optional.empty();
            }

            // Exponential backoff with jitter: base 10ms, cap 100ms for test responsiveness
            attempt++;
            long baseBackoff = Math.min(100, 10 * (1L << Math.min(attempt, 4)));
            long jitteredBackoff = ThreadLocalRandom.current().nextLong(1, baseBackoff + 1);
            long sleepTime = Math.min(remainingMillis, jitteredBackoff);

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    /**
     * Atomically attempts to acquire the lock key once.
     * Guaranteed atomic via {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}.
     */
    private LockToken tryAcquireOnce(String lockKey, String ownerToken, Duration leaseDuration) {
        AtomicReference<LockToken> result = new AtomicReference<>();

        locks.compute(lockKey, (key, existingToken) -> {
            Instant now = Instant.now();
            if (existingToken == null || existingToken.isExpired()) {
                LockToken newToken = new LockToken(lockKey, ownerToken, now, now.plus(leaseDuration));
                result.set(newToken);
                return newToken;
            }
            // Key already held and unexpired; keep existing
            return existingToken;
        });

        return result.get();
    }

    @Override
    public boolean release(DistributedLock lock) {
        if (lock == null || lock.getToken() == null) {
            return false;
        }

        LockToken tokenToRelease = lock.getToken();
        AtomicBoolean released = new AtomicBoolean(false);

        // Atomically release only if current key's owner matches this lock's owner
        // Mirrors the Redis Lua script: if get(k) == owner then del(k) else 0 end
        locks.computeIfPresent(tokenToRelease.lockKey(), (key, existingToken) -> {
            if (existingToken.ownerToken().equals(tokenToRelease.ownerToken())) {
                released.set(true);
                return null; // Deletes key atomically
            }
            return existingToken; // Retain token belonging to different owner
        });

        return released.get();
    }

    @Override
    public boolean isLocked(String lockKey) {
        if (lockKey == null) {
            return false;
        }
        LockToken token = locks.get(lockKey);
        return token != null && !token.isExpired();
    }

    private String generateOwnerToken() {
        return instanceId + ":" + Thread.currentThread().getName() + ":" + UUID.randomUUID();
    }

    /**
     * Internal implementation of {@link DistributedLock} bound to this manager.
     */
    private final class LocalDistributedLock implements DistributedLock {

        private final LockToken token;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private LocalDistributedLock(LockToken token) {
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
            LockToken current = locks.get(token.lockKey());
            return current != null
                    && current.ownerToken().equals(token.ownerToken())
                    && !current.isExpired();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(this);
            }
        }
    }
}
