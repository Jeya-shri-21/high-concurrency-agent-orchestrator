package com.zaalima.orchestrator.handler;

import com.zaalima.orchestrator.model.Task;
import com.zaalima.orchestrator.model.TaskResult;

/**
 * Reusable functional interface for executing and processing autonomous agent tasks.
 * Designed for asynchronous and virtual-thread-based dispatch.
 */
@FunctionalInterface
public interface TaskHandler {

    /**
     * Processes the given task and returns an execution result.
     *
     * @param task the task to be processed
     * @return the result of task execution
     * @throws Exception if processing encounters an unrecoverable failure
     */
    TaskResult handle(Task task) throws Exception;

    /**
     * Determines whether this handler can process the specified task type.
     * Defaults to true for general-purpose handlers.
     *
     * @param taskType the type or category of the task
     * @return true if supported, false otherwise
     */
    default boolean supports(String taskType) {
        return true;
    }
}
