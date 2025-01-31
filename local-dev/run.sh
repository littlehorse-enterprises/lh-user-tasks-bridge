#!/bin/sh

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_DIR=$(cd "$CONTEXT_DIR/.." && pwd)
CONSOLE_DIR=$(cd "$WORKSPACE_DIR/lh-user-tasks-bridge" && pwd)

if [ ! -d "${CONSOLE_DIR}" ]; then
  echo "Please clone the Console"
  exit 1
fi

cd "${CONTEXT_DIR}"

./gradlew backend:build
# "${CONSOLE_DIR}/local-dev/build.sh"
docker compose up -d --build
