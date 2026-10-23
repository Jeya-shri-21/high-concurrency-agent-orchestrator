package com.zaalima.orchestrator.service;

import com.zaalima.orchestrator.lock.DistributedLock;
import com.zaalima.orchestrator.lock.DistributedLockManager;
import com.zaalima.orchestrator.lock.LockAcquisitionException;
import com.zaalima.orchestrator.lock.LockToken;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskExecutionService Locking Integration Tests")
class TaskExecutionServiceTest {

    @Mock
    private DistributedLockManager lockManager;

    @Mock
    private DistributedLock distributedLock;

    private TaskExecutionService service;

    @BeforeEach
    void setUp() {
        service = new TaskExecutionService(lockManager);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    @DisplayName("Task executes and completes when lock is acquired successfully, releasing lock upon exit")
    void testTaskExecutionSuccessWithLock() throws Exception {
        when(lockManager.tryAcquire(eq("lock:task:task-101"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.of(distributedLock));

        AgentTask task = new AgentTask("task-101", "agent-1", "PROCESS", "payload");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        verify(lockManager, times(1)).tryAcquire(eq("lock:task:task-101"), any(Duration.class), any(Duration.class));
        verify(distributedLock, times(1)).close();
    }

    @Test
    @DisplayName("Task fails immediately if lock cannot be acquired within timeout")
    void testTaskFailsWhenLockAcquisitionTimesOut() throws Exception {
        when(lockManager.tryAcquire(eq("lock:task:task-102"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.empty());

        AgentTask task = new AgentTask("task-102", "agent-1", "PROCESS", "payload");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.FAILED, task.getStatus());
        verify(lockManager, times(1)).tryAcquire(eq("lock:task:task-102"), any(Duration.class), any(Duration.class));
        verifyNoInteractions(distributedLock);
    }

    @Test
    @DisplayName("Task fails immediately if lock acquisition throws exception (fail-closed)")
    void testTaskFailsWhenLockAcquisitionThrowsException() throws Exception {
        when(lockManager.tryAcquire(eq("lock:task:task-103"), any(Duration.class), any(Duration.class)))
                .thenThrow(new LockAcquisitionException("Redis down"));

        AgentTask task = new AgentTask("task-103", "agent-1", "PROCESS", "payload");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.FAILED, task.getStatus());
        verifyNoInteractions(distributedLock);
    }

    @Test
    @DisplayName("Lock is closed even if task is interrupted during execution")
    void testLockReleasedWhenTaskInterrupted() throws Exception {
        when(lockManager.tryAcquire(eq("lock:task:task-104"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.of(distributedLock));

        AgentTask task = new AgentTask("task-104", "agent-1", "PROCESS", "payload");
        Future<?> future = service.submitTask(task);

        // Cancel with interrupt while task is sleeping
        Thread.sleep(100);
        future.cancel(true);

        // Allow virtual thread to process interrupt
        Thread.sleep(200);

        assertEquals(TaskStatus.FAILED, task.getStatus());
        verify(distributedLock, times(1)).close();
    }
}
