package com.zaalima.orchestrator.redis;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NodeLoadReader {

    private final StringRedisTemplate redisTemplate;

    public NodeLoadReader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // returns nodeId -> active task count for all live nodes
    public Map<String, Integer> getNodeLoads() {
        Map<String, Integer> loads = new HashMap<>();
        Set<String> keys = redisTemplate.keys(NodeLoadReporter.KEY_PREFIX + "*");
        if (keys == null) {
            return loads;
        }
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                String nodeId = key.substring(NodeLoadReporter.KEY_PREFIX.length());
                loads.put(nodeId, Integer.parseInt(value));
            }
        }
        return loads;
    }

    // convenience: least loaded node, or null if no nodes
    public String getLeastLoadedNode() {
        return getNodeLoads().entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}