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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentRegistryServiceTest {

    @Mock
    private AgentGrpcService agentGrpcService;

    private AgentRegistryService agentRegistryService;

    @BeforeEach
    void setUp() {
        agentRegistryService = new AgentRegistryService(agentGrpcService);
    }

    @Test
    @DisplayName("Register agent succeeds when ID is not registered and gRPC service succeeds")
    void testRegisterAgent_Success() {
        AgentRegistrationRequest request = new AgentRegistrationRequest("agent-alpha", "analytics-worker");

        when(agentGrpcService.isAgentRegistered("agent-alpha")).thenReturn(false);

        // Mock gRPC behavior
        doAnswer(invocation -> {
            StreamObserver<RegisterAgentResponse> observer = invocation.getArgument(1);
            observer.onNext(RegisterAgentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Agent registered successfully: agent-alpha")
                    .build());
            observer.onCompleted();
            return null;
        }).when(agentGrpcService).registerAgent(any(RegisterAgentRequest.class), any());

        AgentResponse response = agentRegistryService.registerAgent(request);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals("agent-alpha", response.agentId());
        assertEquals("analytics-worker", response.agentType());
        assertEquals("ACTIVE", response.status());
        assertTrue(response.registeredAt() > 0);

        // Verify gRPC call
        ArgumentCaptor<RegisterAgentRequest> captor = ArgumentCaptor.forClass(RegisterAgentRequest.class);
        verify(agentGrpcService, times(1)).registerAgent(captor.capture(), any());
        assertEquals("agent-alpha", captor.getValue().getAgentId());
        assertEquals("analytics-worker", captor.getValue().getAgentType());
    }

    @Test
    @DisplayName("Register agent throws DuplicateAgentException when ID is already registered")
    void testRegisterAgent_DuplicateId_ThrowsException() {
        AgentRegistrationRequest request = new AgentRegistrationRequest("agent-existing", "worker");
        when(agentGrpcService.isAgentRegistered("agent-existing")).thenReturn(true);

        DuplicateAgentException ex = assertThrows(DuplicateAgentException.class, () ->
                agentRegistryService.registerAgent(request)
        );

        assertTrue(ex.getMessage().contains("Agent with ID 'agent-existing' is already registered"));
        verify(agentGrpcService, never()).registerAgent(any(), any());
    }

    @Test
    @DisplayName("Register agent throws exception when gRPC service returns failure response")
    void testRegisterAgent_GrpcFailure_ThrowsException() {
        AgentRegistrationRequest request = new AgentRegistrationRequest("agent-fail", "worker");
        when(agentGrpcService.isAgentRegistered("agent-fail")).thenReturn(false);

        doAnswer(invocation -> {
            StreamObserver<RegisterAgentResponse> observer = invocation.getArgument(1);
            observer.onNext(RegisterAgentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Internal registry rejection")
                    .build());
            observer.onCompleted();
            return null;
        }).when(agentGrpcService).registerAgent(any(RegisterAgentRequest.class), any());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                agentRegistryService.registerAgent(request)
        );

        assertTrue(ex.getMessage().contains("Internal registry rejection"));
    }

    @Test
    @DisplayName("Get agent returns existing agent details")
    void testGetAgent_ExistingAgent_ReturnsAgent() {
        AgentRegistrationRequest regRequest = new AgentRegistrationRequest("agent-007", "security");
        when(agentGrpcService.isAgentRegistered("agent-007")).thenReturn(false, true);

        doAnswer(invocation -> {
            StreamObserver<RegisterAgentResponse> observer = invocation.getArgument(1);
            observer.onNext(RegisterAgentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Registered")
                    .build());
            observer.onCompleted();
            return null;
        }).when(agentGrpcService).registerAgent(any(RegisterAgentRequest.class), any());

        agentRegistryService.registerAgent(regRequest);

        Agent agent = agentRegistryService.getAgent("agent-007");
        assertNotNull(agent);
        assertEquals("agent-007", agent.getAgentId());
        assertEquals("security", agent.getAgentType());
        assertEquals("ACTIVE", agent.getStatus());
    }

    @Test
    @DisplayName("Get agent for unregistered agent throws AgentNotFoundException")
    void testGetAgent_UnregisteredAgent_ThrowsException() {
        when(agentGrpcService.isAgentRegistered("agent-unknown")).thenReturn(false);

        assertThrows(AgentNotFoundException.class, () ->
                agentRegistryService.getAgent("agent-unknown")
        );
    }

    @Test
    @DisplayName("Get all agents lists agents registered across both REST and gRPC")
    void testGetAllAgents_CombinesRegistry() {
        when(agentGrpcService.getRegisteredAgents()).thenReturn(Map.of(
                "agent-grpc-1", "grpc-worker",
                "agent-grpc-2", "calculator"
        ));

        List<Agent> agents = agentRegistryService.getAllAgents();
        assertEquals(2, agents.size());
    }

    @Test
    @DisplayName("isAgentRegistered delegates directly to AgentGrpcService")
    void testIsAgentRegistered_Delegation() {
        when(agentGrpcService.isAgentRegistered("agent-live")).thenReturn(true);
        when(agentGrpcService.isAgentRegistered("agent-offline")).thenReturn(false);

        assertTrue(agentRegistryService.isAgentRegistered("agent-live"));
        assertFalse(agentRegistryService.isAgentRegistered("agent-offline"));
        assertFalse(agentRegistryService.isAgentRegistered(null));
    }
}
