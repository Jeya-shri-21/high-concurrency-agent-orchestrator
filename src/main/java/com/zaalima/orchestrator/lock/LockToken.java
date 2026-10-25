package com.zaalima.orchestrator.lock;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable token representing ownership and lease duration of an acquired lock.
 */
public record LockToken(
        String lockKey,
        String ownerToken,
        Instant acquiredAt,
        Instant expiresAt
) {
    public LockToken {
        Objects.requireNonNull(lockKey, "lockKey must not be null");
        Objects.requireNonNull(ownerToken, "ownerToken must not be null");
        Objects.requireNonNull(acquiredAt, "acquiredAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (expiresAt.isBefore(acquiredAt)) {
            throw new IllegalArgumentException("expiresAt cannot be before acquiredAt");
        }
    }

    /**
     * Checks if the lock lease has expired according to the current system clock.
     *
     * @return true if current instant is after expiresAt, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
