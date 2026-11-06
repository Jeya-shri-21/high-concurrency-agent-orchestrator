package com.zaalima.orchestrator.dashboard.model;

/**
 * Health and reporting status of an orchestrator node in the cluster.
 */
public enum NodeStatus {
    /**
     * Node is active and reporting fresh metrics within the stale threshold.
     */
    UP,

    /**
     * Node has reported previously, but its latest snapshot timestamp exceeds the stale threshold.
     */
    STALE,

    /**
     * Node is configured or expected, but is currently unreachable or missing from cluster registry.
     */
    DOWN
}
