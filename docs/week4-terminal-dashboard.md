# Week 4 — Member 4: Interactive Virtual Thread Cluster Dashboard

**Project:** High-Concurrency Autonomous Agent Orchestrator  
**Organization:** Zaalima Development Pvt. Ltd.  
**Member:** Deepansh — Member 4  
**Feature Branch:** `deepansh-week4-terminal-dashboard`  
**Target Branch:** `main`  

---

## 8.1 Overview

The **Interactive Virtual Thread Cluster Dashboard** provides real-time, cluster-wide terminal and REST observability for the High-Concurrency Agent Orchestrator. 

In high-concurrency systems running on Java 21 Virtual Threads (Project Loom), monitoring systems must observe massive volumes of concurrent agent tasks without overwhelming the JVM, distorting runtime execution, or blocking worker threads. 

This dashboard solves this challenge by:
1. Collecting **real, verifiable runtime metrics** from the running JVM and application state (no fabricated data).
2. Providing a non-blocking terminal console with live automatic refresh and interactive keyboard controls.
3. Supporting **multi-node cluster aggregation** via Redis snapshots with automatic staleness and health tracking.
4. Seamlessly falling back to **local standalone monitoring** when Redis is offline, ensuring zero startup or operational crashes.
5. Accurately segregating **application-level Virtual Thread tasks** from **JVM platform/carrier thread pools**, adhering strictly to JVM specification constraints.

---

## 8.2 Architecture

The dashboard implementation decouples metric acquisition, cluster aggregation, terminal rendering, and user input into distinct, independently testable layers:

```
                            ┌─────────────────────────────────┐
                            │    TerminalDashboardRunner      │
                            │  (Daemon Console & Key Listener)│
                            └───────────────┬─────────────────┘
                                            │ Controls & Redraw
                                            ▼
                            ┌─────────────────────────────────┐
                            │        DashboardService         │
                            │ (State Machine, Pause, Select)  │
                            └───────┬─────────────────┬───────┘
                                    │                 │
                ┌───────────────────┘                 └───────────────────┐
                ▼                                                         ▼
    ┌───────────────────────┐                                 ┌───────────────────────┐
    │  TerminalRenderer     │                                 │ClusterMetricsCollector│
    │  (ANSI Box Rendering, │                                 │(Aggregates Snapshots, │
    │   Color, Help View)   │                                 │ Detects Stale / Down) │
    └───────────────────────┘                                 └───────────┬───────────┘
                                                                          │
                                       ┌──────────────────────────────────┴──────────────────┐
                                       ▼                                                     ▼
                           ┌───────────────────────┐                             ┌───────────────────────┐
                           │RuntimeMetricsCollector│                             │  Redis StringTemplate │
                           │(Local JVM, Memory, VT │                             │ (Key: node:metrics:*  │
                           │ Tasks, Carrier States)│                             │  TTL: 30s Heartbeats) │
                           └───────────┬───────────┘                             └───────────┬───────────┘
                                       │                                                     │
                                       └───────────────────┬─────────────────────────────────┘
                                                           ▼
                                               ┌───────────────────────┐
                                               │  NodeMetricsReporter  │
                                               │ (Periodic 5s Publish) │
                                               └───────────────────────┘
```

### Architectural Responsibilities:
- **`RuntimeMetricsCollector` & `LocalRuntimeMetricsCollector`:** Queries `RuntimeMXBean`, `MemoryMXBean`, `ThreadMXBean`, and `TaskExecutionService` for real point-in-time metrics.
- **`NodeMetricsReporter`:** Periodically publishes local node JSON snapshots to Redis key `node:metrics:<nodeId>` with a 30-second TTL. Gracefully catches exceptions when Redis is offline.
- **`ClusterMetricsCollector`:** Scans Redis for all active node snapshots, flags nodes older than `staleThresholdMs` (15s) as `STALE`, marks missing registered nodes as `DOWN`, and calculates cluster-wide sums.
- **`TerminalRenderer`:** Pure functional renderer formatting ANSI escape codes, box-drawing characters, colors, tabular node views, and help screens.
- **`DashboardInputHandler`:** Decoupled keyboard command processor handling `'Q'`, `'R'`, `'P'`, `'N'`, `'H'`, `'ESC'`, and `'D'`.
- **`DashboardService`:** Thread-safe state holder coordinating refresh cycles, paused state, and selected node index.
- **`DashboardController`:** REST API exposing `/api/dashboard/status`, `/api/dashboard/local`, and `/api/dashboard/render`.

---

## 8.3 Prerequisites

- **Java Development Kit:** JDK 21 or JDK 23 (with `--enable-preview`).
- **Build Tool:** Apache Maven 3.9+ (or included `./mvnw`).
- **Operating System:** Windows, Linux, or macOS. ANSI colors and escape sequences are supported across PowerShell, Windows Terminal, CMD, bash, and zsh.
- **Optional Infrastructure:** Redis 6.x+ (default: `localhost:6379`). If Redis is not running, the dashboard automatically starts in standalone mode for the local node.

---

## 8.4 Startup Instructions

### 1. Start an Orchestrator Node with Interactive Dashboard
To launch the Spring Boot application with the interactive terminal dashboard active:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--orchestrator.dashboard.interactive=true"
```
Or with Java directly:
```bash
java --enable-preview -jar target/high-concurrency-agent-orchestrator-0.0.1-SNAPSHOT.jar --orchestrator.dashboard.interactive=true
```

### 2. Start Multiple Nodes for Cluster Testing
To start a secondary node on a different port and node identifier:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --grpc.server.port=4065 --orchestrator.node.id=node-2"
```

### 3. Access Metrics via REST API or curl
You can inspect the live dashboard output or JSON status via HTTP:
```bash
# Formatted ANSI text view
curl http://localhost:8080/api/dashboard/render

# Cluster JSON status
curl http://localhost:8080/api/dashboard/status

# Local node snapshot
curl http://localhost:8080/api/dashboard/local
```

### 4. Stopping the Dashboard
Press `Q` in the terminal or send `SIGINT` (`Ctrl+C`). The dashboard restores normal terminal cursor state and terminates background executors cleanly.

---

## 8.5 Dashboard Usage & Keyboard Controls

| Key | Action | Description |
| :---: | :--- | :--- |
| **`Q` / `q`** | **Quit** | Shuts down the interactive runner and restores terminal cursor settings. |
| **`R` / `r`** | **Refresh** | Immediately forces a fresh collection of cluster metrics and redraws the screen. |
| **`P` / `p`** | **Pause / Resume** | Pauses automatic periodic redraws so details can be inspected without the screen moving. |
| **`N` / `n`** | **Next Node** | Cycles the selected node pointer (`> 1*`, `> 2*`, etc.) to inspect individual node details. |
| **`H` / `h`** | **Help** | Toggles the in-terminal documentation and keybinding help overlay screen. |
| **`ESC` / `B`** | **Back / Dismiss** | Closes the Help screen and returns to the live cluster dashboard. |
| **`D` / `d`** | **Demo Mode** | Toggles simulated multi-node cluster data for presentation and demonstration purposes. |

---

## 8.6 Metric Definitions & Concurrency Model

### Important JVM Virtual Thread Limitation
Under the Java Virtual Machine specification, standard management APIs (`ThreadMXBean`, `Thread.getAllStackTraces()`) **do not provide a live inventory of all unpinned Virtual Threads**. Virtual Threads are lightweight, user-mode constructs managed by the JVM scheduler, multiplexed onto a smaller pool of OS carrier threads.

To provide honest, accurate metrics without misleading fabricated numbers:
1. **Virtual Thread Tasks (Application Level):**
   - **Active Tasks:** Tasks currently executing on Java 21 Virtual Threads within `TaskExecutionService` (`status == RUNNING`).
   - **Task Lifetime Metrics:** Total submitted, completed, and failed tasks managed by the orchestrator.
   - **Executor:** Backed by `Executors.newVirtualThreadPerTaskExecutor()`.
2. **JVM Platform/Carrier Threads (JVM Level):**
   - **Live Platform Threads:** Actual operating system threads hosting the JVM.
   - **Peak Platform Threads:** Historical peak count of live OS threads.
   - **Total Started Threads:** Total platform threads created since JVM boot.
   - **Carrier Thread States:** Accurate state breakdown of JVM carrier threads into `RUNNABLE`, `WAITING`, `TIMED_WAITING`, and `BLOCKED`.
3. **Node Status:**
   - **`UP` (Green):** Node is active and published a snapshot within `staleThresholdMs` (15s).
   - **`STALE` (Yellow):** Node previously reported, but its snapshot is older than `staleThresholdMs`.
   - **`DOWN` (Red):** Known registered agent/node that has failed to publish any metrics.
4. **Memory Metrics:**
   - **Heap Used / Max:** Collected directly from `MemoryMXBean.getHeapMemoryUsage()`.

---

## 8.7 Testing & Verification

Automated unit tests, collector tests, input handler tests, and controller tests were implemented using JUnit 5 and Mockito.

### 1. Dashboard Test Suite Execution
```bash
./mvnw test -Dtest="LocalRuntimeMetricsCollectorTest,ClusterMetricsCollectorTest,TerminalRendererTest,DashboardInputHandlerTest,DashboardServiceTest,NodeMetricsReporterTest,DashboardControllerTest,TerminalDashboardRunnerTest"
```
**Results:** **36 tests run, 0 failures, 0 errors, 0 skipped (100% PASS)**.

#### Test Class Coverage:
- **`LocalRuntimeMetricsCollectorTest` (3 tests):** Validates real JVM uptime, memory, platform thread states, and application-level virtual thread task counting.
- **`ClusterMetricsCollectorTest` (5 tests):** Validates multi-node Redis snapshot parsing, stale node detection (>15s), unreachable node identification, Redis failure resilience fallback, and demo mode segregation.
- **`TerminalRendererTest` (5 tests):** Verifies complete main dashboard view formatting, empty node notice, help screen rendering, paused status, and demo data labels.
- **`DashboardInputHandlerTest` (8 tests):** Verifies `Q`, `R`, `P`, `N`, `H`, `ESC`, `D`, and unrecognized key safety.
- **`DashboardServiceTest` (5 tests):** Tests metric refresh cycles, node selection cycling, pause toggle, help toggle, and clean shutdown.
- **`NodeMetricsReporterTest` (3 tests):** Validates periodic publishing to Redis with TTL, offline Redis exception handling, and disabled state bypass.
- **`DashboardControllerTest` (5 tests):** Tests `/api/dashboard/status`, `/api/dashboard/local`, `/api/dashboard/render`, `/api/dashboard/refresh`, and `/api/dashboard/demo`.
- **`TerminalDashboardRunnerTest` (2 tests):** Verifies runner is inactive by default and starts background threads when interactive mode is requested.

### 2. Full Regression Suite Execution
```bash
./mvnw test -Dtest="LeastUtilizedLoadBalancerTest,TaskRoutingServiceTest,AgentTaskControllerTest,AgentRegistrationControllerTest,AgentRegistryServiceTest,AgentGrpcServiceTest,InMemoryDistributedLockManagerTest,LockTokenTest,RedisDistributedLockManagerTest,RedisConfigLifecycleTest,BulkheadTest,CircuitBreakerTest,ExternalAgentClientTest,TimeoutTest,TaskExecutionServiceTest,StructuredConcurrencyServiceTest"
```
**Results:** **122 tests run, 0 failures, 0 errors, 0 skipped (100% PASS)**.

---

## 8.8 Troubleshooting

1. **Redis Offline or Connection Refused:**
   - **Behavior:** The dashboard detects that Redis is unreachable, logs a debug warning, and falls back to **Standalone Local Mode**.
   - **Resolution:** Start Redis on `localhost:6379` or set `REDIS_HOST` / `REDIS_PORT`. Once Redis comes online, the dashboard automatically discovers remote nodes on the next refresh cycle.
2. **Terminal Displays Garbled Characters:**
   - **Cause:** Using a legacy Windows command prompt without ANSI VT100 support.
   - **Resolution:** Use Windows Terminal, PowerShell 7+, or VS Code integrated terminal which natively support full ANSI 24-bit color.
3. **Node Displayed as `STALE`:**
   - **Cause:** A remote orchestrator node has stopped sending snapshots or is experiencing high GC pauses exceeding 15 seconds.
   - **Resolution:** Inspect the remote node logs. Once the node resumes sending snapshots, its status automatically returns to `UP`.

---

## 8.9 Known Limitations

1. **Virtual Thread Enumeration:** As per JDK 21+ JVM specifications, `ThreadMXBean` only enumerates platform/carrier threads. Live Virtual Thread counts are accurately reflected via orchestrator task execution metrics (`TaskExecutionService`).
2. **Cluster Visibility in Standalone Mode:** When running without Redis or a shared network transport, each node monitors only its own local JVM instance. This is clearly communicated on the dashboard header: `Mode: STANDALONE (Redis Offline - Local Node Only)`.
