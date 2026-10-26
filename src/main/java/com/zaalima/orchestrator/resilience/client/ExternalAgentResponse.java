package com.zaalima.orchestrator.resilience.client;

/**
 * Encapsulates the response received from an external third-party agent.
 */
public record ExternalAgentResponse(
        int statusCode,
        String body,
        boolean success
) {
    public static ExternalAgentResponse success(int statusCode, String body) {
        return new ExternalAgentResponse(statusCode, body, true);
    }

    public static ExternalAgentResponse failure(int statusCode, String body) {
        return new ExternalAgentResponse(statusCode, body, false);
    }
}
