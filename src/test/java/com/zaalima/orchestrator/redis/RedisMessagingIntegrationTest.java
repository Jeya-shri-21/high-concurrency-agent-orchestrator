package com.zaalima.orchestrator.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.redis.config.RedisConnectionValidator;
import com.zaalima.orchestrator.redis.model.TaskEventType;
import com.zaalima.orchestrator.redis.model.TaskMessage;
import com.zaalima.orchestrator.redis.publisher.RedisTaskMessagePublisher;
import com.zaalima.orchestrator.redis.subscriber.TaskMessageSubscriber;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for Redis connection and Pub/Sub messaging against a real
 * Redis instance running in Testcontainers (redis:7-alpine).
 * <p>
 * If Docker is unavailable in the environment, these tests are skipped cleanly via JUnit 5 assumptions.
 * </p>
 */
@DisplayName("RedisMessaging Integration Tests")
class RedisMessagingIntegrationTest {

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static boolean dockerAvailable = false;

    private RedisConnectionValidator connectionValidator;
    private ChannelTopic topic;
    private ObjectMapper objectMapper;
    private RedisTaskMessagePublisher publisher;
    private TaskMessageSubscriber subscriber;
    private RedisMessageListenerContainer listenerContainer;

    @BeforeAll
    static void setUpAll() {
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }

        if (dockerAvailable) {
            redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redisContainer.start();

            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(
                    redisContainer.getHost(),
                    redisContainer.getMappedPort(6379)
            );
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(3))
                    .build();

            connectionFactory = new LettuceConnectionFactory(redisConfig, clientConfig);
            connectionFactory.afterPropertiesSet();

            redisTemplate = new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(dockerAvailable, "Docker environment is not available; skipping real Redis integration tests");

        connectionValidator = new RedisConnectionValidator(connectionFactory);
        topic = new ChannelTopic("test:integration:tasks");
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        publisher = new RedisTaskMessagePublisher(redisTemplate, topic, objectMapper);
        subscriber = new TaskMessageSubscriber(objectMapper, null);

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        listenerContainer.addMessageListener(subscriber, topic);
        listenerContainer.setTaskExecutor(Executors.newVirtualThreadPerTaskExecutor());
        listenerContainer.setSubscriptionExecutor(Executors.newVirtualThreadPerTaskExecutor());
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
    }

    @AfterEach
    void tearDown() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Test
    @DisplayName("RedisConnectionValidator reports active connection to Testcontainers Redis")
    void testRedisConnectivity() {
        assertTrue(connectionValidator.isConnected(), "RedisConnectionValidator should report connected");
    }

    @Test
    @DisplayName("Publishes message and subscriber receives it asynchronously via real Redis Pub/Sub")
    void testPublishAndReceiveMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TaskMessage> received = new AtomicReference<>();

        TaskMessageSubscriber customSubscriber = new TaskMessageSubscriber(objectMapper, null, msg -> {
            received.set(msg);
            latch.countDown();
        });

        listenerContainer.addMessageListener(customSubscriber, topic);

        // Allow subscription to establish
        Thread.sleep(500);

        TaskMessage testMessage = new TaskMessage(
                "task-integration-001",
                "agent-worker-integration",
                "BENCHMARK",
                "payload-testcontainers",
                TaskStatus.PENDING,
                TaskEventType.TASK_SUBMITTED,
                "corr-int-1",
                Instant.now()
        );

        publisher.publish(testMessage);

        boolean reached = latch.await(5, TimeUnit.SECONDS);
        assertTrue(reached, "Subscriber did not receive message within timeout");

        TaskMessage result = received.get();
        assertNotNull(result);
        assertEquals("task-integration-001", result.getTaskId());
        assertEquals("agent-worker-integration", result.getAgentId());
        assertEquals(TaskStatus.PENDING, result.getStatus());
        assertEquals(TaskEventType.TASK_SUBMITTED, result.getEventType());
    }
}
