package com.zaalima.orchestrator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskStatusTest {

    @Test
    @DisplayName("TaskStatus contains all required 4 enum values")
    void testEnumValues() {
        TaskStatus[] values = TaskStatus.values();
        assertEquals(4, values.length);
        assertNotNull(TaskStatus.valueOf("PENDING"));
        assertNotNull(TaskStatus.valueOf("RUNNING"));
        assertNotNull(TaskStatus.valueOf("COMPLETED"));
        assertNotNull(TaskStatus.valueOf("FAILED"));
    }

    @Test
    @DisplayName("isTerminal identifies terminal states correctly")
    void testIsTerminal() {
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.RUNNING.isTerminal());
        assertTrue(TaskStatus.COMPLETED.isTerminal());
        assertTrue(TaskStatus.FAILED.isTerminal());
    }
}
