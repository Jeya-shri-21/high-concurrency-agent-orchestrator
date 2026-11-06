package com.zaalima.orchestrator.loadbalancer;

import com.zaalima.orchestrator.exception.NoEligibleNodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Load-balancing algorithm that selects the eligible node with the lowest valid utilization.
 * Uses deterministic tie-breaking (lexicographical node ID order) when utilization is equal.
 *
 * Strict Concurrency & Health Rule:
 * Nodes with missing or invalid (e.g. negative) utilization data are NEVER assumed to be zero
 * and are excluded from candidate selection to avoid routing to broken or unmonitored nodes.
 */
@Component
public class LeastUtilizedLoadBalancer implements LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LeastUtilizedLoadBalancer.class);

    @Override
    public String selectNode(List<String> eligibleNodeIds, Map<String, Integer> nodeLoads) {
        if (eligibleNodeIds == null || eligibleNodeIds.isEmpty()) {
            throw new NoEligibleNodeException("No eligible nodes available for load balancing");
        }

        if (nodeLoads == null || nodeLoads.isEmpty()) {
            throw new NoEligibleNodeException("No utilization metrics available for eligible nodes");
        }

        // Filter eligible nodes that possess valid non-negative utilization metrics.
        // NEVER treat missing/unknown utilization as zero!
        Map<String, Integer> validCandidates = new HashMap<>();
        for (String nodeId : eligibleNodeIds) {
            if (nodeId == null || nodeId.isBlank()) {
                continue;
            }
            Integer load = nodeLoads.get(nodeId);
            if (load == null) {
                log.warn("Node '{}' has no reported utilization in Redis; skipping candidate", nodeId);
                continue;
            }
            if (load < 0) {
                log.warn("Node '{}' reported invalid negative utilization: {}; skipping candidate", nodeId, load);
                continue;
            }
            validCandidates.put(nodeId, load);
        }

        if (validCandidates.isEmpty()) {
            throw new NoEligibleNodeException("No eligible nodes have valid utilization metrics reported");
        }

        // Find minimum utilization value
        int minLoad = Collections.min(validCandidates.values());

        // Collect all nodes that tie for the minimum utilization
        List<String> tiedNodes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : validCandidates.entrySet()) {
            if (entry.getValue() == minLoad) {
                tiedNodes.add(entry.getKey());
            }
        }

        // Deterministic tie-breaking: sort lexicographically by nodeId
        Collections.sort(tiedNodes);
        String selected = tiedNodes.get(0);

        log.info("Selected least-utilized node '{}' with load {} (valid candidates: {}, tied: {})",
                selected, minLoad, validCandidates.size(), tiedNodes.size());

        return selected;
    }
}
