# Week 3: Least-Utilized Node Load-Balancing Algorithm

**Project:** High-Concurrency Autonomous Agent Orchestrator  
**Organization:** Zaalima Development Pvt. Ltd.  
**Week:** 3  
**Member:** 1 (Load-Balancing Algorithm)  
**Branch:** `deepansh-week3-Load-Balancing-Algorithm`  

---

## 1. Overview & Architecture

In Week 3, **Member 1** delivers the core **Least-Utilized Node Load-Balancing Algorithm** and **Task Payload Routing** workflow for the High-Concurrency Autonomous Agent Orchestrator.

The load-balancing system dynamically evaluates candidate nodes across the cluster, retrieves real-time utilization metrics reported in Redis (integrated with Member 2's `NodeLoadReader`), and routes task payloads to the least-utilized eligible node.

```
                              [ Incoming Task Payload ]
                                         │
                                         ▼
                            ┌────────────────────────┐
                            │   TaskRoutingService   │
                            └────────────┬───────────┘
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 │                                               │
                 ▼                                               ▼
     ┌───────────────────────┐                       ┌───────────────────────┐
     │  AgentRegistryService │                       │    NodeLoadReader     │
     │  (Discover Eligible   │                       │  (Redis Utilization:  │
     │     Active Nodes)     │                       │   active task count)  │
     └───────────┬───────────┘                       └───────────┬───────────┘
                 │                                               │
                 │ Eligible Node IDs: [A, B, C]                  │ Loads: {A:80, B:30, C:60}
                 └───────────────────────┬───────────────────────┘
                                         │
                                         ▼
                            ┌────────────────────────┐
                            │ LeastUtilizedLoadBalancer
                            │ - Filter Valid Loads   │
                            │ - Min Utilization      │
                            │ - Deterministic Tie    │
                            └────────────┬───────────┘
                                         │ Selected: Node B (30)
                                         ▼
                            ┌────────────────────────┐
                            │  Pre-Routing Check     │
                            │  (Ensure Still Active) │
                            └────────────┬───────────┘
                                         │ Verified
                                         ▼
                            ┌────────────────────────┐
                            │  TaskExecutionService  │
                            │  (Virtual Thread Exec) │
                            └────────────────────────┘
```

---

## 2. Core Components

### 1. `LoadBalancer.java` & `LeastUtilizedLoadBalancer.java`
- **Package:** `com.zaalima.orchestrator.loadbalancer`
- **Responsibility:** Pure, decoupled strategy algorithm that inspects eligible node IDs and their reported utilization metrics.
- **Key Rules:**
  - **Metric:** Active tasks count reported in Redis (unit: integer tasks).
  - **Least-Utilized Selection:** Selects the eligible candidate with minimum valid load.
  - **Deterministic Tie-Breaking:** If multiple nodes share the lowest load, tie-breaking sorts the candidate node IDs lexicographically (`Collections.sort(...)`) and deterministically selects the first.
  - **Strict Concurrency & Health Rule:** Missing, unmonitored, or invalid (negative) utilization metrics are **NEVER** silently treated as zero. Nodes lacking valid metrics in Redis are skipped to avoid routing tasks to silent or dead nodes.

### 2. `TaskRoutingService.java`
- **Package:** `com.zaalima.orchestrator.service`
- **Responsibility:** Orchestrates discovery, metric retrieval, candidate selection, pre-routing validation, and task submission.
- **Step-by-step workflow:**
  1. **Discover eligible nodes:** Queries `AgentRegistryService.getAllAgents()` and filters for nodes with `"ACTIVE"` status.
  2. **Retrieve utilization:** Queries Member 2's `NodeLoadReader.getNodeLoads()` backed by Redis.
  3. **Select least-utilized node:** Invokes `LeastUtilizedLoadBalancer.selectNode(...)`.
  4. **Pre-routing validation:** Confirms the selected node is still registered and active in `AgentRegistryService`.
  5. **Execute routing:** Instantiates `AgentTask` bound to the selected node and submits to `TaskExecutionService` for virtual-thread execution.

### 3. `AgentTaskController.java` Updates
- **Package:** `com.zaalima.orchestrator.controller`
- **Endpoints:**
  - `POST /api/tasks`: Supports automatic least-utilized routing when `agentId` is omitted, blank, or set to `"auto"`. Direct submission with explicit `agentId` is preserved.
  - `POST /api/tasks/route`: Dedicated route endpoint accepting `RoutedTaskRequest(taskType, payload)`.
  - `GET /api/tasks/load-balancer/status`: Returns current eligible nodes, loads, and optimal node.

### 4. Custom Exceptions & Error Handling
- **`NoEligibleNodeException`:** Thrown when no registered nodes exist, all nodes are inactive, or no nodes have valid utilization metrics reported (mapped to HTTP `503 Service Unavailable`).
- **`NodeUtilizationException`:** Thrown when the Redis utilization service fails or is unreachable (mapped to HTTP `503 Service Unavailable`).
- **`TaskRoutingException`:** Thrown when pre-routing verification fails or dispatch submission fails (mapped to HTTP `500 Internal Server Error`).

---

## 3. Usage Examples

### Route Task via Load Balancer
```http
POST /api/tasks
Content-Type: application/json

{
  "taskType": "DATA_AGGREGATION",
  "payload": "{\"records\": 5000}"
}
```

**Response:**
```json
{
  "taskId": "7a3b4c1d-1234-5678-abcd-ef0123456789",
  "agentId": "node-2",
  "taskType": "DATA_AGGREGATION",
  "payload": "{\"records\": 5000}",
  "status": "PENDING"
}
```

### Inspect Load Balancer Status
```http
GET /api/tasks/load-balancer/status
```

**Response:**
```json
{
  "status": "OK",
  "leastUtilizedNode": "node-2",
  "eligibleNodes": ["node-1", "node-2", "node-3"]
}
```

---

## 4. Verification & Testing

Unit tests were implemented without requiring live Redis or platform dependencies:
- **`LeastUtilizedLoadBalancerTest` (8 tests):** Minimum load selection across 3+ nodes, deterministic tie-breaking, empty registry handling, missing metrics validation (never treated as 0), invalid negative value handling.
- **`TaskRoutingServiceTest` (7 tests):** Successful routing dispatch, empty registry exception, inactive nodes filtering, Redis failure handling, pre-routing availability check, task execution failure handling.
- **`AgentTaskControllerTest` (6 tests):** Auto-routing on missing agentId, explicit agentId preservation, `/api/tasks/route` endpoint, 503 error responses for no eligible node and utilization failure.

**Test Run Summary:**
```bash
./mvnw test -Dtest=LeastUtilizedLoadBalancerTest,TaskRoutingServiceTest,AgentTaskControllerTest
```
**Results:** **21 tests run, 0 failures, 0 errors, 0 skipped** (100% PASS).
