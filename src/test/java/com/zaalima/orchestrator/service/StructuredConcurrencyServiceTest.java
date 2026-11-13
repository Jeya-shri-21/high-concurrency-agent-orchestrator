
package com.zaalima.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

class StructuredConcurrencyServiceTest {

    @Test
    void executeTasksShouldReturnBothTaskResults() throws Exception {
        StructuredConcurrencyService service =
                new StructuredConcurrencyService(3000);

        String result = service.executeTasks();

        assertNotNull(result);
        assertTrue(result.contains("Agent Task 1 completed"));
        assertTrue(result.contains("Agent Task 2 completed"));
    }

    @Test
    void executeTasksShouldTimeoutWhenDeadlineExceeded() {
        StructuredConcurrencyService service =
                new StructuredConcurrencyService(100);

        assertThrows(
                TimeoutException.class,
                service::executeTasks
        );
    }

    @Test
    void constructorShouldRejectZeroTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StructuredConcurrencyService(0)
        );
    }

    @Test
    void constructorShouldRejectNegativeTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StructuredConcurrencyService(-100)
        );
    }
}
