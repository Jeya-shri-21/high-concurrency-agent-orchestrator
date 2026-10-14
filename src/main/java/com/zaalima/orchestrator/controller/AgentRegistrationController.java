package com.zaalima.orchestrator.controller;

import com.zaalima.orchestrator.dto.AgentRegistrationRequest;
import com.zaalima.orchestrator.dto.AgentResponse;
import com.zaalima.orchestrator.model.Agent;
import com.zaalima.orchestrator.service.AgentRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST Controller exposing endpoints for autonomous agent registration and lookup.
 * Conforms to Member 4 specifications.
 */
@RestController
@RequestMapping("/api/agents")
@CrossOrigin(origins = "*")
public class AgentRegistrationController {

    private final AgentRegistryService agentRegistryService;

    public AgentRegistrationController(AgentRegistryService agentRegistryService) {
        this.agentRegistryService = agentRegistryService;
    }

    /**
     * Registers a new agent with the orchestrator.
     *
     * @param request the registration details, validated with Jakarta validation
     * @return HTTP 201 Created with agent registration details
     */
    @PostMapping
    public ResponseEntity<AgentResponse> registerAgent(@Valid @RequestBody AgentRegistrationRequest request) {
        AgentResponse response = agentRegistryService.registerAgent(request);
        URI location = URI.create("/api/agents/" + response.agentId());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves details for a specific registered agent by ID.
     *
     * @param agentId unique ID of the agent
     * @return HTTP 200 OK with the Agent entity
     */
    @GetMapping("/{agentId}")
    public ResponseEntity<Agent> getAgent(@PathVariable String agentId) {
        Agent agent = agentRegistryService.getAgent(agentId);
        return ResponseEntity.ok(agent);
    }

    /**
     * Retrieves all registered agents in the orchestrator.
     *
     * @return HTTP 200 OK with list of agents
     */
    @GetMapping
    public ResponseEntity<List<Agent>> getAllAgents() {
        List<Agent> agents = agentRegistryService.getAllAgents();
        return ResponseEntity.ok(agents);
    }

    /**
     * Checks if a specific agent ID is currently registered.
     *
     * @param agentId unique ID of the agent
     * @return HTTP 200 OK with registration status
     */
    @GetMapping("/{agentId}/status")
    public ResponseEntity<Map<String, Object>> getAgentStatus(@PathVariable String agentId) {
        boolean registered = agentRegistryService.isAgentRegistered(agentId);
        return ResponseEntity.ok(Map.of(
                "agentId", agentId,
                "registered", registered
        ));
    }
}
