# LittleHorse Workflow Diffuser

The **Workflow Diffuser** (WFD) is a Kafka Streams application that consumes the LittleHorse Output Topic and transforms raw execution and metadata records into queryable, tenant-scoped **Streamlets** — one per WfSpec, ExternalEventDef, UserTaskDef, and WorkflowEventDef. This enables downstream analytics, cascading workflows, and real-time business insights from LH workflow data.

Part of the [LH StreamSense](https://github.com/littlehorse-enterprises) product.

## Architecture

![Kafka Streams Topology Design](docs/design/topology-design/LittleHorse%20Workflow%20Diffuser%20v0.2.0.excalidraw.png)

## Build & Run

### Prerequisites

- Java 25
- Docker (for integration tests and local development)

### Build

```bash
./gradlew build          # Build + unit tests
./gradlew test           # Unit tests only
./gradlew clean build    # Clean rebuild
```

### Integration Tests

Integration tests use Testcontainers and require Docker:

```bash
./gradlew integrationTest
```

### Run Locally

Start Kafka and Apicurio:

```bash
docker compose up -d
```

Run the application:

```bash
./gradlew run
```

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

docs/
  requirements/   # Product requirements
  design/         # Design docs (main design doc, topology, dynamic routing)
  context/        # Reference material (LH glossary, output topic spec, data analysis)
  utils/          # Tool references (lhctl)
```

See [docs/design/workflow-diffuser.md](docs/design/workflow-diffuser.md) for the full design document.
