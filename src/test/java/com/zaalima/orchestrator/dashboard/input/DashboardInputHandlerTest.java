package com.zaalima.orchestrator.dashboard.input;

import com.zaalima.orchestrator.dashboard.model.DashboardState;
import com.zaalima.orchestrator.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard Input Handler Tests")
class DashboardInputHandlerTest {

    @Mock
    private DashboardService dashboardService;

    private DashboardInputHandler inputHandler;
    private DashboardState state;

    @BeforeEach
    void setUp() {
        inputHandler = new DashboardInputHandler();
        state = new DashboardState();
        lenient().when(dashboardService.getState()).thenReturn(state);
    }

    @Test
    @DisplayName("Q and q should stop dashboard and signal exit")
    void testQuitCommand() {
        boolean quitUpper = inputHandler.handleInput('Q', dashboardService);
        assertTrue(quitUpper);
        verify(dashboardService).stop();

        boolean quitLower = inputHandler.handleInput('q', dashboardService);
        assertTrue(quitLower);
        verify(dashboardService, times(2)).stop();
    }

    @Test
    @DisplayName("R and r should trigger immediate refresh")
    void testRefreshCommand() {
        boolean quit = inputHandler.handleInput('r', dashboardService);
        assertFalse(quit);
        verify(dashboardService).refreshNow();

        inputHandler.handleInput('R', dashboardService);
        verify(dashboardService, times(2)).refreshNow();
    }

    @Test
    @DisplayName("P and p should toggle pause/resume")
    void testPauseCommand() {
        boolean quit = inputHandler.handleInput('p', dashboardService);
        assertFalse(quit);
        verify(dashboardService).togglePause();
    }

    @Test
    @DisplayName("N and n should cycle to next node")
    void testNextNodeCommand() {
        boolean quit = inputHandler.handleInput('n', dashboardService);
        assertFalse(quit);
        verify(dashboardService).cycleNextNode();
    }

    @Test
    @DisplayName("H and h should toggle help screen")
    void testHelpCommand() {
        boolean quit = inputHandler.handleInput('h', dashboardService);
        assertFalse(quit);
        verify(dashboardService).toggleHelp();
    }

    @Test
    @DisplayName("ESC or B should dismiss help screen when open")
    void testDismissHelpCommand() {
        state.setShowHelp(true);

        boolean quit = inputHandler.handleInput((char) 27, dashboardService); // ESC
        assertFalse(quit);
        assertFalse(state.isShowHelp());

        state.setShowHelp(true);
        inputHandler.handleInput('b', dashboardService);
        assertFalse(state.isShowHelp());
    }

    @Test
    @DisplayName("D and d should toggle demo mode")
    void testDemoCommand() {
        boolean quit = inputHandler.handleInput('d', dashboardService);
        assertFalse(quit);
        verify(dashboardService).toggleDemoMode();
    }

    @Test
    @DisplayName("Unrecognized characters and null service should not cause exceptions")
    void testUnknownCommandAndNullSafety() {
        assertFalse(inputHandler.handleInput('z', dashboardService));
        assertFalse(inputHandler.handleInput('1', dashboardService));
        assertFalse(inputHandler.handleInput('Q', null));
    }
}
