package com.zaalima.orchestrator.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Manages the lifecycle of {@link RedisMessageListenerContainer} safely.
 * <p>
 * Prevents Spring application startup failures when Redis is offline by verifying
 * active connectivity via {@link RedisConnectionValidator} before starting the listener container.
 * </p>
 */
@Component
public class RedisListenerLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(RedisListenerLifecycleManager.class);

    private final RedisMessageListenerContainer container;
    private final RedisConnectionValidator validator;

    @Autowired
    public RedisListenerLifecycleManager(
            @Nullable RedisMessageListenerContainer container,
            RedisConnectionValidator validator) {

        this.container = container;
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (container == null) {
            log.debug("RedisMessageListenerContainer bean is not present; skipping listener startup");
            return;
        }

        if (validator.isConnected()) {
            try {
                if (!container.isRunning()) {
                    container.start();
                    log.info("RedisMessageListenerContainer started successfully after verifying Redis connectivity");
                }
            } catch (Exception e) {
                log.warn("Could not start RedisMessageListenerContainer: {}", e.getMessage());
            }
        } else {
            log.warn("Redis is not available at application startup; RedisMessageListenerContainer will remain idle until Redis is reachable");
        }
    }

    /**
     * Attempts to start the listener container if Redis is currently responsive.
     *
     * @return {@code true} if running or started, {@code false} if Redis is unavailable or startup failed
     */
    public boolean startListener() {
        if (container == null) {
            return false;
        }
        if (container.isRunning()) {
            return true;
        }
        if (validator.isConnected()) {
            try {
                container.start();
                log.info("RedisMessageListenerContainer manually started");
                return true;
            } catch (Exception e) {
                log.warn("Failed to start RedisMessageListenerContainer: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Gracefully stops the listener container.
     */
    public void stopListener() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("RedisMessageListenerContainer stopped");
        }
    }

    /**
     * Checks whether the listener container is actively running.
     *
     * @return {@code true} if running, {@code false} otherwise
     */
    public boolean isRunning() {
        return container != null && container.isRunning();
    }
}
