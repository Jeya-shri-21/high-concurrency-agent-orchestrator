package com.zaalima.orchestrator.dashboard.collector;

import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.redis.NodeLoadReporter;
import com.zaalima.orchestrator.service.TaskExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Local Runtime Metrics Collector Tests")
class LocalRuntimeMetricsCollectorTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private NodeLoadReporter nodeLoadReporter;

    @Test
    @DisplayName("Should collect real JVM metrics and task execution states")
    void testCollectLocalMetrics_WithTasks() {
        AgentTask runningTask = new AgentTask("task-1", "agent-1", "COMPUTE", "payload");
        runningTask.setStatus(TaskStatus.RUNNING);

        AgentTask completedTask = new AgentTask("task-2", "agent-1", "COMPUTE", "payload");
        completedTask.setStatus(TaskStatus.COMPLETED);

        AgentTask failedTask = new AgentTask("task-3", "agent-1", "COMPUTE", "payload");
        failedTask.setStatus(TaskStatus.FAILED);

        when(taskExecutionService.getAllTasks()).thenReturn(Map.of(
                "task-1", runningTask,
                "task-2", completedTask,
                "task-3", failedTask
        ));

        LocalRuntimeMetricsCollector collector = new LocalRuntimeMetricsCollector(
                "test-node-1",
                taskExecutionService,
                nodeLoadReporter
        );

        NodeMetricsSnapshot snapshot = collector.collectLocalMetrics();

        assertNotNull(snapshot);
        assertEquals("test-node-1", snapshot.nodeId());
        assertEquals(NodeStatus.UP, snapshot.status());
        assertTrue(snapshot.isLocal());
        assertTrue(snapshot.timestamp() > 0);
        assertTrue(snapshot.uptimeSeconds() >= 0);

        // Task assertions
        assertEquals(1, snapshot.activeTasks());
        assertEquals(1, snapshot.completedTasks());
        assertEquals(1, snapshot.failedTasks());
        assertEquals(3, snapshot.totalTasksSubmitted());

        // JVM Thread assertions (real JVM metrics)
        assertTrue(snapshot.livePlatformThreads() > 0);
        assertTrue(snapshot.peakPlatformThreads() > 0);
        assertTrue(snapshot.totalStartedThreads() > 0);
        assertNotNull(snapshot.threadStateCounts());
        assertTrue(snapshot.threadStateCounts().containsKey("RUNNABLE"));

        // Heap assertions
        assertTrue(snapshot.heapUsedMb() > 0.0);
        assertTrue(snapshot.heapMaxMb() > 0.0);
    }

    @Test
    @DisplayName("Should fallback gracefully when TaskExecutionService has no tasks")
    void testCollectLocalMetrics_FallbackToNodeLoadReporter() {
        when(taskExecutionService.getAllTasks()).thenReturn(Map.of());
        when(nodeLoadReporter.getActiveTasks()).thenReturn(5);

        LocalRuntimeMetricsCollector collector = new LocalRuntimeMetricsCollector(
                "test-node-2",
                taskExecutionService,
                nodeLoadReporter
        );

        NodeMetricsSnapshot snapshot = collector.collectLocalMetrics();

        assertEquals(5, snapshot.activeTasks());
        assertEquals(0, snapshot.totalTasksSubmitted());
    }

    @Test
    @DisplayName("Should handle null dependencies without crashing")
    void testCollectLocalMetrics_NullDependencies() {
        LocalRuntimeMetricsCollector collector = new LocalRuntimeMetricsCollector(
                "standalone-node",
                null,
                null
        );

        NodeMetricsSnapshot snapshot = collector.collectLocalMetrics();

        assertNotNull(snapshot);
        assertEquals("standalone-node", snapshot.nodeId());
        assertEquals(0, snapshot.activeTasks());
        assertTrue(snapshot.livePlatformThreads() > 0);
    }
}
