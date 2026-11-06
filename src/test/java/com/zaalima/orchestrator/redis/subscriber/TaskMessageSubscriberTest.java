package com.zaalima.orchestrator.redis.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.redis.model.TaskEventType;
import com.zaalima.orchestrator.redis.model.TaskMessage;
import com.zaalima.orchestrator.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskMessageSubscriber Unit Tests")
class TaskMessageSubscriberTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    private ObjectMapper objectMapper;
    private TaskMessageSubscriber subscriber;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        subscriber = new TaskMessageSubscriber(objectMapper, taskExecutionService);
    }

    private Message createRedisMessage(String channel, String bodyJson) {
        return new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                bodyJson.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @DisplayName("Successfully consumes and validates well-formed TaskMessage")
    void consumesValidMessage() throws Exception {
        TaskMessage original = new TaskMessage(
                "task-201",
                "agent-201",
                "SUMMARIZE",
                "doc content",
                TaskStatus.PENDING,
                TaskEventType.TASK_SUBMITTED,
                "corr-201",
                Instant.now()
        );

        String json = objectMapper.writeValueAsString(original);
        Message message = createRedisMessage("orchestrator:tasks:events", json);

        assertDoesNotThrow(() -> subscriber.onMessage(message, null));

        assertEquals(1, subscriber.getReceivedCount());
        assertEquals(0, subscriber.getErrorCount());
        assertNotNull(subscriber.getLastReceivedMessage());
        assertEquals("task-201", subscriber.getLastReceivedMessage().getTaskId());
    }

    @Test
    @DisplayName("TASK_DISPATCH event dispatches task to TaskExecutionService")
    void dispatchesTaskOnDispatchEvent() throws Exception {
        TaskMessage dispatchMsg = new TaskMessage(
                "task-dispatch-1",
                "agent-worker-1",
                "EXECUTE_QUERY",
                "SELECT 1",
                TaskStatus.PENDING,
                TaskEventType.TASK_DISPATCH,
                "corr-dispatch-1",
                Instant.now()
        );

        String json = objectMapper.writeValueAsString(dispatchMsg);
        Message message = createRedisMessage("orchestrator:tasks:events", json);

        subscriber.onMessage(message, null);

        assertEquals(1, subscriber.getReceivedCount());
        verify(taskExecutionService, times(1)).submitTask(any(AgentTask.class));
    }

    @Test
    @DisplayName("Executes custom TaskMessageHandler when configured")
    void executesCustomHandler() throws Exception {
        AtomicBoolean customHandlerInvoked = new AtomicBoolean(false);
        TaskMessageHandler handler = msg -> customHandlerInvoked.set(true);

        TaskMessageSubscriber customSubscriber =
                new TaskMessageSubscriber(objectMapper, taskExecutionService, handler);

        TaskMessage original = new TaskMessage(
                "task-custom",
                "agent-custom",
                "RUN",
                "param=1",
                TaskStatus.RUNNING,
                TaskEventType.TASK_STARTED,
                "corr-c",
                Instant.now()
        );

        Message message = createRedisMessage("orchestrator:tasks:events", objectMapper.writeValueAsString(original));
        customSubscriber.onMessage(message, null);

        assertTrue(customHandlerInvoked.get());
        assertEquals(1, customSubscriber.getReceivedCount());
    }

    @Test
    @DisplayName("Empty or null message is handled safely without throwing")
    void handlesNullOrEmptyMessageSafely() {
        assertDoesNotThrow(() -> subscriber.onMessage(null, null));
        assertEquals(1, subscriber.getErrorCount());

        Message emptyMessage = new DefaultMessage("channel".getBytes(), new byte[0]);
        assertDoesNotThrow(() -> subscriber.onMessage(emptyMessage, null));
        assertEquals(2, subscriber.getErrorCount());
    }

    @Test
    @DisplayName("Malformed JSON is handled gracefully and does not crash the subscriber")
    void handlesMalformedJsonGracefully() {
        Message badJsonMessage = createRedisMessage("channel", "{not valid json at all}");

        assertDoesNotThrow(() -> subscriber.onMessage(badJsonMessage, null));
        assertEquals(1, subscriber.getErrorCount());
        assertEquals(0, subscriber.getReceivedCount());
    }

    @Test
    @DisplayName("Message with invalid schema (missing required fields) is rejected safely")
    void handlesInvalidSchemaSafely() {
        String missingFieldsJson = "{\"taskId\":\"\", \"agentId\":null}";
        Message invalidMessage = createRedisMessage("channel", missingFieldsJson);

        assertDoesNotThrow(() -> subscriber.onMessage(invalidMessage, null));
        assertEquals(1, subscriber.getErrorCount());
        assertEquals(0, subscriber.getReceivedCount());
    }

    @Test
    @DisplayName("Exception inside custom handler is caught and logged without crashing subscriber")
    void catchesHandlerExceptionSafely() throws Exception {
        TaskMessageHandler failingHandler = msg -> {
            throw new RuntimeException("Handler exploded!");
        };

        TaskMessageSubscriber subscriberWithFailingHandler =
                new TaskMessageSubscriber(objectMapper, taskExecutionService, failingHandler);

        TaskMessage msg = new TaskMessage(
                "task-fail-handler",
                "agent-x",
                "TEST",
                "data",
                TaskStatus.PENDING,
                TaskEventType.TASK_SUBMITTED,
                "corr-fail",
                Instant.now()
        );

        Message redisMessage = createRedisMessage("channel", objectMapper.writeValueAsString(msg));

        assertDoesNotThrow(() -> subscriberWithFailingHandler.onMessage(redisMessage, null));
        assertEquals(1, subscriberWithFailingHandler.getReceivedCount());
    }

    @Test
    @DisplayName("resetMetrics clears accumulated message and error counts")
    void resetMetricsClearsState() throws Exception {
        TaskMessage msg = new TaskMessage(
                "task-metric",
                "agent-metric",
                "TEST",
                "data",
                TaskStatus.PENDING,
                TaskEventType.TASK_SUBMITTED,
                "corr-m",
                Instant.now()
        );

        subscriber.onMessage(createRedisMessage("channel", objectMapper.writeValueAsString(msg)), null);
        subscriber.onMessage(null, null);

        assertEquals(1, subscriber.getReceivedCount());
        assertEquals(1, subscriber.getErrorCount());

        subscriber.resetMetrics();

        assertEquals(0, subscriber.getReceivedCount());
        assertEquals(0, subscriber.getErrorCount());
        assertNull(subscriber.getLastReceivedMessage());
    }
}
