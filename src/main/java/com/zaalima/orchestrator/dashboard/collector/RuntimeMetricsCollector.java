package com.zaalima.orchestrator.dashboard.collector;

import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;

/**
 * Interface for capturing runtime metrics snapshots from a running orchestrator node.
 */
public interface RuntimeMetricsCollector {

    /**
     * Collects real, observable runtime metrics from the local JVM and orchestrator task executor.
     *
     * @return a point-in-time NodeMetricsSnapshot
     */
    NodeMetricsSnapshot collectLocalMetrics();
}
