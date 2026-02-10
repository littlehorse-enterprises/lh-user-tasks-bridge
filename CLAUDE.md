# CLAUDE.md - LH Workflow Diffuser (WFD)

## Terminology

- **LH** = LittleHorse
- **WFD** = Workflow Diffuser (this project)

## Project Overview

The **WFD** is a core component of the LH StreamSense product. It reads the LH Output Topic (a Kafka topic containing real-time updates from workflow execution) and "diffuses" it into distinct entity-style `Streamlet`s for different metadata types:

- **WfSpec Streamlets** - workflow runs as queryable entities with variables as fields
- **ExternalEventDef Streamlets** - external event payloads
- **UserTaskDef Streamlets** - user task data
- **WorkflowEventDef Streamlets** - workflow event payloads

This enables downstream analytics, cascading workflows, and real-time business insights from LH workflow data.

## Tech Stack

- **Language:** Java (JDK: 25)
- **Build:** Gradle
- **Streaming:** Apache Kafka Streams
- **Schema Registry:** Apache ApiCurio (JSON format initially)
- **Frameworks:** SmallRye Config
- **Serialization:** Protocol Buffers (input), plain JSON, or JSON with ApiCurio Serdes (output)

## Project Status

Early stage - repository initialized, requirements and design docs complete, implementation pending.

## Build & Run Commands

```bash
./gradlew build          # Build the project
./gradlew test           # Run tests
./gradlew clean build    # Clean and rebuild
```

## Architecture

### Input
- Two Kafka topics per Tenant, named `{LHS_CLUSTER_ID}_{tenant}_execution` and `{LHS_CLUSTER_ID}_{tenant}_metadata`
- **Execution topic** (multi-partition): `OutputTopicRecord` protobuf messages with a `oneof` payload containing TaskRun, WfRun, UserTaskRun, Variable, ExternalEvent, or WorkflowEvent
- **Metadata topic** (single-partition, compacted): `MetadataOutputTopicRecord` with WfSpec, TaskDef, ExternalEventDef, WorkflowEventDef, UserTaskDef — used as a global store for joins
- Recording level is configurable per Tenant (`ALL_ENTITY_EVENTS` / `NO_ENTITY_EVENTS`) with per-metadata overrides

### Processing
- Kafka Streams topology filters and enriches raw records
- Groups by WfSpec, ExternalEventDef, UserTaskDef, WorkflowEventDef
- Enriches with business meaning from WfSpec metadata

### Output
- Produces to multiple Kafka topics (one per Streamlet)
- Output format for WfSpec Streamlets:
```json
{
  "id": "<wfRunId>",
  "status": "<status>",
  "variables": {
    "user-id": "obi-wan",
    "order-status": "SHIPPING",
    "item-id": "lightsaber"
  },
  "createdAt": "<timestamp>"
}
```
- Schemas registered in ApiCurio

### Multi-Tenancy
- Single WFD cluster processes all configured WfSpecs across tenants
- Topic/consumer group naming: `{tenant-id}__<streamlet-name>`
- Schema naming: `{tenant-id}__<schema-name>` in ApiCurio
- Per-tenant Kafka users with ACL restrictions
- Configuration-driven (rolling restart for config updates, no new pods needed)

## Key Domain Concepts

- **WfSpec** - workflow specification (blueprint)
- **WfRun** - workflow run instance
- **TaskDef** / **TaskRun** - task definition and execution
- **ExternalEventDef** / **ExternalEvent** - events coming into a workflow
- **WorkflowEventDef** / **WorkflowEvent** - events emitted by a workflow
- **UserTaskDef** / **UserTaskRun** - human-executed tasks
- **Streamlet** - a Kafka topic with an associated Schema (Saddle concept)
- **Schema** - references ApiCurio schemas for key and value of a Streamlet
- **Tenant** - logically isolated environment within LH
- **Getable** - API resource retrievable from the LH Server (metadata or execution data)
- **Output Topic** - Kafka topic the LH Server produces to with real-time updates

## Design References

Design documents are in the `docs/` directory:
- `docs/requirements/workflow-diffuser-requirements.md` - Detailed requirements spec for the WFD
- `docs/design/workflow-diffuser.md` - WFD design document (architecture, config, schemas, multi-tenancy)
- `docs/design/topology-design/topology-design.md` - Kafka Streams topology design
- `docs/design/dynamic-topic-routing.md` - Dynamic topic routing design
- `docs/context/saddle-glossary.md` - LH glossary and platform architecture
- `docs/context/saddle-multitenancy.md` - Multi-tenancy strategy and conventions
- `docs/context/output-topic.md` - Output Topic design: topic structure, protobuf schemas, partitioning, and per-tenant configuration
- `docs/context/littlehorse.md` - LittleHorse platform overview
- `docs/context/metadata-topic-analysis.md` - Schema derivation analysis from live metadata samples
- `docs/context/execution-topic-analysis.md` - Execution record sequences and Streamlet transformation analysis

## Available Tools

- **`lhctl`** — the LH CLI (v0.16.0) is installed locally. Use it to inspect and interact with the LH Kernel: get/search/list resources, run workflows, post events, etc. See `docs/utils/lhctl.md` for full command reference.

## Code Conventions

- Follow existing LH Java conventions
- Design for configurability - new tenants and *specs via config, not code changes

## Git Commits

- **No Co-Authored-By trailers**: Do not add "Co-Authored-By" trailers to commit messages.
