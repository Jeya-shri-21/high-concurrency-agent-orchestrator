package com.zaalima.orchestrator.exception;

/**
 * Thrown when an agent lookup is requested for an unregistered agent.
 */
public class AgentNotFoundException extends RuntimeException {

    public AgentNotFoundException(String message) {
        super(message);
    }
}
