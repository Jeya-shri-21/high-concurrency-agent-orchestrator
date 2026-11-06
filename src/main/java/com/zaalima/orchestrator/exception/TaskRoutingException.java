package com.zaalima.orchestrator.exception;

/**
 * Thrown when an error occurs while dispatching or routing a task payload to a selected node.
 */
public class TaskRoutingException extends RuntimeException {

    public TaskRoutingException(String message) {
        super(message);
    }

    public TaskRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
