package com.zaalima.orchestrator.resilience.exception;

/**
 * Thrown when an external API call exceeds a configured timeout limit
 * (connection timeout, request timeout, or overall operation deadline).
 */
public class ExternalApiTimeoutException extends ResilienceException {

    private final TimeoutType timeoutType;
    private final long timeoutMillis;

    public enum TimeoutType {
        CONNECT,
        REQUEST,
        OPERATION
    }

    public ExternalApiTimeoutException(TimeoutType timeoutType, long timeoutMillis, String message) {
        super(message);
        this.timeoutType = timeoutType;
        this.timeoutMillis = timeoutMillis;
    }

    public ExternalApiTimeoutException(TimeoutType timeoutType, long timeoutMillis, String message, Throwable cause) {
        super(message, cause);
        this.timeoutType = timeoutType;
        this.timeoutMillis = timeoutMillis;
    }

    public TimeoutType getTimeoutType() {
        return timeoutType;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
