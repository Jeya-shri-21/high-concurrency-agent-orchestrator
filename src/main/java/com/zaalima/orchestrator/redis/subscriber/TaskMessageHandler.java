package com.zaalima.orchestrator.redis.subscriber;

import com.zaalima.orchestrator.redis.model.TaskMessage;

/**
 * Functional callback interface for processing incoming validated {@link TaskMessage} instances.
 */
@FunctionalInterface
public interface TaskMessageHandler {

    /**
     * Handles an incoming task message.
     *
     * @param message the validated task message
     */
    void handle(TaskMessage message);
}
