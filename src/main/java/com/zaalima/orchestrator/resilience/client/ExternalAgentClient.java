package com.zaalima.orchestrator.resilience.client;

import com.zaalima.orchestrator.model.AgentTask;

/**
 * Contract for dispatching requests to external or remote agent services.
 * All implementations must adhere to configured resilience boundaries
 * (Circuit Breaker, Bulkhead, and Timeouts).
 */
public interface ExternalAgentClient {

    /**
     * Sends an HTTP request to an external agent endpoint protected by resilience decorators.
     *
     * @param request the request configuration
     * @return the agent response
     */
    ExternalAgentResponse sendRequest(ExternalAgentRequest request);

    /**
     * Executes an AgentTask by dispatching it to an external agent endpoint.
     *
     * @param agentId unique agent identifier
     * @param task the task to execute
     * @return the agent response
     */
    ExternalAgentResponse executeTask(String agentId, AgentTask task);
}
