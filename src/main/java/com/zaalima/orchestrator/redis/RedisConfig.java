package com.zaalima.orchestrator.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaalima.orchestrator.redis.subscriber.TaskMessageSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring configuration for Redis connectivity, Pub/Sub topics, serialization,
 * and resilient listener lifecycle management.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    public static final String TOPIC = "agent-orchestrator";

    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean("taskChannelTopic")
    @Primary
    public ChannelTopic taskChannelTopic(
            @Value("${orchestrator.redis.messaging.task-channel:orchestrator:tasks:events}") String taskChannelName) {
        return new ChannelTopic(taskChannelName);
    }

    @Bean("agentTopic")
    public ChannelTopic agentTopic(
            @Value("${orchestrator.redis.messaging.agent-topic:" + TOPIC + "}") String agentTopicName) {
        return new ChannelTopic(agentTopicName);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    @ConditionalOnProperty(name = "orchestrator.redis.messaging.enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            @Nullable TaskMessageSubscriber taskMessageSubscriber,
            @Nullable RedisMessageSubscriber subscriber,
            @Qualifier("taskChannelTopic") ChannelTopic taskChannelTopic,
            @Qualifier("agentTopic") ChannelTopic agentTopic,
            @Value("${orchestrator.redis.listener.auto-startup:true}") boolean autoStartup,
            @Value("${orchestrator.redis.required:false}") boolean redisRequired,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort) {

        ResilientRedisMessageListenerContainer container =
                new ResilientRedisMessageListenerContainer(autoStartup, redisRequired, redisHost, redisPort);

        container.setConnectionFactory(connectionFactory);
        if (taskMessageSubscriber != null && taskChannelTopic != null) {
            container.addMessageListener(taskMessageSubscriber, taskChannelTopic);
        }
        if (subscriber != null && agentTopic != null) {
            container.addMessageListener(subscriber, agentTopic);
        }

        // Utilize Java 21 Virtual Threads for concurrent, lightweight subscriber processing
        ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        container.setTaskExecutor(virtualThreadExecutor);
        container.setSubscriptionExecutor(virtualThreadExecutor);

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
