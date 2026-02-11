# Examples

Local development environment for the Workflow Diffuser.

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

### 3. Start the Workflow Diffuser

From the project root:

```bash
LH_CONFIG_FILES=examples/application.yaml ./gradlew run
```

This loads the example tenant/spec configuration from `examples/application.yaml`, which overrides
the defaults in `src/main/resources/application.yaml`. Debug logging for `io.littlehorse.streamsense`
is configured in `examples/application.yaml` via the `logging.level` section.

Log levels can also be set via environment variables, e.g.:
`LH_LOGGING_LEVEL_IO_LITTLEHORSE_STREAMSENSE=DEBUG`

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
