package com.zaalima.orchestrator.redis;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NodeLoadReporter {

    public static final String KEY_PREFIX = "node:load:";

    private final StringRedisTemplate redisTemplate;
    private final String nodeId;
    private final Duration ttl;
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    public NodeLoadReporter(
            StringRedisTemplate redisTemplate,
            @Value("${orchestrator.node.id}") String nodeId,
            @Value("${orchestrator.node.load-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.nodeId = nodeId;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    // call when a task starts
    public void taskStarted() {
        activeTasks.incrementAndGet();
    }

    // call when a task finishes
    public void taskFinished() {
        activeTasks.updateAndGet(v -> Math.max(0, v - 1));
    }

    public int getActiveTasks() {
        return activeTasks.get();
    }

    // publishes current load to Redis every 5 seconds
    @Scheduled(fixedRate = 5000)
    public void reportLoad() {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + nodeId,
                String.valueOf(activeTasks.get()),
                ttl);
    }
}
