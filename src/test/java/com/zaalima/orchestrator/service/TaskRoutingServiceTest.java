package com.zaalima.orchestrator.service;

import com.zaalima.orchestrator.exception.AgentNotFoundException;
import com.zaalima.orchestrator.exception.NoEligibleNodeException;
import com.zaalima.orchestrator.exception.NodeUtilizationException;
import com.zaalima.orchestrator.exception.TaskRoutingException;
import com.zaalima.orchestrator.loadbalancer.LoadBalancer;
import com.zaalima.orchestrator.model.Agent;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.redis.NodeLoadReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task Routing Service Unit Tests")
class TaskRoutingServiceTest {

    @Mock
    private AgentRegistryService agentRegistryService;

    @Mock
    private NodeLoadReader nodeLoadReader;

    @Mock
    private LoadBalancer loadBalancer;

    @Mock
    private TaskExecutionService taskExecutionService;

    private TaskRoutingService taskRoutingService;

    @BeforeEach
    void setUp() {
        taskRoutingService = new TaskRoutingService(
                agentRegistryService,
                nodeLoadReader,
                loadBalancer,
                taskExecutionService
        );
    }

    @Test
    @DisplayName("Should successfully select least-utilized node and route task to execution service")
    void testSuccessfulRouting_RoutesToSelectedNode() {
        Agent node1 = new Agent("node-1", "WORKER", "ACTIVE", 1000L);
        Agent node2 = new Agent("node-2", "WORKER", "ACTIVE", 2000L);
        when(agentRegistryService.getAllAgents()).thenReturn(List.of(node1, node2));

        Map<String, Integer> loads = Map.of("node-1", 10, "node-2", 2);
        when(nodeLoadReader.getNodeLoads()).thenReturn(loads);
        when(loadBalancer.selectNode(List.of("node-1", "node-2"), loads)).thenReturn("node-2");
        when(agentRegistryService.getAgent("node-2")).thenReturn(node2);

        Future<?> mockFuture = CompletableFuture.completedFuture(null);
        doReturn(mockFuture).when(taskExecutionService).submitTask(any(AgentTask.class));

        AgentTask routedTask = taskRoutingService.routeTask("task-100", "INFERENCE", "test-payload");

        assertNotNull(routedTask);
        assertEquals("task-100", routedTask.getTaskId());
        assertEquals("node-2", routedTask.getAgentId());
        assertEquals("INFERENCE", routedTask.getTaskType());
        assertEquals("test-payload", routedTask.getPayload());

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskExecutionService).submitTask(captor.capture());
        assertEquals("node-2", captor.getValue().getAgentId());
    }

    @Test
    @DisplayName("Should throw NoEligibleNodeException when node registry is empty")
    void testEmptyNodeRegistry_ThrowsNoEligibleNodeException() {
        when(agentRegistryService.getAllAgents()).thenReturn(Collections.emptyList());

        assertThrows(NoEligibleNodeException.class, () ->
                taskRoutingService.routeTask("task-1", "DEFAULT", "payload"));

        verifyNoInteractions(nodeLoadReader);
        verifyNoInteractions(loadBalancer);
        verifyNoInteractions(taskExecutionService);
    }

    @Test
    @DisplayName("Should throw NoEligibleNodeException when all registered nodes are inactive or unavailable")
    void testAllNodesUnavailable_ThrowsNoEligibleNodeException() {
        Agent inactive1 = new Agent("node-1", "WORKER", "INACTIVE", 1000L);
        Agent offline2 = new Agent("node-2", "WORKER", "OFFLINE", 2000L);
        when(agentRegistryService.getAllAgents()).thenReturn(List.of(inactive1, offline2));

        assertThrows(NoEligibleNodeException.class, () ->
                taskRoutingService.routeTask("task-1", "DEFAULT", "payload"));

        verifyNoInteractions(nodeLoadReader);
        verifyNoInteractions(loadBalancer);
        verifyNoInteractions(taskExecutionService);
    }

    @Test
    @DisplayName("Should throw NodeUtilizationException when Redis utilization service fails")
    void testUtilizationServiceFailure_ThrowsNodeUtilizationException() {
        Agent active = new Agent("node-1", "WORKER", "ACTIVE", 1000L);
        when(agentRegistryService.getAllAgents()).thenReturn(List.of(active));
        when(nodeLoadReader.getNodeLoads()).thenThrow(new RuntimeException("Redis connection timed out"));

        assertThrows(NodeUtilizationException.class, () ->
                taskRoutingService.routeTask("task-1", "DEFAULT", "payload"));

        verifyNoInteractions(loadBalancer);
        verifyNoInteractions(taskExecutionService);
    }

    @Test
    @DisplayName("Should verify routing does not occur when load balancer cannot select a node")
    void testRoutingDoesNotOccurWhenSelectionFails() {
        Agent active = new Agent("node-1", "WORKER", "ACTIVE", 1000L);
        when(agentRegistryService.getAllAgents()).thenReturn(List.of(active));
        when(nodeLoadReader.getNodeLoads()).thenReturn(Collections.emptyMap());
        when(loadBalancer.selectNode(any(), any()))
                .thenThrow(new NoEligibleNodeException("No valid metrics reported"));

        assertThrows(NoEligibleNodeException.class, () ->
                taskRoutingService.routeTask("task-1", "DEFAULT", "payload"));

        verifyNoInteractions(taskExecutionService);
    }

    @Test
    @DisplayName("Should throw TaskRoutingException when selected node becomes unavailable before routing")
    void testSelectedNodeBecomesUnavailableBeforeRouting_ThrowsTaskRoutingException() {
        Agent activeNode = new Agent("node-1", "WORKER", "ACTIVE", 1000L);
        when(agentRegistryService.getAllAgents()).thenReturn(List.of(activeNode));
        when(nodeLoadReader.getNodeLoads()).thenReturn(Map.of("node-1", 5));
        when(loadBalancer.selectNode(any(), any())).thenReturn("node-1");

        // Simulate agent becoming inactive or dropped right before submission
        when(agentRegistryService.getAgent("node-1"))
                .thenThrow(new AgentNotFoundException("Agent not found with ID: node-1"));

        assertThrows(TaskRoutingException.class, () ->
                taskRoutingService.routeTask("task-1", "DEFAULT", "payload"));

        verifyNoInteractions(taskExecutionService);
    }

    @Test
    @DisplayName("Should throw TaskRoutingException when TaskExecutionService fails during dispatch")
    void testRoutingExecutionFailure_ThrowsTaskRoutingException() {
        Agent activeNode = new Agent("node-1", "WORKER", "ACTIVE", 1000L);
        when(agentRegistryService.getAllAgents()).thenReturn(List.of(activeNode));
        when(nodeLoadReader.getNodeLoads()).thenReturn(Map.of("node-1", 5));
        when(loadBalancer.selectNode(any(), any())).thenReturn("node-1");
        when(agentRegistryService.getAgent("node-1")).thenReturn(activeNode);

        when(taskExecutionService.submitTask(any(AgentTask.class)))
                .thenThrow(new RuntimeException("Executor submission rejected"));

        assertThrows(TaskRoutingException.class, () ->
                taskRoutingService.routeTask("task-1", "DEFAULT", "payload"));
    }
}
