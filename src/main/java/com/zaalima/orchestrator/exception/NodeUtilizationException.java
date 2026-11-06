package com.zaalima.orchestrator.exception;

/**
 * Thrown when node utilization metrics cannot be obtained or are invalid.
 */
public class NodeUtilizationException extends RuntimeException {

    public NodeUtilizationException(String message) {
        super(message);
    }

    public NodeUtilizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
