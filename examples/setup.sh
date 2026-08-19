#!/usr/bin/env bash
#
# Provisions the local Workflow Diffuser example after the shared Docker Compose
# environment is running. It creates the example-specific Kafka topic and registers
# the example streamlet schemas in Apicurio.
#
# Usage:
#   ./examples/setup.sh
#   REGISTRY_URL=http://host:8085/apis/registry/v3 ./examples/setup.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REGISTRY_URL="${REGISTRY_URL:-http://localhost:8085/apis/registry/v3}"
SCHEMAS_DIR="$SCRIPT_DIR/schemas"
LH_VERSION="${LH_VERSION:-$(sed -n 's/^version=//p' "$REPO_ROOT/gradle.properties")}"
COMPOSE=(env "LH_VERSION=$LH_VERSION" docker compose --project-directory "$REPO_ROOT" -f "$REPO_ROOT/docker-compose.yml")
TOPIC="cluster1-wf-diffuser-global-metadata-changelog"

wait_for_kafka() {
  local attempt=0
  until "${COMPOSE[@]}" exec -T lh-kafka /bin/kafka-topics \
    --bootstrap-server=lh-kafka:29092 --list >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -eq 30 ]; then
      echo "ERROR: Kafka did not become ready. Start it with ./gradlew dockerComposeUp." >&2
      exit 1
    fi
    sleep 2
  done
}

wait_for_registry() {
  local attempt=0
  until curl --fail --silent --show-error "$REGISTRY_URL/system/info" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -eq 30 ]; then
      echo "ERROR: Apicurio did not become ready at $REGISTRY_URL." >&2
      exit 1
    fi
    sleep 2
  done
}

if [ ! -d "$SCHEMAS_DIR" ]; then
  echo "ERROR: schemas directory not found at $SCHEMAS_DIR" >&2
  exit 1
fi

wait_for_kafka
wait_for_registry

echo "Creating example Kafka topic..."
"${COMPOSE[@]}" exec -T lh-kafka /bin/kafka-topics \
  --bootstrap-server=lh-kafka:29092 \
  --create \
  --topic="$TOPIC" \
  --partitions=1 \
  --if-not-exists

echo "Registering schemas in $REGISTRY_URL..."
success=0
failure=0

for schema_file in "$SCHEMAS_DIR"/*.json; do
  [ -f "$schema_file" ] || continue

  artifact_id="$(basename "$schema_file" .json)"
  schema_content="$(python3 -c 'import json, pathlib, sys; print(json.dumps(pathlib.Path(sys.argv[1]).read_text()))' "$schema_file")"
  body="$(printf '{"artifactId":"%s","artifactType":"JSON","firstVersion":{"content":{"content":%s,"contentType":"application/json"}}}' "$artifact_id" "$schema_content")"

  if ! response="$(curl --silent --show-error --write-out $'\n%{http_code}' \
    --request POST \
    --header "Content-Type: application/json" \
    --data "$body" \
    "$REGISTRY_URL/groups/default/artifacts?ifExists=FIND_OR_CREATE_VERSION")"; then
    echo "  FAIL $artifact_id (connection error)" >&2
    failure=$((failure + 1))
    continue
  fi

  http_code="${response##*$'\n'}"
  response_body="${response%$'\n'*}"
  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    echo "  OK   $artifact_id (HTTP $http_code)"
    success=$((success + 1))
  else
    echo "  FAIL $artifact_id (HTTP $http_code): $response_body" >&2
    failure=$((failure + 1))
  fi
done

echo "Setup complete: $success schemas registered, $failure failures."

if [ "$failure" -gt 0 ]; then
  exit 1
fi
