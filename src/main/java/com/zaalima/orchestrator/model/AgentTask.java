
package com.zaalima.orchestrator.model;

public class AgentTask {

    private String taskId;
    private String agentId;
    private String taskType;
    private String payload;
    private volatile TaskStatus status;

    public AgentTask(String taskId, String agentId,
                     String taskType, String payload) {

        this.taskId = taskId;
        this.agentId = agentId;
        this.taskType = taskType;
        this.payload = payload;
        this.status = TaskStatus.PENDING;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAgentId() {
        return agentId;
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

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}