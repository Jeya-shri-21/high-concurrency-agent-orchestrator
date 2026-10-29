# Week 3 Resilience4j Circuit Breaker, Bulkhead & API Timeouts Design

## 1. Problem Statement and Goals

In the **Zaalima High-Concurrency Autonomous Agent Orchestrator**, tasks are processed concurrently using Java 21 Virtual Threads. As the orchestrator coordinates autonomous agents across heterogeneous environments, it routinely communicates with external third-party services and remote agent instances via network boundaries (HTTP/REST).

Without explicit fault-tolerance mechanisms:
- **Cascading Failures:** A slow or unavailable remote agent can cause upstream callers to hang, causing resource starvation across the orchestrator.
- **Thread Exhaustion / Saturation:** Even with lightweight Virtual Threads, an unbounded number of concurrent outbound requests can saturate downstream endpoints, open excessive network sockets, and exhaust memory buffers.
- **Unbounded Latency:** Missing finite timeouts allow hanging remote connections to remain open indefinitely.
- **Thundering Herd:** Repeatedly pounding a struggling third-party API prevents the dependency from recovering.

### Key Goals for Week 3:
1. **Resilience4j Circuit Breaker:** Detect downstream remote failures and slow calls, fail fast when the failure threshold is breached, and allow controlled recovery in `HALF_OPEN` state.
2. **Resilience4j Bulkhead:** Enforce a strict concurrency limit on outbound external requests using a semaphore-based bulkhead that does not block or pin Java 21 Virtual Threads.
3. **Finite Timeout Management:** Implement finite, distinct connection, request, and overall operation deadlines using native Java 21 `HttpClient` timeouts and Resilience4j `TimeLimiter`.
4. **Deterministic Testing & Seamless Integration:** Provide end-to-end unit and integration tests with deterministic synchronization and integrate seamlessly with `TaskExecutionService` without breaking other members' contributions.

---

## 2. Architecture and Integration Point

```
                          [ Incoming Agent Task / Request ]
                                         │
                                         ▼
                            [ TaskExecutionService ]
                                         │
                        Is remote / external agent task?
                                ├── No ──► [ Local Simulation Logic ]
                                │
                                └── Yes
                                         │
                                         ▼
                         [ ExternalAgentClient Boundary ]
                                         │
                                         ▼
                       [ ResilientOperationExecutor Pipeline ]
                      ┌──────────────────────────────────────┐
                      │ 1. Bulkhead (Concurrency Control)    │
                      │    - Semaphore-based admission check │
                      │    - Rejects with BulkheadFullEx     │
                      ├──────────────────────────────────────┤
                      │ 2. Circuit Breaker (Health & State)  │
                      │    - Closed / Open / Half-Open       │
                      │    - Rejects with CircuitBreakerOpen │
                      ├──────────────────────────────────────┤
                      │ 3. TimeLimiter (Operation Deadline)  │
                      │    - Enforces end-to-end timeout     │
                      │    - Propagates cancel & interrupt   │
                      ├──────────────────────────────────────┤
                      │ 4. HTTP Client (Network Boundary)    │
                      │    - Connect Timeout                 │
                      │    - Request Timeout                 │
                      │    - 4xx ignored / 5xx recorded      │
                      └──────────────────────────────────────┘
                                         │
                                         ▼
                         [ Remote Autonomous Agent API ]
```

### Decorator Execution Order
The decorator pipeline uses the following ordering:
$$\text{Bulkhead} \longrightarrow \text{CircuitBreaker} \longrightarrow \text{TimeLimiter} \longrightarrow \text{HTTP Execution}$$

- **Bulkhead is Outermost:** Concurrency admission is checked first. If the orchestrator's local outbound capacity is saturated, the call is rejected immediately (`BulkheadFullException`). Crucially, this local rejection does **not** register as a failure in the Circuit Breaker because the downstream service was never called and is not at fault.
- **Circuit Breaker is Middle:** Checks the health of the downstream agent. If `OPEN`, it rejects calls immediately (`CircuitBreakerOpenException`), allowing the Bulkhead permit to be released instantly without consuming thread time or network resources.
- **TimeLimiter is Inner:** Enforces the total operation deadline. If the remote service fails to complete in time, the `TimeLimiter` cancels the task, interrupts the worker thread, and throws `ExternalApiTimeoutException`. This timeout **is** recorded as a failure in the Circuit Breaker because an unresponsive remote service indicates downstream distress.
- **Resource Cleanup:** In all scenarios (success, circuit rejection, downstream error, or timeout), the Bulkhead permit is guaranteed to be released in a `finally` block.

---

## 3. Circuit Breaker Implementation

Implemented using Resilience4j's `CircuitBreaker` and `CircuitBreakerRegistry` with state transitions:

- **CLOSED:** Calls are permitted normally. Failures and slow calls are recorded in a sliding window.
- **OPEN:** Calls fail fast with `CircuitBreakerOpenException` without touching the network.
- **HALF_OPEN:** After `waitDurationInOpenState` elapses, a configured trial quota (`permittedNumberOfCallsInHalfOpenState`) is permitted to evaluate whether the downstream service has recovered.
  - If trial calls succeed: Circuit returns to `CLOSED`.
  - If trial calls fail: Circuit returns to `OPEN`.

### Failure vs. Ignored Exception Classification
- **Recorded as Failures:**
  - `ExternalApiException` (HTTP 5xx server errors, downstream crashes).
  - `ExternalApiTimeoutException` (Connection, request, or operation deadlines exceeded).
  - `IOException`, `ConnectException`, `HttpTimeoutException`.
- **Ignored (Not Counted as Failures):**
  - `ExternalApiValidationException` (HTTP 4xx client errors, bad request, unprocessable entity). Client errors indicate caller invalidity, not downstream infrastructure failure.
  - `IllegalArgumentException`.

---

## 4. Bulkhead Implementation

### Semaphore-Based Bulkhead for Virtual Threads
Rather than introducing a ThreadPool Bulkhead (which introduces fixed carrier thread pools and queueing that defeat the purpose of Project Loom), the orchestrator uses Resilience4j's **Semaphore Bulkhead**:
- Directly limits concurrent outbound calls using atomic permits (`maxConcurrentCalls`).
- Non-blocking permit acquisition with configurable wait duration (`maxWaitDurationMs`).
- Never pins carrier threads during Virtual Thread execution.
- Prevents memory exhaustion from unbounded outbound HTTP requests during peak load.

---

## 5. Third-Party API Timeout Handling

Timeout handling protects three distinct phases of external communication:

| Timeout Layer | Mechanism | Default | Purpose |
| :--- | :--- | :--- | :--- |
| **Connect Timeout** | `HttpClient.connectTimeout` | 2000 ms | Protects the initial TCP handshake and TLS negotiation. |
| **Request / Read Timeout** | `HttpRequest.timeout` | 3000 ms | Protects waiting for remote HTTP response headers and body bytes. |
| **Operation Timeout** | Resilience4j `TimeLimiter` | 3500 ms | Protects the overall end-to-end operation deadline, including retries or delays. |

### Cancellation & Interruption Propagation
When an operation exceeds the `TimeLimiter` deadline:
1. The `CompletableFuture` is cancelled (`cancel(true)`).
2. A completion hook automatically issues `cancel(true)` to the underlying Virtual Thread future.
3. The executing thread receives an interrupt (`Thread.interrupt()`), unblocking sleep, I/O waits, or socket reads immediately.

---

## 6. Configuration Reference

All settings are centralized under `orchestrator.resilience` in `src/main/resources/application.properties` and validated via `ResilienceProperties`:

```properties
# Resilience4j Circuit Breaker
orchestrator.resilience.circuit-breaker.sliding-window-size=10
orchestrator.resilience.circuit-breaker.minimum-number-of-calls=5
orchestrator.resilience.circuit-breaker.failure-rate-threshold=50.0
orchestrator.resilience.circuit-breaker.slow-call-rate-threshold=50.0
orchestrator.resilience.circuit-breaker.slow-call-duration-threshold-ms=2000
orchestrator.resilience.circuit-breaker.wait-duration-in-open-state-ms=5000
orchestrator.resilience.circuit-breaker.permitted-number-of-calls-in-half-open-state=3
orchestrator.resilience.circuit-breaker.automatic-transition-from-open-to-half-open-enabled=true

# Resilience4j Bulkhead
orchestrator.resilience.bulkhead.max-concurrent-calls=10
orchestrator.resilience.bulkhead.max-wait-duration-ms=200

# External API Timeouts
orchestrator.resilience.timeout.connect-timeout-ms=2000
orchestrator.resilience.timeout.request-timeout-ms=3000
orchestrator.resilience.timeout.operation-timeout-ms=3500
```

---

## 7. Logging and Observability

`ResilienceManager` hooks into Resilience4j event publishers using SLF4J:
- **Circuit Breaker Transitions:** Logs `INFO` on state transitions (e.g., `CLOSED -> OPEN`, `OPEN -> HALF_OPEN`).
- **Circuit Breaker Rejections:** Logs `WARN` with circuit state on fast rejections.
- **Bulkhead Rejections:** Logs `WARN` indicating concurrency saturation.
- **Timeouts & Slow Calls:** Logs `WARN` with elapsed durations and threshold comparisons.

---

## 8. Automated Tests and Verification

All 32 resilience tests are deterministic, mock all external dependencies, and require no live network or Redis connection.

| Test Class | Tests | Key Scenarios Verified |
| :--- | :---: | :--- |
| `CircuitBreakerTest` | 8 | CLOSED state pass-through, failure-rate threshold tripping, fast rejection while OPEN, HALF_OPEN permitted call limits, successful recovery to CLOSED, failed recovery back to OPEN, 4xx ignored exceptions, slow-call rate tripping. |
| `BulkheadTest` | 5 | Concurrency limit admission, fast rejection on overflow (`BulkheadFullException`), permit release on success, permit release on exception, permit release on timeout, subsequent calls after release. |
| `TimeoutTest` | 4 | Fast execution within deadline, timeout deadline exceeded (`ExternalApiTimeoutException`), Circuit Breaker failure recording, thread interrupt and cancellation propagation. |
| `ResilienceIntegrationTest` | 5 | End-to-end pipeline execution, Bulkhead saturation isolation from Circuit Breaker, open circuit immediate Bulkhead permit release, repeated timeouts tripping Circuit Breaker, 5xx vs 4xx classification. |
| `ExternalAgentClientTest` | 5 | Mock `HttpClient` 200 OK handling, 400 Bad Request translation, 500 Server Error translation, `HttpTimeoutException` mapping, task-to-endpoint routing. |
| `TaskExecutionResilienceTest` | 5 | Remote task execution via `ExternalAgentClient`, task status `FAILED` on open circuit, task status `FAILED` on bulkhead saturation, task status `FAILED` on timeout, standard local tasks unaffected. |

### How to Run Tests
```bash
# Run all resilience tests
mvn test -Dtest=CircuitBreakerTest,BulkheadTest,TimeoutTest,ResilienceIntegrationTest,ExternalAgentClientTest,TaskExecutionResilienceTest

# Run full project test suite
mvn test
```

---

## 9. Known Limitations and Future Work

1. **Environment Infrastructure:** Running the full Maven build (`mvn test`) without a local Redis server or Docker reports 6 application-context initialization errors in pre-existing tests (`AgentOrchestratorApplicationTests` and `AgentGrpcServiceIntegrationTest`). In CI environments with Redis running as a service, the entire test suite passes.
2. **Dynamic Endpoint Resolution:** Currently, `HttpExternalAgentClient` dispatches to a configurable base URL (`http://localhost:8081/agents/{agentId}/tasks`). Future integration with Member 1's service discovery / load balancer can resolve dynamic agent instance addresses.
