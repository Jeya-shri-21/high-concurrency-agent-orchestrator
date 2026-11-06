package com.zaalima.orchestrator.redis.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.redis.exception.MessageValidationException;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates an agent task event communicated over Redis messaging channels.
 * <p>
 * Reuses {@link TaskStatus} to maintain contract consistency with the core orchestrator model.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;
    private String agentId;
    private String taskType;
    private String payload;
    private TaskStatus status;
    private TaskEventType eventType;
    private String correlationId;
    private Instant timestamp;

    public TaskMessage() {
        this.timestamp = Instant.now();
        this.correlationId = UUID.randomUUID().toString();
    }

    @JsonCreator
    public TaskMessage(
            @JsonProperty("taskId") String taskId,
            @JsonProperty("agentId") String agentId,
            @JsonProperty("taskType") String taskType,
            @JsonProperty("payload") String payload,
            @JsonProperty("status") TaskStatus status,
            @JsonProperty("eventType") TaskEventType eventType,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("timestamp") Instant timestamp) {

        this.taskId = taskId;
        this.agentId = agentId;
        this.taskType = taskType;
        this.payload = payload;
        this.status = status;
        this.eventType = eventType;
        this.correlationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();
        this.timestamp = (timestamp != null) ? timestamp : Instant.now();
    }

    public static TaskMessage fromAgentTask(AgentTask task, TaskEventType eventType, String correlationId) {
        Objects.requireNonNull(task, "task must not be null");
        return new TaskMessage(
                task.getTaskId(),
                task.getAgentId(),
                task.getTaskType(),
                task.getPayload(),
                task.getStatus(),
                eventType != null ? eventType : TaskEventType.TASK_SUBMITTED,
                correlationId,
                Instant.now()
        );
    }

    public AgentTask toAgentTask() {
        AgentTask task = new AgentTask(taskId, agentId, taskType, payload);
        if (status != null) {
            task.setStatus(status);
        }
        return task;
    }

    /**
     * Validates that all mandatory fields are present and valid.
     *
     * @throws MessageValidationException if validation fails
     */
    public void validate() {
        if (taskId == null || taskId.isBlank()) {
            throw new MessageValidationException("TaskMessage 'taskId' must not be null or blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new MessageValidationException("TaskMessage 'agentId' must not be null or blank");
        }
        if (taskType == null || taskType.isBlank()) {
            throw new MessageValidationException("TaskMessage 'taskType' must not be null or blank");
        }
        if (status == null) {
            throw new MessageValidationException("TaskMessage 'status' must not be null");
        }
        if (eventType == null) {
            throw new MessageValidationException("TaskMessage 'eventType' must not be null");
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskEventType getEventType() {
        return eventType;
    }

    public void setEventType(TaskEventType eventType) {
        this.eventType = eventType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskMessage that)) return false;
        return Objects.equals(taskId, that.taskId)
                && Objects.equals(agentId, that.agentId)
                && Objects.equals(taskType, that.taskType)
                && Objects.equals(payload, that.payload)
                && status == that.status
                && eventType == that.eventType
                && Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, agentId, taskType, payload, status, eventType, correlationId);
    }

    @Override
    public String toString() {
        return "TaskMessage{" +
                "taskId='" + taskId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", taskType='" + taskType + '\'' +
                ", status=" + status +
                ", eventType=" + eventType +
                ", correlationId='" + correlationId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
