# Evaluation Roadmap: High-Concurrency Autonomous Agent Orchestrator

Zaalima Development Pvt. Ltd. internship, Project 1

> **Status: DRAFT.** Items marked **TBD** (to be decided by the team) or **[verify]**
> (to be checked against the code or confirmed with the mentor) are not final.

---

## 1. Project Overview

### 1.1 Project
Project 1: High-Concurrency Autonomous Agent Orchestrator (4 weeks).

### 1.2 Problem Statement
Traditional Java thread pools become a bottleneck when they handle thousands of
simultaneous I/O-bound AI agent workflows. The goal is to design a highly concurrent,
distributed task orchestrator that uses Java 21 Virtual Threads (Project Loom) and
Structured Concurrency to execute a massive number of autonomous microservice requests
without exhausting operating system resources.

### 1.3 Objectives
By the end of the project, the orchestrator should:
- Run on a Spring Boot 3 backend using Java 21 Virtual Threads for lightweight, high-throughput execution.
- Let agents register securely through gRPC and REST interfaces.
- Fork and join thousands of parallel sub-tasks (for example fetching analytics or user profiles) under a single manageable scope using Structured Concurrency.
- Coordinate orchestrator nodes through Redis Pub/Sub messaging and distributed locking.
- Route incoming agent payloads to the least-utilized node in the cluster.
- Stay stable during third-party API timeouts using Resilience4j (Circuit Breaker, Bulkhead).
- Show no thread-pinning or memory leaks in the Virtual Thread executor, verified by automated tests.
- Run on Docker Compose, with memory consumption and throughput profiled against a legacy platform-thread baseline.
- Provide an interactive terminal dashboard of live Virtual Thread execution states across the cluster.

### 1.4 Week-wise Scope

| Week | Focus | Deliverables |
|---|---|---|
| 1 | Foundation | Core Spring Boot 3 backend with Virtual Threads; branch management protocol with strict GitHub commit signatures for all backend modules; gRPC and REST interfaces for agent registration |
| 2 | Concurrency and distribution | Structured Concurrency for parallel sub-tasks; Redis Pub/Sub for inter-node communication; distributed locking; foundational concurrency models submitted for the first peer review |
| 3 | Scale and resilience | Least-utilized-node load balancing; Circuit Breaker and Bulkhead (Resilience4j); automated tests for thread-pinning and memory leaks |
| 4 | Deployment and evaluation | Docker Compose deployment with memory and throughput profiling against a platform-thread baseline; operational structure and scoring matrices for the evaluation roadmap; terminal dashboard for Virtual Thread states |

### 1.5 Scope of This Document
This document explains how the orchestrator will be evaluated: what is measured, how it is
scored, how the Virtual Thread implementation is compared with the platform-thread baseline,
and how results are reported.

---

## 2. Operational Structure

### 2.1 High-level flow

```
Agent --(REST / gRPC)--> Orchestrator node
                              |
                              |  1. register agent / receive task payload
                              v
                    Load balancer picks the least-utilized node
                              |
                              v
              Task runs on a Virtual Thread, sub-tasks are forked and
              joined inside one Structured Concurrency scope
                              |
                              v
        Redis: node load, Pub/Sub messages, distributed locks shared by all nodes
```

### 2.2 Main components

| Component | Responsibility | Where it lives [verify package/class names] |
|---|---|---|
| REST API | Agent registration and task submission over HTTP | `controller` package |
| gRPC API | Agent registration and task submission over gRPC (`agent.proto`) | `grpc` package, `src/main/proto` |
| Agent registry | Keeps track of registered agents | `service` package (`AgentRegistryService`) |
| Task execution | Runs tasks on Virtual Threads | `service` package (`TaskExecutionService`, `VirtualThreadService`) |
| Structured Concurrency | Forks and joins parallel sub-tasks under one scope so failures and cancellation stay contained | `service` package [verify] |
| Redis Pub/Sub | Inter-node messaging on the channel `agent-orchestrator` | `redis` package (`RedisConfig`, `RedisMessagePublisher`, `RedisMessageSubscriber`) |
| Distributed lock | Stops two nodes from working on the same resource at once | Week 2 lock implementation [verify class and approach] |
| Node load tracking | Each node writes its active task count to Redis; the balancer reads all nodes' loads | `redis` package (`NodeLoadReporter`, `NodeLoadReader`) |
| Load balancer | Routes each payload to the least-utilized node | Week 3 [verify class] |
| Resilience | Circuit Breaker and Bulkhead around third-party API calls | Resilience4j, Week 3 [verify] |
| Deployment | Docker Compose: orchestrator nodes and Redis | Week 4 [verify] |
| Terminal dashboard | Live view of Virtual Thread states across the cluster | Week 4 [verify] |

### 2.3 How tasks are received and distributed
1. An agent registers through the REST or gRPC interface.
2. A task payload arrives at any node.
3. The load balancer reads every node's current load from Redis and routes the payload to the node with the fewest active tasks.
4. The chosen node starts a task and increments its active task count.
5. When the task ends, the count is decremented.

### 2.4 How tasks run concurrently
- Each task runs on a Virtual Thread, so thousands of I/O-bound tasks can be in flight without one operating system thread per task.
- Sub-tasks (for example fetching analytics and user profiles) are forked inside one Structured Concurrency scope and joined together. If one sub-task fails, the scope can cancel the rest, so no sub-task is left running by accident.

### 2.5 How Redis messaging, locking and load tracking work
- **Pub/Sub.** Nodes publish and subscribe on the channel `agent-orchestrator` to exchange messages.
- **Distributed lock.** A lock is taken in Redis before a shared resource is changed and released when the work is done. A time-to-live protects against a node that crashes while holding a lock. [verify exact implementation]
- **Node load.** Every 5 seconds each node writes `node:load:<nodeId> = <active tasks>` to Redis with a 15-second time-to-live. A node that stops reporting disappears from Redis automatically, so the balancer stops sending it work.

### 2.6 How failures are handled

| Failure | What happens |
|---|---|
| A node goes down | Its load key expires after 15 seconds and the load balancer stops routing to it |
| A third-party API is slow or failing | The Circuit Breaker opens after repeated failures and returns a fallback; the Bulkhead limits how many calls can wait at once [verify settings] |
| A sub-task fails | Structured Concurrency cancels the sibling sub-tasks in the same scope |
| Redis is unreachable | Nodes cannot report load or exchange messages. The current behaviour (fail fast or degrade) should be tested and recorded in the evaluation [verify] |

---

## 3. Evaluation Criteria

| # | Criterion | What it tells us |
|---|---|---|
| 1 | Performance and throughput | How many tasks per second the orchestrator completes |
| 2 | Memory consumption | How much memory it uses under different workloads |
| 3 | Concurrency | How effectively many simultaneous tasks are handled with Virtual Threads, including no thread-pinning |
| 4 | Reliability and resilience | Behaviour during Redis failures, API timeouts and network problems |
| 5 | Scalability | How performance changes as concurrent tasks or nodes increase |
| 6 | Monitoring | How clearly task execution states can be observed across the cluster |

---

## 4. Scoring Matrix

Weights and target values are **TBD** and must be agreed by the team (and confirmed with the
mentor) before this document is finalized. They are intentionally not filled in here.

| # | Criterion | Weight | Scale | Measurement method | Metric | Target |
|---|---|---|---|---|---|---|
| 1 | Performance and throughput | TBD | 1 to 5 | Load test with a fixed workload, same for both implementations | Tasks per second | TBD |
| 2 | Memory consumption | TBD | 1 to 5 | Heap and process memory sampled during the load test | MB at fixed task counts | TBD |
| 3 | Concurrency | TBD | 1 to 5 | Automated tests for thread-pinning and leaks; load test with many simultaneous tasks | Max concurrent tasks without errors; pinning events found | TBD |
| 4 | Reliability and resilience | TBD | 1 to 5 | Fault injection: stop Redis, delay the third-party API, stop a node | Success and failure rate; recovery time | TBD |
| 5 | Scalability | TBD | 1 to 5 | Repeat the load test with 1, 2 and more nodes and growing task counts | Throughput change per added node | TBD |
| 6 | Monitoring | TBD | 1 to 5 | Check the dashboard against known running tasks | Share of task states shown correctly | TBD |

### Scoring scale (proposed, to be agreed)

| Score | Meaning |
|---|---|
| 5 | Meets or beats the target |
| 4 | Slightly below the target, no functional problem |
| 3 | Clearly below the target but works |
| 2 | Works only in limited cases |
| 1 | Does not work or could not be measured |

---

## 5. Benchmarking Plan

**Goal.** Compare the Virtual Thread orchestrator with a legacy platform-thread implementation.

**Rules for a fair comparison**
- Same machine, same Docker Compose setup, same Redis and the same simulated third-party API for both runs.
- Same workload for both runs (same task mix and sub-task counts).
- Each test run several times, with the average reported.
- Warm-up period before measuring.

**Workload levels (example, to be agreed)**

| Level | Concurrent tasks |
|---|---|
| Low | TBD |
| Medium | TBD |
| High | TBD |

**What is measured for each level**
- Throughput (tasks per second)
- Memory usage (MB)
- Response time (average and 95th percentile)
- Error rate

**Tool:** TBD (to be chosen by the member doing the profiling).

**Result table (to be filled after testing)**

| Level | Implementation | Throughput | Memory | Avg response | p95 response | Error rate |
|---|---|---|---|---|---|---|
| Low | Platform threads | TBD | TBD | TBD | TBD | TBD |
| Low | Virtual Threads | TBD | TBD | TBD | TBD | TBD |
| Medium | Platform threads | TBD | TBD | TBD | TBD | TBD |
| Medium | Virtual Threads | TBD | TBD | TBD | TBD | TBD |
| High | Platform threads | TBD | TBD | TBD | TBD | TBD |
| High | Virtual Threads | TBD | TBD | TBD | TBD | TBD |

---

## 6. Operational Workflow and Responsibilities

### 6.1 Evaluation stages

| Stage | Activity | Output |
|---|---|---|
| 1 | Agree criteria, weights and targets | Final scoring matrix |
| 2 | Deploy with Docker Compose | Running cluster |
| 3 | Run benchmarks and fault tests | Raw results |
| 4 | Validate results (repeat runs, check for mistakes) | Validated results |
| 5 | Score each criterion | Filled scoring matrix |
| 6 | Write findings and recommendations | Final assessment (Section 8) |

### 6.2 Responsibilities

| Responsibility | Owner |
|---|---|
| Collect benchmark results | TBD (profiling member) |
| Validate results | TBD (a different member) |
| Maintain this document | TBD |
| Final review and merge | Team lead |

### 6.3 Program compliance (Zaalima SOP v1.1)

This part comes from the Zaalima SOP for Intern Projects, Version 1.1. It describes how the
team itself is reviewed.

| Item | Rule |
|---|---|
| Projects | Each team completes 2 projects out of 3, one per month, each in 4 weekly phases |
| Branches | Each member commits to their own branch; the team lead merges branches into main |
| Mid Review | Checks Week 1 and Week 2 work. The team repository needs commits on at least 10 different days in the 14 days before the review |
| Final Review | Checks Week 3 and Week 4 work. The team repository needs commits on every day of the 20 days before the review, with no gaps |
| Communication | A dedicated team channel; replies within 24 hours; 48 hours without a reply counts as unresponsive |
| Unresponsive member | Try all channels, wait up to 48 hours, inform Zaalima with proof, continue without them, update the Work Distribution Document |
| Documents | The Work Distribution Document is submitted before the Month 1 Mid Review; the lag document is submitted if the team falls behind |

**Review dates that need confirmation [verify with mentor]**
- Final Review: the SOP says "between the 5th and 10th" in one place and "7th to 15th" in the review summary table.
- Mid Review: the SOP says "23rd to 30th", but its example uses the 22nd.

---

## 7. Evaluation Roadmap

| Phase | Activity | Deliverable | Completion criteria | Owner | Date |
|---|---|---|---|---|---|
| 1 | Agree criteria, weights and targets | Final scoring matrix | Team and mentor sign off | TBD | TBD |
| 2 | Prepare the platform-thread baseline | Baseline build | Runs the same workload as the Virtual Thread version | TBD | TBD |
| 3 | Deploy both versions with Docker Compose | Running setups | Both start with one command | TBD | TBD |
| 4 | Run benchmarks | Raw results | Every workload level run several times | TBD | TBD |
| 5 | Run fault tests | Fault test log | Redis stop, API delay and node stop all tested | TBD | TBD |
| 6 | Validate and score | Filled scoring matrix | Results re-checked by a second member | TBD | TBD |
| 7 | Write the final assessment | Section 8 completed | Reviewed and merged by the team lead | TBD | TBD |

---

## 8. Final Assessment and Recommendations

*(To be completed after testing.)*

### 8.1 Summary of results
TBD

### 8.2 Final score

| Criterion | Weight | Score (1 to 5) | Weighted score |
|---|---|---|---|
| Performance and throughput | TBD | TBD | TBD |
| Memory consumption | TBD | TBD | TBD |
| Concurrency | TBD | TBD | TBD |
| Reliability and resilience | TBD | TBD | TBD |
| Scalability | TBD | TBD | TBD |
| Monitoring | TBD | TBD | TBD |
| **Total** | | | TBD |

### 8.3 Limitations
TBD (for example: tests ran on a single laptop, simulated third-party API, Redis hosted outside the cluster).

### 8.4 Recommended improvements
TBD
