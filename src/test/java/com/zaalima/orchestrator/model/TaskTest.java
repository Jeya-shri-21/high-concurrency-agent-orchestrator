package com.zaalima.orchestrator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    @DisplayName("Task constructor initializes with default PENDING status and current timestamp")
    void testConstructor_Defaults() {
        long before = System.currentTimeMillis();
        Task task = new Task("task-01", "DATA_ANALYSIS", "{\"query\":\"SELECT 1\"}");
        long after = System.currentTimeMillis();

        assertEquals("task-01", task.getTaskId());
        assertEquals("DATA_ANALYSIS", task.getTaskType());
        assertEquals("{\"query\":\"SELECT 1\"}", task.getPayload());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertTrue(task.getCreatedAt() >= before && task.getCreatedAt() <= after);
    }

    @Test
    @DisplayName("Task constructor with explicit parameters sets all fields correctly")
    void testConstructor_Explicit() {
        Task task = new Task("task-02", "INFERENCE", "prompt", TaskStatus.RUNNING, 1000L);

        assertEquals("task-02", task.getTaskId());
        assertEquals("INFERENCE", task.getTaskType());
        assertEquals("prompt", task.getPayload());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertEquals(1000L, task.getCreatedAt());
    }

    @Test
    @DisplayName("Task throws NullPointerException when mandatory taskId or taskType is null")
    void testConstructor_NullValidation() {
        assertThrows(NullPointerException.class, () -> new Task(null, "TYPE", "payload"));
        assertThrows(NullPointerException.class, () -> new Task("id", null, "payload"));
    }

    @Test
    @DisplayName("setStatus updates task status safely")
    void testSetStatus() {
        Task task = new Task("task-03", "TRANSFORM", "raw-data");
        assertEquals(TaskStatus.PENDING, task.getStatus());

        task.setStatus(TaskStatus.RUNNING);
        assertEquals(TaskStatus.RUNNING, task.getStatus());

        task.setStatus(TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, task.getStatus());

        assertThrows(NullPointerException.class, () -> task.setStatus(null));
    }

    @Test
    @DisplayName("Task equals and hashCode conform to contract based on taskId")
    void testEqualsAndHashCode() {
        Task task1 = new Task("task-same", "TYPE_A", "payload A");
        Task task2 = new Task("task-same", "TYPE_B", "payload B", TaskStatus.COMPLETED, 5000L);
        Task task3 = new Task("task-diff", "TYPE_A", "payload A");

        assertEquals(task1, task2);
        assertEquals(task1.hashCode(), task2.hashCode());
        assertNotEquals(task1, task3);
    }

    @Test
    @DisplayName("Task status updates safely across concurrent virtual threads")
    void testConcurrentStatusUpdates() throws InterruptedException {
        Task task = new Task("task-concurrent", "CONCURRENT_JOB", "data");
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        task.setStatus(index % 2 == 0 ? TaskStatus.RUNNING : TaskStatus.COMPLETED);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(task.getStatus());
        }
    }
}
