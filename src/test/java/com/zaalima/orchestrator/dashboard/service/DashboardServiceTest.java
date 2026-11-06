package com.zaalima.orchestrator.dashboard.service;

import com.zaalima.orchestrator.dashboard.collector.ClusterMetricsCollector;
import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import com.zaalima.orchestrator.dashboard.renderer.TerminalRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard Service Tests")
class DashboardServiceTest {

    @Mock
    private ClusterMetricsCollector clusterCollector;

    @Mock
    private TerminalRenderer terminalRenderer;

    private DashboardProperties properties;
    private DashboardService service;

    private NodeMetricsSnapshot node1;
    private NodeMetricsSnapshot node2;

    @BeforeEach
    void setUp() {
        properties = new DashboardProperties();

        node1 = new NodeMetricsSnapshot("node-1", System.currentTimeMillis(), NodeStatus.UP,
                100, 2, 10, 0, 12, 100.0, 500.0, 10, 15, 50, null, true, null, true);
        node2 = new NodeMetricsSnapshot("node-2", System.currentTimeMillis(), NodeStatus.UP,
                200, 4, 20, 1, 25, 120.0, 500.0, 12, 18, 60, null, true, null, false);

        lenient().when(clusterCollector.collectClusterSnapshots(anyBoolean())).thenReturn(List.of(node1, node2));
        lenient().when(clusterCollector.aggregate(anyList(), anyBoolean(), anyBoolean()))
                .thenReturn(ClusterMetricsSummary.empty("CLUSTER"));

        service = new DashboardService(clusterCollector, terminalRenderer, properties);
    }

    @Test
    @DisplayName("Should refresh metrics and update state")
    void testRefreshMetrics() {
        service.refreshMetrics();

        assertEquals(2, service.getState().getNodeSnapshots().size());
        assertEquals("node-1", service.getState().getSelectedNode().nodeId());
        assertTrue(service.getState().getLastRefreshTime() > 0);
    }

    @Test
    @DisplayName("Should cycle selected node index safely")
    void testCycleNextNode() {
        service.refreshMetrics();
        assertEquals(0, service.getState().getSelectedNodeIndex());
        assertEquals("node-1", service.getState().getSelectedNode().nodeId());

        service.cycleNextNode();
        assertEquals(1, service.getState().getSelectedNodeIndex());
        assertEquals("node-2", service.getState().getSelectedNode().nodeId());

        service.cycleNextNode(); // wraps around
        assertEquals(0, service.getState().getSelectedNodeIndex());
        assertEquals("node-1", service.getState().getSelectedNode().nodeId());
    }

    @Test
    @DisplayName("Should toggle pause state and update status message")
    void testTogglePause() {
        assertFalse(service.getState().isPaused());

        service.togglePause();
        assertTrue(service.getState().isPaused());
        assertTrue(service.getState().getStatusMessage().contains("PAUSED"));

        service.togglePause();
        assertFalse(service.getState().isPaused());
    }

    @Test
    @DisplayName("Should toggle help overlay and demo mode")
    void testToggles() {
        assertFalse(service.getState().isShowHelp());
        service.toggleHelp();
        assertTrue(service.getState().isShowHelp());

        assertFalse(service.getState().isDemoMode());
        service.toggleDemoMode();
        assertTrue(service.getState().isDemoMode());
    }

    @Test
    @DisplayName("Should stop cleanly and reject further updates")
    void testStop() {
        assertTrue(service.isRunning());
        service.stop();
        assertFalse(service.isRunning());

        // Further refresh when stopped should do nothing
        service.refreshMetrics();
        verify(clusterCollector, atMost(1)).collectClusterSnapshots(anyBoolean());
    }
}
