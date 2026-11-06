package com.zaalima.orchestrator.dashboard.controller;

import com.zaalima.orchestrator.dashboard.collector.RuntimeMetricsCollector;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.service.DashboardService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API Controller exposing live Virtual Thread cluster metrics and dashboard view state.
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;
    private final RuntimeMetricsCollector localCollector;

    public DashboardController(
            DashboardService dashboardService,
            RuntimeMetricsCollector localCollector) {
        this.dashboardService = dashboardService;
        this.localCollector = localCollector;
    }

    /**
     * Retrieves cluster-wide metrics summary, all node snapshots, and view state.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDashboardStatus() {
        dashboardService.refreshMetrics();
        return ResponseEntity.ok(Map.of(
                "cluster", dashboardService.getState().getClusterSummary(),
                "nodes", dashboardService.getState().getNodeSnapshots(),
                "selectedNode", dashboardService.getState().getSelectedNode() != null
                        ? dashboardService.getState().getSelectedNode()
                        : "None",
                "paused", dashboardService.getState().isPaused(),
                "demoMode", dashboardService.getState().isDemoMode(),
                "statusMessage", dashboardService.getState().getStatusMessage(),
                "lastUpdated", dashboardService.getState().getLastRefreshTime()
        ));
    }

    /**
     * Retrieves real point-in-time metrics for the local node.
     */
    @GetMapping("/local")
    public ResponseEntity<NodeMetricsSnapshot> getLocalMetrics() {
        return ResponseEntity.ok(localCollector.collectLocalMetrics());
    }

    /**
     * Retrieves raw formatted ANSI/text dashboard view, allowing easy monitoring via curl.
     */
    @GetMapping(value = "/render", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRenderedDashboard() {
        dashboardService.refreshMetrics();
        return ResponseEntity.ok(dashboardService.renderCurrentView());
    }

    /**
     * Triggers an immediate refresh cycle.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        dashboardService.refreshNow();
        return ResponseEntity.ok(Map.of(
                "status", "REFRESHED",
                "message", dashboardService.getState().getStatusMessage()
        ));
    }

    /**
     * Toggles demo data simulation mode.
     */
    @PostMapping("/demo")
    public ResponseEntity<Map<String, Object>> toggleDemo() {
        dashboardService.toggleDemoMode();
        return ResponseEntity.ok(Map.of(
                "demoMode", dashboardService.getState().isDemoMode(),
                "message", dashboardService.getState().getStatusMessage()
        ));
    }
}
