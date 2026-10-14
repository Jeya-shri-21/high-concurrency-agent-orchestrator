package com.zaalima.orchestrator.dto;

/**
 * Standard API response payload returned upon agent registration or query.
 */
public record AgentResponse(
    boolean success,
    String message,
    String agentId,
    String agentType,
    String status,
    long registeredAt
) {}
