package com.zaalima.orchestrator.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Interactive Virtual Thread Cluster Dashboard.
 */
@Configuration
@ConfigurationProperties(prefix = "orchestrator.dashboard")
public class DashboardProperties {

    /**
     * Whether the dashboard metrics subsystem and REST endpoints are enabled.
     */
    private boolean enabled = true;

    /**
     * Whether to start the interactive terminal console loop on application startup.
     * Default is false to prevent blocking tests, headless CI/CD, or background containers.
     */
    private boolean interactive = false;

    /**
     * Frequency in milliseconds at which the dashboard screen refreshes metrics.
     */
    private long refreshIntervalMs = 2000;

    /**
     * Threshold in milliseconds after which a node snapshot is considered stale.
     */
    private long staleThresholdMs = 15000;

    /**
     * Whether to initialize the dashboard in simulated demo mode.
     */
    private boolean demoMode = false;

    /**
     * Redis key prefix used for publishing and scanning node metrics snapshots.
     */
    private String reportKeyPrefix = "node:metrics:";

    /**
     * TTL in seconds for node metrics keys in Redis.
     */
    private long reportTtlSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = Math.max(500, refreshIntervalMs);
    }

    public long getStaleThresholdMs() {
        return staleThresholdMs;
    }

    public void setStaleThresholdMs(long staleThresholdMs) {
        this.staleThresholdMs = Math.max(1000, staleThresholdMs);
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public String getReportKeyPrefix() {
        return reportKeyPrefix;
    }

    public void setReportKeyPrefix(String reportKeyPrefix) {
        this.reportKeyPrefix = reportKeyPrefix;
    }

    public long getReportTtlSeconds() {
        return reportTtlSeconds;
    }

    public void setReportTtlSeconds(long reportTtlSeconds) {
        this.reportTtlSeconds = reportTtlSeconds;
    }
}
