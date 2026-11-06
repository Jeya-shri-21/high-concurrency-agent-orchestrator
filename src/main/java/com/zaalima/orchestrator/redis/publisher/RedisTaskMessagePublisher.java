package com.zaalima.orchestrator.redis.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.redis.exception.MessagePublishingException;
import com.zaalima.orchestrator.redis.model.TaskEventType;
import com.zaalima.orchestrator.redis.model.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Redis Pub/Sub implementation of {@link TaskMessagePublisher}.
 * <p>
 * Publishes JSON-serialized {@link TaskMessage} instances to a configured Redis channel.
 * Implements strict input validation, structured logging, and comprehensive error handling.
 * </p>
 */
@Service
public class RedisTaskMessagePublisher implements TaskMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskMessagePublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic topic;
    private final ObjectMapper objectMapper;

    public RedisTaskMessagePublisher(
            StringRedisTemplate redisTemplate,
            @Qualifier("taskChannelTopic") ChannelTopic topic,
            ObjectMapper objectMapper) {

        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void publish(TaskMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        message.validate();

        String channelName = topic.getTopic();
        try {
            String payloadJson = objectMapper.writeValueAsString(message);
            Long subscriberCount = redisTemplate.convertAndSend(channelName, payloadJson);

            log.info("Published task message to Redis channel '{}' [taskId={}, eventType={}, activeSubscribers={}]",
                    channelName, message.getTaskId(), message.getEventType(), subscriberCount);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TaskMessage to JSON for taskId: {}", message.getTaskId(), e);
            throw new MessagePublishingException("Serialization failed for task " + message.getTaskId(), e);
        } catch (DataAccessException e) {
            log.error("Redis infrastructure error while publishing to channel '{}' for taskId: {}",
                    channelName, message.getTaskId(), e);
            throw new MessagePublishingException("Redis publication failed for task " + message.getTaskId(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing to Redis channel '{}' for taskId: {}",
                    channelName, message.getTaskId(), e);
            throw new MessagePublishingException("Unexpected publication failure for task " + message.getTaskId(), e);
        }
    }

    @Override
    public boolean tryPublish(TaskMessage message) {
        if (message == null) {
            log.warn("Cannot publish null TaskMessage");
            return false;
        }

        try {
            publish(message);
            return true;
        } catch (Exception e) {
            log.warn("tryPublish failed for taskId={}: {}", message.getTaskId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void publishTaskEvent(AgentTask task, TaskEventType eventType) {
        Objects.requireNonNull(task, "task must not be null");
        String correlationId = UUID.randomUUID().toString();
        TaskMessage message = TaskMessage.fromAgentTask(task, eventType, correlationId);
        publish(message);
    }

    public String getChannelName() {
        return topic.getTopic();
    }
}
