package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.lock.InMemoryDistributedLockManager;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.resilience.client.ExternalAgentClient;
import com.zaalima.orchestrator.resilience.client.ExternalAgentResponse;
import com.zaalima.orchestrator.resilience.exception.BulkheadFullException;
import com.zaalima.orchestrator.resilience.exception.CircuitBreakerOpenException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiTimeoutException;
import com.zaalima.orchestrator.service.TaskExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Task Execution Service Resilience Integration Tests")
class TaskExecutionResilienceTest {

    private ExternalAgentClient mockExternalClient;
    private TaskExecutionService service;

    @BeforeEach
    void setUp() {
        mockExternalClient = mock(ExternalAgentClient.class);
        service = new TaskExecutionService(new InMemoryDistributedLockManager(), mockExternalClient);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    @DisplayName("Remote task completes successfully when ExternalAgentClient succeeds")
    void shouldCompleteRemoteTaskWhenExternalAgentSucceeds() throws Exception {
        when(mockExternalClient.executeTask(eq("agent-1"), any(AgentTask.class)))
                .thenReturn(ExternalAgentResponse.success(200, "{\"status\":\"completed\"}"));

        AgentTask task = new AgentTask("task-res-1", "agent-1", "REMOTE_AGENT", "payload-1");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        verify(mockExternalClient, times(1)).executeTask(eq("agent-1"), any(AgentTask.class));
    }

    @Test
    @DisplayName("Remote task is marked FAILED when CircuitBreaker is open")
    void shouldFailTaskWhenCircuitBreakerIsOpen() throws Exception {
        when(mockExternalClient.executeTask(eq("agent-1"), any(AgentTask.class)))
                .thenThrow(new CircuitBreakerOpenException("external-agent", "Circuit is OPEN"));

        AgentTask task = new AgentTask("task-res-cb-open", "agent-1", "REMOTE_AGENT", "payload-2");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.FAILED, task.getStatus());
    }

    @Test
    @DisplayName("Remote task is marked FAILED when Bulkhead is saturated")
    void shouldFailTaskWhenBulkheadIsFull() throws Exception {
        when(mockExternalClient.executeTask(eq("agent-1"), any(AgentTask.class)))
                .thenThrow(new BulkheadFullException("external-agent", "Bulkhead limit reached"));

        AgentTask task = new AgentTask("task-res-bh-full", "agent-1", "REMOTE_AGENT", "payload-3");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.FAILED, task.getStatus());
    }

    @Test
    @DisplayName("Remote task is marked FAILED when call times out")
    void shouldFailTaskWhenTimeoutOccurs() throws Exception {
        when(mockExternalClient.executeTask(eq("agent-1"), any(AgentTask.class)))
                .thenThrow(new ExternalApiTimeoutException(
                        ExternalApiTimeoutException.TimeoutType.REQUEST, 3000L, "Request timed out"));

        AgentTask task = new AgentTask("task-res-timeout", "agent-1", "REMOTE_AGENT", "payload-4");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.FAILED, task.getStatus());
    }

    @Test
    @DisplayName("Standard non-remote tasks execute normally without invoking external client")
    void shouldExecuteNonRemoteTasksNormallyWithoutExternalClient() throws Exception {
        AgentTask task = new AgentTask("task-local-1", "agent-2", "DATA_PROCESSING", "local-payload");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        verify(mockExternalClient, never()).executeTask(any(), any());
    }
}
