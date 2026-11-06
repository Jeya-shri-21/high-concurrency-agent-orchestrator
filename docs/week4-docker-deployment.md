# Week 4 Docker Compose Deployment Guide

## 1. Overview and Architecture

This deployment module provides production-grade containerization and orchestration for the **Zaalima High-Concurrency Autonomous Agent Orchestrator** using Docker and Docker Compose.

The deployment infrastructure orchestrates:
- **Redis (`orchestrator-redis`):** Acts as the centralized cluster backbone for distributed locks (`lock:task:{taskId}`), node utilization reporting (`node:load:{nodeId}`), and cluster Pub/Sub event broadcasting (`agent-orchestrator`).
- **Primary Orchestrator (`orchestrator-node-1`):** Core Spring Boot orchestrator instance exposing HTTP REST (port 8080) and gRPC (port 9090).
- **Secondary Orchestrator (`orchestrator-node-2`):** Optional replica node activated via Compose profiles to demonstrate multi-node coordination without port collisions.
- **Shared Bridge Network (`orchestrator-network`):** Isolated internal network enabling secure, DNS-based inter-container communication (`redis:6379`).
- **Persistent Volume (`redis-data`):** Preserves Redis Append-Only File (AOF) state across container restarts.

```
                    ┌────────────────────────────────────────────────────────┐
                    │                      Docker Host                       │
                    │                                                        │
                    │   HTTP: 8080 ──────────────┐    HTTP: 8081 ────────┐   │
                    │   gRPC: 9090 ────────┐     │    gRPC: 9091 ──┐     │   │
                    │                      │     │                 │     │   │
                    │   Redis: 6379 ─┐     │     │                 │     │   │
                    │                │     │     │                 │     │   │
  ┌─────────────────┼────────────────┼─────┼─────┼─────────────────┼─────┼───┼──────────┐
  │                 │                ▼     ▼     ▼                 ▼     ▼   ▼          │
  │                 │             ┌─────────────────┐       ┌─────────────────┐       │
  │  orchestrator-  │             │ orchestrator-   │       │ orchestrator-   │       │
  │  network        │             │ node-1 (8080)   │       │ node-2 (8080)   │       │
  │  (bridge)       │             └────────┬────────┘       └────────┬────────┘       │
  │                 │                      │ DNS: redis:6379         │ DNS: redis:6379│
  │                 │                      ▼                         ▼                │
  │                 │             ┌───────────────────────────────────────────┐       │
  │                 └────────────►│               Redis (6379)                │       │
  │                               │   Distributed Locks, PubSub, Node Loads   │       │
  │                               └─────────────────────┬─────────────────────┘       │
  │                                                     │                             │
  │                                                     ▼                             │
  │                                           [ redis-data Volume ]                   │
  └───────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Prerequisites

- **Docker Engine:** Version 24.0 or later
- **Docker Compose:** Version 2.20 or later (Docker Compose v2)
- **Host Resources:** Minimum 2 GB available RAM and 5 GB disk space

Verify installed versions:
```bash
docker --version
docker compose version
```

---

## 3. Configuration and Environment Variables

Configuration is externalized through `.env`. A complete template is provided in [`.env.example`](file:///.env.example).

### Step 1: Initialize Environment File
```bash
cp .env.example .env
```

### Configuration Parameters
| Parameter | Default | Description |
| :--- | :--- | :--- |
| `HTTP_PORT` | `8080` | Host port mapped to primary orchestrator HTTP REST |
| `GRPC_PORT` | `9090` | Host port mapped to primary orchestrator gRPC service |
| `HTTP_PORT_NODE2` | `8081` | Host port mapped to secondary orchestrator HTTP REST |
| `GRPC_PORT_NODE2` | `9091` | Host port mapped to secondary orchestrator gRPC service |
| `NODE_ID_PRIMARY` | `node-1` | Unique node identifier for primary instance |
| `NODE_ID_SECONDARY`| `node-2` | Unique node identifier for secondary replica |
| `NODE_LOAD_TTL_SECONDS`| `15` | TTL for node load heartbeats in Redis |
| `REDIS_HOST` | `redis` | Internal DNS name of Redis container |
| `REDIS_PORT` | `6379` | Internal Redis port |
| `REDIS_PORT_HOST` | `6379` | Host port mapped to Redis (for debugging/CLI) |
| `JAVA_TOOL_OPTIONS` | `--enable-preview -XX:+UseG1GC -XX:MaxRAMPercentage=75.0` | Container-aware JVM memory & preview flags |

---

## 4. Building and Starting Services

### Validate Compose Syntax
```bash
docker compose config
```

### Build the Orchestrator Image
Builds a minimal, secure multi-stage image with Eclipse Temurin OpenJDK 21:
```bash
docker compose build
```

### Start Single-Node Deployment (Default)
Starts the `redis` service and the primary `orchestrator` node:
```bash
docker compose up -d
```

### Start Multi-Node Cluster Deployment
Activates the secondary replica node using the `multi-node` profile:
```bash
docker compose --profile multi-node up -d
```

---

## 5. Monitoring, Health Checks, and Logs

### Check Running Containers and Health Status
```bash
docker compose ps
```
Expected output:
```text
NAME                  IMAGE                                      COMMAND                  SERVICE               STATUS
orchestrator-redis    redis:7.4-alpine                           "docker-entrypoint.s…"   redis                 Up (healthy)
orchestrator-node-1   high-concurrency-agent-orchestrator:latest "java --enable-previe…"  orchestrator          Up (healthy)
orchestrator-node-2   high-concurrency-agent-orchestrator:latest "java --enable-previe…"  orchestrator-node-2   Up (healthy)
```

### Service Healthcheck Details
- **Redis Healthcheck:** Uses `redis-cli ping`. Evaluated every 5 seconds.
- **Orchestrator Healthcheck:** Uses `curl -f http://localhost:8080/`. The orchestrator only reports healthy once Spring Boot and the embedded Tomcat server are ready to accept traffic.
- **Startup Ordering (`depends_on`):** Orchestrator nodes wait for `redis` condition `service_healthy` before starting. This completely prevents Redis connection refused exceptions on initial boot.

### View Container Logs
```bash
# Stream logs for all services
docker compose logs -f

# View orchestrator node 1 logs only
docker compose logs -f orchestrator

# View Redis logs only
docker compose logs -f redis
```

---

## 6. Verifying Deployment Endpoints

Once the containers report `healthy`, verify HTTP, gRPC, and Redis functionality from the host machine:

### 1. Root Orchestrator Status
```bash
curl http://localhost:8080/
```
*Expected response:* `Zaalima Agent Orchestrator is running!`

### 2. Virtual Thread Concurrency Endpoint
```bash
curl http://localhost:8080/virtual-thread
```

### 3. Agent Registration (REST)
```bash
curl -X POST http://localhost:8080/api/agents \
  -H "Content-Type: application/json" \
  -d '{"agentId": "docker-worker-01", "agentType": "COMPUTE_NODE"}'
```

### 4. Query Agent Status
```bash
curl http://localhost:8080/api/agents/docker-worker-01
```

### 5. Verify Node Load Reporting in Redis
```bash
# Connect to Redis CLI inside container
docker exec -it orchestrator-redis redis-cli get node:load:node-1
```
*Expected response:* Current count of active tasks (e.g. `"0"`).

---

## 7. Stopping and Resetting Services

### Stop Containers (Preserve Data)
```bash
docker compose down
```
*Note:* The Redis data volume (`redis-data`) is preserved. Active distributed locks and registered node heartbeats remain in storage according to their TTLs.

### Rebuild Image After Code Changes
```bash
docker compose build --no-cache
docker compose up -d
```

### Complete Reset (Warning: Destructive)
> [!CAUTION]
> The following command deletes all container volumes, permanently erasing all stored Redis state, task queues, and metadata.
```bash
docker compose down -v
```

---

## 8. Ports and Network Security

| Service | Container Port | Default Host Port | Protocol | Scope | Purpose |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `orchestrator` | `8080` | `8080` | HTTP | Public | REST API, Healthchecks, Agent registration |
| `orchestrator` | `9090` | `9090` | gRPC | Public | High-throughput gRPC AgentService |
| `orchestrator-node-2` | `8080` | `8081` | HTTP | Public | Secondary cluster node REST API |
| `orchestrator-node-2` | `9090` | `9091` | gRPC | Public | Secondary cluster node gRPC |
| `redis` | `6379` | `6379` | TCP | Host / Bridge | Cluster coordination, Pub/Sub, distributed locks |

- Containers run on the private bridge network `orchestrator-network`.
- The application connects to Redis via internal hostname `redis:6379`.
- The application runs inside the container as the non-root user `orchestrator` (UID 999).

---

## 9. Troubleshooting and Known Limitations

### 1. Java 21 Preview Features
The orchestrator leverages Java 21 Preview Features (Structured Concurrency via `StructuredTaskScope`). The `Dockerfile` and `docker-compose.yml` explicitly inject `--enable-preview` in both the compiler stage and runtime JVM parameters (`JAVA_TOOL_OPTIONS`). Do not remove `--enable-preview`.

### 2. Port Collisions on Host
If port `8080` or `6379` is already in use by a local process, override host ports in `.env`:
```ini
HTTP_PORT=18080
REDIS_PORT_HOST=16379
```

### 3. Graceful Shutdown
The application entrypoint uses Docker's exec format (`ENTRYPOINT ["java", ...]`). This ensures the JVM process is PID 1 and receives `SIGTERM` signals directly from Docker, allowing in-flight virtual thread tasks to complete before termination.
