package com.zaalima.orchestrator.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
public class RedisMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic agentTopic;

    public RedisMessagePublisher(
            StringRedisTemplate redisTemplate,
            ChannelTopic agentTopic) {
        this.redisTemplate = redisTemplate;
        this.agentTopic = agentTopic;
    }

    public void publish(String message) {
        redisTemplate.convertAndSend(agentTopic.getTopic(), message);
        System.out.println("Redis message published: " + message);
    }
}
