# Week 2: Common Task Model and Interfaces

**Project:** High-Concurrency Autonomous Agent Orchestrator  
**Organization:** Zaalima Development Pvt. Ltd.  
**Member:** 4 (Common Task Model and Interfaces)  
**Branch:** `deepansh-week2-Common-Task-Model-and-Interfaces`  

---

## 1. Overview

In Week 2, **Member 4** establishes the **Common Task Model and Interfaces** for the High-Concurrency Autonomous Agent Orchestrator. 

This model serves as the unified, decoupled contract across all orchestrator components—including Member 1's backend services, Member 2's task execution and structured concurrency, and Member 3's gRPC services and distributed locking.

### Architecture & Design Goals

* **High-Concurrency Safety:** Uses immutable attributes for identifiers, types, payloads, and timestamps, combined with `volatile` state fields to ensure memory visibility across Java 21 Virtual Threads without contention.
* **Extensibility:** Provides the `@FunctionalInterface TaskHandler` contract so arbitrary task processors, agent delegates, or remote executors can be plugged in easily.
* **Zero Breaking Changes:** Maintains full backward compatibility with the existing project codebase and teammate modules.

```
                           +------------------------+
                           |       Task Model       |
                           |------------------------|
                           | - taskId: String       |
                           | - taskType: String     |
                           | - payload: String      |
                           | - status: TaskStatus   |
                           | - createdAt: long      |
                           +-----------+------------+
                                       |
                                       | Passed into
                                       v
                           +------------------------+
                           |  <<interface>>         |
                           |     TaskHandler        |
                           |------------------------|
                           | + handle(Task)         |
                           | + supports(taskType)   |
                           +-----------+------------+
                                       |
                                       | Produces
                                       v
                           +------------------------+
                           |      TaskResult        |
                           |------------------------|
                           | - taskId: String       |
                           | - status: TaskStatus   |
                           | - output: String       |
                           | - errorDetails: String |
                           +------------------------+
```

---

## 2. Core Classes and Interfaces

### 1. `Task.java`
* **Package:** `com.zaalima.orchestrator.model`
* **Purpose:** Represents an autonomous agent task with its metadata and lifecycle status.
* **Attributes:**
  * `taskId` (`String`, `final`): Unique identifier of the task.
  * `taskType` (`String`, `final`): Category or functional type of the task (e.g., `DATA_PROCESSING`, `INFERENCE`).
  * `payload` (`String`, `final`): Input parameters, payload data, or serialized JSON instructions.
  * `status` (`TaskStatus`, `volatile`): Current lifecycle execution state (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`).
  * `createdAt` (`long`, `final`): Epoch timestamp in milliseconds when the task was created.
* **Concurrency Design:** Immutable fields guarantee that task identity and input cannot be corrupted by concurrent worker threads. The volatile `status` field guarantees immediate visibility of state changes across virtual threads without synchronization overhead.

---

### 2. `TaskStatus.java`
* **Package:** `com.zaalima.orchestrator.model`
* **Purpose:** Defines the standardized lifecycle states of a task.
* **Values:**
  * `PENDING`: Task has been created and submitted, awaiting execution.
  * `RUNNING`: Task is actively being executed on a worker/virtual thread.
  * `COMPLETED`: Task finished successfully with output.
  * `FAILED`: Task execution encountered an error or exception.
* **Helper Methods:**
  * `isTerminal()`: Returns `true` if the status is `COMPLETED` or `FAILED`.

---

### 3. `TaskResult.java`
* **Package:** `com.zaalima.orchestrator.model`
* **Purpose:** Represents the final outcome of an executed task.
* **Type:** Java 21 `record` (immutable and thread-safe).
* **Components:**
  * `taskId` (`String`): Identifier of the task that was executed.
  * `status` (`TaskStatus`): Execution status (`COMPLETED` or `FAILED`).
  * `output` (`String`): Result payload or computed data on success (null on failure).
  * `errorDetails` (`String`): Error message or stack trace description on failure (null on success).
* **Factory Methods:**
  * `TaskResult.success(taskId, output)`: Convenience factory for successful outcomes.
  * `TaskResult.failure(taskId, errorDetails)`: Convenience factory for failed outcomes.
  * `isSuccess()`: Returns `true` if `status == TaskStatus.COMPLETED`.

---

### 4. `TaskHandler.java`
* **Package:** `com.zaalima.orchestrator.handler`
* **Purpose:** Reusable functional interface defining how tasks are executed.
* **Methods:**
  * `TaskResult handle(Task task) throws Exception`: Core execution method accepting a `Task` and returning a `TaskResult`.
  * `default boolean supports(String taskType)`: Predicate to check if this handler supports a specific task type.

---

## 3. Usage Examples

### A. Basic Processing Example
```java
// 1. Create a task
Task task = new Task("task-101", "TEXT_TRANSFORM", "hello zaalima");

// 2. Define a TaskHandler
TaskHandler handler = (t) -> {
    t.setStatus(TaskStatus.RUNNING);
    
    // Simulate task processing
    String transformed = t.getPayload().toUpperCase();
    
    t.setStatus(TaskStatus.COMPLETED);
    return TaskResult.success(t.getTaskId(), transformed);
};

// 3. Execute and inspect result
TaskResult result = handler.handle(task);
System.out.println("Status: " + result.status()); // COMPLETED
System.out.println("Output: " + result.output()); // HELLO ZAALIMA
```

### B. High-Concurrency Virtual Thread Execution Example
```java
TaskHandler computeHandler = task -> {
    task.setStatus(TaskStatus.RUNNING);
    Thread.sleep(100); // Simulate I/O or agent processing on virtual thread
    task.setStatus(TaskStatus.COMPLETED);
    return TaskResult.success(task.getTaskId(), "Processed: " + task.getTaskId());
};

try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 1000; i++) {
        Task task = new Task("task-" + i, "COMPUTE", "input-" + i);
        executor.submit(() -> {
            try {
                TaskResult result = computeHandler.handle(task);
                assert result.isSuccess();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
```

---

## 4. Verification and Test Results

Automated unit tests were added to thoroughly test all 4 components:

1. **`TaskTest` (6 tests):** Default constructor, explicit constructor, null validation, volatile status updates, equals/hashCode, concurrent virtual thread safety.
2. **`TaskResultTest` (4 tests):** Success factory, failure factory, null validation, record equality and immutability.
3. **`TaskStatusTest` (2 tests):** Enum constant verification, terminal status identification.
4. **`TaskHandlerTest` (4 tests):** Successful handling, failure handling, `supports()` filtering, virtual thread concurrency.

**Test Command:**
```bash
./mvnw clean test
```
**Results:** **48 tests run, 0 failures, 0 errors, 0 skipped** (100% PASS).
