
package com.zaalima.orchestrator.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class StructuredConcurrencyServiceTest {

    private final StructuredConcurrencyService service =
            new StructuredConcurrencyService();

    @Test
    void executeTasksShouldReturnBothTaskResults() throws Exception {

        String result = service.executeTasks();

        assertNotNull(result);
        assertTrue(result.contains("Agent Task 1 completed"));
        assertTrue(result.contains("Agent Task 2 completed"));
    }
}
