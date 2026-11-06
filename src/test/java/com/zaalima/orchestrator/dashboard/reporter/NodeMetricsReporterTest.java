package com.zaalima.orchestrator.dashboard.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.dashboard.collector.RuntimeMetricsCollector;
import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import com.zaalima.orchestrator.dashboard.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Node Metrics Reporter Tests")
class NodeMetricsReporterTest {

    @Mock
    private RuntimeMetricsCollector localCollector;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DashboardProperties properties;
    private ObjectMapper objectMapper;
    private NodeMetricsReporter reporter;

    @BeforeEach
    void setUp() {
        properties = new DashboardProperties();
        properties.setReportKeyPrefix("node:metrics:");
        properties.setReportTtlSeconds(30);
        objectMapper = new ObjectMapper();

        reporter = new NodeMetricsReporter(localCollector, redisTemplate, properties, objectMapper);
    }

    @Test
    @DisplayName("Should report local metrics snapshot to Redis with configured TTL")
    void testReportMetrics_Success() {
        NodeMetricsSnapshot snapshot = new NodeMetricsSnapshot(
                "node-report-1", System.currentTimeMillis(), NodeStatus.UP,
                100, 2, 5, 0, 7, 100.0, 500.0, 10, 15, 30, null, true, null, true
        );

        when(localCollector.collectLocalMetrics()).thenReturn(snapshot);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        reporter.reportMetrics();

        verify(valueOperations).set(
                eq("node:metrics:node-report-1"),
                contains("node-report-1"),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    @DisplayName("Should gracefully handle Redis failure without throwing an exception")
    void testReportMetrics_RedisFailureHandledGracefully() {
        NodeMetricsSnapshot snapshot = new NodeMetricsSnapshot(
                "node-fail-1", System.currentTimeMillis(), NodeStatus.UP,
                100, 2, 5, 0, 7, 100.0, 500.0, 10, 15, 30, null, true, null, true
        );

        when(localCollector.collectLocalMetrics()).thenReturn(snapshot);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Connection timed out to Redis"));

        // Should not throw
        assertDoesNotThrow(() -> reporter.reportMetrics());
    }

    @Test
    @DisplayName("Should skip reporting when dashboard is disabled or redisTemplate is null")
    void testReportMetrics_Disabled() {
        properties.setEnabled(false);
        reporter.reportMetrics();
        verifyNoInteractions(localCollector);

        properties.setEnabled(true);
        NodeMetricsReporter nullRedisReporter = new NodeMetricsReporter(localCollector, null, properties, objectMapper);
        nullRedisReporter.reportMetrics();
        verifyNoInteractions(localCollector);
    }
}
