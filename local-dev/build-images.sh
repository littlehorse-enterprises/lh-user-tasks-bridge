#!/bin/sh

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
WORKSPACE_DIR=$(cd "$CONTEXT_DIR/.." && pwd)
BRIDGE_DIR=$(cd "$WORKSPACE_DIR/lh-user-tasks-bridge" && pwd)

if [ ! -d "${BRIDGE_DIR}" ]; then
  echo "Please clone the Console"
  exit 1
fi

cd "${CONTEXT_DIR}"

echo "Building lh-user-tasks-bridge-backend"
./gradlew backend:build
docker build -t littlehorse/lh-user-tasks-bridge-backend:latest .

echo "Building lh-user-tasks-bridge-console and lh-user-tasks-bridge-demo-workflow"
"${BRIDGE_DIR}/local-dev/build-images.sh"

echo "Building lh-user-tasks-bridge-standalone"

mkdir -p ./console
cp -r "${BRIDGE_DIR}/console/.next" ./console/.next
cp -r "${BRIDGE_DIR}/node_modules" ./node_modules
cp -r "${BRIDGE_DIR}/demo-workflow" ./demo-workflow

docker build -t littlehorse/lh-user-tasks-bridge-standalone:latest -f ./standalone/Dockerfile .

rm -rf ./console
rm -rf ./node_modules
rm -rf ./demo-workflow