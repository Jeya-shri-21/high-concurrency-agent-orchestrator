# Member 2: Virtual Thread Execution & Agent Task Handling

## Overview

This module implements the task execution and management functionality for the **High-Concurrency Autonomous Agent Orchestrator** project. It uses Java 21 Virtual Threads to execute multiple agent tasks concurrently with lightweight thread management.

The module allows tasks to be submitted, tracked, executed asynchronously, and queried by their task IDs.

## Technologies Used

* Java 21
* Spring Boot 3
* Spring Web
* Maven
* Java Virtual Threads (Project Loom)
* `ExecutorService`
* `ConcurrentHashMap`
* JUnit 5

## Responsibilities

As Member 2, my responsibilities include:

* Designing the agent task data model.
* Implementing task status management.
* Submitting tasks to Java Virtual Threads for asynchronous execution.
* Supporting concurrent task submissions.
* Tracking task execution status.
* Providing REST endpoints to submit and retrieve tasks.
* Writing tests to verify concurrent task execution.

## Project Structure

```text
src/
├── main/
│   └── java/
│       └── com/zaalima/orchestrator/
│           ├── VirtualThreadTaskHandlingApplication.java
│           ├── controller/
│           │   └── AgentTaskController.java
│           ├── model/
│           │   ├── AgentTask.java
│           │   └── TaskStatus.java
│           └── service/
│               └── TaskExecutionService.java
└── test/
    └── java/
        └── com/zaalima/orchestrator/
            └── VirtualThreadTaskHandlingApplicationTests.java
```

## Main Components

### 1. AgentTask

Represents an individual task submitted to an agent.

Task information includes:

* `taskId` — unique identifier for the task.
* `agentId` — identifier of the target agent.
* `taskType` — category of the task.
* `payload` — task input data.
* `status` — current execution status.

### 2. TaskStatus

Defines the possible states of a task:

* `PENDING` — the task has been submitted.
* `RUNNING` — the task is executing.
* `COMPLETED` — the task has finished successfully.
* `FAILED` — the task was interrupted or failed.

### 3. TaskExecutionService

Responsible for executing and tracking tasks.

Key functionality:

* Creates a virtual-thread-per-task executor.
* Submits tasks for asynchronous execution.
* Updates task status during execution.
* Stores task objects in a thread-safe `ConcurrentHashMap`.
* Allows tasks to be retrieved by task ID.
* Handles task interruption and executor shutdown.

The current implementation simulates task processing with a short delay. Actual agent processing logic can be integrated later.

### 4. AgentTaskController

Exposes REST API endpoints for task management.

| Method | Endpoint              | Description                      |
| ------ | --------------------- | -------------------------------- |
| POST   | `/api/tasks`          | Submit a new task                |
| GET    | `/api/tasks/{taskId}` | Retrieve task details and status |

### 5. Concurrency Testing

JUnit 5 tests submit multiple tasks and verify that all submitted tasks reach the `COMPLETED` state.

The current test submits 10 tasks and waits for their execution to finish, helping verify concurrent execution through Java Virtual Threads.

## API Usage

### Submit a task

**Request**

```http
POST /api/tasks
Content-Type: application/json
```

**Request body**

```json
{
  "agentId": "AGENT-01",
  "taskType": "DATA_PROCESSING",
  "payload": "Sample task payload"
}
```

The API generates a unique task ID and returns the task details. The task is then submitted for asynchronous execution.

### Retrieve a task

**Request**

```http
GET /api/tasks/{taskId}
```

Replace `{taskId}` with the ID returned by the task submission endpoint.

The response contains the task information and its current status. If the task ID does not exist, the API returns HTTP `404 Not Found`.

## How to Run

### Prerequisites

* JDK 21
* Maven
* Eclipse IDE or another Java IDE

### Run tests

From the project root, execute:

```bash
mvn test
```

### Start the application

Run:

```bash
mvn spring-boot:run
```

The application runs on the default Spring Boot port, `8080`, unless configured otherwise.

### Test the API

Use a REST client or send HTTP requests to:

```text
POST http://localhost:8080/api/tasks
GET  http://localhost:8080/api/tasks/{taskId}
```

## Current Limitations

* Task records are stored in memory and are lost when the application restarts.
* Task processing is simulated; actual autonomous agent logic is not yet integrated.
* The current implementation uses a local executor. Integration with the shared execution configuration should be coordinated with the team.
* Persistent storage, advanced failure handling, and distributed task coordination can be added in later development phases.

## Future Enhancements

* Integrate the shared virtual-thread execution configuration.
* Connect task execution with registered agents.
* Add task result storage and richer error reporting.
* Integrate with the team's REST and gRPC communication modules.
* Add further concurrency, load, and failure-handling tests.

## Contribution Summary

My contribution focuses on the **Virtual Thread Execution and Agent Task Handling** module, including the task model, asynchronous execution service, task status tracking, REST endpoints, and concurrency tests.

This module provides a foundation for handling multiple agent tasks concurrently within the High-Concurrency Autonomous Agent Orchestrator.
