# High-Concurrency Autonomous Agent Orchestrator

A Java-based distributed agent orchestration system built with **Spring Boot 3.5.6 and Java 21**. The project explores high-concurrency task execution using virtual threads, agent registration through REST and gRPC, intelligent task routing, Redis-based coordination, and resilience mechanisms.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [REST API Endpoints](#rest-api-endpoints)
- [Docker Deployment](#docker-deployment)
- [Testing](#testing)
- [Performance Benchmark](#performance-benchmark)
- [Team Contributions](#team-contributions)

## Overview

The High-Concurrency Autonomous Agent Orchestrator provides a foundation for managing multiple autonomous agents and executing their tasks concurrently.

The system uses Java 21 Virtual Threads to support lightweight concurrent task execution. It provides REST and gRPC communication, agent registration, task routing, load balancing, Redis-based node coordination, distributed locking, and resilience mechanisms.

## Key Features

### 1. Virtual Thread Task Execution
- Uses Java 21 virtual threads for lightweight concurrent execution.
- Supports asynchronous task submission.
- Tracks task execution status.
- Allows task retrieval using unique task IDs.
- Uses thread-safe data structures for concurrent task management.

### 2. Structured Concurrency
- Uses Java's preview Structured Concurrency API.
- Implements `StructuredTaskScope.ShutdownOnFailure`.
- Coordinates related concurrent tasks and handles failures.

### 3. Agent Registration and Management
- Registers agents through REST APIs.
- Retrieves individual agents and registered agent lists.
- Checks agent registration status.
- Validates registration requests.
- Detects duplicate agent registrations.
- Shares agent registry information with the gRPC service.

### 4. gRPC Communication
- Provides gRPC-based agent communication.
- Uses Protocol Buffers for service definitions and message serialization.
- Integrates with the shared agent registry.

### 5. Task Routing and Load Balancing
- Supports direct task submission to a specified agent.
- Supports automatic task routing.
- Uses a least-utilized load-balancing strategy.
- Discovers eligible nodes for task execution.

### 6. Redis Integration
- Supports Redis Pub/Sub messaging.
- Maintains node-load information.
- Supports Redis-based distributed locking with owner verification tokens and atomic Lua scripts.
- Uses Redis for coordination between orchestrator nodes.

### 7. Resilience and Fault Handling
- Circuit breaker mechanism.
- Semaphore-based bulkhead protection.
- External API timeout handling.
- Centralized exception handling.

### 8. Multi-Node Deployment
- Supports multiple orchestrator instances.
- Uses Docker Compose for deployment.
- Provides Redis as a shared coordination service.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Backend development |
| Spring Boot 3.5.6 | Application framework |
| Spring Web | REST APIs |
| Java Virtual Threads | Concurrent task execution |
| Structured Concurrency | Coordinated concurrent operations |
| gRPC | Agent communication |
| Protocol Buffers | Message serialization |
| Redis 7.4 | Messaging and distributed coordination |
| Resilience4j 2.2.0 | Fault tolerance |
| Maven | Build and dependency management |
| JUnit 5 | Testing |
| Mockito | Mock-based testing |
| Testcontainers | Integration testing |
| Docker | Containerization |
| Docker Compose | Multi-service deployment |

## Architecture

The project follows a modular architecture.

```text
                  External Clients
                         |
               +---------+---------+
               |                   |
             REST                 gRPC
               |                   |
               v                   v
       Agent Registration     Agent gRPC Service
          Controller                |
               |                   |
               +---------+---------+
                         |
                         v
                Agent Registry Service
                         |
                         v
                  Task Management
                         |
               +---------+---------+
               |                   |
               v                   v
         Task Execution       Task Routing
         (Virtual Threads)    (Load Balancer)
               |                   |
               +---------+---------+
                         |
                         v
                 Orchestrator Nodes
                         |
                         v
                       Redis
             (Messaging / Node Load /
               Distributed Locking)
```

### Main Components

- **Controller:** Exposes REST endpoints for agent registration, task submission, and task retrieval.
- **Service:** Handles agent registration, task execution, task routing, and structured concurrency.
- **gRPC:** Provides communication between agent services.
- **Redis:** Supports messaging, node-load tracking, and distributed coordination.
- **Load Balancer:** Selects eligible nodes using the least-utilized strategy.
- **Resilience:** Protects external operations using circuit breakers, bulkheads, and timeouts.

## Project Structure

```text
high-concurrency-agent-orchestrator/
│
├── src/
│   ├── main/
│   │   ├── java/com/zaalima/orchestrator/
│   │   │   ├── benchmark/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── exception/
│   │   │   ├── grpc/
│   │   │   ├── loadbalancer/
│   │   │   ├── lock/
│   │   │   ├── model/
│   │   │   ├── redis/
│   │   │   ├── resilience/
│   │   │   │   ├── client/
│   │   │   │   ├── config/
│   │   │   │   └── exception/
│   │   │   ├── service/
│   │   │   ├── OrchestratorController.java
│   │   │   └── VirtualThreadTaskHandlingApplication.java
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│       └── java/com/zaalima/orchestrator/
│           ├── controller/
│           ├── grpc/
│           ├── loadbalancer/
│           ├── lock/
│           ├── resilience/
│           ├── service/
│           └── application tests
│
├── docs/
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .dockerignore
├── pom.xml
├── mvnw
├── MEMBER1-README.md
├── MEMBER2-README.md
├── MEMBER4-README.md
├── GIT-WORKFLOW-GUIDE.md
└── README.md
```

## Prerequisites

Before running the application, install:

- JDK 21
- Maven 3.9 or later (or use the included Maven wrapper)
- Git
- Redis (for Redis-dependent functionality)
- Docker Desktop (for Docker deployment)

**Important:** This project uses Java 21 preview features for Structured Concurrency. Use Java 21 and enable preview features when compiling and running the application.

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/Jeya-shri-21/high-concurrency-agent-orchestrator.git
```

### 2. Navigate to the Project

```bash
cd high-concurrency-agent-orchestrator
```

### 3. Build the Project

Using Maven:

```bash
mvn clean package
```

Or using the Maven wrapper:

```bash
./mvnw clean package
```

### 4. Run Tests

```bash
./mvnw test
```

Some integration tests use Testcontainers and may require Docker.

### 5. Start the Application

```bash
./mvnw spring-boot:run
```

The Spring Boot Maven plugin is configured to enable Java preview features.

The application uses port `8080` by default.

### 6. Verify the Application

Open the following URL in your browser:

```text
http://localhost:8080/
```

The root endpoint returns an application-running message.

## Configuration

Application configuration is maintained in:

```text
src/main/resources/application.properties
```

### Redis Configuration

| Property | Default Value |
|---|---|
| Redis host | `localhost` |
| Redis port | `6379` |
| Redis username | `default` |
| Redis password | Empty |
| Node ID | `node-1` |
| Node load TTL | `15` seconds |
| Redis listener auto-start | `true` |
| Redis required | `false` |

### Environment Variables

The project supports environment-based configuration.

```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=
NODE_ID=node-1
```

Redis is configured as optional by default. Redis-dependent messaging, node coordination, and distributed-lock functionality require an appropriately configured Redis service.

### Resilience Configuration

The application includes configurable settings for:

- Circuit breaker sliding window and failure thresholds.
- Bulkhead concurrent-call limits.
- External API connection and request timeouts.
- Operation timeout handling.

These settings are maintained in `application.properties`.

## REST API Endpoints

Base URL:

```text
http://localhost:8080
```

### Agent Registration APIs

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/agents` | Register a new agent |
| GET | `/api/agents` | Retrieve all registered agents |
| GET | `/api/agents/{agentId}` | Retrieve agent details |
| GET | `/api/agents/{agentId}/status` | Check agent registration status |

#### Register an Agent

**Request:**

```http
POST /api/agents
Content-Type: application/json
```

**Request Body:**

```json
{
  "agentId": "agent-alpha-01",
  "agentType": "DATA_PROCESSOR"
}
```

A successful registration returns HTTP `201 Created`.

### Task Management APIs

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/tasks` | Submit a task to an agent or automatically route it |
| POST | `/api/tasks/route` | Route a task using the load balancer |
| GET | `/api/tasks/load-balancer/status` | Retrieve load-balancer status |
| GET | `/api/tasks/{taskId}` | Retrieve task details and status |

#### Submit a Task

**Request:**

```http
POST /api/tasks
Content-Type: application/json
```

**Request Body:**

```json
{
  "agentId": "agent-alpha-01",
  "taskType": "DATA_PROCESSING",
  "payload": "Sample task payload"
}
```

The API generates a unique task ID and submits the task for asynchronous execution.

For automatic routing, use `"auto"` as the agent ID:

```json
{
  "agentId": "auto",
  "taskType": "DATA_PROCESSING",
  "payload": "Sample task payload"
}
```

The system selects an eligible node using the task-routing service.

#### Route a Task

**Request:**

```http
POST /api/tasks/route
Content-Type: application/json
```

**Request Body:**

```json
{
  "taskType": "DATA_PROCESSING",
  "payload": "Sample task payload"
}
```

#### Check Load Balancer Status

```http
GET /api/tasks/load-balancer/status
```

Returns information about the least-utilized node and eligible nodes.

#### Retrieve Task Details

```http
GET /api/tasks/{taskId}
```

Replace `{taskId}` with the ID returned by task submission.

If the task does not exist, the API returns HTTP `404 Not Found`.

### General Application APIs

| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | Application health/message endpoint |
| GET | `/virtual-thread` | Virtual-thread endpoint |
| GET | `/redis/publish` | Redis publish endpoint |

## Docker Deployment

The project includes a multi-stage Dockerfile and Docker Compose configuration.

Docker Compose starts Redis and the primary orchestrator together.

### 1. Create the Environment File

Copy the example configuration:

```bash
cp .env.example .env
```

### 2. Start Single-Node Deployment

```bash
docker compose up --build -d
```

### 3. Start Multi-Node Deployment

To activate the secondary orchestrator node:

```bash
docker compose --profile multi-node up --build -d
```

### 4. Check Running Containers

```bash
docker compose ps
```

### 5. View Logs

```bash
docker compose logs -f
```

### 6. Stop the Services

```bash
docker compose down
```

### Docker Ports

| Service | Host Port | Container Port |
|---|---:|---:|
| Primary REST API | 8080 | 8080 |
| Primary gRPC | 9090 | 9090 |
| Secondary REST API | 8081 | 8080 |
| Secondary gRPC | 9091 | 9090 |
| Redis | 6379 | 6379 |

Redis data is persisted using a Docker volume.

## Testing

The project includes unit and integration tests using JUnit 5, Spring Boot Test, Mockito, and Testcontainers.

### Authoritative Test Results

Full verification executed via `mvn clean package -DskipTests=false` on Adoptium Java 21:

| Metric | Count | Details |
|---|---:|---|
| **Total Test Cases** | **244** | Total across all 38 test classes |
| **Passed Tests** | **231** | 100% execution pass rate |
| **Failures** | **0** | Zero functional failures |
| **Errors** | **0** | Zero unhandled runtime exceptions |
| **Skipped Tests** | **13** | Docker-dependent integration tests cleanly skipped when Docker daemon is not active |

> **Note on Skipped Tests:** The 13 skipped tests (`RedisDistributedLockManagerIntegrationTest`: 11, `RedisMessagingIntegrationTest`: 2) use JUnit 5 `Assumptions.assumeTrue(isDockerAvailable())`. They require a live Docker daemon to instantiate dynamic Redis Testcontainers. When executed on Docker-enabled hosts, all 13 tests execute and pass completely.

### Test Coverage Areas

Tests cover core architectural domains:
- Agent registration, validation, and conflict handling.
- gRPC bidirectional service operations and in-process channel integration.
- Virtual-thread asynchronous task execution and structured concurrency.
- Task routing and least-utilized load balancing.
- Distributed locking (`RedisDistributedLockManager` with Lua scripts & `InMemoryDistributedLockManager`).
- Resilience4j Circuit Breakers, Semaphore Bulkheads, and TimeLimiters.
- Redis lifecycle resilience, connection validation, and dynamic listener container management.
- Terminal monitoring dashboard, ANSI rendering, and cluster metrics collection.

Run the complete test suite:

```bash
./mvnw test
```

To build the application:

```bash
./mvnw clean package
```

## Performance Benchmark

The project includes a benchmark comparing Java platform threads and virtual threads.

Benchmark source:

```text
src/main/java/com/zaalima/orchestrator/benchmark/ThreadProfilingBenchmark.java
```

Benchmark documentation:

```text
src/main/java/com/zaalima/orchestrator/benchmark/BENCHMARK_RESULTS.md
```

An observed benchmark for 1,000 waiting-oriented tasks reported:

| Thread Type | Execution Time |
|---|---:|
| Platform Threads | Approximately 1.12 seconds |
| Virtual Threads | Approximately 0.133 seconds |

Virtual threads completed this particular workload approximately **8.4 times faster**.

These are workload-specific benchmark results, not a guarantee of the same performance improvement for every application.

## Team Contributions

The project is developed collaboratively with distinct engineering roles across the platform:

### Member 1: Platform & Resilience Engineer (Kaushal)
*Detailed Portfolio:* See [MEMBER1-README.md](MEMBER1-README.md)
- **gRPC Communication Gateway:** Designed Protobuf v3 schema (`agent.proto`) and implemented `AgentGrpcService` for low-latency agent registration, heartbeat monitoring, and status tracking.
- **Distributed Locking Engine:** Implemented production-grade `RedisDistributedLockManager` with cryptographic owner tokens (`{instanceId}:{threadName}:{uuid}`), atomic Lua release and renewal scripts, jittered exponential backoff retries, and background watchdog lease auto-renewal.
- **In-Memory Lock Fallback:** Implemented `InMemoryDistributedLockManager` using `ConcurrentHashMap` for zero-dependency local development (single-JVM scope).
- **Resilience4j Fault Tolerance:** Architected `ResilienceManager`, Sliding Window Circuit Breakers, Virtual-Thread-optimized Semaphore Bulkheads, and TimeLimiters.
- **Resilient External HTTP Client:** Implemented `HttpExternalAgentClient` utilizing Java 21 `HttpClient` with Virtual Threads and structured exception hierarchies.
- **Cluster Deployment & Redis Resilience:** Authored multi-stage `Dockerfile`, `docker-compose.yml` multi-node cluster configuration, and resilient Redis lifecycle management (`RedisConfig`, `RedisConnectionValidator`, `RedisListenerLifecycleManager`) with offline tolerance and PR #17/PR #18 conflict resolution.

### Member 2: Virtual Thread Execution and Agent Task Handling

- Designed the agent task model.
- Implemented virtual-thread-based asynchronous execution.
- Implemented task status tracking.
- Added task retrieval functionality.
- Worked on Structured Concurrency.
- Added virtual-thread concurrency and resource-cleanup tests.
- Developed platform-thread versus virtual-thread benchmarks.

### Member 3: Redis Integration & Load Balancing

- Implemented Redis Pub/Sub messaging publisher and subscriber infrastructure (`TaskMessage`).
- Developed cluster node load reporting and periodic heartbeat publishing (`NodeLoadReporter`).
- Implemented dynamic task routing service (`TaskRoutingService`).
- Designed and verified the Least-Utilized load-balancing algorithm (`LeastUtilizedLoadBalancer`).
- Built cluster and node load metrics aggregation for distributed scheduling.

### Member 4: REST API Agent Registration

- Implemented agent registration REST endpoints.
- Added request validation.
- Implemented duplicate-agent detection.
- Integrated REST registration with the shared agent registry.
- Added centralized exception handling.
- Developed controller and service tests.

## Future Improvements

Potential areas for further development include:

- Persistent task and execution-result storage.
- More advanced agent scheduling strategies.
- Additional monitoring and observability.
- Enhanced distributed coordination.
- Expanded performance and failure-testing scenarios.

## Repository

GitHub:

https://github.com/Jeya-shri-21/high-concurrency-agent-orchestrator

---

**Developed as part of the Zaalima Development Pvt. Ltd. project.**
