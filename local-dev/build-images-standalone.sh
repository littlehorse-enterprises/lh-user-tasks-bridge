#!/bin/sh

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

cd "${CONTEXT_DIR}"

echo "Building lh-user-tasks-bridge-backend"
./gradlew backend:build
docker build -t littlehorse/lh-user-tasks-bridge-backend:latest .

echo "Building lh-user-tasks-bridge-console and lh-user-tasks-bridge-demo-workflow"
"${CONTEXT_DIR}/local-dev/build-images.sh"

echo "Building lh-user-tasks-bridge-standalone"

docker build -t littlehorse/lh-user-tasks-bridge-standalone:latest -f ./standalone/Dockerfile .
