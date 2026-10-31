package com.zaalima.orchestrator.dashboard.input;

import com.zaalima.orchestrator.dashboard.service.DashboardService;
import org.springframework.stereotype.Component;

/**
 * Handles interactive keyboard inputs from the terminal console.
 * Decoupled from System.in to allow deterministic unit testing.
 */
@Component
public class DashboardInputHandler {

    /**
     * Processes a keyboard command character.
     *
     * @param key the keystroke character
     * @param service the DashboardService instance
     * @return true if the command is a quit request (Q), false otherwise
     */
    public boolean handleInput(char key, DashboardService service) {
        if (service == null) {
            return false;
        }

        switch (key) {
            case 'q':
            case 'Q':
                service.stop();
                return true;

            case 'r':
            case 'R':
                service.refreshNow();
                return false;

            case 'p':
            case 'P':
                service.togglePause();
                return false;

            case 'n':
            case 'N':
                service.cycleNextNode();
                return false;

            case 'h':
            case 'H':
                service.toggleHelp();
                return false;

            case 27: // ESC key
            case 'b':
            case 'B':
                if (service.getState().isShowHelp()) {
                    service.getState().setShowHelp(false);
                    service.getState().setStatusMessage("Returned to live dashboard.");
                }
                return false;

            case 'd':
            case 'D':
                service.toggleDemoMode();
                return false;

            default:
                // Ignore unrecognized keystrokes without disrupting display
                return false;
        }
    }
}
