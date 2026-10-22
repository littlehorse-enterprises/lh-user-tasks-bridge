# AGENTS.md - LH Workflow Diffuser (WFD)

## Imported module context

This source now lives in the Business Console repository under `diffuser/`. The combined backend embeds Kafka Streams in its Spring lifecycle and exposes health/metrics through Actuator; the standalone main remains available for development. Diffusion is opt-in through `LHBC_DIFFUSER_ENABLED=true` and `LH_CONFIG_FILES` YAML. See `../docs/diffuser-integration.md` for configuration and the original history import.

Use Java 21 and the repository-root Gradle wrapper. Run `./gradlew build :diffuser:installDist` and `./gradlew :diffuser:integrationTest` from the repository root. The imported code is implemented; the earlier “implementation pending” status below is historical. Current WfRun output uses `columns`; dynamic topic names are `{cluster}_{tenant}_streamlet_{workflow|usertask|externalevent|workflowevent}-{name}`. The standalone defaults resource is `diffuser-defaults.yaml`, and standalone logging is `diffuser-logback.xml`, to avoid collisions with the embedding application. The client version comes from the shared `littlehorseVersion` property, independently of the project release version. These current source facts supersede older examples below.

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

- **Language:** Java (JDK: 21)
- **Build:** Gradle
- **Streaming:** Apache Kafka Streams
- **Schema Registry:** Apache ApiCurio (JSON format initially)
- **Frameworks:** SmallRye Config
- **Serialization:** Protocol Buffers (input), plain JSON, or JSON with ApiCurio Serdes (output)

## Project Status

Implemented streaming component; business-console analytics and optional Saddle configuration-provider features remain separate roadmap work.

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

- `docs/requirements/workflow-diffuser-requirements.md` - Detailed requirements spec
- `docs/design/workflow-diffuser.md` - **Base design document** — references all sub-docs below
  - `docs/design/topology-design/topology-design.md` - Kafka Streams topology design
  - `docs/design/details/dynamic-topic-routing.md` - Dynamic topic routing design
- `docs/context/` - Reference material (LH glossary, output topic spec, multitenancy, data analysis)

## Available Tools

- **`lhctl`** — the LH CLI (v0.16.0) is installed locally. Use it to inspect and interact with the LH Kernel: get/search/list resources, run workflows, post events, etc. See `docs/utils/lhctl.md` for full command reference.

## Code Conventions

- Follow existing LH Java conventions.
- Design for configurability - new tenants and *specs via config, not code changes.
- **Design docs**: `docs/design/workflow-diffuser.md` is the base design document. All other design docs go in subfolders of `docs/design/` and must be referenced from the base doc.

## Git Commits

- Use Conventional Commits, with a mandatory scope: `<type>(<scope>): <description>`.
- Use a lowercase Conventional Commit type and an imperative, concise description; do not end the subject with a period. The description must summarize the overall feature or fix, not enumerate each individual change.
- Pull request titles follow the same Conventional Commit convention, including a mandatory scope.
- Add a `Co-authored-by` trailer when appropriate; it is optional.
- Append an attribution footer identifying the model used for the commit, separated from the subject/body by a blank line. For example:

  ```text
  Assisted-by: Claude Opus 4.8
  ```

  Substitute the active model name and version for each commit. For example:

  ```text
  Assisted-by: Codex GPT-5.6
  ```

  ```text
  Assisted-by: Claude Sonnet 4.5
  ```
