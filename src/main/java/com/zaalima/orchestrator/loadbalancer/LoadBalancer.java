package com.zaalima.orchestrator.loadbalancer;

import com.zaalima.orchestrator.exception.NoEligibleNodeException;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for selecting an eligible node from available orchestrator nodes.
 */
public interface LoadBalancer {

    /**
     * Selects an eligible node ID based on load-balancing criteria and node utilization metrics.
     *
     * @param eligibleNodeIds list of eligible registered node IDs available to receive tasks
     * @param nodeLoads map containing node ID to current utilization metric (active task count)
     * @return the selected node ID
     * @throws NoEligibleNodeException if no eligible node can be selected
     */
    String selectNode(List<String> eligibleNodeIds, Map<String, Integer> nodeLoads);
}
