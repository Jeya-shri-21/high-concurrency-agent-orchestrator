# Member 4: REST API Agent Registration & GitHub Integration

## Overview

This module implements the **REST API Agent Registration Layer** for the **High-Concurrency Autonomous Agent Orchestrator** at **Zaalima Development Pvt. Ltd.**

As **Member 4**, the primary objective is to expose clean, robust, and validated HTTP/REST endpoints for autonomous agent registration and discovery, while integrating seamlessly with Member 3's gRPC service and Member 2's Virtual Thread task engine.

---

## Architecture & Integration

```
                 +--------------------------------------------+
                 |       External Clients (REST & gRPC)       |
                 +--------------------+-----------------------+
                                      |
                     REST (HTTP)      |      gRPC (Proto)
                          v           |           v
           +--------------------------+-----------------------+
           | AgentRegistration        | AgentGrpcService      |
           | Controller (/api/agents) | (Member 3)            |
           +--------------------------+-----------------------+
                          \           /
                           \         /
                            v       v
           +--------------------------------------------------+
           |        AgentRegistryService (Single Source)      |
           |   * Concurrent in-memory agent registry          |
           |   * Validation & Duplicate identity check        |
           |   * Bridged via gRPC interface                   |
           +--------------------------+-----------------------+
                                      |
                                      v
           +--------------------------------------------------+
           |       TaskExecutionService (Member 2)            |
           |   * Java 21 Virtual Thread Per Task Executor     |
           |   * Concurrent task scheduling & tracking        |
           +--------------------------------------------------+
```

### Key Architectural Decisions

1. **Non-Invasive Integration with Member 3 (gRPC):**
   * Instead of duplicating the agent registry or modifying Member 3's code, `AgentRegistryService` interacts with `AgentGrpcService` through its public service interface (`registerAgent`, `isAgentRegistered`, `getRegisteredAgents`).
   * Agents registered via REST are immediately recognized by gRPC task submission routines (`sendTask`), and agents registered via gRPC are instantly visible in REST queries.
2. **Robust Input Validation:**
   * Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Pattern`) ensures that malformed or empty payloads are rejected early with informative error messages before hitting business logic.
3. **Structured Global Error Handling:**
   * Standardized JSON error response format via `GlobalExceptionHandler` (`@RestControllerAdvice`).
   * Eliminates unhandled exceptions or stack trace leaks, preventing server crashes.
4. **Clean Concurrency:**
   * Leverages thread-safe collections (`ConcurrentHashMap`) ensuring high-throughput concurrent registrations without race conditions.

---

## Technologies Used

* **Java 21**
* **Spring Boot 3.5.6** (`spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`)
* **gRPC & Protocol Buffers** (`grpc-server-spring-boot-starter`, `grpc-protobuf`, `grpc-stub`)
* **JUnit 5 & Mockito** (for WebMvc and Service unit/integration testing)
* **Maven** (build and dependency management)

---

## REST Endpoints Specification

Base URL: `http://localhost:8080/api/agents`

### 1. Register a New Agent

Registers an autonomous agent with the orchestrator.

* **Method:** `POST`
* **Path:** `/api/agents`
* **Headers:** `Content-Type: application/json`

#### Request Body Example
```json
{
  "agentId": "agent-alpha-01",
  "agentType": "DATA_PROCESSOR"
}
```

#### Field Validation Rules
| Field | Type | Validation Rules | Description |
| :--- | :--- | :--- | :--- |
| `agentId` | String | Required, Not Blank, 2–50 characters, alphanumeric with `_` or `-` | Unique identifier for the agent |
| `agentType` | String | Required, Not Blank, 2–50 characters | Functional role/category of the agent |

#### Responses

* **`201 Created`** (Success)
  * **Headers:** `Location: /api/agents/agent-alpha-01`
  * **Body:**
    ```json
    {
      "success": true,
      "message": "Agent registered successfully: agent-alpha-01",
      "agentId": "agent-alpha-01",
      "agentType": "DATA_PROCESSOR",
      "status": "ACTIVE",
      "registeredAt": 1728924873123
    }
    ```

* **`400 Bad Request`** (Validation Failure)
  * **Body:**
    ```json
    {
      "status": 400,
      "error": "Bad Request",
      "message": "Validation failed for one or more fields",
      "timestamp": 1728924873150,
      "validationErrors": {
        "agentId": "agentId must contain only alphanumeric characters, underscores, or hyphens",
        "agentType": "agentType is required and must not be blank"
      }
    }
    ```

* **`409 Conflict`** (Duplicate Agent ID)
  * **Body:**
    ```json
    {
      "status": 409,
      "error": "Conflict",
      "message": "Agent with ID 'agent-alpha-01' is already registered",
      "timestamp": 1728924873180
    }
    ```

---

### 2. Retrieve an Agent by ID

Fetches details of a registered agent.

* **Method:** `GET`
* **Path:** `/api/agents/{agentId}`

#### Responses

* **`200 OK`** (Agent Found)
  ```json
  {
    "agentId": "agent-alpha-01",
    "agentType": "DATA_PROCESSOR",
    "status": "ACTIVE",
    "registeredAt": 1728924873123
  }
  ```

* **`404 Not Found`** (Agent Not Registered)
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "Agent not found with ID: unknown-agent",
    "timestamp": 1728924873200
  }
  ```

---

### 3. List All Registered Agents

Retrieves all agents currently registered across both REST and gRPC interfaces.

* **Method:** `GET`
* **Path:** `/api/agents`

#### Responses

* **`200 OK`**
  ```json
  [
    {
      "agentId": "agent-alpha-01",
      "agentType": "DATA_PROCESSOR",
      "status": "ACTIVE",
      "registeredAt": 1728924873123
    },
    {
      "agentId": "agent-live-02",
      "agentType": "CALCULATOR",
      "status": "ACTIVE",
      "registeredAt": 1728924873500
    }
  ]
  ```

---

### 4. Check Agent Registration Status

Fast status check returning a boolean flag.

* **Method:** `GET`
* **Path:** `/api/agents/{agentId}/status`

#### Responses

* **`200 OK`**
  ```json
  {
    "agentId": "agent-alpha-01",
    "registered": true
  }
  ```

---

## Testing & Verification

### Executed Test Suite
The Member 4 test suite includes unit and web integration tests:

1. **`AgentRegistrationControllerTest` (9 tests):**
   * `POST /api/agents` returns `201 Created` with valid body and `Location` header.
   * `POST /api/agents` returns `400 Bad Request` on blank `agentId`.
   * `POST /api/agents` returns `400 Bad Request` on blank `agentType`.
   * `POST /api/agents` returns `400 Bad Request` on invalid regex characters.
   * `POST /api/agents` returns `409 Conflict` on duplicate registration.
   * `GET /api/agents/{agentId}` returns `200 OK` when agent exists.
   * `GET /api/agents/{agentId}` returns `404 Not Found` when agent does not exist.
   * `GET /api/agents` returns `200 OK` with full agent list.
   * `GET /api/agents/{agentId}/status` returns boolean status.
2. **`AgentRegistryServiceTest` (7 tests):**
   * Successful registration and delegation to Member 3's gRPC service.
   * Duplicate agent rejection and exception propagation.
   * gRPC error response handling.
   * In-memory cache and metadata preservation.
   * Lookup and cross-protocol visibility.

### How to Run Tests
From Git Bash or terminal:
```bash
./mvnw test
```
All **28 tests** across all modules (REST, gRPC, and Virtual Thread execution) pass with 0 failures and 0 errors.

---

## Member 4 Contribution Checklist

* [x] REST endpoint for registering an agent (`POST /api/agents`).
* [x] Request validation with Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Pattern`).
* [x] Reused existing gRPC service interface without rewriting Member 3's module.
* [x] Meaningful HTTP status codes (`201 Created`, `400 Bad Request`, `404 Not Found`, `409 Conflict`).
* [x] Duplicate registration detection and conflict response.
* [x] Graceful failure handling via `@RestControllerAdvice` (`GlobalExceptionHandler`).
* [x] Separation of REST transport layer from business logic.
* [x] Comprehensive automated tests (MockMvc + Mockito).
* [x] API and workflow documentation (`MEMBER4-README.md` and `GIT-WORKFLOW-GUIDE.md`).
