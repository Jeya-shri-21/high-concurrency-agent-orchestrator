package com.zaalima.orchestrator.model;

import java.util.Objects;

/**
 * Common Task model for the High-Concurrency Autonomous Agent Orchestrator.
 * Designed with thread-safe practices for safe concurrent access across virtual threads.
 */
public class Task {

    private final String taskId;
    private final String taskType;
    private final String payload;
    private volatile TaskStatus status;
    private final long createdAt;

    /**
     * Creates a new task with default PENDING status and current timestamp.
     *
     * @param taskId unique identifier for the task
     * @param taskType category or type of the task
     * @param payload input payload or task instructions
     */
    public Task(String taskId, String taskType, String payload) {
        this(taskId, taskType, payload, TaskStatus.PENDING, System.currentTimeMillis());
    }

    /**
     * Creates a task with explicit status and creation timestamp.
     *
     * @param taskId unique identifier for the task
     * @param taskType category or type of the task
     * @param payload input payload or task instructions
     * @param status initial task status
     * @param createdAt timestamp in milliseconds when the task was created
     */
    public Task(String taskId, String taskType, String payload, TaskStatus status, long createdAt) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.taskType = Objects.requireNonNull(taskType, "taskType must not be null");
        this.payload = payload != null ? payload : "";
        this.status = status != null ? status : TaskStatus.PENDING;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getPayload() {
        return payload;
    }

    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Updates the status of the task. Volatile field ensures visibility across concurrent threads.
     *
     * @param status the new task status
     */
    public void setStatus(TaskStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(taskId, task.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }

    @Override
    public String toString() {
        return "Task{" +
                "taskId='" + taskId + '\'' +
                ", taskType='" + taskType + '\'' +
                ", payload='" + payload + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
