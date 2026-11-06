package com.zaalima.orchestrator.dashboard.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import com.zaalima.orchestrator.model.Agent;
import com.zaalima.orchestrator.service.AgentRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates runtime snapshots across all reachable orchestrator nodes via Redis and local metrics.
 */
@Component
public class ClusterMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(ClusterMetricsCollector.class);

    private final RuntimeMetricsCollector localCollector;
    private final StringRedisTemplate redisTemplate;
    private final DashboardProperties properties;
    private final ObjectMapper objectMapper;
    private final AgentRegistryService agentRegistryService;

    public ClusterMetricsCollector(
            RuntimeMetricsCollector localCollector,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            DashboardProperties properties,
            ObjectMapper objectMapper,
            @Autowired(required = false) AgentRegistryService agentRegistryService) {
        this.localCollector = localCollector;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.agentRegistryService = agentRegistryService;
    }

    /**
     * Collects snapshots from all available nodes in the cluster.
     *
     * @param isDemoMode whether demo mode is requested
     * @return list of node metrics snapshots sorted by node ID
     */
    public List<NodeMetricsSnapshot> collectClusterSnapshots(boolean isDemoMode) {
        if (isDemoMode) {
            return generateDemoSnapshots();
        }

        NodeMetricsSnapshot localSnapshot = localCollector.collectLocalMetrics();
        Map<String, NodeMetricsSnapshot> snapshotMap = new LinkedHashMap<>();
        snapshotMap.put(localSnapshot.nodeId(), localSnapshot);

        boolean redisSuccess = false;
        if (redisTemplate != null) {
            try {
                String prefix = properties.getReportKeyPrefix();
                Set<String> keys = redisTemplate.keys(prefix + "*");
                if (keys != null) {
                    long now = System.currentTimeMillis();
                    for (String key : keys) {
                        try {
                            String json = redisTemplate.opsForValue().get(key);
                            if (json != null && !json.isBlank()) {
                                NodeMetricsSnapshot snapshot = objectMapper.readValue(json, NodeMetricsSnapshot.class);
                                if (snapshot != null && snapshot.nodeId() != null) {
                                    // Evaluate freshness against stale threshold
                                    boolean isStale = (now - snapshot.timestamp()) > properties.getStaleThresholdMs();
                                    NodeStatus status = isStale ? NodeStatus.STALE : NodeStatus.UP;

                                    boolean isLocal = snapshot.nodeId().equals(localSnapshot.nodeId());
                                    // Preserve local snapshot's live in-memory metrics if it's the local node
                                    if (isLocal) {
                                        snapshotMap.put(snapshot.nodeId(), localSnapshot);
                                    } else {
                                        snapshotMap.put(snapshot.nodeId(), snapshot.withStatus(status));
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            log.debug("Error parsing node snapshot for key {}: {}", key, ex.getMessage());
                        }
                    }
                    redisSuccess = true;
                }
            } catch (Exception ex) {
                log.debug("Redis unavailable during cluster snapshot collection: {}", ex.getMessage());
            }
        }

        // Include any registered agent nodes from AgentRegistryService that have no snapshot reported
        if (agentRegistryService != null) {
            try {
                List<Agent> registeredAgents = agentRegistryService.getAllAgents();
                if (registeredAgents != null) {
                    for (Agent agent : registeredAgents) {
                        if (agent != null && agent.getAgentId() != null && !snapshotMap.containsKey(agent.getAgentId())) {
                            snapshotMap.put(agent.getAgentId(), NodeMetricsSnapshot.down(agent.getAgentId(), "No metrics reported"));
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Error inspecting AgentRegistryService for expected nodes: {}", ex.getMessage());
            }
        }

        List<NodeMetricsSnapshot> results = new ArrayList<>(snapshotMap.values());
        results.sort(Comparator.comparing(NodeMetricsSnapshot::nodeId));
        return results;
    }

    /**
     * Aggregates a list of node snapshots into a cluster-wide summary.
     */
    public ClusterMetricsSummary aggregate(List<NodeMetricsSnapshot> snapshots, boolean isDemoMode, boolean redisOnline) {
        if (snapshots == null || snapshots.isEmpty()) {
            return ClusterMetricsSummary.empty(determineClusterMode(0, isDemoMode, redisOnline));
        }

        long now = System.currentTimeMillis();
        int totalNodes = snapshots.size();
        int reachableNodes = 0;
        int staleNodes = 0;
        int unreachableNodes = 0;
        int totalActiveTasks = 0;
        long totalCompletedTasks = 0;
        long totalFailedTasks = 0;
        int totalPlatformThreads = 0;
        double totalHeapUsedMb = 0.0;
        double totalHeapMaxMb = 0.0;
        int collectionErrors = 0;

        for (NodeMetricsSnapshot s : snapshots) {
            if (s.status() == NodeStatus.UP) {
                reachableNodes++;
            } else if (s.status() == NodeStatus.STALE) {
                staleNodes++;
            } else {
                unreachableNodes++;
            }

            totalActiveTasks += s.activeTasks();
            totalCompletedTasks += s.completedTasks();
            totalFailedTasks += s.failedTasks();
            totalPlatformThreads += s.livePlatformThreads();
            totalHeapUsedMb += s.heapUsedMb();
            totalHeapMaxMb += s.heapMaxMb();

            if (s.errorMessage() != null && !s.errorMessage().isBlank()) {
                collectionErrors++;
            }
        }

        totalHeapUsedMb = Math.round(totalHeapUsedMb * 10.0) / 10.0;
        totalHeapMaxMb = Math.round(totalHeapMaxMb * 10.0) / 10.0;

        String mode = determineClusterMode(totalNodes, isDemoMode, redisOnline);

        return new ClusterMetricsSummary(
                now,
                totalNodes,
                reachableNodes,
                staleNodes,
                unreachableNodes,
                totalActiveTasks,
                totalCompletedTasks,
                totalFailedTasks,
                totalPlatformThreads,
                totalHeapUsedMb,
                totalHeapMaxMb,
                collectionErrors,
                mode
        );
    }

    public boolean isRedisOnline() {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String res = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(res);
        } catch (Exception ex) {
            return false;
        }
    }

    private String determineClusterMode(int nodeCount, boolean isDemoMode, boolean redisOnline) {
        if (isDemoMode) {
            return "DEMO MODE (Simulated Cluster Data)";
        }
        if (redisOnline) {
            if (nodeCount > 1) {
                return "CLUSTER (Redis Active - " + nodeCount + " Nodes)";
            } else {
                return "SINGLE NODE (Redis Active - 1 Node Reporting)";
            }
        } else {
            return "STANDALONE (Redis Offline - Local Node Only)";
        }
    }

    private List<NodeMetricsSnapshot> generateDemoSnapshots() {
        long now = System.currentTimeMillis();
        Map<String, Integer> demoStates1 = Map.of("RUNNABLE", 14, "WAITING", 8, "TIMED_WAITING", 4, "BLOCKED", 0);
        Map<String, Integer> demoStates2 = Map.of("RUNNABLE", 10, "WAITING", 6, "TIMED_WAITING", 2, "BLOCKED", 0);
        Map<String, Integer> demoStates3 = Map.of("RUNNABLE", 4, "WAITING", 4, "TIMED_WAITING", 1, "BLOCKED", 0);

        return List.of(
                new NodeMetricsSnapshot("node-1 [DEMO]", now, NodeStatus.UP, 3600, 8, 142, 2, 152, 128.4, 512.0, 26, 38, 210, demoStates1, true, null, true),
                new NodeMetricsSnapshot("node-2 [DEMO]", now, NodeStatus.UP, 2400, 5, 98, 1, 104, 114.2, 512.0, 18, 25, 140, demoStates2, true, null, false),
                new NodeMetricsSnapshot("node-3 [DEMO]", now - 20000, NodeStatus.STALE, 1800, 0, 45, 0, 45, 82.5, 256.0, 9, 15, 60, demoStates3, true, "Snapshot is older than 15s threshold", false)
        );
    }
}
