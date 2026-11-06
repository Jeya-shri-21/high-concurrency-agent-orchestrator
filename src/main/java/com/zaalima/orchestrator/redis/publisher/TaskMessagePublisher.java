package com.zaalima.orchestrator.redis.publisher;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.redis.exception.MessagePublishingException;
import com.zaalima.orchestrator.redis.model.TaskEventType;
import com.zaalima.orchestrator.redis.model.TaskMessage;

/**
 * Interface for publishing agent task events and messages across the orchestrator cluster using Redis.
 */
public interface TaskMessagePublisher {

    /**
     * Publishes a validated task message to the designated Redis channel.
     *
     * @param message the message to publish
     * @throws MessagePublishingException if publication fails due to Redis errors or serialization issues
     * @throws com.zaalima.orchestrator.redis.exception.MessageValidationException if message validation fails
     */
    void publish(TaskMessage message);

    /**
     * Attempts to publish a task message, returning {@code true} on success and {@code false} on failure
     * without throwing exceptions.
     *
     * @param message the message to publish
     * @return {@code true} if successfully published, {@code false} otherwise
     */
    boolean tryPublish(TaskMessage message);

    /**
     * Convenience method to construct and publish a {@link TaskMessage} from an {@link AgentTask}.
     *
     * @param task the agent task
     * @param eventType the lifecycle event type
     */
    void publishTaskEvent(AgentTask task, TaskEventType eventType);
}
