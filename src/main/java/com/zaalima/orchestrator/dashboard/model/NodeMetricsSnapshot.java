package com.zaalima.orchestrator.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of real runtime and virtual-thread task metrics for a single node.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NodeMetricsSnapshot(
        String nodeId,
        long timestamp,
        NodeStatus status,
        long uptimeSeconds,
        int activeTasks,
        long completedTasks,
        long failedTasks,
        long totalTasksSubmitted,
        double heapUsedMb,
        double heapMaxMb,
        int livePlatformThreads,
        int peakPlatformThreads,
        long totalStartedThreads,
        Map<String, Integer> threadStateCounts,
        boolean virtualThreadSupported,
        String errorMessage,
        boolean isLocal
) {
    public NodeMetricsSnapshot {
        if (threadStateCounts == null) {
            threadStateCounts = Collections.emptyMap();
        }
    }

    /**
     * Creates a copy of this snapshot with an updated node status.
     */
    public NodeMetricsSnapshot withStatus(NodeStatus newStatus) {
        return new NodeMetricsSnapshot(
                nodeId,
                timestamp,
                newStatus,
                uptimeSeconds,
                activeTasks,
                completedTasks,
                failedTasks,
                totalTasksSubmitted,
                heapUsedMb,
                heapMaxMb,
                livePlatformThreads,
                peakPlatformThreads,
                totalStartedThreads,
                threadStateCounts,
                virtualThreadSupported,
                errorMessage,
                isLocal
        );
    }

    /**
     * Creates an unreachable / DOWN snapshot placeholder for an expected node.
     */
    public static NodeMetricsSnapshot down(String nodeId, String error) {
        return new NodeMetricsSnapshot(
                nodeId,
                System.currentTimeMillis(),
                NodeStatus.DOWN,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0.0,
                0,
                0,
                0,
                Collections.emptyMap(),
                true,
                error,
                false
        );
    }
}
