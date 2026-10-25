# Week 2 Distributed Lock Design & Implementation Guide

## 1. Context and Problem Statement
The Zaalima High-Concurrency Autonomous Agent Orchestrator is designed to scale horizontally across multiple instances (nodes/containers). Tasks and agent interactions are submitted via REST endpoints (`AgentRegistrationController`, `AgentTaskController`) and gRPC services (`AgentGrpcService`), and processed concurrently across virtual threads via `TaskExecutionService`.

Without coordinated distributed locking across instances:
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

- **Token Structure**: `{instanceId}:{threadName}:{randomUUID}`
  - `instanceId`: Identifies the orchestrator instance (e.g., hostname, pod name, or Spring Boot instance identifier).
  - `threadName`: Identifies the virtual thread (e.g., `virtualThread-#78`).
  - `randomUUID`: A cryptographically random UUID ensuring absolute uniqueness even if hostnames or thread names are reused.
  - **Example**: `orch-node-2a:VirtualThread[#78]:4f2c9081-3c66-4e52-a5e2-0fbc358d7120`

---

## 3. Acquisition, Release, Renewal, and Failure Semantics

### Acquisition Semantics
1. **Strictly Atomic Acquisition**:
   - Acquisition is executed atomically in a single Redis command:
     ```text
     SET lock:task:{taskId} {ownerToken} NX PX {leaseTimeMillis}
     ```
   - `NX`: Set if Not Exists.
   - `PX`: Automatic lease expiration in milliseconds.
   - **Anti-pattern prohibition**: Any check-then-set pattern (such as checking `EXISTS` or `GET`, followed by `SET`) is inherently prone to race conditions and is strictly forbidden.

2. **Acquisition Timeout (`acquireTimeout`)**:
   - Callers specify a maximum wait timeout (e.g., `Duration.ofSeconds(5)`).
   - If the lock is held by another instance, the calling thread retries with backoff until acquired or until the deadline expires.
   - If `acquireTimeout` expires without acquiring the lock, `tryAcquire` returns `Optional.empty()`.

### Ownership-Safe Release Semantics
A lock lease may expire while the original holder is still finishing work. If Instance A's lease expires and Instance B acquires the lock, Instance A must **never** delete Instance B's lock.

Release is performed atomically via an evaluation Lua script:
```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
```
- Returns `1`: Key existed and matched `ownerToken`; successfully released.
- Returns `0`: Key did not exist (already expired) or belongs to another owner. Logged as a warning without throwing unexpected runtime failures.

### Atomic Lease Renewal Semantics
To support long-running tasks without risking premature expiration, leases can be extended atomically using an evaluation Lua script:
```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("pexpire", KEYS[1], ARGV[2])
else
    return 0
end
```
- `KEYS[1]`: Lock key.
- `ARGV[1]`: Calling owner token.
- `ARGV[2]`: New TTL in milliseconds.
- Returns `1`: Key existed and matched `ownerToken`; lease TTL extended.
- Returns `0`: Key expired or was reassigned to another node; renewal rejected.

### Failure Semantics
- **Redis Connection / Timeout Failure during Acquire**: Fail-closed. Throws `LockAcquisitionException`. The orchestrator must not proceed with uncoordinated execution.
- **Redis Failure during Release / Renew**: Logs an error with owner token and key, returning `false`. The key will naturally expire upon TTL expiration, avoiding permanent lockout.

---

## 4. Retry Limits, Backoff, and Avoiding Indefinite Waits

### Strategy: Truncated Exponential Backoff with Full Jitter
1. **Initial Backoff**: `50 ms`
2. **Multiplier**: `1.5`
3. **Maximum Backoff Cap**: `500 ms`
4. **Full Jitter**: `sleep = random(1, min(maxBackoff, initialBackoff * (multiplier ^ attempt)))`
   - Jitter de-synchronizes contending virtual threads, preventing the "thundering herd" problem when a lock is freed.
5. **Hard Deadline**:
   - `Instant deadline = Instant.now().plus(acquireTimeout);`
   - Before each retry, if `!Instant.now().isBefore(deadline)`, terminate immediately.
   - No thread blocks indefinitely.
6. **Thread Interruption**:
   - `InterruptedException` preserves thread interruption status via `Thread.currentThread().interrupt()` and returns `Optional.empty()`.

---

## 5. Lease Expiry Risks & Mitigations

### The Problem: Task Execution Outliving Lease TTL
If a task runs longer than anticipated due to external API latencies or carrier thread stalls, the lease may expire in Redis. If a second orchestrator instance acquires the lock, both instances could execute concurrently.

### Implemented Mitigations
1. **Lock Watchdog / Heartbeat (Auto-Renewal)**:
   - `RedisDistributedLockManager` supports `tryAcquireWithAutoRenewal(...)` and `DistributedLock.renew(Duration)`.
   - A background task scheduled on a managed `ScheduledExecutorService` periodically calls `renew()`.
   - Auto-renewal automatically stops when `lock.close()` is called, when ownership is lost, or when renewal fails.
2. **Task Cancellation & Validity Checkpoints**:
   - Executing tasks check `DistributedLock.isValid()` at critical processing boundaries (such as before committing task completion in `TaskExecutionService`).
   - If `isValid()` returns `false`, `TaskExecutionService` fails the task (`TaskStatus.FAILED`), preventing inconsistent state writes.
3. **External Fencing Tokens (Recommended for Downstream Databases)**:
   - For irreversible mutations to relational databases, downstream tables should enforce numeric epoch fencing tokens (`lock:epoch:{taskId}`).

---

## 6. Architecture Integration

### Virtual Thread Integration (`TaskExecutionService`)
- Fully compatible with Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`).
- Employs `try-with-resources` (`AutoCloseable`) ensuring lock handles are closed across normal completions, task exceptions, and interruptions.
- Enforces `lock:task:{taskId}` namespace.
- Prevents task execution if lock acquisition fails or times out.

### gRPC & REST Decoupling
- `DistributedLockManager`, `DistributedLock`, and `LockToken` have zero dependency on HTTP or gRPC classes.
- Spring Boot wiring:
  - `InMemoryDistributedLockManager` (`@Primary`) provides default single-JVM support for tests and development.
  - `RedisDistributedLockManager` (`@Component("redisDistributedLockManager")`) provides distributed multi-node coordination.

---

## 7. Testing & Verification

| Test Suite | Scope | Status |
| :--- | :--- | :--- |
| [`InMemoryDistributedLockManagerTest`](file:///src/test/java/com/zaalima/orchestrator/lock/InMemoryDistributedLockManagerTest.java) | 8 unit tests: thread safety, lease expiration, timeouts, multi-threaded virtual-thread stress | PASSED |
| [`LockTokenTest`](file:///src/test/java/com/zaalima/orchestrator/lock/LockTokenTest.java) | 4 unit tests: token immutability, validation, expiration calculation | PASSED |
| [`RedisDistributedLockManagerTest`](file:///src/test/java/com/zaalima/orchestrator/lock/RedisDistributedLockManagerTest.java) | 24 unit tests: input validation, single-attempt acquire, retry backoff, interruption handling, fail-closed on Redis error, atomic Lua release, atomic Lua renewal, auto-renewal lifecycle | PASSED |
| [`TaskExecutionServiceTest`](file:///src/test/java/com/zaalima/orchestrator/service/TaskExecutionServiceTest.java) | 6 integration tests: task locking, timeout failure, exception fail-closed, interrupt cleanup, validity checkpoints, concurrent conflicting tasks | PASSED |
| [`RedisDistributedLockManagerIntegrationTest`](file:///src/test/java/com/zaalima/orchestrator/lock/RedisDistributedLockManagerIntegrationTest.java) | 11 Testcontainers integration tests (`redis:7-alpine`): multi-node competition, stale-owner release rejection, lease expiration recovery, renewal TTL extension, fail-closed bad port, 20 concurrent contenders across 4 nodes | Registered (Cleanly skipped on environments without Docker) |

### Test Commands
```bash
# Run full project test suite
mvn clean test

# Run Redis lock unit tests
mvn test -Dtest=RedisDistributedLockManagerTest

# Run Task Execution locking tests
mvn test -Dtest=TaskExecutionServiceTest

# Run Testcontainers Redis integration tests (requires active Docker engine)
mvn test -Dtest=RedisDistributedLockManagerIntegrationTest
```

---

## 8. Remaining Architectural Decisions for Team Alignment

1. **Delayed Re-queue vs Immediate Failure on Contention**:
   - Current implementation: When lock acquisition fails or times out, `TaskExecutionService` immediately transitions the task to `TaskStatus.FAILED`.
   - Team decision needed for Week 3: Should contentious tasks instead be diverted to a delayed Redis stream/queue for deferred retries?
2. **Cluster Topology in Production**:
   - Production deployments should configure Redis Sentinel or Redis Cluster via Spring Data Redis connection properties in `application-prod.yml`.
