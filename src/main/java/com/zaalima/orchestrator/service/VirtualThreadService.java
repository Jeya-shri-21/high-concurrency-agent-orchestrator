package com.zaalima.orchestrator.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class VirtualThreadService {

    private final ExecutorService executor;

    public VirtualThreadService(ExecutorService virtualThreadExecutor) {
        this.executor = virtualThreadExecutor;
    }

    public List<String> executeTasks() throws Exception {

        List<Future<String>> futures = List.of(
            executor.submit(() -> {
                Thread.sleep(1000);
                return "Task 1 executed by: " + Thread.currentThread();
            }),
            executor.submit(() -> {
                Thread.sleep(1000);
                return "Task 2 executed by: " + Thread.currentThread();
            }),
            executor.submit(() -> {
                Thread.sleep(1000);
                return "Task 3 executed by: " + Thread.currentThread();
            })
        );

        return futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }
}
