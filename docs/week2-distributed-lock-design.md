# Week 2 Distributed Lock Design

## 1. Context and Problem Statement
The Zaalima High-Concurrency Autonomous Agent Orchestrator is designed to scale horizontally across multiple instances (nodes/containers). Tasks and agent interactions are submitted via REST endpoints (`AgentRegistrationController`, `AgentTaskController`) and gRPC services (`AgentGrpcService`), and processed concurrently across virtual threads via `TaskExecutionService`.

In upcoming Week 2 work, distributed messaging (e.g., Redis streams/queues) and multi-instance orchestration will be introduced. Without coordinated distributed locking across instances:
- Multiple orchestrator instances could simultaneously pick up, schedule, or mutate the state of the exact same task (`AgentTask`).
- Multiple orchestrator instances could concurrently issue conflicting commands or duplicate executions to the same autonomous agent.
- In-memory concurrency controls (e.g., `ConcurrentHashMap`, `ReentrantLock`, or `AtomicReference`) protect resources only within a single JVM process. They provide zero mutual exclusion across separate JVM instances.

Therefore, a centralized, distributed mutual exclusion mechanism is required.

---

## 2. Resource/Task Lock Keys & Unique Lock-Owner Tokens

### Standardized Lock Key Namespaces
To prevent collisions, provide predictable observability, and enforce clean isolation, all lock keys follow a strict hierarchical namespace:

| Scope | Key Pattern | Example | Purpose |
| :--- | :--- | :--- | :--- |
| **Task Execution** | `lock:task:{taskId}` | `lock:task:task-1042` | Guarantees at-most-once execution of a specific task across all instances. |
| **Agent Resource** | `lock:agent:{agentId}` | `lock:agent:agent-worker-01` | Enforces single-instance exclusive control over a specific agent when needed. |
| **Global/Coordination** | `lock:orchestrator:{resource}` | `lock:orchestrator:scheduler` | Coordinates cluster-wide singleton operations such as scheduled maintenance. |

### Unique Lock-Owner Tokens
A distributed lock key must never be stored as a bare boolean or static value. The value stored in Redis must be a unique **Lock-Owner Token** that identifies the exact instance and execution context holding the lease.

- **Token Structure**: `{instanceId}:{threadId}:{randomUUID}`
  - `instanceId`: Identifies the orchestrator instance (e.g., hostname, pod name, or Spring Boot instance identifier).
  - `threadId`: Identifies the virtual thread (e.g., `vt-78`).
  - `randomUUID`: A cryptographically random UUID ensuring absolute uniqueness even if hostnames or thread IDs are reused.
  - **Example**: `orch-pod-2a:vt-108:4f2c9081-3c66-4e52-a5e2-0fbc358d7120`

---

## 3. Acquisition, Release, Lease Expiration, Timeout, and Failure Semantics

### Acquisition Semantics
1. **Strictly Atomic Acquisition**:
   - Acquisition MUST be atomic using Redis:
     ```text
     SET lock:task:{taskId} {ownerToken} NX PX {leaseTimeMillis}
     ```
   - `NX`: Set if Not Exists.
   - `PX`: Automatic lease expiration in milliseconds.
   - **Anti-pattern prohibition**: Any check-then-set pattern (such as checking `EXISTS` or `GET`, followed by `SET`) is inherently prone to race conditions and is strictly forbidden.

2. **Acquisition Timeout (`acquireTimeout`)**:
   - Callers specify a maximum wait timeout (e.g., `3000 ms`).
   - If the lock is held by another instance, the calling thread retries with backoff until acquired or until the deadline expires.
   - If `acquireTimeout` expires without acquiring the lock, `tryAcquire` returns `Optional.empty()` or throws `LockAcquisitionException`.

### Ownership-Safe Release Semantics
A lock lease may expire while the original holder is still finishing work. If Instance A's lease expires and Instance B acquires the lock, Instance A must **never** delete Instance B's lock.

Release is performed atomically via an evaluation script:
```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
```
- Returns `1`: Key existed and matched `ownerToken`; successfully released.
- Returns `0`: Key did not exist (already expired) or belongs to another owner. Logged as a warning without throwing unexpected runtime failures.

### Lease Expiration (TTL)
- Every lock acquired MUST have a finite lease duration (e.g., default `30,000 ms`).
- Lease expiration acts as a safety valve: if an orchestrator instance abruptly crashes, is terminated by orchestrator autoscaling, or loses network connectivity, the lock automatically clears, avoiding permanent deadlocks.

### Failure Semantics
- **Redis Connection / Timeout Failure during Acquire**: Fail-closed. Throw a descriptive `LockUnavailableException`. The orchestrator must not proceed with uncoordinated execution.
- **Redis Failure during Release**: Log an error with owner token and key. The key will naturally expire upon TTL expiration, avoiding permanent lockout.

---

## 4. Retry Limits, Backoff, and Avoiding Indefinite Waits

### Avoiding Spinlocks & Redis Overload
A tight polling loop (`while(!acquired)`) causes thread contention, wastes CPU cycles, and saturates Redis bandwidth.

### Strategy: Truncated Exponential Backoff with Full Jitter
1. **Initial Backoff**: `50 ms`
2. **Multiplier**: `1.5`
3. **Maximum Backoff Cap**: `500 ms`
4. **Full Jitter**: `sleep = random(0, min(maxBackoff, initialBackoff * (multiplier ^ attempt)))`
   - Jitter de-synchronizes contending virtual threads, preventing the "thundering herd" problem when a lock is freed.
5. **Hard Deadline**:
   - `long deadline = System.currentTimeMillis() + acquireTimeout.toMillis();`
   - Before each retry, if `System.currentTimeMillis() >= deadline`, terminate immediately.
   - No thread is permitted to block indefinitely.

---

## 5. Lease Expiry Risks & Mitigations

### The Problem: Task Execution Outliving Lease TTL
In distributed systems, a task may run longer than anticipated due to:
- High network latency or slow external agent responses (e.g. LLM API calls).
- Lengthy I/O or carrier thread stalls.
If the lease expires while the task is still executing:
1. Redis evicts the key.
2. A second orchestrator instance acquires the lock and begins executing the same task.
3. Both instances execute concurrently, breaking mutual exclusion guarantees!

### Mitigations
1. **Lock Watchdog / Heartbeat (Auto-Renewal)**:
   - For long-running asynchronous tasks, a background task periodically extends the lease TTL (e.g., every `leaseDuration / 3`) while the executing thread is still alive and processing.
2. **Fencing Tokens**:
   - Lock acquisition returns an incrementing numeric fencing token (e.g., Redis `INCR lock:epoch:{taskId}`).
   - Downstream persistence stores reject any write whose fencing token is lower than the highest token observed.
3. **Task Cancellation Checkpoints**:
   - Executing tasks check `DistributedLock.isValid()` at critical processing boundaries before committing irreversible operations.

---

## 6. Compatibility with Team Architecture

### Virtual Thread Integration (`TaskExecutionService`)
- The lock abstraction is designed for compatibility with Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`).
- Retry backoffs utilize `Thread.sleep()`, allowing virtual threads to yield their underlying platform carrier thread without blocking operating system threads.
- Supports `AutoCloseable` for clean `try-with-resources` management:
  ```java
  try (DistributedLock lock = lockManager.tryAcquire("lock:task:" + task.getTaskId(), leaseDuration, waitTimeout)
          .orElseThrow(() -> new LockAcquisitionException("Could not acquire lock for task: " + task.getTaskId()))) {
      task.setStatus(TaskStatus.RUNNING);
      // execute task logic safely
      task.setStatus(TaskStatus.COMPLETED);
  } catch (Exception e) {
      task.setStatus(TaskStatus.FAILED);
  }
  ```

### gRPC & REST Decoupling
- The lock abstraction (`DistributedLockManager`, `DistributedLock`, `LockToken`) has **zero direct dependency** on gRPC (`AgentGrpcService`, `agent.proto`), Spring MVC controllers, or HTTP DTOs.
- `AgentGrpcService` and controllers interact solely with `TaskExecutionService` or orchestration coordinators. Locking remains an infrastructure/service-layer concern.

### Model Reuse
- Integrates seamlessly with existing team domain models [`com.zaalima.orchestrator.model.AgentTask`](file:///src/main/java/com/zaalima/orchestrator/model/AgentTask.java) and [`com.zaalima.orchestrator.model.TaskStatus`](file:///src/main/java/com/zaalima/orchestrator/model/TaskStatus.java). No duplicate task models are introduced.

---

## 7. Testing Strategy

1. **Unit Tests (Day 1 Scope - Local Test Foundation)**:
   - Verify `LockToken` attributes, non-null guarantees, and expiration detection.
   - Verify `DistributedLockManager` acquisition success and mutual exclusion within the process.
   - Test timeout behavior when contending for an already locked key.
   - Test ownership verification: verify that releasing with an invalid or expired token is safely rejected.
   - Test `AutoCloseable` (`try-with-resources`) release semantics.
2. **Concurrency Tests**:
   - Multi-threaded tests executing across 50-100 virtual threads contending for the same key.
   - Verify that exactly one thread holds the lock at any time and that all threads terminate without deadlocks.
3. **Integration Tests (Day 2+ Scope - Redis Required)**:
   - Redis container via Testcontainers (`redis:7-alpine`).
   - Validate true atomic Redis commands (`SET NX PX`) and Lua script evaluation.
   - Verify lease auto-expiration and recovery when client nodes simulate crash/disconnect.
4. **Failure Injection**:
   - Redis disconnect during acquisition (fail-closed verification).
   - Redis disconnect during release (TTL safety valve verification).

---

## 8. Limitations & Decisions Requiring Team Agreement

> [!WARNING]
> This design document and the Day 1 foundation provide architectural contracts and local test implementations. The system **must not be claimed as production-safe** until Redis integration, clustering failover, and watchdog auto-renewal are implemented and validated under integration testing.

The following architectural decisions require team agreement before Day 2 implementation:
1. **Redis Client Library Selection**:
   - **Option A: `spring-boot-starter-data-redis` (Lettuce)**: Standard Spring Boot dependency. Requires custom Lua scripts for atomic release and custom virtual-thread watchdog renewal.
   - **Option B: `redisson-spring-boot-starter`**: Provides mature distributed locks (`RLock`), built-in watchdog auto-renewal, and Pub/Sub notifications instead of polling.
2. **Lock Failure Policy in `TaskExecutionService`**:
   - When a lock cannot be acquired within `acquireTimeout`:
     - Should the task immediately fail with `TaskStatus.FAILED`?
     - Or should it be re-queued with a delayed backoff retry queue?
3. **Watchdog Requirement**:
   - Agreement on whether task durations will exceed standard lease times (requiring background lock renewal) or if strict task timeouts should be enforced instead.
