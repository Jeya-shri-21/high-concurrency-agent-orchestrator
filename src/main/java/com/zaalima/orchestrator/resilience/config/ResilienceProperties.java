package com.zaalima.orchestrator.resilience.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Resilience4j Circuit Breaker, Bulkhead,
 * and external API timeouts.
 */
@Configuration
@ConfigurationProperties(prefix = "orchestrator.resilience")
public class ResilienceProperties {

    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    private BulkheadProperties bulkhead = new BulkheadProperties();
    private TimeoutProperties timeout = new TimeoutProperties();

    @PostConstruct
    public void validate() {
        circuitBreaker.validate();
        bulkhead.validate();
        timeout.validate();
    }

    public CircuitBreakerProperties getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public BulkheadProperties getBulkhead() {
        return bulkhead;
    }

    public void setBulkhead(BulkheadProperties bulkhead) {
        this.bulkhead = bulkhead;
    }

    public TimeoutProperties getTimeout() {
        return timeout;
    }

    public void setTimeout(TimeoutProperties timeout) {
        this.timeout = timeout;
    }

    public static class CircuitBreakerProperties {
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
        private float failureRateThreshold = 50.0f;
        private float slowCallRateThreshold = 50.0f;
        private long slowCallDurationThresholdMs = 2000L;
        private long waitDurationInOpenStateMs = 5000L;
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;

        public void validate() {
            if (slidingWindowSize <= 0) {
                throw new IllegalArgumentException("slidingWindowSize must be greater than 0");
            }
            if (minimumNumberOfCalls <= 0) {
                throw new IllegalArgumentException("minimumNumberOfCalls must be greater than 0");
            }
            if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
                throw new IllegalArgumentException("failureRateThreshold must be between 1 and 100");
            }
            if (slowCallRateThreshold <= 0 || slowCallRateThreshold > 100) {
                throw new IllegalArgumentException("slowCallRateThreshold must be between 1 and 100");
            }
            if (slowCallDurationThresholdMs <= 0) {
                throw new IllegalArgumentException("slowCallDurationThresholdMs must be greater than 0");
            }
            if (waitDurationInOpenStateMs <= 0) {
                throw new IllegalArgumentException("waitDurationInOpenStateMs must be greater than 0");
            }
            if (permittedNumberOfCallsInHalfOpenState <= 0) {
                throw new IllegalArgumentException("permittedNumberOfCallsInHalfOpenState must be greater than 0");
            }
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public long getSlowCallDurationThresholdMs() {
            return slowCallDurationThresholdMs;
        }

        public void setSlowCallDurationThresholdMs(long slowCallDurationThresholdMs) {
            this.slowCallDurationThresholdMs = slowCallDurationThresholdMs;
        }

        public long getWaitDurationInOpenStateMs() {
            return waitDurationInOpenStateMs;
        }

        public void setWaitDurationInOpenStateMs(long waitDurationInOpenStateMs) {
            this.waitDurationInOpenStateMs = waitDurationInOpenStateMs;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public boolean isAutomaticTransitionFromOpenToHalfOpenEnabled() {
            return automaticTransitionFromOpenToHalfOpenEnabled;
        }

        public void setAutomaticTransitionFromOpenToHalfOpenEnabled(boolean automaticTransitionFromOpenToHalfOpenEnabled) {
            this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
        }
    }

    public static class BulkheadProperties {
        private int maxConcurrentCalls = 10;
        private long maxWaitDurationMs = 200L;

        public void validate() {
            if (maxConcurrentCalls <= 0) {
                throw new IllegalArgumentException("maxConcurrentCalls must be greater than 0");
            }
            if (maxWaitDurationMs < 0) {
                throw new IllegalArgumentException("maxWaitDurationMs cannot be negative");
            }
        }

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public long getMaxWaitDurationMs() {
            return maxWaitDurationMs;
        }

        public void setMaxWaitDurationMs(long maxWaitDurationMs) {
            this.maxWaitDurationMs = maxWaitDurationMs;
        }
    }

    public static class TimeoutProperties {
        private long connectTimeoutMs = 2000L;
        private long requestTimeoutMs = 3000L;
        private long operationTimeoutMs = 3500L;

        public void validate() {
            if (connectTimeoutMs <= 0) {
                throw new IllegalArgumentException("connectTimeoutMs must be greater than 0");
            }
            if (requestTimeoutMs <= 0) {
                throw new IllegalArgumentException("requestTimeoutMs must be greater than 0");
            }
            if (operationTimeoutMs <= 0) {
                throw new IllegalArgumentException("operationTimeoutMs must be greater than 0");
            }
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }

        public long getOperationTimeoutMs() {
            return operationTimeoutMs;
        }

        public void setOperationTimeoutMs(long operationTimeoutMs) {
            this.operationTimeoutMs = operationTimeoutMs;
        }
    }
}
