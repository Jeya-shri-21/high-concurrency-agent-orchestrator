
package com.zaalima.orchestrator.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StructuredConcurrencyService {

    private final Duration timeout;

    public StructuredConcurrencyService(
            @Value("${orchestrator.structured.timeout-ms:3000}")
            long timeoutMs) {

        if (timeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "Timeout must be greater than zero");
        }

        this.timeout = Duration.ofMillis(timeoutMs);
    }

    public String executeTasks()
            throws InterruptedException,
                   ExecutionException,
                   TimeoutException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            StructuredTaskScope.Subtask<String> task1 = scope.fork(() -> {
                Thread.sleep(1000);
                return "Agent Task 1 completed by "
                        + Thread.currentThread();
            });

            StructuredTaskScope.Subtask<String> task2 = scope.fork(() -> {
                Thread.sleep(1500);
                return "Agent Task 2 completed by "
                        + Thread.currentThread();
            });

            try {
                scope.joinUntil(Instant.now().plus(timeout));
            } catch (TimeoutException e) {
                scope.shutdown();
                throw e;
            }

            scope.throwIfFailed();

            return task1.get() + "\n" + task2.get();
        }
    }
}
