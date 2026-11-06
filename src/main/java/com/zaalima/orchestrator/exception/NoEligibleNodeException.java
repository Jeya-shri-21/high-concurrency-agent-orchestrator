package com.zaalima.orchestrator.exception;

/**
 * Thrown when no eligible node is available to accept tasks during load balancing.
 */
public class NoEligibleNodeException extends RuntimeException {

    public NoEligibleNodeException(String message) {
        super(message);
    }

    public NoEligibleNodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
