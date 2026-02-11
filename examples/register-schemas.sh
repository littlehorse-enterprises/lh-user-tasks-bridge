#!/usr/bin/env bash
#
# Registers all JSON Schema files from examples/schemas/ into Apicurio Registry v3.
#
# Usage:
#   ./register-schemas.sh                              # uses default localhost URL
#   REGISTRY_URL=http://host:8085/apis/registry/v3 ./register-schemas.sh
#
set -euo pipefail

REGISTRY_URL="${REGISTRY_URL:-http://localhost:8085/apis/registry/v3}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCHEMAS_DIR="$SCRIPT_DIR/schemas"

if [ ! -d "$SCHEMAS_DIR" ]; then
  echo "ERROR: schemas directory not found at $SCHEMAS_DIR"
  exit 1
fi

echo "Registering schemas to $REGISTRY_URL ..."
echo

SUCCESS=0
FAILURE=0

for schema_file in "$SCHEMAS_DIR"/*.json; do
  [ -f "$schema_file" ] || continue

  artifact_id="$(basename "$schema_file" .json)"
  schema_content="$(cat "$schema_file")"

  body=$(cat <<EOF
{
  "artifactId": "$artifact_id",
  "artifactType": "JSON",
  "firstVersion": {
    "content": {
      "content": $(echo "$schema_content" | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))'),
      "contentType": "application/json"
    }
  }
}
EOF
)

  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$body" \
    "$REGISTRY_URL/groups/default/artifacts?ifExists=FIND_OR_CREATE_VERSION")

  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    echo "  OK  $artifact_id (HTTP $http_code)"
    SUCCESS=$((SUCCESS + 1))
  else
    echo "  FAIL $artifact_id (HTTP $http_code)"
    FAILURE=$((FAILURE + 1))
  fi
done

echo
echo "Done: $SUCCESS succeeded, $FAILURE failed."

if [ "$FAILURE" -gt 0 ]; then
  exit 1
fi
