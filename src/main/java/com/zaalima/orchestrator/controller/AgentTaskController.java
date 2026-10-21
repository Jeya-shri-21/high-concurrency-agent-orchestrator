
package com.zaalima.orchestrator.controller;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.service.TaskExecutionService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class AgentTaskController {

    private final TaskExecutionService taskExecutionService;

    public AgentTaskController(
            TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    @PostMapping
    public AgentTask submitTask(@RequestBody TaskRequest request) {

        AgentTask task = new AgentTask(
            UUID.randomUUID().toString(),
            request.agentId(),
            request.taskType(),
            request.payload()
        );

        taskExecutionService.submitTask(task);

        return task;
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
}