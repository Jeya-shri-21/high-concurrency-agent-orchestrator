package com.zaalima.orchestrator.service;

import java.util.ArrayList;
import java.util.List;
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
        when(distributedLock.isValid()).thenReturn(true);
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
    @DisplayName("Task fails if lock validity check fails before completing execution (stale-owner protection)")
    void testTaskFailsWhenLockValidityCheckFailsBeforeCompletion() throws Exception {
        // Lock acquired, but lease expired / lost before completion checkpoint
        when(distributedLock.isValid()).thenReturn(false);
        when(lockManager.tryAcquire(eq("lock:task:task-stale"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.of(distributedLock));

        AgentTask task = new AgentTask("task-stale", "agent-1", "PROCESS", "payload");
        Future<?> future = service.submitTask(task);

        future.get(5, TimeUnit.SECONDS);

        assertEquals(TaskStatus.FAILED, task.getStatus(), "Task must not complete if lock was lost during execution");
        verify(distributedLock, times(1)).close();
    }

    @Test
    @DisplayName("Concurrent attempts to execute the same task are prevented from executing simultaneously")
    void testConcurrentAttemptsToExecuteSameTask() throws Exception {
        when(distributedLock.isValid()).thenReturn(true);
        when(lockManager.tryAcquire(eq("lock:task:task-dup"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.of(distributedLock))
                .thenReturn(Optional.empty()); // Second contender fails to acquire

        AgentTask task1 = new AgentTask("task-dup", "agent-1", "PROCESS", "payload-1");
        AgentTask task2 = new AgentTask("task-dup", "agent-1", "PROCESS", "payload-2");

        Future<?> f1 = service.submitTask(task1);
        Future<?> f2 = service.submitTask(task2);

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);

        // One completes, the other fails due to lock acquisition timeout
        assertTrue(
                (task1.getStatus() == TaskStatus.COMPLETED && task2.getStatus() == TaskStatus.FAILED) ||
                (task2.getStatus() == TaskStatus.COMPLETED && task1.getStatus() == TaskStatus.FAILED),
                "Exactly one task should complete and the conflicting contender should fail"
        );
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

	@Test
	@DisplayName("Multiple tasks execute concurrently using virtual threads")
	void testMultipleTasksExecuteConcurrently() throws Exception {
	    int taskCount = 3;
	    when(distributedLock.isValid()).thenReturn(true);

	    when(lockManager.tryAcquire(
	            startsWith("lock:task:concurrent-"),
	            any(Duration.class),
	            any(Duration.class)
	    )).thenReturn(Optional.of(distributedLock));
	    List<AgentTask> tasks = new ArrayList<>();
	    List<Future<?>> futures = new ArrayList<>();
	
	    for (int i = 1; i <= taskCount; i++) {
	        AgentTask task = new AgentTask(
	                "concurrent-" + i,
	                "agent-" + i,
	                "PROCESS",
	                "payload-" + i
	        );
	
	        tasks.add(task);
	        futures.add(service.submitTask(task));
	    }
	
	    long startTime = System.nanoTime();
	
	    for (Future<?> future : futures) {
	        future.get(10, TimeUnit.SECONDS);
	    }
	
	    long elapsedMillis =
	            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
	
	    for (AgentTask task : tasks) {
	        assertEquals(
	                TaskStatus.COMPLETED,
	                task.getStatus(),
	                "Each task should complete successfully"
	        );
	    }
	
	    assertTrue(
	            elapsedMillis < 5500,
	            "Tasks should overlap in execution; elapsed time was "
	                    + elapsedMillis + " ms"
	    );
	}

	@Test
	@DisplayName("Executor rejects new tasks after shutdown")
	void testExecutorRejectsTasksAfterShutdown() {
	    service.shutdown();
	
	    AgentTask task = new AgentTask(
	            "task-after-shutdown",
	            "agent-1",
	            "PROCESS",
	            "payload"
	    );
	
	    assertThrows(
	            java.util.concurrent.RejectedExecutionException.class,
	            () -> service.submitTask(task),
	            "Submitting a task after shutdown should be rejected"
	    );
	}
	
	@Test
	@DisplayName("Rejected task is not retained in the task map")
	void testRejectedTaskIsNotRetained() {
	    service.shutdown();
	
	    AgentTask task = new AgentTask(
	            "rejected-task",
	            "agent-1",
	            "PROCESS",
	            "payload"
	    );
	
	    assertThrows(
	            java.util.concurrent.RejectedExecutionException.class,
	            () -> service.submitTask(task)
	    );
	
	    assertNull(
	            service.getTask("rejected-task"),
	            "A task rejected by the executor should not remain in the task map"
	    );
	}

	@Test
	@DisplayName("Distributed lock is released after successful task execution")
	void testLockReleasedAfterSuccessfulTask() throws Exception {
	    AgentTask task = new AgentTask(
	            "task-lock-release",
	            "agent-1",
	            "PROCESS",
	            "payload"
	    );
	
	    when(lockManager.tryAcquire(
	            eq("lock:task:task-lock-release"),
	            any(Duration.class),
	            any(Duration.class)
	    )).thenReturn(Optional.of(distributedLock));
	
	    when(distributedLock.isValid()).thenReturn(true);
	
	    Future<?> future = service.submitTask(task);
	    future.get(5, TimeUnit.SECONDS);
	
	    assertEquals(TaskStatus.COMPLETED, task.getStatus());
	    verify(distributedLock).close();
	}
	@Test
	@DisplayName("Submitted task can be retrieved by its task ID")
	void testSubmittedTaskCanBeRetrievedById() throws Exception {
	    when(lockManager.tryAcquire(
	            eq("lock:task:task-retrieval"),
	            any(Duration.class),
	            any(Duration.class)
	    )).thenReturn(Optional.of(distributedLock));

	    when(distributedLock.isValid()).thenReturn(true);

	    AgentTask task = new AgentTask(
	            "task-retrieval",
	            "agent-1",
	            "PROCESS",
	            "payload"
	    );

	    Future<?> future = service.submitTask(task);
	    future.get(5, TimeUnit.SECONDS);

	    assertSame(task, service.getTask("task-retrieval"));
	    assertEquals(TaskStatus.COMPLETED,
	            service.getTask("task-retrieval").getStatus());
	}

}
