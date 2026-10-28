package com.zaalima.orchestrator.service;

import com.zaalima.orchestrator.exception.NoEligibleNodeException;
import com.zaalima.orchestrator.exception.NodeUtilizationException;
import com.zaalima.orchestrator.exception.TaskRoutingException;
import com.zaalima.orchestrator.loadbalancer.LoadBalancer;
import com.zaalima.orchestrator.model.Agent;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.redis.NodeLoadReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Service orchestrating least-utilized node selection and task payload routing.
 * Integrates AgentRegistryService, Redis-based NodeLoadReader, and TaskExecutionService.
 */
@Service
public class TaskRoutingService {

    private static final Logger log = LoggerFactory.getLogger(TaskRoutingService.class);

    private final AgentRegistryService agentRegistryService;
    private final NodeLoadReader nodeLoadReader;
    private final LoadBalancer loadBalancer;
    private final TaskExecutionService taskExecutionService;

    public TaskRoutingService(
            AgentRegistryService agentRegistryService,
            NodeLoadReader nodeLoadReader,
            LoadBalancer loadBalancer,
            TaskExecutionService taskExecutionService) {
        this.agentRegistryService = Objects.requireNonNull(agentRegistryService, "agentRegistryService must not be null");
        this.nodeLoadReader = Objects.requireNonNull(nodeLoadReader, "nodeLoadReader must not be null");
        this.loadBalancer = Objects.requireNonNull(loadBalancer, "loadBalancer must not be null");
        this.taskExecutionService = Objects.requireNonNull(taskExecutionService, "taskExecutionService must not be null");
    }

    /**
     * Discovers eligible nodes, selects the least-utilized node, and routes the incoming task payload.
     *
     * @param taskType category or functional type of the task
     * @param payload input data or task instructions
     * @return the routed AgentTask submitted for execution
     */
    public AgentTask routeTask(String taskType, String payload) {
        String generatedTaskId = UUID.randomUUID().toString();
        return routeTask(generatedTaskId, taskType, payload);
    }

    /**
     * Discovers eligible nodes, selects the least-utilized node, and routes the incoming task payload with an explicit taskId.
     *
     * @param taskId unique identifier for the task
     * @param taskType category or functional type of the task
     * @param payload input data or task instructions
     * @return the routed AgentTask submitted for execution
     */
    public AgentTask routeTask(String taskId, String taskType, String payload) {
        String effectiveTaskId = (taskId != null && !taskId.isBlank()) ? taskId.trim() : UUID.randomUUID().toString();
        String effectiveTaskType = (taskType != null && !taskType.isBlank()) ? taskType.trim() : "DEFAULT";
        String effectivePayload = (payload != null) ? payload : "";

        // 1. Discover eligible registered nodes
        List<String> eligibleNodeIds = discoverEligibleNodes();
        if (eligibleNodeIds.isEmpty()) {
            throw new NoEligibleNodeException("No eligible active nodes registered to accept tasks");
        }

        // 2. Obtain node utilization metrics from Redis via Member 2's NodeLoadReader
        Map<String, Integer> nodeLoads;
        try {
            nodeLoads = nodeLoadReader.getNodeLoads();
        } catch (Exception ex) {
            log.error("Failed to retrieve node utilization metrics from Redis: {}", ex.getMessage());
            throw new NodeUtilizationException("Node utilization service unavailable: " + ex.getMessage(), ex);
        }

        // 3. Select the least-utilized node via LoadBalancer
        String selectedNodeId = loadBalancer.selectNode(eligibleNodeIds, nodeLoads);
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            throw new NoEligibleNodeException("Load balancer failed to select a node");
        }

        // 4. Pre-routing validation: ensure selected node is still registered and active
        Agent verifiedAgent;
        try {
            verifiedAgent = agentRegistryService.getAgent(selectedNodeId);
        } catch (Exception ex) {
            throw new TaskRoutingException("Selected node '" + selectedNodeId + "' became unavailable before routing", ex);
        }

        if (verifiedAgent == null || !"ACTIVE".equalsIgnoreCase(verifiedAgent.getStatus())) {
            throw new TaskRoutingException("Selected node '" + selectedNodeId + "' became inactive before routing");
        }

        // 5. Construct and route task to the selected node through TaskExecutionService
        AgentTask task = new AgentTask(effectiveTaskId, selectedNodeId, effectiveTaskType, effectivePayload);

        try {
            Future<?> future = taskExecutionService.submitTask(task);
            if (future == null) {
                throw new TaskRoutingException("Execution service failed to accept task: " + effectiveTaskId);
            }
            log.info("Task '{}' successfully routed to least-utilized node '{}'", effectiveTaskId, selectedNodeId);
        } catch (Exception ex) {
            log.error("Failed to dispatch task '{}' to node '{}': {}", effectiveTaskId, selectedNodeId, ex.getMessage());
            throw new TaskRoutingException("Failed to route task to node '" + selectedNodeId + "': " + ex.getMessage(), ex);
        }

        return task;
    }

    /**
     * Discovers all registered nodes from AgentRegistryService that have an "ACTIVE" status.
     * Excludes null, blank, inactive, or unhealthy nodes.
     *
     * @return list of eligible active node IDs
     */
    public List<String> discoverEligibleNodes() {
        List<Agent> agents = agentRegistryService.getAllAgents();
        if (agents == null || agents.isEmpty()) {
            return List.of();
        }

        return agents.stream()
                .filter(agent -> agent != null && agent.getAgentId() != null && !agent.getAgentId().isBlank())
                .filter(agent -> "ACTIVE".equalsIgnoreCase(agent.getStatus()))
                .map(Agent::getAgentId)
                .toList();
    }

    /**
     * Inspects and returns the currently least-utilized eligible node without submitting a task.
     *
     * @return selected node ID
     */
    public String getLeastUtilizedNode() {
        List<String> eligibleNodes = discoverEligibleNodes();
        if (eligibleNodes.isEmpty()) {
            throw new NoEligibleNodeException("No eligible active nodes registered");
        }

        Map<String, Integer> nodeLoads;
        try {
            nodeLoads = nodeLoadReader.getNodeLoads();
        } catch (Exception ex) {
            throw new NodeUtilizationException("Node utilization service unavailable: " + ex.getMessage(), ex);
        }

        return loadBalancer.selectNode(eligibleNodes, nodeLoads);
    }
}
