package com.zaalima.orchestrator.redis.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.redis.exception.MessageValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskMessage Unit Tests")
class TaskMessageTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Valid message passes validation successfully")
        void validMessagePasses() {
            TaskMessage msg = new TaskMessage(
                    "task-123",
                    "agent-456",
                    "INDEX_DATA",
                    "sample-payload",
                    TaskStatus.PENDING,
                    TaskEventType.TASK_SUBMITTED,
                    "corr-789",
                    Instant.now()
            );

            assertDoesNotThrow(msg::validate);
        }

        @Test
        @DisplayName("Blank taskId throws MessageValidationException")
        void blankTaskIdThrows() {
            TaskMessage msg = new TaskMessage(
                    "   ",
                    "agent-456",
                    "INDEX_DATA",
                    "payload",
                    TaskStatus.PENDING,
                    TaskEventType.TASK_SUBMITTED,
                    "corr-789",
                    Instant.now()
            );

            assertThrows(MessageValidationException.class, msg::validate);
        }

        @Test
        @DisplayName("Null agentId throws MessageValidationException")
        void nullAgentIdThrows() {
            TaskMessage msg = new TaskMessage(
                    "task-123",
                    null,
                    "INDEX_DATA",
                    "payload",
                    TaskStatus.PENDING,
                    TaskEventType.TASK_SUBMITTED,
                    "corr-789",
                    Instant.now()
            );

            assertThrows(MessageValidationException.class, msg::validate);
        }

        @Test
        @DisplayName("Null taskType throws MessageValidationException")
        void nullTaskTypeThrows() {
            TaskMessage msg = new TaskMessage(
                    "task-123",
                    "agent-456",
                    null,
                    "payload",
                    TaskStatus.PENDING,
                    TaskEventType.TASK_SUBMITTED,
                    "corr-789",
                    Instant.now()
            );

            assertThrows(MessageValidationException.class, msg::validate);
        }

        @Test
        @DisplayName("Null status throws MessageValidationException")
        void nullStatusThrows() {
            TaskMessage msg = new TaskMessage(
                    "task-123",
                    "agent-456",
                    "PROCESS",
                    "payload",
                    null,
                    TaskEventType.TASK_SUBMITTED,
                    "corr-789",
                    Instant.now()
            );

            assertThrows(MessageValidationException.class, msg::validate);
        }

        @Test
        @DisplayName("Null eventType throws MessageValidationException")
        void nullEventTypeThrows() {
            TaskMessage msg = new TaskMessage(
                    "task-123",
                    "agent-456",
                    "PROCESS",
                    "payload",
                    TaskStatus.PENDING,
                    null,
                    "corr-789",
                    Instant.now()
            );

            assertThrows(MessageValidationException.class, msg::validate);
        }
    }

    @Nested
    @DisplayName("Serialization & Deserialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("Serializes to JSON and deserializes back faithfully")
        void roundTripSerialization() throws Exception {
            Instant now = Instant.now();
            TaskMessage original = new TaskMessage(
                    "task-999",
                    "agent-111",
                    "COMPUTE",
                    "input-data",
                    TaskStatus.RUNNING,
                    TaskEventType.TASK_STARTED,
                    "corr-abc",
                    now
            );

            String json = objectMapper.writeValueAsString(original);
            assertNotNull(json);
            assertTrue(json.contains("\"taskId\":\"task-999\""));
            assertTrue(json.contains("\"eventType\":\"TASK_STARTED\""));

            TaskMessage deserialized = objectMapper.readValue(json, TaskMessage.class);
            assertEquals(original.getTaskId(), deserialized.getTaskId());
            assertEquals(original.getAgentId(), deserialized.getAgentId());
            assertEquals(original.getTaskType(), deserialized.getTaskType());
            assertEquals(original.getPayload(), deserialized.getPayload());
            assertEquals(original.getStatus(), deserialized.getStatus());
            assertEquals(original.getEventType(), deserialized.getEventType());
            assertEquals(original.getCorrelationId(), deserialized.getCorrelationId());
        }

        @Test
        @DisplayName("Deserialization ignores unknown properties safely")
        void ignoresUnknownFields() throws Exception {
            String jsonWithExtra = """
                    {
                        "taskId": "task-555",
                        "agentId": "agent-777",
                        "taskType": "ANALYZE",
                        "payload": "data",
                        "status": "COMPLETED",
                        "eventType": "TASK_COMPLETED",
                        "correlationId": "corr-xyz",
                        "futureField": "unexpected_value"
                    }
                    """;

            TaskMessage deserialized = objectMapper.readValue(jsonWithExtra, TaskMessage.class);
            assertEquals("task-555", deserialized.getTaskId());
            assertEquals(TaskStatus.COMPLETED, deserialized.getStatus());
            assertEquals(TaskEventType.TASK_COMPLETED, deserialized.getEventType());
        }
    }

    @Nested
    @DisplayName("Model Conversion Tests")
    class ConversionTests {

        @Test
        @DisplayName("fromAgentTask correctly populates message fields")
        void fromAgentTaskConversion() {
            AgentTask task = new AgentTask("task-001", "agent-002", "RETRIEVE", "search query");
            task.setStatus(TaskStatus.RUNNING);

            TaskMessage message = TaskMessage.fromAgentTask(task, TaskEventType.TASK_STARTED, "corr-123");

            assertEquals(task.getTaskId(), message.getTaskId());
            assertEquals(task.getAgentId(), message.getAgentId());
            assertEquals(task.getTaskType(), message.getTaskType());
            assertEquals(task.getPayload(), message.getPayload());
            assertEquals(TaskStatus.RUNNING, message.getStatus());
            assertEquals(TaskEventType.TASK_STARTED, message.getEventType());
            assertEquals("corr-123", message.getCorrelationId());
            assertNotNull(message.getTimestamp());
        }

        @Test
        @DisplayName("toAgentTask correctly restores an AgentTask instance")
        void toAgentTaskConversion() {
            TaskMessage message = new TaskMessage(
                    "task-888",
                    "agent-888",
                    "TRAIN_MODEL",
                    "epoch=10",
                    TaskStatus.COMPLETED,
                    TaskEventType.TASK_COMPLETED,
                    UUID.randomUUID().toString(),
                    Instant.now()
            );

            AgentTask task = message.toAgentTask();

            assertEquals("task-888", task.getTaskId());
            assertEquals("agent-888", task.getAgentId());
            assertEquals("TRAIN_MODEL", task.getTaskType());
            assertEquals("epoch=10", task.getPayload());
            assertEquals(TaskStatus.COMPLETED, task.getStatus());
        }
    }
}
