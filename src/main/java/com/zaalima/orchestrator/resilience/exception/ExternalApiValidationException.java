package com.zaalima.orchestrator.resilience.exception;

/**
 * Thrown when an external API call fails due to invalid client request (4xx status code).
 * Explicitly ignored by the Circuit Breaker so client validation mistakes do not falsely
 * trip downstream service health checks.
 */
public class ExternalApiValidationException extends ResilienceException {

    private final int statusCode;

    public ExternalApiValidationException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ExternalApiValidationException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
