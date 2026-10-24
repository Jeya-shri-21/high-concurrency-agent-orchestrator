package com.zaalima.orchestrator.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaalima.orchestrator.redis.RedisMessageSubscriber;
import com.zaalima.orchestrator.redis.subscriber.TaskMessageSubscriber;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring configuration for Redis connectivity and Pub/Sub messaging infrastructure.
 * <p>
 * Reuses existing Spring Data Redis and Lettuce connection factory configurations,
 * integrates with Java 21 Virtual Threads for non-blocking message dispatching,
 * and sets {@code isAutoStartup()} to {@code false} to avoid crashing unit tests when Redis is absent.
 * </p>
 */
@Configuration
public class RedisConfiguration {

    @Value("${orchestrator.redis.messaging.task-channel:orchestrator:tasks:events}")
    private String taskChannelName;

    @Value("${orchestrator.redis.messaging.agent-topic:agent-orchestrator}")
    private String agentTopicName;

    @Bean
    @Primary
    public ChannelTopic taskChannelTopic() {
        return new ChannelTopic(taskChannelName);
    }

    @Bean
    public ChannelTopic agentTopic() {
        return new ChannelTopic(agentTopicName);
    }

    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
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
            TaskMessageSubscriber taskMessageSubscriber,
            RedisMessageSubscriber legacySubscriber,
            ChannelTopic taskChannelTopic,
            ChannelTopic agentTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public boolean isAutoStartup() {
                // Prevent DefaultLifecycleProcessor from failing context startup when Redis is absent
                return false;
            }
        };

        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(taskMessageSubscriber, taskChannelTopic);
        container.addMessageListener(legacySubscriber, agentTopic);

        // Utilize Java 21 Virtual Threads for concurrent, lightweight subscriber processing
        ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        container.setTaskExecutor(virtualThreadExecutor);
        container.setSubscriptionExecutor(virtualThreadExecutor);

        // Resilient reconnect interval when connection is lost
        container.setRecoveryInterval(3000);

        return container;
    }
}
