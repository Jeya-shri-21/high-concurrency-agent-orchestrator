package com.zaalima.orchestrator.dashboard.renderer;

import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.ClusterMetricsSummary;
import com.zaalima.orchestrator.dashboard.model.DashboardState;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renders the terminal dashboard using formatted ANSI text with box-drawing elements.
 * Separated into pure string generation for testability and headless environments.
 */
@Component
public class TerminalRenderer {

    // ANSI Color and Styling Codes
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String CYAN = "\u001B[36m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CLEAR_SCREEN = "\033[H\033[2J";

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Renders the complete dashboard view based on current state and properties.
     */
    public String render(DashboardState state, DashboardProperties properties) {
        if (state.isShowHelp()) {
            return renderHelpScreen(state, properties);
        }
        return renderMainDashboard(state, properties);
    }

    private String renderMainDashboard(DashboardState state, DashboardProperties properties) {
        StringBuilder sb = new StringBuilder(4096);
        ClusterMetricsSummary cluster = state.getClusterSummary();
        List<NodeMetricsSnapshot> nodes = state.getNodeSnapshots();
        NodeMetricsSnapshot selected = state.getSelectedNode();

        String nowFormatted = TIME_FORMATTER.format(Instant.now());
        String lastUpdateFormatted = TIME_FORMATTER.format(Instant.ofEpochMilli(state.getLastRefreshTime()));
        String pauseStatus = state.isPaused() ? (YELLOW + BOLD + "PAUSED [P]" + RESET) : (GREEN + "ACTIVE [LIVE]" + RESET);
        String demoNotice = state.isDemoMode() ? (YELLOW + BOLD + " [DEMO DATA - SIMULATION]" + RESET) : "";

        // 1. Header
        sb.append(CYAN).append("═".repeat(96)).append(RESET).append("\n");
        sb.append(BOLD).append(CYAN).append("   ZAALIMA HIGH-CONCURRENCY AGENT ORCHESTRATOR — CLUSTER DASHBOARD").append(demoNotice).append(RESET).append("\n");
        sb.append(CYAN).append("═".repeat(96)).append(RESET).append("\n");
        sb.append(String.format(" Time: %s | Last Update: %s | Interval: %.1fs | Status: %s\n",
                nowFormatted, lastUpdateFormatted, properties.getRefreshIntervalMs() / 1000.0, pauseStatus));
        sb.append("─".repeat(96)).append("\n");

        // 2. Cluster Summary
        sb.append(BOLD).append(" [ CLUSTER SUMMARY ]").append(RESET).append("\n");
        sb.append(String.format(" Total Nodes: %-3d │ Reachable (UP): %s%-3d%s │ Stale: %s%-3d%s │ Down: %s%-3d%s │ Active Tasks: %s%d%s\n",
                cluster.totalNodes(),
                GREEN, cluster.reachableNodes(), RESET,
                cluster.staleNodes() > 0 ? YELLOW : RESET, cluster.staleNodes(), RESET,
                cluster.unreachableNodes() > 0 ? RED : RESET, cluster.unreachableNodes(), RESET,
                BOLD + CYAN, cluster.totalActiveTasks(), RESET));
        sb.append(String.format(" Platform Threads: %-4d │ Cluster Heap: %.1f MB / %.1f MB │ Mode: %s%s%s\n",
                cluster.totalLivePlatformThreads(),
                cluster.totalHeapUsedMb(), cluster.totalHeapMaxMb(),
                BOLD, cluster.clusterMode(), RESET));
        sb.append("─".repeat(96)).append("\n");

        // 3. Node Table
        sb.append(BOLD).append(" [ ORCHESTRATOR NODES ]").append(RESET);
        if (nodes.isEmpty()) {
            sb.append("\n  No live node metrics are currently available.\n");
        } else {
            sb.append("\n");
            sb.append(String.format("  %-4s %-16s %-10s %-14s %-16s %-13s %-12s %-10s\n",
                    "SEL", "NODE ID", "STATUS", "ACTIVE TASKS", "TASKS (DONE/ERR)", "JVM THREADS", "HEAP USED", "UPTIME"));
            sb.append("  ").append("─".repeat(92)).append("\n");

            for (int i = 0; i < nodes.size(); i++) {
                NodeMetricsSnapshot n = nodes.get(i);
                boolean isSelected = (i == state.getSelectedNodeIndex());
                String marker = isSelected ? (CYAN + BOLD + "> " + (i + 1) + "*" + RESET) : String.format("  %d ", i + 1);
                String statusStr = formatStatus(n.status());
                String localTag = n.isLocal() ? " (local)" : "";
                String activeStr = String.valueOf(n.activeTasks());
                String tasksStr = n.completedTasks() + " / " + n.failedTasks();
                String threadsStr = n.livePlatformThreads() > 0 ? String.valueOf(n.livePlatformThreads()) : "N/A";
                String heapStr = n.heapUsedMb() > 0 ? String.format("%.1f MB", n.heapUsedMb()) : "N/A";
                String uptimeStr = formatUptime(n.uptimeSeconds());

                sb.append(String.format("%-13s %-16s %-19s %-14s %-16s %-13s %-12s %-10s\n",
                        marker,
                        truncate(n.nodeId() + localTag, 16),
                        statusStr,
                        activeStr,
                        tasksStr,
                        threadsStr,
                        heapStr,
                        uptimeStr));
            }
        }
        sb.append("─".repeat(96)).append("\n");

        // 4. Selected Node Detailed Inspection
        if (selected != null) {
            sb.append(BOLD).append(String.format(" [ SELECTED NODE: %s%s ] (Press [N] to cycle nodes)",
                    selected.nodeId(), selected.isLocal() ? " (Current Instance)" : "")).append(RESET).append("\n");
            sb.append(String.format(" • Status: %s | Uptime: %s | Last Snapshot: %s\n",
                    formatStatus(selected.status()), formatUptime(selected.uptimeSeconds()),
                    TIME_FORMATTER.format(Instant.ofEpochMilli(selected.timestamp()))));
            sb.append(String.format(" • Virtual Thread Task Concurrency (Java 21 Project Loom):\n"));
            sb.append(String.format("   Active Tasks on Virtual Threads: %s%d%s | Submitted: %d | Completed: %d | Failed: %d\n",
                    BOLD + CYAN, selected.activeTasks(), RESET,
                    selected.totalTasksSubmitted(), selected.completedTasks(), selected.failedTasks()));
            sb.append(String.format(" • JVM Platform/Carrier Threads (ThreadMXBean):\n"));
            sb.append(String.format("   Live Platform Threads: %d (Peak: %d, Total Started: %d)\n",
                    selected.livePlatformThreads(), selected.peakPlatformThreads(), selected.totalStartedThreads()));

            Map<String, Integer> states = selected.threadStateCounts();
            sb.append(String.format("   ├─ RUNNABLE: %-3d  ├─ WAITING: %-3d  ├─ TIMED_WAITING: %-3d  └─ BLOCKED: %-3d\n",
                    states.getOrDefault("RUNNABLE", 0),
                    states.getOrDefault("WAITING", 0),
                    states.getOrDefault("TIMED_WAITING", 0),
                    states.getOrDefault("BLOCKED", 0)));

            double heapPct = selected.heapMaxMb() > 0 ? (selected.heapUsedMb() / selected.heapMaxMb()) * 100.0 : 0.0;
            sb.append(String.format(" • JVM Heap Memory: %.1f MB / %.1f MB (%.1f%%) | Error: %s\n",
                    selected.heapUsedMb(), selected.heapMaxMb(), heapPct,
                    selected.errorMessage() != null ? (RED + selected.errorMessage() + RESET) : "None"));
        } else {
            sb.append(" [ NO NODE SELECTED ]\n");
        }
        sb.append("─".repeat(96)).append("\n");

        // 5. Controls & Status Message
        sb.append(BOLD).append(" [ KEYBOARD CONTROLS ]").append(RESET).append("\n");
        sb.append(" [Q] Quit  [R] Refresh  [P] Pause/Resume  [N] Next Node  [H] Help  [D] Demo Mode\n");
        sb.append(String.format(" Notice: %s%s%s\n", CYAN, state.getStatusMessage(), RESET));
        sb.append(CYAN).append("═".repeat(96)).append(RESET).append("\n");

        return sb.toString();
    }

    private String renderHelpScreen(DashboardState state, DashboardProperties properties) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append(CYAN).append("═".repeat(96)).append(RESET).append("\n");
        sb.append(BOLD).append(CYAN).append("   INTERACTIVE CLUSTER DASHBOARD — HELP & DOCUMENTATION SCREEN").append(RESET).append("\n");
        sb.append(CYAN).append("═".repeat(96)).append(RESET).append("\n");
        sb.append(BOLD).append(" [ 1. KEYBOARD SHORTCUTS ]").append(RESET).append("\n");
        sb.append("   Q / q    : Exit and shutdown the interactive terminal dashboard cleanly.\n");
        sb.append("   R / r    : Force an immediate refresh of cluster and local node metrics.\n");
        sb.append("   P / p    : Toggle pause/resume of the background auto-refresh loop.\n");
        sb.append("   N / n    : Cycle through available orchestrator nodes to inspect details.\n");
        sb.append("   H / h    : Toggle this Help Screen.\n");
        sb.append("   ESC / B  : Close the Help Screen and return to the live dashboard.\n");
        sb.append("   D / d    : Toggle Demo Mode (shows simulated multi-node cluster data).\n\n");

        sb.append(BOLD).append(" [ 2. METRIC DEFINITIONS & JVM CONCURRENCY ARCHITECTURE ]").append(RESET).append("\n");
        sb.append("   • Virtual Thread Tasks:\n");
        sb.append("     Tasks submitted to Executors.newVirtualThreadPerTaskExecutor(). Each task executes\n");
        sb.append("     on a lightweight Java 21 Virtual Thread. Note: Standard JVM management APIs\n");
        sb.append("     (ThreadMXBean) do not provide a complete live inventory of all Virtual Threads.\n");
        sb.append("     Task metrics represent verified application-level execution states.\n");
        sb.append("   • JVM Platform Threads:\n");
        sb.append("     Physical OS/carrier threads managed by the JVM that schedule Virtual Threads.\n");
        sb.append("     State counts (RUNNABLE, WAITING, TIMED_WAITING, BLOCKED) are captured via ThreadMXBean.\n");
        sb.append("   • Node Status:\n");
        sb.append("     - UP    : Node has reported fresh metrics within the stale threshold (" + properties.getStaleThresholdMs() + "ms).\n");
        sb.append("     - STALE : Node reported previously but latest snapshot timestamp is older than threshold.\n");
        sb.append("     - DOWN  : Expected node is unreachable or missing.\n\n");

        sb.append(BOLD).append(" [ 3. CLUSTER METRICS TRANSPORT ]").append(RESET).append("\n");
        sb.append("   - Nodes publish JSON snapshots to Redis key: " + properties.getReportKeyPrefix() + "<nodeId>\n");
        sb.append("   - If Redis is unavailable, the dashboard operates seamlessly in Local Standalone Mode.\n\n");

        sb.append("─".repeat(96)).append("\n");
        sb.append(BOLD).append(YELLOW).append(" Press [H], [ESC], or [B] to return to the Live Dashboard").append(RESET).append("\n");
        sb.append(CYAN).append("═".repeat(96)).append(RESET).append("\n");
        return sb.toString();
    }

    private String formatStatus(NodeStatus status) {
        if (status == null) return "UNKNOWN";
        return switch (status) {
            case UP -> GREEN + BOLD + "UP" + RESET;
            case STALE -> YELLOW + BOLD + "STALE" + RESET;
            case DOWN -> RED + BOLD + "DOWN" + RESET;
        };
    }

    private String formatUptime(long seconds) {
        if (seconds <= 0) return "0s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%02dh %02dm", hours, minutes);
        }
        return String.format("%02dm %02ds", minutes, secs);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "…";
    }
}
