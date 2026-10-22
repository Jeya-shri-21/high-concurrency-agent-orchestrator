package com.zaalima.orchestrator.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * Core interface for acquiring and releasing distributed locks.
 * <p>
 * Decoupled from transport layers (gRPC, HTTP, messaging), providing a reusable
 * coordination mechanism for orchestrator tasks and agent resources.
 */
public interface DistributedLockManager {

    /**
     * Attempts to acquire an exclusive lock on the given key within the specified timeout.
     *
     * @param lockKey the resource identifier to lock (must not be null or blank)
     * @param leaseDuration how long the lock should be held before automatic expiration
     * @param acquireTimeout maximum duration to wait/retry before failing acquisition
     * @return an {@link Optional} containing the {@link DistributedLock} handle if acquired, or empty if timed out
     */
    Optional<DistributedLock> tryAcquire(String lockKey, Duration leaseDuration, Duration acquireTimeout);

    /**
     * Releases the given lock safely.
     * <p>
     * Releasing must be ownership-safe: if the lock has already expired or been reassigned,
     * the call returns {@code false} and does not delete another owner's lock.
     *
     * @param lock the lock handle to release
     * @return true if the lock was successfully released, false if it had expired or ownership changed
     */
    boolean release(DistributedLock lock);

    /**
     * Checks whether a lock key is currently actively held by any owner.
     *
     * @param lockKey the lock key to check
     * @return true if actively locked and unexpired, false otherwise
     */
    boolean isLocked(String lockKey);
}
