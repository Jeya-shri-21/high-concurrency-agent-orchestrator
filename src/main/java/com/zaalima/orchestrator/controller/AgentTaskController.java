package com.zaalima.orchestrator.controller;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.service.TaskExecutionService;
import com.zaalima.orchestrator.service.TaskRoutingService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class AgentTaskController {

    private final TaskExecutionService taskExecutionService;
    private final TaskRoutingService taskRoutingService;

    public AgentTaskController(
            TaskExecutionService taskExecutionService,
            TaskRoutingService taskRoutingService) {
        this.taskExecutionService = taskExecutionService;
        this.taskRoutingService = taskRoutingService;
    }

    @PostMapping
    public AgentTask submitTask(@RequestBody TaskRequest request) {
        // If agentId is not specified or is "auto", route using least-utilized load balancing
        if (request.agentId() == null || request.agentId().isBlank() || "auto".equalsIgnoreCase(request.agentId().trim())) {
            return taskRoutingService.routeTask(request.taskType(), request.payload());
        }

        // Direct submission to specified agent preserves existing behaviour
        AgentTask task = new AgentTask(
            UUID.randomUUID().toString(),
            request.agentId().trim(),
            request.taskType(),
            request.payload()
        );

        taskExecutionService.submitTask(task);
        return task;
    }

    @PostMapping("/route")
    public AgentTask routeTask(@RequestBody RoutedTaskRequest request) {
        return taskRoutingService.routeTask(request.taskType(), request.payload());
    }

    @GetMapping("/load-balancer/status")
    public Map<String, Object> getLoadBalancerStatus() {
        return Map.of(
            "leastUtilizedNode", taskRoutingService.getLeastUtilizedNode(),
            "eligibleNodes", taskRoutingService.discoverEligibleNodes(),
            "status", "OK"
        );
    }

    @GetMapping("/{taskId}")
    public AgentTask getTask(@PathVariable String taskId) {
        AgentTask task = taskExecutionService.getTask(taskId);

        if (task == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Task not found: " + taskId
            );
        }

        return task;
    }

    public record TaskRequest(
        String agentId,
        String taskType,
        String payload
    ) {}

    public record RoutedTaskRequest(
        String taskType,
        String payload
    ) {}
}
