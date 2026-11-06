package com.zaalima.orchestrator.redis.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisListenerLifecycleManager Unit Tests")
class RedisListenerLifecycleManagerTest {

    @Mock
    private RedisMessageListenerContainer container;

    @Mock
    private RedisConnectionValidator validator;

    private RedisListenerLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        lifecycleManager = new RedisListenerLifecycleManager(container, validator);
    }

    @Test
    @DisplayName("onApplicationReady starts container when Redis is connected and container is stopped")
    void startsContainerWhenConnected() {
        when(validator.isConnected()).thenReturn(true);
        when(container.isRunning()).thenReturn(false);

        lifecycleManager.onApplicationReady();

        verify(container, times(1)).start();
    }

    @Test
    @DisplayName("onApplicationReady does not start container when Redis is disconnected")
    void skipsContainerWhenDisconnected() {
        when(validator.isConnected()).thenReturn(false);

        lifecycleManager.onApplicationReady();

        verify(container, never()).start();
    }

    @Test
    @DisplayName("onApplicationReady does not start container if container is already running")
    void skipsIfAlreadyRunning() {
        when(validator.isConnected()).thenReturn(true);
        when(container.isRunning()).thenReturn(true);

        lifecycleManager.onApplicationReady();

        verify(container, never()).start();
    }

    @Test
    @DisplayName("startListener starts container when connected and stopped")
    void startListenerManually() {
        when(container.isRunning()).thenReturn(false);
        when(validator.isConnected()).thenReturn(true);

        boolean started = lifecycleManager.startListener();

        assertTrue(started);
        verify(container, times(1)).start();
    }

    @Test
    @DisplayName("startListener returns false when Redis is offline")
    void startListenerReturnsFalseWhenOffline() {
        when(container.isRunning()).thenReturn(false);
        when(validator.isConnected()).thenReturn(false);

        boolean started = lifecycleManager.startListener();

        assertFalse(started);
        verify(container, never()).start();
    }

    @Test
    @DisplayName("stopListener stops container when currently running")
    void stopListenerStopsContainer() {
        when(container.isRunning()).thenReturn(true);

        lifecycleManager.stopListener();

        verify(container, times(1)).stop();
    }

    @Test
    @DisplayName("Null container is handled safely")
    void nullContainerHandledSafely() {
        RedisListenerLifecycleManager nullContainerManager =
                new RedisListenerLifecycleManager(null, validator);

        nullContainerManager.onApplicationReady();
        assertFalse(nullContainerManager.startListener());
        nullContainerManager.stopListener();
        assertFalse(nullContainerManager.isRunning());
    }
}
