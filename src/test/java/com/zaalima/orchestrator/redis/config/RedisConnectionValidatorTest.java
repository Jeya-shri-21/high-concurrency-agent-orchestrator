package com.zaalima.orchestrator.redis.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisConnectionValidator Unit Tests")
class RedisConnectionValidatorTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    private RedisConnectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RedisConnectionValidator(connectionFactory);
    }

    @Test
    @DisplayName("Returns true when Redis responds to PING with PONG")
    void returnsTrueWhenRedisRespondsPong() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        boolean result = validator.isConnected();

        assertTrue(result);
        verify(connection, times(1)).ping();
        verify(connection, times(1)).close();
    }

    @Test
    @DisplayName("Returns false when Redis responds with something other than PONG")
    void returnsFalseWhenRedisRespondsUnexpectedly() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("BUSY");

        boolean result = validator.isConnected();

        assertFalse(result);
        verify(connection, times(1)).close();
    }

    @Test
    @DisplayName("Returns false when getConnection throws RedisConnectionFailureException")
    void returnsFalseOnConnectionFailure() {
        when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("Unable to connect"));

        boolean result = validator.isConnected();

        assertFalse(result);
    }

    @Test
    @DisplayName("Returns false when ping throws QueryTimeoutException")
    void returnsFalseOnTimeout() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new QueryTimeoutException("Ping timed out"));

        boolean result = validator.isConnected();

        assertFalse(result);
        verify(connection, times(1)).close();
    }
}
