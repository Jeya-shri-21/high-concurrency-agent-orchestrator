package com.zaalima.orchestrator.resilience.exception;

/**
 * Thrown when an external call is rejected because the Bulkhead concurrency limit has been reached.
 * Allows callers to distinguish local resource saturation from remote service errors.
 */
public class BulkheadFullException extends ResilienceException {

    private final String bulkheadName;

    public BulkheadFullException(String bulkheadName, String message) {
        super(message);
        this.bulkheadName = bulkheadName;
    }

    public BulkheadFullException(String bulkheadName, String message, Throwable cause) {
        super(message, cause);
        this.bulkheadName = bulkheadName;
    }

    public String getBulkheadName() {
        return bulkheadName;
    }
}
