# Week 2 Redis Connection & Messaging Design

**Project:** High-Concurrency Autonomous Agent Orchestrator
**Module:** Member 2 — Redis Connection and Messaging Design
**Author:** Member 2 (Jeyashri) / Helper Implementation
**Status:** Implemented & Verified

---

## 1. Redis Connection Architecture

The Zaalima High-Concurrency Autonomous Agent Orchestrator utilizes **Spring Data Redis** backed by the non-blocking **Lettuce** driver to coordinate distributed state and event notifications across multiple orchestrator nodes.

```
+-------------------------------------------------------------------------------+
|                       Zaalima Orchestrator Node                               |
|                                                                               |
|  +--------------------+         +------------------+                          |
|  | TaskExecution-     | <-----+ | TaskMessage-     |                          |
|  | Service (V-Threads)|         | Subscriber       |                          |
|  +--------------------+         +--------+---------+                          |
|                                          ^                                    |
|                                          | onMessage()                        |
|                                 +--------+---------+                          |
|                                 | RedisMessage-    |                          |
|                                 | ListenerContainer|                          |
|                                 +--------+---------+                          |
|                                          |                                    |
|  +--------------------+         +--------+---------+                          |
|  | TaskMessage-       | ------> | StringRedis-     |                          |
|  | Publisher          |         | Template         |                          |
|  +--------------------+         +--------+---------+                          |
|                                          |                                    |
|                                 +--------v---------+                          |
|                                 | LettuceConnection|                          |
|                                 | Factory          |                          |
|                                 +--------+---------+                          |
+------------------------------------------|------------------------------------+
                                           | TCP (Lettuce NIO / Netty)
                                           v
                          +---------------------------------+
                          |        Redis 7.x Server         |
                          |  Pub/Sub Channel:               |
                          |  'orchestrator:tasks:events'    |
                          +---------------------------------+
```

### Key Architectural Characteristics
- **Shared Connection Factory:** Reuses Spring Boot's auto-configured `LettuceConnectionFactory`, sharing connection pools and thread resources with other Redis-backed components (such as distributed locking) without duplicate client instances.
- **Virtual Thread Integration:** Message listener tasks and background subscriber dispatching execute on dedicated Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), preventing thread starvation on high-concurrency event bursts.
- **Fail-Safe Startup Isolation:** `RedisMessageListenerContainer` overrides `isAutoStartup()` to `false` and delegates startup to `RedisListenerLifecycleManager`. When the application initializes, active connectivity is verified via `RedisConnectionValidator`. If Redis is offline during unit testing or localized service startup, the listener remains idle instead of throwing `ApplicationContextException` and aborting application context refresh.

---

## 2. Required Environment Variables and Configuration

The Redis connectivity and messaging module is fully externalized via Spring Boot properties with production-ready environment variable defaults:

| Property Key | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | Redis server hostname or container DNS name |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | Redis server port number |
| `spring.data.redis.password` | `REDIS_PASSWORD` | *(empty)* | Optional authentication password |
| `spring.data.redis.timeout` | `REDIS_TIMEOUT` | `2000ms` | Network socket read and command timeout |
| `spring.data.redis.lettuce.shutdown-timeout` | `REDIS_SHUTDOWN_TIMEOUT` | `100ms` | Graceful shutdown timeout for Lettuce Netty event loops |
| `orchestrator.redis.messaging.task-channel` | `REDIS_TASK_CHANNEL` | `orchestrator:tasks:events` | Redis Pub/Sub topic name for task lifecycle notifications |
| `orchestrator.redis.messaging.enabled` | `REDIS_MESSAGING_ENABLED` | `true` | Enables or disables Redis messaging beans conditionally |

---

## 3. How to Start or Configure Redis Locally

### Option A: Docker (Recommended)
Run Redis in detached mode exposing default port 6379:
```bash
docker run -d --name redis-orchestrator -p 6379:6379 redis:7-alpine
```

Verify the server is responding:
```bash
docker exec -it redis-orchestrator redis-cli ping
# Expected output: PONG
```

### Option B: Native Windows (WSL 2 or Portable Binary)
If using WSL 2 (Ubuntu):
```bash
sudo apt-get update && sudo apt-get install -y redis-server
sudo service redis-server start
redis-cli ping
```

---

## 4. Selected Messaging Pattern and Rationale

### Selected Pattern: **Redis Pub/Sub (Publish/Subscribe)**

### Rationale
1. **Transient Event Broadcasting:** The orchestrator requires broadcasting low-latency task lifecycle milestone events (`TASK_SUBMITTED`, `TASK_STARTED`, `TASK_COMPLETED`, `TASK_FAILED`, `TASK_DISPATCH`) to any currently active monitoring nodes or worker agents.
2. **Minimal Infrastructure Footprint:** Redis Pub/Sub has near-zero overhead and requires no stream group creation or persistent disk state management for fire-and-forget notification broadcasts.
3. **Decoupled Architecture:** Publishers do not need awareness of connected subscriber counts or identities.
4. **Architectural Fit:** Redis Streams would introduce unnecessary storage retention and consumer group offset management when the immediate requirement is lightweight cluster event dispatching and notification.

---

## 5. Message Format and Serialization

All messages conform to `TaskMessage` (`com.zaalima.orchestrator.redis.model.TaskMessage`).

### Schema Definition
```json
{
  "taskId": "task-uuid-string",
  "agentId": "agent-worker-identifier",
  "taskType": "DATA_PROCESSING",
  "payload": "custom string payload or parameters",
  "status": "PENDING",
  "eventType": "TASK_SUBMITTED",
  "correlationId": "correlation-uuid-string",
  "timestamp": "2026-10-24T14:46:15.123456Z"
}
```

### Field Contracts
- `taskId` (*String, mandatory*): Unique identifier of the task. Must not be null or blank.
- `agentId` (*String, mandatory*): Target or executing agent identifier.
- `taskType` (*String, mandatory*): Category of the task (e.g., `ANALYZE`, `PROCESS`).
- `payload` (*String, optional*): Input data or command payload.
- `status` (*TaskStatus, mandatory*): Current execution status (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`).
- `eventType` (*TaskEventType, mandatory*): Message event category (`TASK_SUBMITTED`, `TASK_STARTED`, `TASK_COMPLETED`, `TASK_FAILED`, `TASK_DISPATCH`).
- `correlationId` (*String, mandatory*): Distributed tracing correlation ID; automatically generated if omitted.
- `timestamp` (*Instant, mandatory*): ISO-8601 UTC timestamp of event generation.

### Serialization Strategy
- Uses Jackson `ObjectMapper` configured with `JavaTimeModule` and `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false` (ISO-8601 string formatting).
- Annotations include `@JsonCreator`, `@JsonProperty`, and `@JsonIgnoreProperties(ignoreUnknown = true)` to maintain forward and backward serialization compatibility across services.

---

## 6. Channel Naming Conventions

To prevent collision across environments and components:
- Default channel: `orchestrator:tasks:events`
- Configurable via `orchestrator.redis.messaging.task-channel` property.
- Hierarchical structure: `<system>:<domain>:<action>`

---

## 7. Publisher and Subscriber Responsibilities

### Publisher: `RedisTaskMessagePublisher`
- **Validation:** Calls `message.validate()` prior to transmission, enforcing non-blank constraints.
- **Serialization:** Converts `TaskMessage` to UTF-8 JSON.
- **Transmission:** Executes `StringRedisTemplate.convertAndSend(channel, jsonPayload)`.
- **Diagnostics:** Logs active subscriber count returned by Redis without logging sensitive payloads or credentials.
- **Resilience:** Implements both `publish()` (throws typed `MessagePublishingException`) and `tryPublish()` (returns `false` gracefully on failure).
- **Domain Integration:** Provides `publishTaskEvent(AgentTask, TaskEventType)` for direct invocation by orchestrator services.

### Subscriber: `TaskMessageSubscriber`
- **Listener Registration:** Registered with `RedisMessageListenerContainer` for the designated `ChannelTopic`.
- **Safe Parsing:** Decodes byte array payloads into UTF-8 JSON and deserializes into `TaskMessage`.
- **Schema Validation:** Verifies required fields on arrival.
- **Error Containment:** Malformed JSON and validation failures increment diagnostic error metrics (`errorCount`) and emit structured logs; unhandled exceptions are caught to protect the listener thread.
- **Task Dispatch:** Automatically forwards `TASK_DISPATCH` events to `TaskExecutionService.submitTask()`.
- **Extensibility:** Supports optional `TaskMessageHandler` functional callbacks.

---

## 8. Delivery and Persistence Guarantees

> [!IMPORTANT]
> **Redis Pub/Sub provides At-Most-Once Delivery Semantics.**
>
> 1. **No Durable Buffering:** Messages are transiently pushed to currently connected TCP client sockets. Redis does not write Pub/Sub messages to disk or buffer them for offline subscribers.
> 2. **Missed Messages:** If an orchestrator instance is disconnected, restarting, or partitioned when a message is published, the message is permanently missed by that subscriber.
> 3. **Delivery vs Execution:** Message publication confirms only that Redis received the command and pushed it to `N` connected subscribers; it does not guarantee downstream task execution success.

---

## 9. Acknowledgment and Duplicate-Handling Behavior

- **No Protocol Acks:** Redis Pub/Sub does not support protocol-level consumer acknowledgments (`ACK`).
- **Duplicate Prevention:** When multiple instances receive a `TASK_DISPATCH` message, duplicate execution must be guarded by distributed mutual exclusion (e.g., Member 3's `DistributedLockManager` acquiring `lock:task:{taskId}`).
- **Idempotency:** Subscribers should ensure task handlers are idempotent based on `taskId` and `correlationId`.

---

## 10. Error Handling and Resilience

1. **Redis Unavailable during Startup:**
   `RedisMessageListenerContainer` does not auto-start with Spring's default lifecycle processor. `RedisListenerLifecycleManager` checks `RedisConnectionValidator.isConnected()`; if Redis is down, it logs a warning and leaves the container stopped.
2. **Network Disconnect during Runtime:**
   Lettuce maintains automatic reconnection logic. `RedisMessageListenerContainer` is configured with `recoveryInterval = 3000ms`, automatically re-subscribing when connectivity restores.
3. **Publication Failures:**
   `DataAccessException` and `QueryTimeoutException` are wrapped into `MessagePublishingException` with clear diagnostics.
4. **Invalid Payloads:**
   Malformed messages increment `errorCount` and are safely dropped without crashing listener threads.

---

## 11. Redis Outage and Reconnection Behavior

- If Redis stops while the orchestrator is running, `RedisConnectionValidator.isConnected()` returns `false`.
- Publisher calls fail fast and throw `MessagePublishingException` or return `false` on `tryPublish`.
- Lettuce continuously attempts background reconnects according to its socket options.
- Once Redis returns online, Lettuce re-establishes the connection and subscriptions resume automatically.

---

## 12. How to Run the Application and Tests

### Run All Tests
```bash
mvn clean test
```

### Run Redis Unit Tests Only
```bash
mvn test -Dtest=TaskMessageTest,RedisTaskMessagePublisherTest,TaskMessageSubscriberTest,RedisConnectionValidatorTest,RedisListenerLifecycleManagerTest
```

### Run Redis Integration Tests (Requires Docker)
```bash
mvn test -Dtest=RedisMessagingIntegrationTest
```

### Start the Application Locally
```bash
# With local Redis running:
mvn spring-boot:run

# With customized Redis host/port:
REDIS_HOST=localhost REDIS_PORT=6379 mvn spring-boot:run
```

---

## 13. Environment Requirements for Integration Tests

- **Unit Tests:** Execute in-memory without requiring Docker or any external Redis server.
- **Integration Tests (`RedisMessagingIntegrationTest`):**
  - Requires a local Docker daemon supported by Testcontainers.
  - Automatically spins up `redis:7-alpine`.
  - When Docker is unavailable, tests are cleanly skipped using JUnit 5 `Assumptions.assumeTrue(...)` without failing the build.

---

## 14. Known Limitations

- **Transient Pub/Sub:** As documented in Section 8, messages sent while a node is down are not replayed upon restart.
- **In-Memory Metric Tracking:** `receivedCount` and `errorCount` on `TaskMessageSubscriber` are stored in JVM memory (`AtomicLong`) and reset on process restart.
- **No Consumer Groups:** Pub/Sub broadcasts to all active listeners. Point-to-point worker queue semantics require distributed locking or Redis Streams.

---

## 15. Team Integration and Dependencies

- **Member 1 (Structured Concurrency):** Redis listener invokes tasks that can be wrapped in virtual threads or structured task scopes without blocking listener Netty threads.
- **Member 3 (Distributed Locking):** Redis configuration reuses `StringRedisTemplate` and `LettuceConnectionFactory` without bean collision. `TASK_DISPATCH` handling leverages `DistributedLockManager` to avoid concurrent task execution.
- **Member 4 (Common Task Model):** Directly reuses `AgentTask` and `TaskStatus`.
