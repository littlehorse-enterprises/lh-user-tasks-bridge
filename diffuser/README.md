# LittleHorse Workflow Diffuser

The **Workflow Diffuser** (WFD) is a Kafka Streams application that consumes the LittleHorse Output Topic and transforms raw execution and metadata records into queryable, tenant-scoped **Streamlets** — one per WfSpec, ExternalEventDef, UserTaskDef, and WorkflowEventDef. This enables downstream analytics, cascading workflows, and real-time business insights from LH workflow data.

Part of the [LH StreamSense](https://github.com/littlehorse-enterprises) product.

## Architecture

![Kafka Streams Topology Design](docs/design/topology-design/LittleHorse%20Workflow%20Diffuser%20v0.3.0.excalidraw.png)

## Build & Run

### Prerequisites

- Java 25
- Docker (for integration tests and local development)
- [pre-commit](https://pre-commit.com/)

Install the repository hooks after cloning:

```bash
pre-commit install
```

The hooks check file hygiene and shell scripts, validate Conventional Commit messages, and run
the unit tests, integration tests, and application distribution build before each commit.

### Build

```bash
./gradlew build          # Build + unit tests
./gradlew test           # Unit tests only
./gradlew clean build    # Clean rebuild
./gradlew installDist    # Create distribution for Docker/runtime packaging
```

### Build Docker Image Locally

```bash
./local-dev/build.sh
```

By default this builds `littlehorse/lh-workflow-diffuser:latest`. Override with `IMAGE_NAME=...` if needed.

### Local Development Environment

The repository-level Docker Compose environment runs Kafka, LittleHorse, Apicurio, and their UIs.
Its LittleHorse services use the version from `gradle.properties`.

```bash
./gradlew dockerComposeUp
./examples/setup.sh
```

The setup script provisions the example-specific Kafka topic and Apicurio schemas. Stop the
environment and remove its volumes with:

```bash
./gradlew dockerComposeDown
```

### Integration Tests

Integration tests use Testcontainers and require Docker:

```bash
./gradlew integrationTest
```

### Run Locally

```bash
./gradlew run
```

### HTTP Endpoints

The application exposes health and metrics endpoints on port **8080** (configurable via `app.server.port`).

| Endpoint | Description | Response |
|---|---|---|
| `GET /health` | Combined liveness + readiness probe | `{"status":"UP","kafkaStreams":{"state":"RUNNING"}}` |
| `GET /health/liveness` | Liveness probe (Kafka Streams not in ERROR/NOT_RUNNING) | Same format |
| `GET /health/readiness` | Readiness probe (Kafka Streams RUNNING or REBALANCING) | Same format |
| `GET /metrics` | Prometheus metrics scrape endpoint | OpenMetrics text format |

Health endpoints return **200** when healthy, **503** when unhealthy.

### Configuration

Configuration is loaded from `src/main/resources/application.yaml` with overrides from:

1. `LH_` prefixed environment variables (highest priority)
2. External YAML files via `LH_CONFIG_FILES` env var
3. Classpath `application.yaml` / `application.yml`

Example environment variable override:

```bash
export LH_KAFKA_STREAMS_BOOTSTRAP_SERVERS=kafka:9092
export LH_APP_SERVER_PORT=9090
```

#### Log Levels

Log levels are configured per-package via `logging.level.*` properties in `application.yaml`:

```yaml
logging:
  level:
    io.littlehorse.streamsense: DEBUG
    org.apache.kafka: WARN
```

These can also be set via environment variables:

```bash
export LH_LOGGING_LEVEL_IO_LITTLEHORSE_STREAMSENSE=DEBUG
```

## Examples

The [`examples/`](examples/) directory contains LH workflow examples and their provisioning script.
Each example owns its setup for resources such as topics and schemas, while the shared Docker Compose
environment remains at the repository root. See the [examples README](examples/README.md) for setup instructions.

## Repo Structure

```
src/main/java/io/littlehorse/streamsense/diffuser/
├── WorkflowDiffuserApp.java          # Main entry point
├── config/                            # SmallRye Config + LH_ env vars
├── server/                            # HTTP health + metrics endpoints
├── topology/                          # Kafka Streams topology + processors
│   └── transformer/                   # Record transformers (protobuf → JSON)
└── serde/                             # Custom serdes (Apicurio + Protobuf)

src/test/java/io/littlehorse/streamsense/diffuser/
├── config/                            # Config unit tests
├── topology/                          # Topology + processor unit tests
│   └── transformer/                   # Transformer unit tests
└── integration/                       # End-to-end tests (Testcontainers)

src/test/resources/
└── logback-test.xml                   # DEBUG-level logging for tests

examples/
├── setup.sh                           # Provisions example topics and Apicurio schemas
├── lh-examples/                       # Gradle subproject: LH workflow examples
└── schemas/                           # JSON Schema files for Apicurio registration

docker-compose.yml                     # Shared local environment (LH Server, Kafka, Apicurio)

docs/
  requirements/   # Product requirements
  design/         # Design docs (main design doc, topology, dynamic routing)
  context/        # Reference material (LH glossary, output topic spec, data analysis)
  utils/          # Tool references (lhctl)
```

See [docs/design/workflow-diffuser.md](docs/design/workflow-diffuser.md) for the full design document.
