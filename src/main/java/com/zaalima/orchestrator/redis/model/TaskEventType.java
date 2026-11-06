package com.zaalima.orchestrator.redis.model;

/**
 * Event types representing lifecycle milestones and messaging events for an {@link com.zaalima.orchestrator.model.AgentTask}.
 */
public enum TaskEventType {
    TASK_SUBMITTED,
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_DISPATCH
}
