package com.zaalima.orchestrator.handler;

import com.zaalima.orchestrator.model.Task;
import com.zaalima.orchestrator.model.TaskResult;
import com.zaalima.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TaskHandlerTest {

    @Test
    @DisplayName("TaskHandler implementation processes task successfully")
    void testTaskHandler_Success() throws Exception {
        TaskHandler handler = task -> {
            task.setStatus(TaskStatus.RUNNING);
            String output = "Processed payload: " + task.getPayload().toUpperCase();
            task.setStatus(TaskStatus.COMPLETED);
            return TaskResult.success(task.getTaskId(), output);
        };

        Task task = new Task("task-100", "TEXT_PROCESSOR", "hello world");
        TaskResult result = handler.handle(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("task-100", result.taskId());
        assertEquals("Processed payload: HELLO WORLD", result.output());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    @DisplayName("TaskHandler returns failure result when execution encounters error")
    void testTaskHandler_Failure() throws Exception {
        TaskHandler handler = task -> {
            task.setStatus(TaskStatus.FAILED);
            return TaskResult.failure(task.getTaskId(), "Invalid input format");
        };

        Task task = new Task("task-101", "VALIDATOR", "bad input");
        TaskResult result = handler.handle(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(TaskStatus.FAILED, result.status());
        assertEquals("Invalid input format", result.errorDetails());
        assertEquals(TaskStatus.FAILED, task.getStatus());
    }

    @Test
    @DisplayName("TaskHandler supports method filters task types")
    void testTaskHandler_Supports() {
        TaskHandler handler = new TaskHandler() {
            @Override
            public TaskResult handle(Task task) {
                return TaskResult.success(task.getTaskId(), "done");
            }

            @Override
            public boolean supports(String taskType) {
                return "MATH_COMPUTE".equalsIgnoreCase(taskType);
            }
        };

        assertTrue(handler.supports("MATH_COMPUTE"));
        assertTrue(handler.supports("math_compute"));
        assertFalse(handler.supports("IMAGE_GEN"));
    }

    @Test
    @DisplayName("TaskHandler executes concurrently on Java 21 Virtual Threads")
    void testTaskHandler_VirtualThreads() throws Exception {
        TaskHandler handler = task -> {
            task.setStatus(TaskStatus.RUNNING);
            Thread.sleep(50);
            task.setStatus(TaskStatus.COMPLETED);
            return TaskResult.success(task.getTaskId(), "Thread: " + Thread.currentThread());
        };

        int count = 20;
        CountDownLatch latch = new CountDownLatch(count);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        Task task = new Task("vt-task-" + idx, "CONCURRENT", "payload-" + idx);
                        TaskResult result = handler.handle(task);
                        assertTrue(result.isSuccess());
                    } catch (Exception e) {
                        fail("Virtual thread execution failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
    }
}
