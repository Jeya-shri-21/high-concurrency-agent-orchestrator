package com.zaalima.orchestrator.dashboard.renderer;

import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.DashboardState;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Terminal Renderer Tests")
class TerminalRendererTest {

    private TerminalRenderer renderer;
    private DashboardProperties properties;
    private DashboardState state;

    @BeforeEach
    void setUp() {
        renderer = new TerminalRenderer();
        properties = new DashboardProperties();
        state = new DashboardState();
    }

    @Test
    @DisplayName("Should render complete main dashboard with cluster summary and node table")
    void testRenderMainDashboard_WithNodes() {
        NodeMetricsSnapshot node1 = new NodeMetricsSnapshot(
                "node-1", System.currentTimeMillis(), NodeStatus.UP,
                3600, 4, 100, 2, 106, 128.5, 512.0,
                24, 32, 120,
                Map.of("RUNNABLE", 12, "WAITING", 8, "TIMED_WAITING", 4, "BLOCKED", 0),
                true, null, true
        );
        NodeMetricsSnapshot node2 = new NodeMetricsSnapshot(
                "node-2", System.currentTimeMillis(), NodeStatus.STALE,
                1800, 0, 50, 0, 50, 96.0, 256.0,
                16, 20, 80,
                Map.of("RUNNABLE", 8, "WAITING", 8),
                true, null, false
        );

        ClusterMetricsSummary summary = new ClusterMetricsSummary(
                System.currentTimeMillis(),
                2, 1, 1, 0,
                4, 150, 2,
                40, 224.5, 768.0, 0,
                "CLUSTER (Redis Active - 2 Nodes)"
        );

        state.updateMetrics(List.of(node1, node2), summary);

        String output = renderer.render(state, properties);

        assertNotNull(output);
        assertTrue(output.contains("ZAALIMA HIGH-CONCURRENCY AGENT ORCHESTRATOR"));
        assertTrue(output.contains("CLUSTER SUMMARY"));
        assertTrue(output.contains("Reachable (UP):"));
        assertTrue(output.contains("node-1"));
        assertTrue(output.contains("node-2"));
        assertTrue(output.contains("ACTIVE TASKS"));
        assertTrue(output.contains("SELECTED NODE: node-1"));
        assertTrue(output.contains("Virtual Thread Task Concurrency"));
        assertTrue(output.contains("JVM Platform/Carrier Threads"));
        assertTrue(output.contains("RUNNABLE"));
        assertTrue(output.contains("KEYBOARD CONTROLS"));
        assertTrue(output.contains("[Q] Quit"));
    }

    @Test
    @DisplayName("Should display clear notice when no nodes are available")
    void testRenderMainDashboard_EmptyNodes() {
        state.updateMetrics(Collections.emptyList(), ClusterMetricsSummary.empty("STANDALONE"));

        String output = renderer.render(state, properties);

        assertNotNull(output);
        assertTrue(output.contains("No live node metrics are currently available."));
    }

    @Test
    @DisplayName("Should render Help documentation screen when requested")
    void testRenderHelpScreen() {
        state.setShowHelp(true);

        String output = renderer.render(state, properties);

        assertNotNull(output);
        assertTrue(output.contains("HELP & DOCUMENTATION SCREEN"));
        assertTrue(output.contains("KEYBOARD SHORTCUTS"));
        assertTrue(output.contains("METRIC DEFINITIONS"));
        assertTrue(output.contains("Press [H], [ESC], or [B] to return"));
    }

    @Test
    @DisplayName("Should display PAUSED status indicator when paused")
    void testRenderMainDashboard_Paused() {
        state.setPaused(true);

        String output = renderer.render(state, properties);

        assertTrue(output.contains("PAUSED"));
    }

    @Test
    @DisplayName("Should display DEMO DATA notice when demo mode is enabled")
    void testRenderMainDashboard_DemoMode() {
        state.setDemoMode(true);

        String output = renderer.render(state, properties);

        assertTrue(output.contains("DEMO DATA"));
    }
}
