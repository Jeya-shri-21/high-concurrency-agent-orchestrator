
package com.zaalima.orchestrator.service;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

@Service
public class StructuredConcurrencyService {

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

            scope.join();
            scope.throwIfFailed();

            return task1.get() + "\n" + task2.get();
        }
    }
}
