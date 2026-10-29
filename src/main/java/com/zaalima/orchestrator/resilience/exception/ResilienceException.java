package com.zaalima.orchestrator.resilience.exception;

/**
 * Base exception for all resilience-related errors in the orchestrator.
 */
public class ResilienceException extends RuntimeException {

    public ResilienceException(String message) {
        super(message);
    }

    public ResilienceException(String message, Throwable cause) {
        super(message, cause);
    }
}
