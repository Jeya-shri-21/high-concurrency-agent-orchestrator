package com.zaalima.orchestrator.model;

import java.util.Objects;

/**
 * Domain model representing a registered agent in the orchestrator.
 */
public class Agent {

    private String agentId;
    private String agentType;
    private String status;
    private long registeredAt;

    public Agent() {
    }

    public Agent(String agentId, String agentType, String status, long registeredAt) {
        this.agentId = agentId;
        this.agentType = agentType;
        this.status = status;
        this.registeredAt = registeredAt;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Agent agent = (Agent) o;
        return Objects.equals(agentId, agent.agentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentId);
    }

    @Override
    public String toString() {
        return "Agent{" +
                "agentId='" + agentId + '\'' +
                ", agentType='" + agentType + '\'' +
                ", status='" + status + '\'' +
                ", registeredAt=" + registeredAt +
                '}';
    }
}
