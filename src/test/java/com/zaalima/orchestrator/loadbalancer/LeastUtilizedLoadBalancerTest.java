package com.zaalima.orchestrator.loadbalancer;

import com.zaalima.orchestrator.exception.NoEligibleNodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Least-Utilized Load Balancer Unit Tests")
class LeastUtilizedLoadBalancerTest {

    private LeastUtilizedLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        loadBalancer = new LeastUtilizedLoadBalancer();
    }

    @Test
    @DisplayName("Should select the least-utilized node when comparing three nodes with distinct loads")
    void testSelectLeastUtilizedNode_ThreeNodes() {
        // Node A: 80, Node B: 30, Node C: 60 -> Expect Node B
        List<String> eligibleNodes = List.of("node-A", "node-B", "node-C");
        Map<String, Integer> loads = Map.of(
                "node-A", 80,
                "node-B", 30,
                "node-C", 60
        );

        String selected = loadBalancer.selectNode(eligibleNodes, loads);
        assertEquals("node-B", selected, "Node B has the lowest utilization (30) and should be selected");
    }

    @Test
    @DisplayName("Should select the least-utilized node when multiple nodes have varying active tasks")
    void testSelectLeastUtilizedNode_MultipleNodes() {
        List<String> eligibleNodes = List.of("worker-1", "worker-2", "worker-3", "worker-4");
        Map<String, Integer> loads = Map.of(
                "worker-1", 12,
                "worker-2", 4,
                "worker-3", 1,
                "worker-4", 8
        );

        String selected = loadBalancer.selectNode(eligibleNodes, loads);
        assertEquals("worker-3", selected);
    }

    @Test
    @DisplayName("Should deterministically break ties using lexicographical node ID ordering")
    void testDeterministicTieBreaking_EqualUtilization() {
        // node-Z and node-A both have utilization 5; node-A should win alphabetically
        List<String> eligibleNodes = List.of("node-Z", "node-A", "node-M");
        Map<String, Integer> loads = Map.of(
                "node-Z", 5,
                "node-A", 5,
                "node-M", 20
        );

        // Run multiple times to verify deterministic stability
        for (int i = 0; i < 5; i++) {
            String selected = loadBalancer.selectNode(eligibleNodes, loads);
            assertEquals("node-A", selected, "Tie-break should deterministically select node-A");
        }
    }

    @Test
    @DisplayName("Should throw NoEligibleNodeException when eligible nodes list is empty or null")
    void testEmptyOrNullEligibleNodes_ThrowsException() {
        Map<String, Integer> loads = Map.of("node-1", 10);

        assertThrows(NoEligibleNodeException.class, () ->
                loadBalancer.selectNode(Collections.emptyList(), loads));

        assertThrows(NoEligibleNodeException.class, () ->
                loadBalancer.selectNode(null, loads));
    }

    @Test
    @DisplayName("Should throw NoEligibleNodeException when node utilization map is empty or null")
    void testEmptyOrNullUtilizationMap_ThrowsException() {
        List<String> eligibleNodes = List.of("node-1", "node-2");

        assertThrows(NoEligibleNodeException.class, () ->
                loadBalancer.selectNode(eligibleNodes, Collections.emptyMap()));

        assertThrows(NoEligibleNodeException.class, () ->
                loadBalancer.selectNode(eligibleNodes, null));
    }

    @Test
    @DisplayName("Should skip nodes with missing utilization data and NEVER treat unknown as zero")
    void testMissingUtilizationData_NeverTreatedAsZero() {
        // node-1 is missing from Redis metrics. node-2 has load 5.
        // If node-1 were treated as 0, node-1 would be incorrectly selected.
        // With correct logic, node-1 is skipped and node-2 is selected.
        List<String> eligibleNodes = List.of("node-1", "node-2");
        Map<String, Integer> loads = Map.of("node-2", 5);

        String selected = loadBalancer.selectNode(eligibleNodes, loads);
        assertEquals("node-2", selected, "node-1 has unknown utilization and must not be treated as 0");
    }

    @Test
    @DisplayName("Should throw NoEligibleNodeException when all eligible nodes have missing utilization")
    void testAllEligibleNodesMissingUtilization_ThrowsException() {
        List<String> eligibleNodes = List.of("node-X", "node-Y");
        // loads only contains unrelated nodes
        Map<String, Integer> loads = Map.of("node-Z", 2);

        assertThrows(NoEligibleNodeException.class, () ->
                loadBalancer.selectNode(eligibleNodes, loads));
    }

    @Test
    @DisplayName("Should ignore invalid negative utilization and throw if no valid candidates remain")
    void testInvalidNegativeUtilization_Ignored() {
        List<String> eligibleNodes = List.of("node-1", "node-2");
        Map<String, Integer> loads = new HashMap<>();
        loads.put("node-1", -5); // invalid
        loads.put("node-2", 10); // valid

        String selected = loadBalancer.selectNode(eligibleNodes, loads);
        assertEquals("node-2", selected, "node-1 has invalid negative load and must be skipped");

        // Both negative -> should throw
        Map<String, Integer> allNegative = Map.of("node-1", -1, "node-2", -10);
        assertThrows(NoEligibleNodeException.class, () ->
                loadBalancer.selectNode(eligibleNodes, allNegative));
    }
}
