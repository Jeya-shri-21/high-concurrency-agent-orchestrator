package com.zaalima.orchestrator.model;

/**
 * Lifecycle execution states for tasks in the orchestrator.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    /**
     * Checks if the status represents a final, terminal state.
     *
     * @return true if completed or failed, false otherwise
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
