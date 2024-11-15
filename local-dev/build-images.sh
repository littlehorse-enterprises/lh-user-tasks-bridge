#!/bin/sh

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_DIR=$(cd "$CONTEXT_DIR/.." && pwd)
UI_DIR=$(cd "$WORKSPACE_DIR/lh-user-tasks/ui" && pwd)

if [ ! -d "${UI_DIR}" ]; then
  echo "Please clone the UI"
  exit 1
fi

cd "${CONTEXT_DIR}"

echo "Building lh-user-tasks-api"
./gradlew api:build
docker build -t littlehorse/lh-user-tasks-api:latest .

echo "Building lh-user-tasks-ui"
"${UI_DIR}/local-dev/build-image.sh"

echo "Building lh-user-tasks-standalone"
rm -rf ./.next
cp -r "${UI_DIR}/.next" ./.next
docker build -t littlehorse/lh-user-tasks-standalone:latest -f ./standalone/Dockerfile .
