package com.zaalima.orchestrator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskResultTest {

    @Test
    @DisplayName("TaskResult.success creates completed result with output")
    void testSuccessFactory() {
        TaskResult result = TaskResult.success("task-01", "{\"status\":\"ok\"}");

        assertEquals("task-01", result.taskId());
        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("{\"status\":\"ok\"}", result.output());
        assertNull(result.errorDetails());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("TaskResult.failure creates failed result with error details")
    void testFailureFactory() {
        TaskResult result = TaskResult.failure("task-02", "Connection timeout to agent node");

        assertEquals("task-02", result.taskId());
        assertEquals(TaskStatus.FAILED, result.status());
        assertNull(result.output());
        assertEquals("Connection timeout to agent node", result.errorDetails());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("TaskResult rejects null taskId or null status")
    void testNullValidation() {
        assertThrows(NullPointerException.class, () -> new TaskResult(null, TaskStatus.COMPLETED, "out", null));
        assertThrows(NullPointerException.class, () -> new TaskResult("task-id", null, "out", null));
    }

    @Test
    @DisplayName("TaskResult record equality and toString contract")
    void testEqualityAndToString() {
        TaskResult r1 = TaskResult.success("task-1", "output");
        TaskResult r2 = TaskResult.success("task-1", "output");
        TaskResult r3 = TaskResult.failure("task-1", "error");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
        assertTrue(r1.toString().contains("task-1"));
    }
}
