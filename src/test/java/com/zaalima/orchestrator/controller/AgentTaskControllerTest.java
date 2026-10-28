package com.zaalima.orchestrator.controller;

import com.zaalima.orchestrator.exception.GlobalExceptionHandler;
import com.zaalima.orchestrator.exception.NoEligibleNodeException;
import com.zaalima.orchestrator.exception.NodeUtilizationException;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.service.TaskExecutionService;
import com.zaalima.orchestrator.service.TaskRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Agent Task Controller Load Balancing Tests")
class AgentTaskControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private TaskRoutingService taskRoutingService;

    @InjectMocks
    private AgentTaskController agentTaskController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agentTaskController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/tasks with null agentId should route using load balancer")
    void testSubmitTask_NullAgentId_RoutesViaLoadBalancer() throws Exception {
        AgentTask routedTask = new AgentTask("task-123", "node-auto", "DATA_PROC", "sample-payload");
        when(taskRoutingService.routeTask(eq("DATA_PROC"), eq("sample-payload")))
                .thenReturn(routedTask);

        String json = """
                {
                    "taskType": "DATA_PROC",
                    "payload": "sample-payload"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-123"))
                .andExpect(jsonPath("$.agentId").value("node-auto"))
                .andExpect(jsonPath("$.taskType").value("DATA_PROC"))
                .andExpect(jsonPath("$.payload").value("sample-payload"));

        verify(taskRoutingService).routeTask("DATA_PROC", "sample-payload");
        verifyNoInteractions(taskExecutionService);
    }

    @Test
    @DisplayName("POST /api/tasks with explicit agentId should preserve existing direct submission")
    void testSubmitTask_ExplicitAgentId_DirectSubmission() throws Exception {
        String json = """
                {
                    "agentId": "node-specific",
                    "taskType": "DATA_PROC",
                    "payload": "sample-payload"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("node-specific"));

        verify(taskExecutionService).submitTask(any());
        verifyNoInteractions(taskRoutingService);
    }

    @Test
    @DisplayName("POST /api/tasks/route should route via load balancer")
    void testRouteEndpoint_Success() throws Exception {
        AgentTask routedTask = new AgentTask("task-456", "node-least", "ANALYTICS", "metrics-payload");
        when(taskRoutingService.routeTask(eq("ANALYTICS"), eq("metrics-payload")))
                .thenReturn(routedTask);

        String json = """
                {
                    "taskType": "ANALYTICS",
                    "payload": "metrics-payload"
                }
                """;

        mockMvc.perform(post("/api/tasks/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-456"))
                .andExpect(jsonPath("$.agentId").value("node-least"));
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable when no eligible nodes exist")
    void testNoEligibleNode_Returns503() throws Exception {
        when(taskRoutingService.routeTask(any(), any()))
                .thenThrow(new NoEligibleNodeException("No eligible nodes available"));

        String json = """
                {
                    "taskType": "DATA_PROC",
                    "payload": "data"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("No eligible nodes available"));
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable when utilization metrics service fails")
    void testUtilizationFailure_Returns503() throws Exception {
        when(taskRoutingService.routeTask(any(), any()))
                .thenThrow(new NodeUtilizationException("Redis unavailable"));

        String json = """
                {
                    "taskType": "DATA_PROC",
                    "payload": "data"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Redis unavailable"));
    }

    @Test
    @DisplayName("GET /api/tasks/load-balancer/status returns load balancer state")
    void testGetLoadBalancerStatus() throws Exception {
        when(taskRoutingService.getLeastUtilizedNode()).thenReturn("node-optimal");
        when(taskRoutingService.discoverEligibleNodes()).thenReturn(List.of("node-optimal", "node-backup"));

        mockMvc.perform(get("/api/tasks/load-balancer/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leastUtilizedNode").value("node-optimal"))
                .andExpect(jsonPath("$.eligibleNodes[0]").value("node-optimal"))
                .andExpect(jsonPath("$.eligibleNodes[1]").value("node-backup"))
                .andExpect(jsonPath("$.status").value("OK"));
    }
}
