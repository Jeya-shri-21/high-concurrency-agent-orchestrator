package com.zaalima.orchestrator.redis.subscriber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.redis.exception.MessageValidationException;
import com.zaalima.orchestrator.redis.model.TaskEventType;
import com.zaalima.orchestrator.redis.model.TaskMessage;
import com.zaalima.orchestrator.service.TaskExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis Pub/Sub message listener that receives and processes {@link TaskMessage} notifications.
 * <p>
 * Ensures safe deserialization, validation of incoming message schemas, structured diagnostic logging,
 * and resilient exception handling to prevent listener container thread starvation.
 * </p>
 */
@Component
public class TaskMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TaskMessageSubscriber.class);

    private final ObjectMapper objectMapper;
    private final TaskExecutionService taskExecutionService;
    private final TaskMessageHandler customHandler;

    private final AtomicReference<TaskMessage> lastReceivedMessage = new AtomicReference<>();
    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    @Autowired
    public TaskMessageSubscriber(
            ObjectMapper objectMapper,
            @Nullable TaskExecutionService taskExecutionService) {

        this(objectMapper, taskExecutionService, null);
    }

    public TaskMessageSubscriber(
            ObjectMapper objectMapper,
            @Nullable TaskExecutionService taskExecutionService,
            @Nullable TaskMessageHandler customHandler) {

        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.taskExecutionService = taskExecutionService;
        this.customHandler = customHandler;
    }

    @Override
    public void onMessage(Message message, @Nullable byte[] pattern) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            log.warn("Received empty or null message from Redis channel");
            errorCount.incrementAndGet();
            return;
        }

        String channelName = (message.getChannel() != null)
                ? new String(message.getChannel(), StandardCharsets.UTF_8)
                : "unknown";

        String rawBody = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            TaskMessage taskMessage = objectMapper.readValue(rawBody, TaskMessage.class);
            taskMessage.validate();

            lastReceivedMessage.set(taskMessage);
            receivedCount.incrementAndGet();

            log.info("Successfully received task message from channel '{}' [taskId={}, eventType={}, correlationId={}]",
                    channelName, taskMessage.getTaskId(), taskMessage.getEventType(), taskMessage.getCorrelationId());

            // Dispatch task to execution service if requested
            if (taskMessage.getEventType() == TaskEventType.TASK_DISPATCH && taskExecutionService != null) {
                log.info("Dispatching task '{}' to TaskExecutionService", taskMessage.getTaskId());
                taskExecutionService.submitTask(taskMessage.toAgentTask());
            }

            // Delegate to custom handler if configured
            if (customHandler != null) {
                try {
                    customHandler.handle(taskMessage);
                } catch (Exception e) {
                    log.error("Error executing custom TaskMessageHandler for taskId '{}'", taskMessage.getTaskId(), e);
                }
            }

        } catch (JsonProcessingException e) {
            errorCount.incrementAndGet();
            log.error("Failed to deserialize JSON payload from channel '{}': {}", channelName, e.getMessage());
        } catch (MessageValidationException e) {
            errorCount.incrementAndGet();
            log.error("TaskMessage validation failed for incoming message from channel '{}': {}", channelName, e.getMessage());
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Unexpected error processing incoming message from channel '{}': {}", channelName, e.getMessage(), e);
        }
    }

    public TaskMessage getLastReceivedMessage() {
        return lastReceivedMessage.get();
    }

    public long getReceivedCount() {
        return receivedCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public void resetMetrics() {
        lastReceivedMessage.set(null);
        receivedCount.set(0);
        errorCount.set(0);
    }
}
