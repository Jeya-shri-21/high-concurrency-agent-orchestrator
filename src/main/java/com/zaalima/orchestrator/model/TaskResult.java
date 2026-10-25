package com.zaalima.orchestrator.model;

import java.util.Objects;

/**
 * Result model representing the outcome of task execution.
 * Immutable and thread-safe for high-concurrency environments.
 */
public record TaskResult(
        String taskId,
        TaskStatus status,
        String output,
        String errorDetails
) {
    public TaskResult {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Factory method for a successful task execution.
     *
     * @param taskId the executed task ID
     * @param output execution output or result payload
     * @return TaskResult with COMPLETED status
     */
    public static TaskResult success(String taskId, String output) {
        return new TaskResult(taskId, TaskStatus.COMPLETED, output, null);
    }

    /**
     * Factory method for a failed task execution.
     *
     * @param taskId the executed task ID
     * @param errorDetails failure description or error message
     * @return TaskResult with FAILED status
     */
    public static TaskResult failure(String taskId, String errorDetails) {
        return new TaskResult(taskId, TaskStatus.FAILED, null, errorDetails);
    }

    /**
     * Checks if the task completed successfully.
     *
     * @return true if COMPLETED, false otherwise
     */
    public boolean isSuccess() {
        return status == TaskStatus.COMPLETED;
    }
}
