package com.zaalima.orchestrator.dashboard.runner;

import com.zaalima.orchestrator.dashboard.config.DashboardProperties;
import com.zaalima.orchestrator.dashboard.input.DashboardInputHandler;
import com.zaalima.orchestrator.dashboard.renderer.TerminalRenderer;
import com.zaalima.orchestrator.dashboard.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the interactive terminal dashboard loop and keyboard input thread when enabled.
 * Designed to cleanly bypass execution in headless, test, or non-interactive environments.
 */
@Component
public class TerminalDashboardRunner implements CommandLineRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TerminalDashboardRunner.class);

    private final DashboardService dashboardService;
    private final DashboardInputHandler inputHandler;
    private final DashboardProperties properties;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private ExecutorService runnerExecutor;

    public TerminalDashboardRunner(
            DashboardService dashboardService,
            DashboardInputHandler inputHandler,
            DashboardProperties properties) {
        this.dashboardService = dashboardService;
        this.inputHandler = inputHandler;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        boolean shouldRunInteractive = properties.isInteractive();
        if (args != null) {
            for (String arg : args) {
                if ("--dashboard".equalsIgnoreCase(arg)
                        || "--orchestrator.dashboard.interactive=true".equalsIgnoreCase(arg)) {
                    shouldRunInteractive = true;
                    break;
                }
            }
        }

        if (!properties.isEnabled() || !shouldRunInteractive) {
            log.info("Interactive Terminal Dashboard runner is inactive (set --orchestrator.dashboard.interactive=true to launch console view).");
            return;
        }

        startInteractiveLoop(System.in);
    }

    /**
     * Starts the dashboard render loop and keyboard listening thread on background threads.
     *
     * @param inputStream the input stream to read keyboard commands from (default System.in)
     */
    public synchronized void startInteractiveLoop(InputStream inputStream) {
        if (!active.compareAndSet(false, true)) {
            return;
        }

        runnerExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "dashboard-runner");
            t.setDaemon(true);
            return t;
        });

        // 1. Periodic Render Thread
        runnerExecutor.submit(() -> {
            log.info("Interactive Terminal Dashboard rendering loop started.");
            while (active.get() && dashboardService.isRunning()) {
                try {
                    if (!dashboardService.getState().isPaused()) {
                        dashboardService.refreshMetrics();
                    }

                    String screen = dashboardService.renderCurrentView();
                    System.out.print(TerminalRenderer.CLEAR_SCREEN);
                    System.out.print(screen);
                    System.out.flush();

                    Thread.sleep(properties.getRefreshIntervalMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    log.error("Error during dashboard render cycle: {}", ex.getMessage());
                }
            }
            cleanupTerminal();
        });

        // 2. Keyboard Input Thread
        runnerExecutor.submit(() -> {
            try {
                while (active.get() && dashboardService.isRunning()) {
                    if (inputStream != null && inputStream.available() > 0) {
                        int ch = inputStream.read();
                        if (ch != -1) {
                            boolean quit = inputHandler.handleInput((char) ch, dashboardService);
                            if (quit) {
                                active.set(false);
                                break;
                            }
                            // Re-render immediately on user interaction
                            System.out.print(TerminalRenderer.CLEAR_SCREEN);
                            System.out.print(dashboardService.renderCurrentView());
                            System.out.flush();
                        }
                    } else {
                        Thread.sleep(100);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.debug("Keyboard listener encountered exception: {}", ex.getMessage());
            } finally {
                active.set(false);
            }
        });
    }

    private void cleanupTerminal() {
        System.out.print(TerminalRenderer.RESET);
        System.out.flush();
        log.info("Terminal settings restored and dashboard runner stopped.");
    }

    @Override
    public void destroy() {
        active.set(false);
        dashboardService.stop();
        if (runnerExecutor != null) {
            runnerExecutor.shutdownNow();
            try {
                runnerExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isActive() {
        return active.get();
    }
}
