package com.zaalima.orchestrator.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    public static final String TOPIC = "agent-orchestrator";

    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public ChannelTopic agentTopic() {
        return new ChannelTopic(TOPIC);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber subscriber,
            ChannelTopic agentTopic,
            @Value("${orchestrator.redis.listener.auto-startup:true}") boolean autoStartup,
            @Value("${orchestrator.redis.required:false}") boolean redisRequired,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort) {

        ResilientRedisMessageListenerContainer container =
                new ResilientRedisMessageListenerContainer(autoStartup, redisRequired, redisHost, redisPort);

        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, agentTopic);
        container.setRecoveryInterval(5000);

        return container;
    }

    /**
     * Resilient extension of {@link RedisMessageListenerContainer} that provides clear diagnostics,
     * supports offline standalone development, and enforces fail-fast behavior when Redis is mandatory.
     */
    public static class ResilientRedisMessageListenerContainer extends RedisMessageListenerContainer {

        private static final Logger containerLog = LoggerFactory.getLogger(ResilientRedisMessageListenerContainer.class);

        private final boolean autoStartup;
        private final boolean required;
        private final String redisHost;
        private final int redisPort;

        public ResilientRedisMessageListenerContainer(boolean autoStartup, boolean required, String redisHost, int redisPort) {
            this.autoStartup = autoStartup;
            this.required = required;
            this.redisHost = redisHost;
            this.redisPort = redisPort;
        }

        @Override
        public boolean isAutoStartup() {
            return autoStartup;
        }

        public boolean isRequired() {
            return required;
        }

        public String getRedisHost() {
            return redisHost;
        }

        public int getRedisPort() {
            return redisPort;
        }

        @Override
        public void start() {
            try {
                super.start();
                containerLog.info("RedisMessageListenerContainer started successfully on {}:{} for channel '{}'",
                        redisHost, redisPort, TOPIC);
            } catch (Exception ex) {
                if (required) {
                    containerLog.error("CRITICAL: Failed to start RedisMessageListenerContainer - Redis is mandatory "
                            + "(orchestrator.redis.required=true) but unreachable at {}:{}. Error: {}",
                            redisHost, redisPort, ex.getMessage(), ex);
                    throw ex;
                } else {
                    containerLog.warn("Redis is unreachable at {}:{}. RedisMessageListenerContainer will not start eagerly. "
                            + "Application starting in standalone/offline mode. To enforce Redis on startup, set orchestrator.redis.required=true. "
                            + "Reason: {}", redisHost, redisPort, ex.getMessage());
                    try {
                        super.stop();
                    } catch (Exception stopEx) {
                        containerLog.debug("Error during listener container cleanup stop: {}", stopEx.getMessage());
                    }
                }
            }
        }
    }
}
