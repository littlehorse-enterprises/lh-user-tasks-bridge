# LittleHorse (LH) Overview

LH is a distributed workflow engine — described as "a distributed JVM" — that lets developers program distributed systems composed of microservices, APIs, and people with familiar programming-language constructs (variables, conditionals, threads, exception handlers, interrupts), but built for a distributed environment.

Source: [littlehorse.io](https://littlehorse.io/) and [littlehorse.io/docs](https://littlehorse.io/docs/)

## Platform Components

| Component | Description |
|---|---|
| **LH Kernel** | The core open-source durable workflow engine. Exposes a gRPC API, backed by Apache Kafka and RocksDB. Licensed under SSPL. |
| **Pony ID** | OIDC-based identity solution (User Tasks Bridge + LH IDP on Keycloak) for authorizing human workflow participants. |
| **LH Connect** | API Gateway + Apache Kafka connectors for event-driven workflow triggering. |
| **LH Runtime** | Quarkus-based framework for building applications against the Kernel, Connect, and Gallop Streams. |
| **StreamSense** | Real-time event processing and analytics layer built on Gallop Streams (Kafka Streams + ApiCurio). The WFD is part of this. |

Components are a-la-carte — you can use the Kernel alone or combine it with Connect, Pony ID, and StreamSense.

## Core Architecture

```
┌─────────────┐    gRPC     ┌──────────────────┐     Kafka      ┌─────────────┐
│  SDK Client  │◄──────────►│   LH Kernel      │──────────────►│ Output Topic │
│  (Java/Go/   │            │  (JVM Server)    │               │ (per Tenant) │
│   Py/C#)     │            └──────────────────┘               └─────────────┘
└─────────────┘                     │
       │                            │  Task Queue (virtual)
       │                            ▼
       │                   ┌──────────────────┐
       └──────────────────►│  Task Workers     │
                           │  (your code)      │
                           └──────────────────┘
```

- The **LH Kernel** is a cluster of JVM instances exposing a unified gRPC API.
- Internally uses **Apache Kafka** for command log, changelog, and output topics.
- Uses **RocksDB** for local state storage.
- **Task Workers** are long-lived processes that open gRPC bi-directional streaming connections to the Kernel — they poll for work, they don't receive inbound connections.
- SDKs available for **Java, Go, Python, C#** (plus Kotlin via Java SDK).

## Data Model

LH has two categories of data:

1. **Metadata** (blueprints/templates) — small, relatively static, global to a cluster.
2. **Execution Data** (things that happen) — large, partitioned, constantly changing.

### Metadata Objects

| Object | Description |
|---|---|
| **WfSpec** | Workflow specification — a directed graph of `Node`s and `Edge`s defining process control flow. Composed of one or more `ThreadSpec`s. |
| **TaskDef** | Task definition — analogous to a method signature. Defines inputs/outputs for computer-executed work. |
| **ExternalEventDef** | Defines a type of event that can arrive from outside a workflow (callbacks, webhooks). |
| **WorkflowEventDef** | Defines a type of event that a workflow can *throw* (emit) to the outside world. |
| **UserTaskDef** | Defines a human-executed task — schema of form fields a person must complete. |
| **StructDef** | Defines a complex data type (like a Go struct) for use as a variable type. |

### Execution Objects

| Object | Description |
|---|---|
| **WfRun** | A running instance of a WfSpec. Contains one or more `ThreadRun`s. Lifecycle mirrors the entrypoint ThreadRun. |
| **ThreadRun** | Executes one Node at a time within a WfRun. Multiple ThreadRuns enable parallel execution. |
| **NodeRun** | Tracks execution of a single Node within a ThreadRun. |
| **TaskRun** | An instance of a TaskDef being executed by a Task Worker. |
| **UserTaskRun** | An instance of a UserTaskDef assigned to a user/group. Statuses: UNASSIGNED → ASSIGNED → DONE/CANCELLED. |
| **ExternalEvent** | An instance of an event posted to a specific WfRun. Contains an optional `VariableValue` payload. |
| **WorkflowEvent** | An event thrown by a WfRun (the inverse of ExternalEvent). |
| **Variable** | A named value within a ThreadRun. Types: `STR`, `INT`, `DOUBLE`, `BOOL`, `JSON_OBJ`, `JSON_ARR`. 250KB size limit. |

### Cluster-Level Objects

| Object | Description |
|---|---|
| **Tenant** | Logically isolated environment. All workflow resources belong to a Tenant. Different Tenants can have identically-named resources. |
| **Principal** | Client identity (human or machine). Authenticated via mTLS or OAuth. Has global and/or per-Tenant ACLs. |

## WfSpec Structure

A WfSpec is a directed graph:

- **ThreadSpec** — a single thread of execution. A WfSpec has one or more ThreadSpecs.
- **Node** — a computational step within a ThreadSpec. Types include:
  - **Task Node** — dispatches a TaskRun to a Task Worker
  - **External Event Node** — blocks until an ExternalEvent arrives
  - **User Task Node** — creates a UserTaskRun and blocks until a human completes it
  - **Throw Event Node** — throws a WorkflowEvent
  - **Start/Wait Thread Nodes** — for spawning and joining child threads
- **Edge** — connects Nodes, with optional conditions for branching

### Workflow Programming Constructs

| Construct | How it maps |
|---|---|
| Variables | `wf.declareStr()`, `wf.declareInt()`, etc. Can be `.required()`, `.searchable()`, `.public()` |
| Conditionals | If/else branching on variable values |
| Loops | While-style loops with conditions |
| Threads | `spawnThread()` for parallel execution, `waitForThreads()` to join |
| Exception handling | `handleException()` (business failures), `handleError()` (technical failures), `handleAnyFailure()` |
| Interrupts | `handleInterrupt()` — halts a ThreadRun when a specific ExternalEvent arrives, runs handler as child thread |
| Variable mutation | `mutate()` to assign/modify variables between steps |

## Failure Model

LH distinguishes two failure categories:

- **EXCEPTION** — business-level failures (e.g., "insufficient-funds", "order-rejected"). Thrown explicitly via `wf.fail()`.
- **ERROR** — technical failures (e.g., TIMEOUT, network issues, uncaught Task Worker exceptions).

Handlers are attached to `NodeOutput`s:
- `handleException("name", handler)` — catches a specific business exception
- `handleError(ErrorType, handler)` — catches a specific technical error
- `handleAnyFailure(handler)` — catches anything

First matching handler executes. If a handler itself fails, subsequent steps don't run unless caught by another handler.

## Task Worker Model

1. Developer writes a function annotated with `@LHTaskMethod` (Java) or equivalent.
2. Task Worker process connects to the Kernel via gRPC bi-directional streaming.
3. When a WfRun reaches a Task Node, the Kernel places a `ScheduledTask` on a virtual queue.
4. The ThreadRun blocks. A Task Worker picks up the task, executes it, reports back.
5. The ThreadRun unblocks and advances.

Task Workers can run anywhere (bare metal, Docker, K8s, ECS). Multiple TaskDefs can be served by a single process.

## External Events

External Events are callbacks/webhooks that signal something to a WfRun:

- Register an `ExternalEventDef` first.
- In a WfSpec, use `waitForEvent("event-name")` to block until the event arrives.
- Post events via `PutExternalEvent` RPC with a `wf_run_id`, `external_event_def_id`, and optional payload.
- Supports idempotency via a `guid` field.

**Interrupts** are a special use of External Events: when a registered event arrives, the current ThreadRun is halted and a handler runs as a child thread with access to the parent's variables. An ExternalEventDef used for interrupts cannot also be used for `waitForEvent` nodes.

## User Tasks

User Tasks bring humans into workflows:

- Define a `UserTaskDef` with form fields.
- In a WfSpec, a `UserTaskNode` creates a `UserTaskRun` and blocks.
- UserTaskRuns are assigned to a `user_id` and/or `user_group` (plain strings, not validated against an IdP).
- Lifecycle: UNASSIGNED → ASSIGNED → DONE/CANCELLED.
- Supports automatic reassignment on timeout, reminder tasks, and lifecycle hooks.
- Completed via `CompleteUserTaskRun` RPC.

## Multi-Tenancy & Security

- Every resource belongs to a **Tenant** (default: `default`).
- **Principals** authenticate via mTLS (cert Common Name) or OAuth (User/Client ID from token).
- ACLs are checked per request — global or per-Tenant.
- Default setup is fully permissive (anonymous Principal with admin rights) — must be hardened for production.
- Clients specify Tenant via `LHC_TENANT_ID` config.

## Output Topic

The Kernel produces real-time updates to per-Tenant Kafka topics (this is the input to the WFD):

- **Execution Output** (`{cluster}_{tenant}_execution-output`) — multi-partition, `OutputTopicRecord` protobuf messages containing TaskRun, WfRun, UserTaskRun, Variable, ExternalEvent, or WorkflowEvent updates.
- **Metadata Output** (`{cluster}_{tenant}_metadata-output`) — single-partition, compacted, `MetadataOutputTopicRecord` with WfSpec, TaskDef, ExternalEventDef, WorkflowEventDef, UserTaskDef snapshots.
- Configurable per Tenant (`ALL_ENTITY_EVENTS` / `NO_ENTITY_EVENTS`) with per-metadata overrides.

See `docs/context/output-topic.md` for the full Output Topic design.

## gRPC API

The Kernel exposes its entire API as gRPC (defined in `service.proto`). Key operations:

- **Metadata management** — Put/Get WfSpec, TaskDef, ExternalEventDef, etc.
- **Running workflows** — `RunWf` to create a WfRun
- **Posting events** — `PutExternalEvent`
- **User tasks** — `CompleteUserTaskRun`, `AssignUserTaskRun`
- **Search** — query WfRuns, TaskRuns, etc. by variable values and status
- **Task dispatch** — bi-directional streaming for Task Workers (`PollTask` / `ReportTaskRun`)

SDKs (Java, Go, Python, C#) are convenience wrappers around this gRPC API. The CLI tool `lhctl` also uses it.

## Variable Types Reference

| Type | Description |
|---|---|
| `STR` | String |
| `INT` | Integer |
| `DOUBLE` | Floating-point |
| `BOOL` | Boolean |
| `JSON_OBJ` | JSON object (supports `jsonPath()` access) |
| `JSON_ARR` | JSON array (supports `jsonPath()` access) |

Variables can be:
- **`.required()`** — must be provided as input when running the WfSpec
- **`.searchable()`** — enables querying WfRuns by this variable's value
- **`.public()`** — part of the WfSpec's public API; eligible for Output Topic by default
