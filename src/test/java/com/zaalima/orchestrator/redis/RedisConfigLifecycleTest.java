package com.zaalima.orchestrator.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Redis Configuration and Listener Lifecycle Tests")
class RedisConfigLifecycleTest {

    @Test
    @DisplayName("Resilient container starts successfully in available mode")
    void testResilientListenerContainerWhenRedisAvailable() {
        RedisConnectionFactory mockConnectionFactory = mock(RedisConnectionFactory.class);

        RedisConfig.ResilientRedisMessageListenerContainer container =
                new RedisConfig.ResilientRedisMessageListenerContainer(true, false, "localhost", 6379);

        container.setConnectionFactory(mockConnectionFactory);
        container.afterPropertiesSet();

        assertTrue(container.isAutoStartup());
        assertFalse(container.isRequired());
        assertEquals("localhost", container.getRedisHost());
        assertEquals(6379, container.getRedisPort());

        assertDoesNotThrow(container::start);
        assertTrue(container.isRunning(), "Container should be marked running after successful start");

        container.stop();
        assertFalse(container.isRunning(), "Container should stop cleanly");
    }

    @Test
    @DisplayName("Resilient container handles offline Redis gracefully in optional (default) mode")
    void testResilientListenerContainerWhenRedisUnavailableOptionalMode() {
        RedisConnectionFactory mockConnectionFactory = mock(RedisConnectionFactory.class);
        when(mockConnectionFactory.getConnection())
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis at localhost:6379"));

        RedisMessageSubscriber mockSubscriber = mock(RedisMessageSubscriber.class);
        ChannelTopic topic = new ChannelTopic(RedisConfig.TOPIC);

        RedisConfig.ResilientRedisMessageListenerContainer container =
                new RedisConfig.ResilientRedisMessageListenerContainer(true, false, "localhost", 6379);

        container.setConnectionFactory(mockConnectionFactory);
        container.addMessageListener(mockSubscriber, topic);
        container.afterPropertiesSet();

        // In optional mode (required = false), startup failure must NOT propagate an unhandled exception
        assertDoesNotThrow(container::start, "Optional mode must handle connection failure gracefully");
        assertFalse(container.isRunning(), "Container should not remain running if Redis is offline");
    }

    @Test
    @DisplayName("Resilient container fails fast when Redis is marked mandatory (required = true)")
    void testResilientListenerContainerWhenRedisUnavailableRequiredMode() {
        RedisConnectionFactory mockConnectionFactory = mock(RedisConnectionFactory.class);
        when(mockConnectionFactory.getConnection())
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis at redis.cluster:6379"));

        RedisMessageSubscriber mockSubscriber = mock(RedisMessageSubscriber.class);
        ChannelTopic topic = new ChannelTopic(RedisConfig.TOPIC);

        RedisConfig.ResilientRedisMessageListenerContainer container =
                new RedisConfig.ResilientRedisMessageListenerContainer(true, true, "redis.cluster", 6379);

        container.setConnectionFactory(mockConnectionFactory);
        container.addMessageListener(mockSubscriber, topic);
        container.afterPropertiesSet();

        assertTrue(container.isRequired());

        // In mandatory mode, the connection failure must be re-thrown
        assertThrows(Exception.class, container::start,
                "Mandatory mode must fail fast when Redis is unavailable");
    }

    @Test
    @DisplayName("Auto-startup can be disabled via configuration")
    void testResilientListenerContainerAutoStartupDisabled() {
        RedisConfig.ResilientRedisMessageListenerContainer container =
                new RedisConfig.ResilientRedisMessageListenerContainer(false, false, "localhost", 6379);

        assertFalse(container.isAutoStartup(), "Container auto-startup should reflect configuration");
    }

    @Test
    @DisplayName("NodeLoadReporter handles offline Redis during scheduled reporting without crashing")
    @SuppressWarnings("unchecked")
    void testNodeLoadReporterHandlesRedisOfflineGracefully() {
        StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = mock(ValueOperations.class);
        when(mockTemplate.opsForValue()).thenReturn(mockOps);
        doThrow(new RedisConnectionFailureException("Connection refused: Redis offline"))
                .when(mockOps).set(any(String.class), any(String.class), any(Duration.class));

        NodeLoadReporter reporter = new NodeLoadReporter(mockTemplate, "test-node", 15);
        reporter.taskStarted();
        assertEquals(1, reporter.getActiveTasks());

        // reportLoad must catch exception internally and not rethrow
        assertDoesNotThrow(reporter::reportLoad);
        reporter.taskFinished();
        assertEquals(0, reporter.getActiveTasks());
    }
}
