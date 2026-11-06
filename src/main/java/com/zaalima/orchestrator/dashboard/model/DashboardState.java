package com.zaalima.orchestrator.dashboard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe container managing live dashboard view state, active selection, and UI flags.
 */
public class DashboardState {

    private List<NodeMetricsSnapshot> nodeSnapshots = Collections.emptyList();
    private ClusterMetricsSummary clusterSummary = ClusterMetricsSummary.empty("INITIALIZING");
    private int selectedNodeIndex = 0;
    private boolean paused = false;
    private boolean showHelp = false;
    private boolean demoMode = false;
    private String statusMessage = "Dashboard initialized. Ready.";
    private long lastRefreshTime = System.currentTimeMillis();

    public synchronized void updateMetrics(List<NodeMetricsSnapshot> snapshots, ClusterMetricsSummary summary) {
        this.nodeSnapshots = (snapshots != null) ? new ArrayList<>(snapshots) : Collections.emptyList();
        this.clusterSummary = (summary != null) ? summary : ClusterMetricsSummary.empty("UNKNOWN");
        this.lastRefreshTime = System.currentTimeMillis();

        if (selectedNodeIndex >= nodeSnapshots.size()) {
            selectedNodeIndex = Math.max(0, nodeSnapshots.size() - 1);
        }
    }

    public synchronized List<NodeMetricsSnapshot> getNodeSnapshots() {
        return Collections.unmodifiableList(nodeSnapshots);
    }

    public synchronized ClusterMetricsSummary getClusterSummary() {
        return clusterSummary;
    }

    public synchronized int getSelectedNodeIndex() {
        return selectedNodeIndex;
    }

    public synchronized void cycleNextNode() {
        if (nodeSnapshots == null || nodeSnapshots.isEmpty()) {
            selectedNodeIndex = 0;
            return;
        }
        selectedNodeIndex = (selectedNodeIndex + 1) % nodeSnapshots.size();
        statusMessage = "Selected node: " + getSelectedNode().nodeId();
    }

    public synchronized NodeMetricsSnapshot getSelectedNode() {
        if (nodeSnapshots == null || nodeSnapshots.isEmpty()) {
            return null;
        }
        if (selectedNodeIndex < 0 || selectedNodeIndex >= nodeSnapshots.size()) {
            selectedNodeIndex = 0;
        }
        return nodeSnapshots.get(selectedNodeIndex);
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized void togglePause() {
        this.paused = !this.paused;
        this.statusMessage = this.paused ? "Auto-refresh PAUSED (Press P to resume)" : "Auto-refresh RESUMED";
    }

    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    public synchronized boolean isShowHelp() {
        return showHelp;
    }

    public synchronized void toggleHelp() {
        this.showHelp = !this.showHelp;
    }

    public synchronized void setShowHelp(boolean showHelp) {
        this.showHelp = showHelp;
    }

    public synchronized boolean isDemoMode() {
        return demoMode;
    }

    public synchronized void toggleDemoMode() {
        this.demoMode = !this.demoMode;
        this.statusMessage = this.demoMode ? "DEMO MODE ENABLED (Simulated Data)" : "DEMO MODE DISABLED (Live Monitoring)";
    }

    public synchronized void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public synchronized String getStatusMessage() {
        return statusMessage;
    }

    public synchronized void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public synchronized long getLastRefreshTime() {
        return lastRefreshTime;
    }
}
