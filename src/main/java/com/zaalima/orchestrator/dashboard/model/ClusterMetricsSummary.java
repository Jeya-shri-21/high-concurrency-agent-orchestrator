package com.zaalima.orchestrator.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Aggregated metrics summary across all observable orchestrator nodes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClusterMetricsSummary(
        long timestamp,
        int totalNodes,
        int reachableNodes,
        int staleNodes,
        int unreachableNodes,
        int totalActiveTasks,
        long totalCompletedTasks,
        long totalFailedTasks,
        int totalLivePlatformThreads,
        double totalHeapUsedMb,
        double totalHeapMaxMb,
        int collectionErrors,
        String clusterMode
) {
    public static ClusterMetricsSummary empty(String clusterMode) {
        return new ClusterMetricsSummary(
                System.currentTimeMillis(),
                0, 0, 0, 0,
                0, 0, 0, 0,
                0.0, 0.0,
                0,
                clusterMode
        );
    }
}
