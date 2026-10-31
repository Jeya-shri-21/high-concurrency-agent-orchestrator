package com.zaalima.orchestrator.dashboard.service;

import com.zaalima.orchestrator.dashboard.collector.ClusterMetricsCollector;
import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.DashboardState;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.renderer.TerminalRenderer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service managing dashboard state lifecycle, periodic refresh cycles, and user interactions.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final ClusterMetricsCollector clusterCollector;
    private final TerminalRenderer terminalRenderer;
    private final DashboardProperties properties;
    private final DashboardState state;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DashboardService(
            ClusterMetricsCollector clusterCollector,
            TerminalRenderer terminalRenderer,
            DashboardProperties properties) {
        this.clusterCollector = clusterCollector;
        this.terminalRenderer = terminalRenderer;
        this.properties = properties;
        this.state = new DashboardState();
        if (properties.isDemoMode()) {
            this.state.setDemoMode(true);
        }
    }

    @PostConstruct
    public void init() {
        refreshMetrics();
    }

    /**
     * Refreshes metrics across the cluster and updates state.
     */
    public synchronized void refreshMetrics() {
        if (!running.get()) {
            return;
        }

        try {
            boolean isDemo = state.isDemoMode();
            List<NodeMetricsSnapshot> snapshots = clusterCollector.collectClusterSnapshots(isDemo);
            boolean redisOnline = clusterCollector.isRedisOnline();
            ClusterMetricsSummary summary = clusterCollector.aggregate(snapshots, isDemo, redisOnline);

            state.updateMetrics(snapshots, summary);
        } catch (Exception ex) {
            log.error("Error refreshing dashboard metrics: {}", ex.getMessage());
            state.setStatusMessage("Metrics refresh error: " + ex.getMessage());
        }
    }

    /**
     * Forces an immediate manual refresh.
     */
    public void refreshNow() {
        refreshMetrics();
        state.setStatusMessage("Manual refresh completed at " + System.currentTimeMillis());
    }

    /**
     * Toggles the pause state of automatic updates.
     */
    public void togglePause() {
        state.togglePause();
    }

    /**
     * Cycles the selected node to the next in the list.
     */
    public void cycleNextNode() {
        state.cycleNextNode();
    }

    /**
     * Toggles the help documentation overlay.
     */
    public void toggleHelp() {
        state.toggleHelp();
        if (state.isShowHelp()) {
            state.setStatusMessage("Showing Help Screen (Press H or ESC to return)");
        } else {
            state.setStatusMessage("Returned to Live Dashboard");
        }
    }

    /**
     * Toggles demo data simulation mode.
     */
    public void toggleDemoMode() {
        state.toggleDemoMode();
        refreshMetrics();
    }

    /**
     * Generates the rendered ANSI terminal string for the current state.
     */
    public String renderCurrentView() {
        return terminalRenderer.render(state, properties);
    }

    public DashboardState getState() {
        return state;
    }

    public DashboardProperties getProperties() {
        return properties;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            state.setStatusMessage("Dashboard stopped.");
            log.info("DashboardService stopped.");
        }
    }
}
