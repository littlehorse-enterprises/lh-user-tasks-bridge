#!/bin/sh

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

cd "${CONTEXT_DIR}"

./gradlew backend:build
"${CONTEXT_DIR}/local-dev/build.sh"
docker compose up -d --build
