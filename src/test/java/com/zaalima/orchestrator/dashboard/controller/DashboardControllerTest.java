package com.zaalima.orchestrator.dashboard.controller;

import com.zaalima.orchestrator.dashboard.collector.RuntimeMetricsCollector;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.DashboardState;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import com.zaalima.orchestrator.dashboard.service.DashboardService;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard REST Controller Tests")
class DashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private RuntimeMetricsCollector localCollector;

    @InjectMocks
    private DashboardController dashboardController;

    private DashboardState state;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dashboardController).build();

        state = new DashboardState();
        NodeMetricsSnapshot localNode = new NodeMetricsSnapshot(
                "node-local", System.currentTimeMillis(), NodeStatus.UP,
                500, 2, 10, 0, 12, 100.0, 500.0, 10, 15, 30, null, true, null, true
        );
        state.updateMetrics(List.of(localNode), ClusterMetricsSummary.empty("STANDALONE"));

        lenient().when(dashboardService.getState()).thenReturn(state);
    }

    @Test
    @DisplayName("GET /api/dashboard/status should return JSON with cluster summary and nodes")
    void testGetDashboardStatus() throws Exception {
        mockMvc.perform(get("/api/dashboard/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cluster").exists())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes[0].nodeId").value("node-local"))
                .andExpect(jsonPath("$.paused").value(false))
                .andExpect(jsonPath("$.demoMode").value(false));

        verify(dashboardService).refreshMetrics();
    }

    @Test
    @DisplayName("GET /api/dashboard/local should return real local node metrics")
    void testGetLocalMetrics() throws Exception {
        NodeMetricsSnapshot snapshot = new NodeMetricsSnapshot(
                "node-direct", System.currentTimeMillis(), NodeStatus.UP,
                1200, 3, 25, 1, 29, 150.0, 512.0, 20, 25, 100, null, true, null, true
        );
        when(localCollector.collectLocalMetrics()).thenReturn(snapshot);

        mockMvc.perform(get("/api/dashboard/local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("node-direct"))
                .andExpect(jsonPath("$.activeTasks").value(3))
                .andExpect(jsonPath("$.completedTasks").value(25))
                .andExpect(jsonPath("$.isLocal").value(true));
    }

    @Test
    @DisplayName("GET /api/dashboard/render should return formatted plain text view")
    void testGetRenderedDashboard() throws Exception {
        when(dashboardService.renderCurrentView()).thenReturn("== DASHBOARD VIEW ==");

        mockMvc.perform(get("/api/dashboard/render"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DASHBOARD VIEW")));
    }

    @Test
    @DisplayName("POST /api/dashboard/refresh should trigger manual refresh")
    void testRefreshEndpoint() throws Exception {
        mockMvc.perform(post("/api/dashboard/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFRESHED"));

        verify(dashboardService).refreshNow();
    }

    @Test
    @DisplayName("POST /api/dashboard/demo should toggle demo mode")
    void testDemoEndpoint() throws Exception {
        mockMvc.perform(post("/api/dashboard/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").exists());

        verify(dashboardService).toggleDemoMode();
    }
}
