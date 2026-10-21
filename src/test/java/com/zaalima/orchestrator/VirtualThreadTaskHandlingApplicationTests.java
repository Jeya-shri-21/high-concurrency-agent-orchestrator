
package com.zaalima.orchestrator;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.model.TaskStatus;
import com.zaalima.orchestrator.service.TaskExecutionService;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualThreadTaskHandlingApplicationTests {

    @Test
    void shouldExecuteMultipleTasksConcurrently() throws Exception {

        TaskExecutionService service = new TaskExecutionService();

        try {
            List<AgentTask> tasks = new ArrayList<>();
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 1; i <= 10; i++) {
                AgentTask task = new AgentTask(
                    "TASK-" + i,
                    "AGENT-01",
                    "DATA_PROCESSING",
                    "Payload-" + i
                );

                tasks.add(task);
                futures.add(service.submitTask(task));
            }

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            for (AgentTask task : tasks) {
                assertEquals(
                    TaskStatus.COMPLETED,
                    task.getStatus(),
                    task.getTaskId() + " should be completed"
                );
            }

            System.out.println(
                "All 10 tasks completed successfully!"
            );

        } finally {
            service.shutdown();
        }
    }
}