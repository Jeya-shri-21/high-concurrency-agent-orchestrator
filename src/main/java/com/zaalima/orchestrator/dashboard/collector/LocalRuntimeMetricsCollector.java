package com.zaalima.orchestrator.dashboard.collector;

import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.redis.NodeLoadReporter;
import com.zaalima.orchestrator.service.TaskExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects real, verifiable runtime metrics from the local JVM and Spring Boot application.
 *
 * JVM & VIRTUAL THREAD BOUNDARY NOTE:
 * Standard JVM management APIs (ThreadMXBean) track carrier and platform threads.
 * Virtual Threads are lightweight concurrency units managed by the Java runtime scheduler
 * whose exact live count cannot be fully queried via standard ThreadMXBean.
 * Therefore, Virtual Thread task execution counts are obtained from TaskExecutionService and
 * NodeLoadReporter, and clearly segregated from platform thread counts.
 */
@Component
public class LocalRuntimeMetricsCollector implements RuntimeMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(LocalRuntimeMetricsCollector.class);

    private final String nodeId;
    private final TaskExecutionService taskExecutionService;
    private final NodeLoadReporter nodeLoadReporter;

    public LocalRuntimeMetricsCollector(
            @Value("${orchestrator.node.id:node-1}") String nodeId,
            @Autowired(required = false) TaskExecutionService taskExecutionService,
            @Autowired(required = false) NodeLoadReporter nodeLoadReporter) {
        this.nodeId = nodeId != null && !nodeId.isBlank() ? nodeId.trim() : "node-1";
        this.taskExecutionService = taskExecutionService;
        this.nodeLoadReporter = nodeLoadReporter;
    }

    @Override
    public NodeMetricsSnapshot collectLocalMetrics() {
        long now = System.currentTimeMillis();
        long uptimeSeconds = 0;
        double heapUsedMb = 0.0;
        double heapMaxMb = 0.0;
        int livePlatformThreads = 0;
        int peakPlatformThreads = 0;
        long totalStartedThreads = 0;
        Map<String, Integer> threadStateCounts = new HashMap<>();
        threadStateCounts.put("RUNNABLE", 0);
        threadStateCounts.put("WAITING", 0);
        threadStateCounts.put("TIMED_WAITING", 0);
        threadStateCounts.put("BLOCKED", 0);

        String errorMsg = null;

        // 1. JVM Uptime
        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            uptimeSeconds = runtimeBean.getUptime() / 1000;
        } catch (Exception ex) {
            log.debug("Unable to read RuntimeMXBean uptime: {}", ex.getMessage());
            errorMsg = "Uptime unavailable: " + ex.getMessage();
        }

        // 2. JVM Heap Memory
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            heapUsedMb = Math.round((heapUsage.getUsed() / (1024.0 * 1024.0)) * 10.0) / 10.0;
            long maxBytes = heapUsage.getMax() > 0 ? heapUsage.getMax() : heapUsage.getCommitted();
            heapMaxMb = Math.round((maxBytes / (1024.0 * 1024.0)) * 10.0) / 10.0;
        } catch (Exception ex) {
            log.debug("Unable to read MemoryMXBean: {}", ex.getMessage());
            if (errorMsg == null) errorMsg = "Memory metrics unavailable: " + ex.getMessage();
        }

        // 3. JVM Platform Threads & Thread States
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            livePlatformThreads = threadBean.getThreadCount();
            peakPlatformThreads = threadBean.getPeakThreadCount();
            totalStartedThreads = threadBean.getTotalStartedThreadCount();

            long[] threadIds = threadBean.getAllThreadIds();
            if (threadIds != null && threadIds.length > 0) {
                ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
                if (threadInfos != null) {
                    for (ThreadInfo info : threadInfos) {
                        if (info != null && info.getThreadState() != null) {
                            String state = info.getThreadState().name();
                            threadStateCounts.put(state, threadStateCounts.getOrDefault(state, 0) + 1);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Unable to read ThreadMXBean: {}", ex.getMessage());
            if (errorMsg == null) errorMsg = "Thread metrics unavailable: " + ex.getMessage();
        }

        // 4. Application-Level Virtual Thread Tasks
        int activeTasks = 0;
        long completedTasks = 0;
        long failedTasks = 0;
        long totalTasksSubmitted = 0;

        if (taskExecutionService != null) {
            try {
                Map<String, AgentTask> allTasks = taskExecutionService.getAllTasks();
                if (allTasks != null) {
                    totalTasksSubmitted = allTasks.size();
                    for (AgentTask task : allTasks.values()) {
                        if (task != null && task.getStatus() != null) {
                            if (task.getStatus() == TaskStatus.RUNNING) {
                                activeTasks++;
                            } else if (task.getStatus() == TaskStatus.COMPLETED) {
                                completedTasks++;
                            } else if (task.getStatus() == TaskStatus.FAILED) {
                                failedTasks++;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Unable to read tasks from TaskExecutionService: {}", ex.getMessage());
            }
        }

        // Fallback: NodeLoadReporter active tasks if taskExecutionService had none
        if (activeTasks == 0 && nodeLoadReporter != null) {
            try {
                activeTasks = nodeLoadReporter.getActiveTasks();
            } catch (Exception ex) {
                log.debug("Unable to read active tasks from NodeLoadReporter: {}", ex.getMessage());
            }
        }

        return new NodeMetricsSnapshot(
                nodeId,
                now,
                NodeStatus.UP,
                uptimeSeconds,
                activeTasks,
                completedTasks,
                failedTasks,
                totalTasksSubmitted,
                heapUsedMb,
                heapMaxMb,
                livePlatformThreads,
                peakPlatformThreads,
                totalStartedThreads,
                threadStateCounts,
                true, // Java 21+ Virtual Threads supported
                errorMsg,
                true // local node
        );
    }

    public String getNodeId() {
        return nodeId;
    }
}
