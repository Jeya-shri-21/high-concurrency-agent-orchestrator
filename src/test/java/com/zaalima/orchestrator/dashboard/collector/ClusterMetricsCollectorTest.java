package com.zaalima.orchestrator.dashboard.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import com.zaalima.orchestrator.model.Agent;
import com.zaalima.orchestrator.service.AgentRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cluster Metrics Collector Tests")
class ClusterMetricsCollectorTest {

    @Mock
    private RuntimeMetricsCollector localCollector;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private AgentRegistryService agentRegistryService;

    private DashboardProperties properties;
    private ObjectMapper objectMapper;
    private ClusterMetricsCollector collector;

    private NodeMetricsSnapshot localSnapshot;

    @BeforeEach
    void setUp() {
        properties = new DashboardProperties();
        properties.setStaleThresholdMs(15000);
        properties.setReportKeyPrefix("node:metrics:");
        objectMapper = new ObjectMapper();

        localSnapshot = new NodeMetricsSnapshot(
                "node-1",
                System.currentTimeMillis(),
                NodeStatus.UP,
                1000,
                3,
                50,
                1,
                54,
                120.0,
                512.0,
                20,
                30,
                150,
                Map.of("RUNNABLE", 10, "WAITING", 10),
                true,
                null,
                true
        );

        lenient().when(localCollector.collectLocalMetrics()).thenReturn(localSnapshot);

        collector = new ClusterMetricsCollector(
                localCollector,
                redisTemplate,
                properties,
                objectMapper,
                agentRegistryService
        );
    }

    @Test
    @DisplayName("Should collect and aggregate multiple healthy nodes from Redis")
    void testCollectClusterSnapshots_MultipleHealthyNodes() throws Exception {
        when(redisTemplate.keys("node:metrics:*")).thenReturn(Set.of("node:metrics:node-1", "node:metrics:node-2"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        NodeMetricsSnapshot remoteSnapshot = new NodeMetricsSnapshot(
                "node-2",
                System.currentTimeMillis(),
                NodeStatus.UP,
                2000,
                5,
                70,
                0,
                75,
                150.0,
                512.0,
                25,
                35,
                180,
                Map.of("RUNNABLE", 15, "WAITING", 10),
                true,
                null,
                false
        );

        when(valueOperations.get("node:metrics:node-1")).thenReturn(objectMapper.writeValueAsString(localSnapshot));
        when(valueOperations.get("node:metrics:node-2")).thenReturn(objectMapper.writeValueAsString(remoteSnapshot));

        List<NodeMetricsSnapshot> snapshots = collector.collectClusterSnapshots(false);

        assertEquals(2, snapshots.size());
        assertEquals("node-1", snapshots.get(0).nodeId());
        assertEquals("node-2", snapshots.get(1).nodeId());
        assertEquals(NodeStatus.UP, snapshots.get(0).status());
        assertEquals(NodeStatus.UP, snapshots.get(1).status());

        ClusterMetricsSummary summary = collector.aggregate(snapshots, false, true);

        assertEquals(2, summary.totalNodes());
        assertEquals(2, summary.reachableNodes());
        assertEquals(0, summary.staleNodes());
        assertEquals(0, summary.unreachableNodes());
        assertEquals(8, summary.totalActiveTasks()); // 3 + 5
        assertEquals(120, summary.totalCompletedTasks()); // 50 + 70
        assertEquals(45, summary.totalLivePlatformThreads()); // 20 + 25
        assertEquals(270.0, summary.totalHeapUsedMb()); // 120 + 150
        assertTrue(summary.clusterMode().contains("CLUSTER"));
    }

    @Test
    @DisplayName("Should detect STALE nodes when timestamp exceeds stale threshold")
    void testCollectClusterSnapshots_StaleNodeDetection() throws Exception {
        when(redisTemplate.keys("node:metrics:*")).thenReturn(Set.of("node:metrics:node-stale"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        long staleTimestamp = System.currentTimeMillis() - 25000; // 25s ago (> 15s threshold)
        NodeMetricsSnapshot staleNode = new NodeMetricsSnapshot(
                "node-stale",
                staleTimestamp,
                NodeStatus.UP, // was UP when published
                500,
                0,
                10,
                0,
                10,
                80.0,
                256.0,
                10,
                15,
                50,
                Collections.emptyMap(),
                true,
                null,
                false
        );

        when(valueOperations.get("node:metrics:node-stale")).thenReturn(objectMapper.writeValueAsString(staleNode));

        List<NodeMetricsSnapshot> snapshots = collector.collectClusterSnapshots(false);

        assertEquals(2, snapshots.size()); // node-1 (local) and node-stale
        NodeMetricsSnapshot foundStale = snapshots.stream()
                .filter(s -> s.nodeId().equals("node-stale"))
                .findFirst()
                .orElseThrow();

        assertEquals(NodeStatus.STALE, foundStale.status(), "Node snapshot older than 15s must be marked STALE");

        ClusterMetricsSummary summary = collector.aggregate(snapshots, false, true);
        assertEquals(1, summary.reachableNodes());
        assertEquals(1, summary.staleNodes());
    }

    @Test
    @DisplayName("Should identify DOWN nodes when registered agents have no reported metrics")
    void testCollectClusterSnapshots_IdentifiesDownNodes() {
        when(redisTemplate.keys("node:metrics:*")).thenReturn(Collections.emptySet());

        Agent registeredAgent = new Agent("agent-worker-99", "WORKER", "ACTIVE", 1000L);
        when(agentRegistryService.getAllAgents()).thenReturn(List.of(registeredAgent));

        List<NodeMetricsSnapshot> snapshots = collector.collectClusterSnapshots(false);

        assertEquals(2, snapshots.size()); // local node + missing agent
        NodeMetricsSnapshot downNode = snapshots.stream()
                .filter(s -> s.nodeId().equals("agent-worker-99"))
                .findFirst()
                .orElseThrow();

        assertEquals(NodeStatus.DOWN, downNode.status());
        assertNotNull(downNode.errorMessage());

        ClusterMetricsSummary summary = collector.aggregate(snapshots, false, true);
        assertEquals(1, summary.unreachableNodes());
    }

    @Test
    @DisplayName("Should fallback cleanly to local node when Redis is unreachable")
    void testCollectClusterSnapshots_RedisUnavailable_FallbackToLocal() {
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        List<NodeMetricsSnapshot> snapshots = collector.collectClusterSnapshots(false);

        assertNotNull(snapshots);
        assertEquals(1, snapshots.size());
        assertEquals("node-1", snapshots.get(0).nodeId());
        assertEquals(NodeStatus.UP, snapshots.get(0).status());
        assertTrue(snapshots.get(0).isLocal());

        ClusterMetricsSummary summary = collector.aggregate(snapshots, false, false);
        assertEquals(1, summary.totalNodes());
        assertEquals(1, summary.reachableNodes());
        assertTrue(summary.clusterMode().contains("STANDALONE"));
    }

    @Test
    @DisplayName("Should generate segregated simulated snapshots in demo mode")
    void testCollectClusterSnapshots_DemoMode() {
        List<NodeMetricsSnapshot> demoSnapshots = collector.collectClusterSnapshots(true);

        assertNotNull(demoSnapshots);
        assertEquals(3, demoSnapshots.size());
        assertTrue(demoSnapshots.get(0).nodeId().contains("[DEMO]"));

        ClusterMetricsSummary summary = collector.aggregate(demoSnapshots, true, false);
        assertEquals(3, summary.totalNodes());
        assertEquals(2, summary.reachableNodes());
        assertEquals(1, summary.staleNodes());
        assertTrue(summary.clusterMode().contains("DEMO MODE"));
    }
}
