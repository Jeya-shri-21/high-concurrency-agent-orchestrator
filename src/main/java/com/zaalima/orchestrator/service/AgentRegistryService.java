package com.zaalima.orchestrator.service;

import com.zaalima.orchestrator.dto.AgentRegistrationRequest;
import com.zaalima.orchestrator.dto.AgentResponse;
import com.zaalima.orchestrator.exception.AgentNotFoundException;
import com.zaalima.orchestrator.exception.DuplicateAgentException;
import com.zaalima.orchestrator.grpc.AgentGrpcService;
import com.zaalima.orchestrator.grpc.RegisterAgentRequest;
import com.zaalima.orchestrator.grpc.RegisterAgentResponse;
import com.zaalima.orchestrator.model.Agent;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service managing agent registration and lookup.
 * Acts as the bridge between REST endpoints and the orchestrator's agent registry,
 * integrating directly with Member 3's gRPC service interface without duplicating business logic.
 */
@Service
public class AgentRegistryService {

    private final AgentGrpcService agentGrpcService;
    private final Map<String, Agent> agentMetadata = new ConcurrentHashMap<>();

    public AgentRegistryService(AgentGrpcService agentGrpcService) {
        this.agentGrpcService = agentGrpcService;
    }

    /**
     * Registers a new agent in the orchestrator.
     *
     * @param request the registration details
     * @return AgentResponse confirming successful registration
     * @throws DuplicateAgentException if an agent with the given ID is already registered
     * @throws IllegalArgumentException if the request is invalid
     */
    public AgentResponse registerAgent(AgentRegistrationRequest request) {
        String agentId = request.agentId().trim();
        String agentType = request.agentType().trim();

        if (agentGrpcService.isAgentRegistered(agentId)) {
            throw new DuplicateAgentException("Agent with ID '" + agentId + "' is already registered");
        }

        // Bridge request to Member 3's existing gRPC registration method
        RegisterAgentRequest grpcRequest = RegisterAgentRequest.newBuilder()
                .setAgentId(agentId)
                .setAgentType(agentType)
                .build();

        AtomicReference<RegisterAgentResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        agentGrpcService.registerAgent(grpcRequest, new StreamObserver<RegisterAgentResponse>() {
            @Override
            public void onNext(RegisterAgentResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
            }

            @Override
            public void onCompleted() {
                // Completed synchronously
            }
        });

        if (errorRef.get() != null) {
            throw new RuntimeException("Underlying registration service failed: " + errorRef.get().getMessage(), errorRef.get());
        }

        RegisterAgentResponse response = responseRef.get();
        if (response == null || !response.getSuccess()) {
            String errorMsg = (response != null) ? response.getMessage() : "Registration failed";
            throw new IllegalArgumentException(errorMsg);
        }

        long registeredAt = System.currentTimeMillis();
        Agent agent = new Agent(agentId, agentType, "ACTIVE", registeredAt);
        agentMetadata.put(agentId, agent);

        return new AgentResponse(true, response.getMessage(), agentId, agentType, "ACTIVE", registeredAt);
    }

    /**
     * Retrieves an agent by its unique ID.
     *
     * @param agentId unique ID of the agent
     * @return the Agent entity
     * @throws AgentNotFoundException if no agent is found
     */
    public Agent getAgent(String agentId) {
        if (agentId == null || !agentGrpcService.isAgentRegistered(agentId.trim())) {
            throw new AgentNotFoundException("Agent not found with ID: " + agentId);
        }

        String trimmedId = agentId.trim();
        Agent agent = agentMetadata.get(trimmedId);
        if (agent == null) {
            // Agent was registered directly through gRPC; construct metadata entry
            String agentType = agentGrpcService.getRegisteredAgents().get(trimmedId);
            agent = new Agent(trimmedId, agentType != null ? agentType : "UNKNOWN", "ACTIVE", System.currentTimeMillis());
            agentMetadata.put(trimmedId, agent);
        }

        return agent;
    }

    /**
     * Returns a list of all currently registered agents across both REST and gRPC.
     *
     * @return list of registered agents
     */
    public List<Agent> getAllAgents() {
        Map<String, String> registered = agentGrpcService.getRegisteredAgents();
        List<Agent> agents = new ArrayList<>();

        for (Map.Entry<String, String> entry : registered.entrySet()) {
            String id = entry.getKey();
            Agent agent = agentMetadata.get(id);
            if (agent == null) {
                agent = new Agent(id, entry.getValue(), "ACTIVE", System.currentTimeMillis());
                agentMetadata.put(id, agent);
            }
            agents.add(agent);
        }

        return agents;
    }

    /**
     * Checks if an agent is currently registered.
     *
     * @param agentId unique ID of the agent
     * @return true if registered, false otherwise
     */
    public boolean isAgentRegistered(String agentId) {
        return agentId != null && agentGrpcService.isAgentRegistered(agentId.trim());
    }
}
