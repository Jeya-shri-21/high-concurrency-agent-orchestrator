
package com.zaalima.orchestrator.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Node Load Reporter Tests")
class NodeLoadReporterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private NodeLoadReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new NodeLoadReporter(redisTemplate, "test-node", 15);
    }

    @Test
    @DisplayName("Should increment active task count when a task starts")
    void shouldIncrementActiveTaskCount() {
        reporter.taskStarted();
        reporter.taskStarted();

        assertEquals(2, reporter.getActiveTasks());
    }

    @Test
    @DisplayName("Should decrement active task count when a task finishes")
    void shouldDecrementActiveTaskCount() {
        reporter.taskStarted();
        reporter.taskStarted();

        reporter.taskFinished();

        assertEquals(1, reporter.getActiveTasks());
    }

    @Test
    @DisplayName("Should never allow active task count below zero")
    void shouldNotAllowNegativeActiveTaskCount() {
        reporter.taskFinished();
        reporter.taskFinished();

        assertEquals(0, reporter.getActiveTasks());
    }

    @Test
    @DisplayName("Should publish active task count to Redis with configured TTL")
    void shouldPublishActiveTaskCountToRedis() {
        reporter.taskStarted();
        reporter.taskStarted();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        reporter.reportLoad();

        verify(valueOperations).set(
                eq("node:load:test-node"),
                eq("2"),
                eq(Duration.ofSeconds(15))
        );
    }

    @Test
    @DisplayName("Should handle Redis failure without throwing an exception")
    void shouldHandleRedisFailureGracefully() {
        when(redisTemplate.opsForValue())
                .thenThrow(new RuntimeException("Redis unavailable"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> reporter.reportLoad()
        );
    }
}
