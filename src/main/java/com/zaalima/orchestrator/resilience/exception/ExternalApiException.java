package com.zaalima.orchestrator.resilience.exception;

/**
 * Thrown when an external API or remote agent service returns a 5xx server error,
 * network failure, or downstream exception.
 * Classified as a failure by the Circuit Breaker.
 */
public class ExternalApiException extends ResilienceException {

    private final int statusCode;

    public ExternalApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ExternalApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
