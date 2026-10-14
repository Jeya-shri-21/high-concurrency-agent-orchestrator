package com.zaalima.orchestrator.exception;

/**
 * Thrown when an agent registration request uses an agent ID that is already registered.
 */
public class DuplicateAgentException extends RuntimeException {

    public DuplicateAgentException(String message) {
        super(message);
    }
}
