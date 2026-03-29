#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
IMAGE_NAME="${IMAGE_NAME:-littlehorse/lh-workflow-diffuser:latest}"

cd "${SCRIPT_DIR}/.."

./gradlew clean build installDist -x test
docker build -f Dockerfile --tag "${IMAGE_NAME}" .
