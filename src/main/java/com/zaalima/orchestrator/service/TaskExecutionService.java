
package com.zaalima.orchestrator.service;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;

@Service
public class TaskExecutionService {

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    private final Map<String, AgentTask> tasks =
            new ConcurrentHashMap<>();

    public Future<?> submitTask(AgentTask task) {

        tasks.put(task.getTaskId(), task);

        return executor.submit(() -> {
            try {
                task.setStatus(TaskStatus.RUNNING);

                System.out.println(
                    "Task " + task.getTaskId()
                    + " started on " + Thread.currentThread()
                );

                Thread.sleep(2000);

                task.setStatus(TaskStatus.COMPLETED);

                System.out.println(
                    "Task " + task.getTaskId() + " completed"
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.setStatus(TaskStatus.FAILED);

                System.out.println(
                    "Task " + task.getTaskId() + " was interrupted"
                );
            }
        });
    }

    public AgentTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    public void shutdown() {
        executor.close();
    }
}