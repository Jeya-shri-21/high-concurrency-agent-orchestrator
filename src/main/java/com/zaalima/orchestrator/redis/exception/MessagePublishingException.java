package com.zaalima.orchestrator.redis.exception;

/**
 * Thrown when an error occurs while publishing a message to Redis.
 */
public class MessagePublishingException extends RuntimeException {

    public MessagePublishingException(String message) {
        super(message);
    }

    public MessagePublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
