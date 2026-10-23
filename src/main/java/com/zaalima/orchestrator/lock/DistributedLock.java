package com.zaalima.orchestrator.lock;

/**
 * Handle representing an acquired lock.
 * Implements {@link AutoCloseable} to support idiom-friendly try-with-resources release.
 */
public interface DistributedLock extends AutoCloseable {

    /**
     * @return the unique key/resource identifier for this lock
     */
    String getLockKey();

    /**
     * @return the immutable {@link LockToken} containing ownership and expiration details
     */
    LockToken getToken();

    /**
     * Checks if this lock handle is currently valid (held by this token and not expired).
     *
     * @return true if still valid, false otherwise
     */
    boolean isValid();

    /**
     * Atomically extends this lock's lease duration by the specified duration,
     * provided this handle still holds the active lock in the underlying store.
     *
     * @param extensionDuration the duration to extend the lease by (must be positive)
     * @return true if the lease was successfully renewed, false if expired, ownership was lost, or lock is closed
     */
    default boolean renew(java.time.Duration extensionDuration) {
        return false;
    }

    /**
     * Releases this lock safely. Releasing is ownership-safe: if the lock has expired or has
     * been acquired by another owner, this handle will not delete the new owner's lock.
     */
    @Override
    void close();
}
