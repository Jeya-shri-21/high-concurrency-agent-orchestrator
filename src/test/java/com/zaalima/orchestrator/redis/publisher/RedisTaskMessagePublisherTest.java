package com.zaalima.orchestrator.redis.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.redis.exception.MessagePublishingException;
import com.zaalima.orchestrator.redis.exception.MessageValidationException;
import com.zaalima.orchestrator.redis.model.TaskEventType;
import com.zaalima.orchestrator.redis.model.TaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTaskMessagePublisher Unit Tests")
class RedisTaskMessagePublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private ChannelTopic topic;
    private ObjectMapper objectMapper;
    private RedisTaskMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        topic = new ChannelTopic("test:tasks:events");
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        publisher = new RedisTaskMessagePublisher(redisTemplate, topic, objectMapper);
    }

    @Test
    @DisplayName("Publishes valid TaskMessage to configured channel successfully")
    void publishValidMessageSuccess() {
        TaskMessage message = new TaskMessage(
                "task-100",
                "agent-200",
                "COMPUTE",
                "payload",
                TaskStatus.PENDING,
                TaskEventType.TASK_SUBMITTED,
                "corr-1",
                Instant.now()
        );

        when(redisTemplate.convertAndSend(eq("test:tasks:events"), anyString())).thenReturn(1L);

        assertDoesNotThrow(() -> publisher.publish(message));

        verify(redisTemplate, times(1)).convertAndSend(eq("test:tasks:events"), contains("task-100"));
    }

    @Test
    @DisplayName("Publish throws MessageValidationException when required fields are missing")
    void publishInvalidMessageThrowsValidationException() {
        TaskMessage invalidMessage = new TaskMessage();
        invalidMessage.setTaskId(""); // blank taskId

        assertThrows(MessageValidationException.class, () -> publisher.publish(invalidMessage));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Publish throws MessagePublishingException when Redis connection fails")
    void publishRedisFailureThrowsPublishingException() {
        TaskMessage message = new TaskMessage(
                "task-101",
                "agent-201",
                "COMPUTE",
                "payload",
                TaskStatus.PENDING,
                TaskEventType.TASK_SUBMITTED,
                "corr-2",
                Instant.now()
        );

        when(redisTemplate.convertAndSend(eq("test:tasks:events"), anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        MessagePublishingException ex = assertThrows(MessagePublishingException.class, () -> publisher.publish(message));
        assertTrue(ex.getMessage().contains("task-101"));
    }

    @Test
    @DisplayName("Publish throws MessagePublishingException when Redis query times out")
    void publishRedisTimeoutThrowsPublishingException() {
        TaskMessage message = new TaskMessage(
                "task-102",
                "agent-202",
                "COMPUTE",
                "payload",
                TaskStatus.PENDING,
                TaskEventType.TASK_SUBMITTED,
                "corr-3",
                Instant.now()
        );

        when(redisTemplate.convertAndSend(eq("test:tasks:events"), anyString()))
                .thenThrow(new QueryTimeoutException("Redis command timed out"));

        assertThrows(MessagePublishingException.class, () -> publisher.publish(message));
    }

    @Test
    @DisplayName("tryPublish returns true on success")
    void tryPublishSuccess() {
        TaskMessage message = new TaskMessage(
                "task-103",
                "agent-203",
                "ANALYZE",
                "payload",
                TaskStatus.RUNNING,
                TaskEventType.TASK_STARTED,
                "corr-4",
                Instant.now()
        );

        when(redisTemplate.convertAndSend(eq("test:tasks:events"), anyString())).thenReturn(2L);

        boolean result = publisher.tryPublish(message);
        assertTrue(result);
    }

    @Test
    @DisplayName("tryPublish returns false without throwing on Redis failure")
    void tryPublishReturnsFalseOnFailure() {
        TaskMessage message = new TaskMessage(
                "task-104",
                "agent-204",
                "ANALYZE",
                "payload",
                TaskStatus.RUNNING,
                TaskEventType.TASK_STARTED,
                "corr-5",
                Instant.now()
        );

        when(redisTemplate.convertAndSend(eq("test:tasks:events"), anyString()))
                .thenThrow(new RedisConnectionFailureException("Network partitioned"));

        boolean result = publisher.tryPublish(message);
        assertFalse(result);
    }

    @Test
    @DisplayName("tryPublish returns false when message is null")
    void tryPublishNullMessageReturnsFalse() {
        boolean result = publisher.tryPublish(null);
        assertFalse(result);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("publishTaskEvent constructs and publishes message successfully")
    void publishTaskEventSuccess() {
        AgentTask task = new AgentTask("task-105", "agent-205", "TRANSFORM", "raw-data");
        when(redisTemplate.convertAndSend(eq("test:tasks:events"), anyString())).thenReturn(1L);

        assertDoesNotThrow(() -> publisher.publishTaskEvent(task, TaskEventType.TASK_SUBMITTED));
        verify(redisTemplate, times(1)).convertAndSend(eq("test:tasks:events"), contains("task-105"));
    }
}
