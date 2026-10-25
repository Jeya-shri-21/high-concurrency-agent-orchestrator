package com.zaalima.orchestrator.lock;

/**
 * Exception thrown when a lock cannot be acquired or when a lock operation fails unexpectedly.
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
