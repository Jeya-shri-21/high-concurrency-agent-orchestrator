
package com.zaalima.orchestrator.benchmark;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadProfilingBenchmark {

    private static final int TASK_COUNT = 1000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Thread Profiling Benchmark ===");
        System.out.println("Number of tasks: " + TASK_COUNT);

        runBenchmark("Platform Threads",
                Executors.newFixedThreadPool(100));

        runBenchmark("Virtual Threads",
                Executors.newVirtualThreadPerTaskExecutor());
    }

    private static void runBenchmark(
            String threadType,
            ExecutorService executor) throws Exception {

        AtomicInteger completedTasks = new AtomicInteger();

        long memoryBefore = usedMemory();
        long startTime = System.nanoTime();

        try (executor) {
            Future<?>[] futures = new Future<?>[TASK_COUNT];

            for (int i = 0; i < TASK_COUNT; i++) {
                futures[i] = executor.submit(() -> {
                    try {
                        Thread.sleep(100);
                        completedTasks.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            for (Future<?> future : futures) {
                future.get();
            }
        }

        long endTime = System.nanoTime();
        long memoryAfter = usedMemory();

        double elapsedSeconds =
                (endTime - startTime) / 1_000_000_000.0;

        double throughput =
                completedTasks.get() / elapsedSeconds;

        System.out.println("\n--- " + threadType + " ---");
        System.out.println("Completed tasks: "
                + completedTasks.get());
        System.out.printf("Execution time: %.2f seconds%n",
                elapsedSeconds);
        System.out.printf("Throughput: %.2f tasks/second%n",
                throughput);
        System.out.println("Memory change during run: "
                + (memoryAfter - memoryBefore) / 1024
                + " KB");
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
