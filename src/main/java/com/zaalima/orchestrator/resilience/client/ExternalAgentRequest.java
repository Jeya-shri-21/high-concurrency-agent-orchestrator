package com.zaalima.orchestrator.resilience.client;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a request payload dispatched to an external third-party agent.
 */
public record ExternalAgentRequest(
        String agentId,
        String endpointUrl,
        String method,
        String payload,
        Map<String, String> headers
) {
    public ExternalAgentRequest {
        if (headers == null) {
            headers = Collections.emptyMap();
        }
        if (method == null || method.isBlank()) {
            method = "POST";
        }
    }

    public static ExternalAgentRequest of(String agentId, String endpointUrl, String payload) {
        return new ExternalAgentRequest(agentId, endpointUrl, "POST", payload, Collections.emptyMap());
    }
}
