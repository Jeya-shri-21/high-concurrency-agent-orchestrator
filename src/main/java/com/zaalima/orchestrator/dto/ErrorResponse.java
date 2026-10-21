package com.zaalima.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Standard structured error response for REST API failure cases.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    long timestamp,
    Map<String, String> validationErrors
) {
    public ErrorResponse(int status, String error, String message, long timestamp) {
        this(status, error, message, timestamp, null);
    }
}
