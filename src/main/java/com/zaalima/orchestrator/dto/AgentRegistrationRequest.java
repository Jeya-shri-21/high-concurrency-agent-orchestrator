package com.zaalima.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for registering an agent via REST.
 */
public record AgentRegistrationRequest(

    @NotBlank(message = "agentId is required and must not be blank")
    @Size(min = 2, max = 50, message = "agentId must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "agentId must contain only alphanumeric characters, underscores, or hyphens")
    String agentId,

    @NotBlank(message = "agentType is required and must not be blank")
    @Size(min = 2, max = 50, message = "agentType must be between 2 and 50 characters")
    String agentType
) {}
