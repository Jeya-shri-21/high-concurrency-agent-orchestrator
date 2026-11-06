package com.zaalima.orchestrator.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Validates active Redis connectivity by issuing a safe PING command to the datastore.
 * <p>
 * Ensures transparent failure reporting without emitting false success messages when Redis is offline.
 * </p>
 */
@Component
public class RedisConnectionValidator {

    private static final Logger log = LoggerFactory.getLogger(RedisConnectionValidator.class);

    private final RedisConnectionFactory connectionFactory;

    @Autowired
    public RedisConnectionValidator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
    }

    /**
     * Checks if the Redis server is currently reachable and responsive.
     *
     * @return {@code true} if Redis responded to PING with PONG, {@code false} otherwise
     */
    public boolean isConnected() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            boolean reachable = "PONG".equalsIgnoreCase(response);
            if (reachable) {
                log.info("Redis health check verified: server responded with PONG");
            } else {
                log.warn("Redis health check returned unexpected response: {}", response);
            }
            return reachable;
        } catch (Exception e) {
            log.warn("Redis connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}
