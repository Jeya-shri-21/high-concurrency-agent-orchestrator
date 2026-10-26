package com.zaalima.orchestrator.resilience.exception;

/**
 * Thrown when an external call is rejected because the Circuit Breaker is in the OPEN state.
 * Allows callers to distinguish fast circuit rejection from actual downstream dependency failures.
 */
public class CircuitBreakerOpenException extends ResilienceException {

    private final String circuitBreakerName;

    public CircuitBreakerOpenException(String circuitBreakerName, String message) {
        super(message);
        this.circuitBreakerName = circuitBreakerName;
    }

    public CircuitBreakerOpenException(String circuitBreakerName, String message, Throwable cause) {
        super(message, cause);
        this.circuitBreakerName = circuitBreakerName;
    }

    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }
}
