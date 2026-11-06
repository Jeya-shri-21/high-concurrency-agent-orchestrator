package com.zaalima.orchestrator.redis.exception;

/**
 * Thrown when an incoming or outgoing Redis task message fails required field validation.
 */
public class MessageValidationException extends RuntimeException {

    public MessageValidationException(String message) {
        super(message);
    }

    public MessageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
