package com.zaalima.orchestrator.dashboard.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaalima.orchestrator.dashboard.collector.RuntimeMetricsCollector;
import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.model.NodeMetricsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Periodically reports the local node's metrics snapshot to Redis for cluster-wide observability.
 */
@Service
public class NodeMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger(NodeMetricsReporter.class);

    private final RuntimeMetricsCollector localCollector;
    private final StringRedisTemplate redisTemplate;
    private final DashboardProperties properties;
    private final ObjectMapper objectMapper;

    public NodeMetricsReporter(
            RuntimeMetricsCollector localCollector,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            DashboardProperties properties,
            ObjectMapper objectMapper) {
        this.localCollector = localCollector;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes local node snapshot to Redis with configured TTL.
     */
    @Scheduled(fixedRateString = "${orchestrator.dashboard.report-interval-ms:5000}")
    public void reportMetrics() {
        if (!properties.isEnabled() || redisTemplate == null) {
            return;
        }

        try {
            NodeMetricsSnapshot snapshot = localCollector.collectLocalMetrics();
            String json = objectMapper.writeValueAsString(snapshot);
            String key = properties.getReportKeyPrefix() + snapshot.nodeId();
            Duration ttl = Duration.ofSeconds(properties.getReportTtlSeconds());

            redisTemplate.opsForValue().set(key, json, ttl);
            log.trace("Reported local node metrics to Redis key: {}", key);
        } catch (Exception ex) {
            log.debug("Unable to publish node metrics to Redis (Redis offline): {}", ex.getMessage());
        }
    }
}
