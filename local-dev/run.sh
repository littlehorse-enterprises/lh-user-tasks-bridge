#!/bin/sh

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_DIR=$(cd "$CONTEXT_DIR/.." && pwd)
UI_DIR=$(cd "$WORKSPACE_DIR/lh-user-tasks" && pwd)

if [ ! -d "${UI_DIR}" ]; then
  echo "Please clone the UI"
  exit 1
fi

cd "${CONTEXT_DIR}"

./gradlew api:build
"${UI_DIR}/local-dev/build.sh"
docker compose up -d --build
