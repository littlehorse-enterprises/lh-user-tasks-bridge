#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
IMAGE_NAME="${IMAGE_NAME:-littlehorse/lh-workflow-diffuser:latest}"

cd "${CONTEXT_DIR}"

./gradlew clean build installDist -x test
docker build -f Dockerfile --tag "${IMAGE_NAME}" .
