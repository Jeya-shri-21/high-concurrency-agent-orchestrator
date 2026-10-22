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
     * Releases this lock safely. Releasing is ownership-safe: if the lock has expired or has
     * been acquired by another owner, this handle will not delete the new owner's lock.
     */
    @Override
    void close();
}
