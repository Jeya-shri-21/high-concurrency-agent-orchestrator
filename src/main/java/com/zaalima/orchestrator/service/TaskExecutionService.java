package com.zaalima.orchestrator.service;

import com.zaalima.orchestrator.lock.DistributedLock;
import com.zaalima.orchestrator.lock.DistributedLockManager;
import com.zaalima.orchestrator.lock.InMemoryDistributedLockManager;
import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TaskExecutionService {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration DEFAULT_ACQUIRE_TIMEOUT = Duration.ofSeconds(5);

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    private final Map<String, AgentTask> tasks =
            new ConcurrentHashMap<>();

    private final DistributedLockManager lockManager;

    @Autowired
    public TaskExecutionService(DistributedLockManager lockManager) {
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager must not be null");
    }

    public TaskExecutionService() {
        this(new InMemoryDistributedLockManager());
    }

    public Future<?> submitTask(AgentTask task) {

        Objects.requireNonNull(task, "task must not be null");

        Future<?> future = executor.submit(() -> {
            String lockKey = "lock:task:" + task.getTaskId();
            Optional<DistributedLock> lockOpt;
            try {
                lockOpt = lockManager.tryAcquire(lockKey, DEFAULT_LEASE_DURATION, DEFAULT_ACQUIRE_TIMEOUT);
            } catch (Exception e) {
                task.setStatus(TaskStatus.FAILED);
                System.err.println("Lock acquisition failed for task " + task.getTaskId() + ": " + e.getMessage());
                return;
            }

            if (lockOpt.isEmpty()) {
                task.setStatus(TaskStatus.FAILED);
                System.err.println("Could not acquire lock for task " + task.getTaskId() + " within timeout");
                return;
            }

            try (DistributedLock lock = lockOpt.get()) {
                task.setStatus(TaskStatus.RUNNING);

                System.out.println(
                    "Task " + task.getTaskId()
                    + " started on " + Thread.currentThread()
                );

                Thread.sleep(2000);

                // Checkpoint: verify lock validity before committing completion
                if (!lock.isValid()) {
                    task.setStatus(TaskStatus.FAILED);
                    System.err.println(
                        "Task " + task.getTaskId()
                        + " lost lock lease or ownership during execution; failing task"
                    );
                    return;
                }

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
            } catch (Exception e) {
                task.setStatus(TaskStatus.FAILED);
                System.out.println(
                    "Task " + task.getTaskId() + " failed: " + e.getMessage()
                );
            }
        });

        tasks.put(task.getTaskId(), task);
        return future;
    }

    public AgentTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    public void shutdown() {
        executor.close();
    }
}
