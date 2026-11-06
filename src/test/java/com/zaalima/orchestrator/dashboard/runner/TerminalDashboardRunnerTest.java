package com.zaalima.orchestrator.dashboard.runner;

import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.input.DashboardInputHandler;
import com.zaalima.orchestrator.dashboard.model.DashboardState;
import com.zaalima.orchestrator.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Terminal Dashboard Runner Tests")
class TerminalDashboardRunnerTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private DashboardInputHandler inputHandler;

    private DashboardProperties properties;
    private TerminalDashboardRunner runner;

    @BeforeEach
    void setUp() {
        properties = new DashboardProperties();
        runner = new TerminalDashboardRunner(dashboardService, inputHandler, properties);
    }

    @Test
    @DisplayName("Should remain inactive by default when interactive is false")
    void testRun_InactiveByDefault() {
        properties.setInteractive(false);
        runner.run();

        assertFalse(runner.isActive());
        verifyNoInteractions(dashboardService);
    }

    @Test
    @DisplayName("Should start interactive loop and process input when enabled")
    void testStartInteractiveLoop_ProcessesInput() throws Exception {
        properties.setInteractive(true);
        properties.setRefreshIntervalMs(100);

        DashboardState state = new DashboardState();
        when(dashboardService.getState()).thenReturn(state);
        when(dashboardService.isRunning()).thenReturn(true, true, false);
        when(dashboardService.renderCurrentView()).thenReturn("RENDERED VIEW");
        when(inputHandler.handleInput(anyChar(), eq(dashboardService))).thenReturn(false);

        ByteArrayInputStream in = new ByteArrayInputStream("r".getBytes());
        runner.startInteractiveLoop(in);

        assertTrue(runner.isActive());

        // Allow background thread to process
        Thread.sleep(300);

        runner.destroy();
        assertFalse(runner.isActive());
        verify(dashboardService, atLeastOnce()).stop();
    }
}
