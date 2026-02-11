# Examples

Local development environment for the Workflow Diffuser.

## Prerequisites

- Docker and Docker Compose
- JDK 25
- [`lhctl`](https://littlehorse.dev/docs/developer-guide/install) v0.16.0 (LittleHorse CLI)

## Quick Start

### 1. Start infrastructure

```bash
cd examples
docker compose up -d
```

### 2. Register schemas in Apicurio

```bash
./register-schemas.sh
```

This registers all JSON Schema files from `schemas/` into the Apicurio Registry. You can verify
at the Apicurio UI: http://localhost:8086

### 3. Create the `cars` tenant

```bash
lhctl put tenant cars --all-entity-events
```

### 4. Register WfSpecs and start task workers

From the project root:

```bash
./gradlew :examples:lh-examples:run
```

This registers all example WfSpecs, TaskDefs, ExternalEventDefs, WorkflowEventDefs,
and UserTaskDefs with the LH Server, then starts task workers so workflows can execute.
Requires a running LH Server (started by docker compose in step 1).

### 5. Start the Workflow Diffuser

From the project root:

```bash
LH_CONFIG_FILES=examples/application.yaml ./gradlew run
```

This loads the example tenant/spec configuration from `examples/application.yaml`, which overrides
the defaults in `src/main/resources/application.yaml`. Debug logging for `io.littlehorse.streamsense`
is configured in `examples/application.yaml` via the `logging.level` section.

Log levels can also be set via environment variables, e.g.:
`LH_LOGGING_LEVEL_IO_LITTLEHORSE_STREAMSENSE=DEBUG`

### 6. Run a workflow

```bash
lhctl run output-topic input-name R2D2
```

You can inspect the result with `lhctl get wfRun <wfRunId>` using the ID from the output.

For more ways to interact with each example (posting external events, running user tasks, etc.),
see the READMEs in the LittleHorse examples repo:

- [output-topic](https://github.com/littlehorse-enterprises/littlehorse/tree/master/examples/java/output-topic)
- [external-event](https://github.com/littlehorse-enterprises/littlehorse/tree/master/examples/java/external-event)
- [await-workflow-event](https://github.com/littlehorse-enterprises/littlehorse/tree/master/examples/java/await-workflow-event)
- [variables](https://github.com/littlehorse-enterprises/littlehorse/tree/master/examples/java/variables)
- [user-tasks](https://github.com/littlehorse-enterprises/littlehorse/tree/master/examples/java/user-tasks)
- [json](https://github.com/littlehorse-enterprises/littlehorse/tree/master/examples/java/json)

## Services

| Service           | URL                     |
|-------------------|-------------------------|
| Kafka             | `localhost:9092`        |
| Apicurio API      | http://localhost:8085   |
| Apicurio UI       | http://localhost:8086   |
| Apicurio Console  | http://localhost:8087   |

## Schema Files

The `schemas/` directory contains one JSON Schema file per streamlet topic. Each file is named
using the Apicurio TopicIdStrategy convention: `{topic}-value.json`.

To add schemas for new streamlets, create a new `.json` file in `schemas/` following the naming
pattern and re-run `./register-schemas.sh`.
