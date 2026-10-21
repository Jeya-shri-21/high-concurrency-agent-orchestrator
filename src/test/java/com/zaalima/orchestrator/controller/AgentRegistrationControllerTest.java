package com.zaalima.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.dto.AgentRegistrationRequest;
import com.zaalima.orchestrator.dto.AgentResponse;
import com.zaalima.orchestrator.exception.AgentNotFoundException;
import com.zaalima.orchestrator.exception.DuplicateAgentException;
import com.zaalima.orchestrator.exception.GlobalExceptionHandler;
import com.zaalima.orchestrator.model.Agent;
import com.zaalima.orchestrator.service.AgentRegistryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AgentRegistrationController.class)
@Import(GlobalExceptionHandler.class)
class AgentRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AgentRegistryService agentRegistryService;

    @Test
    @DisplayName("POST /api/agents with valid body returns 201 Created and Location header")
    void testRegisterAgent_ValidRequest_Returns201Created() throws Exception {
        AgentRegistrationRequest request = new AgentRegistrationRequest("agent-01", "worker");
        AgentResponse expectedResponse = new AgentResponse(
                true,
                "Agent registered successfully: agent-01",
                "agent-01",
                "worker",
                "ACTIVE",
                System.currentTimeMillis()
        );

        when(agentRegistryService.registerAgent(any(AgentRegistrationRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/agents/agent-01"))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.agentId", is("agent-01")))
                .andExpect(jsonPath("$.agentType", is("worker")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.message", containsString("Agent registered successfully")));
    }

    @Test
    @DisplayName("POST /api/agents with blank agentId returns 400 Bad Request with validation errors")
    void testRegisterAgent_BlankAgentId_Returns400BadRequest() throws Exception {
        AgentRegistrationRequest request = new AgentRegistrationRequest("   ", "worker");

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.validationErrors.agentId", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/agents with blank agentType returns 400 Bad Request with validation errors")
    void testRegisterAgent_BlankAgentType_Returns400BadRequest() throws Exception {
        AgentRegistrationRequest request = new AgentRegistrationRequest("agent-02", "   ");

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.agentType", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/agents with invalid characters in agentId returns 400 Bad Request")
    void testRegisterAgent_InvalidPattern_Returns400BadRequest() throws Exception {
        AgentRegistrationRequest request = new AgentRegistrationRequest("agent@bad!", "worker");

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.agentId", containsString("alphanumeric")));
    }

    @Test
    @DisplayName("POST /api/agents with duplicate ID returns 409 Conflict")
    void testRegisterAgent_DuplicateId_Returns409Conflict() throws Exception {
        AgentRegistrationRequest request = new AgentRegistrationRequest("agent-duplicate", "worker");

        when(agentRegistryService.registerAgent(any(AgentRegistrationRequest.class)))
                .thenThrow(new DuplicateAgentException("Agent with ID 'agent-duplicate' is already registered"));

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("GET /api/agents/{agentId} returns 200 OK with agent details")
    void testGetAgent_ExistingAgent_Returns200Ok() throws Exception {
        Agent agent = new Agent("agent-10", "analyzer", "ACTIVE", System.currentTimeMillis());

        when(agentRegistryService.getAgent("agent-10")).thenReturn(agent);

        mockMvc.perform(get("/api/agents/agent-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId", is("agent-10")))
                .andExpect(jsonPath("$.agentType", is("analyzer")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("GET /api/agents/{agentId} for unregistered agent returns 404 Not Found")
    void testGetAgent_NonExistentAgent_Returns404NotFound() throws Exception {
        when(agentRegistryService.getAgent("unknown-agent"))
                .thenThrow(new AgentNotFoundException("Agent not found with ID: unknown-agent"));

        mockMvc.perform(get("/api/agents/unknown-agent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Agent not found")));
    }

    @Test
    @DisplayName("GET /api/agents returns 200 OK with list of all agents")
    void testGetAllAgents_ReturnsList() throws Exception {
        List<Agent> agents = List.of(
                new Agent("agent-1", "worker", "ACTIVE", System.currentTimeMillis()),
                new Agent("agent-2", "supervisor", "ACTIVE", System.currentTimeMillis())
        );

        when(agentRegistryService.getAllAgents()).thenReturn(agents);

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].agentId", is("agent-1")))
                .andExpect(jsonPath("$[1].agentId", is("agent-2")));
    }

    @Test
    @DisplayName("GET /api/agents/{agentId}/status returns 200 OK with registered flag")
    void testGetAgentStatus_ReturnsStatus() throws Exception {
        when(agentRegistryService.isAgentRegistered("agent-active")).thenReturn(true);
        when(agentRegistryService.isAgentRegistered("agent-idle")).thenReturn(false);

        mockMvc.perform(get("/api/agents/agent-active/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId", is("agent-active")))
                .andExpect(jsonPath("$.registered", is(true)));

        mockMvc.perform(get("/api/agents/agent-idle/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId", is("agent-idle")))
                .andExpect(jsonPath("$.registered", is(false)));
    }
}
