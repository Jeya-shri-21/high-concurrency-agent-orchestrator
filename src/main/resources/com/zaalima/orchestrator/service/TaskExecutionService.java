
package com.zaalima.orchestrator.service;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TaskExecutionService {

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    public Future<?> submitTask(AgentTask task) {

        return executor.submit(() -> {

            try {
                task.setStatus(TaskStatus.RUNNING);

                System.out.println(
                        "Task " + task.getTaskId()
                        + " started on "
                        + Thread.currentThread()
                );

                // Simulate an I/O-bound operation
                Thread.sleep(2000);

                task.setStatus(TaskStatus.COMPLETED);

                System.out.println(
                        "Task " + task.getTaskId()
                        + " completed"
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                task.setStatus(TaskStatus.FAILED);

                System.out.println(
                        "Task " + task.getTaskId()
                        + " was interrupted"
                );
            }
        });
    }
}