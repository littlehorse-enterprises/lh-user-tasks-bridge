#!/bin/bash

set -e

export LHC_API_HOST=littlehorse
export LHC_API_PORT=2023
export LHC_TENANT_ID=lh
export LHUT_OIDC_CONFIG_FILE_LOCATION=/user-task-api/api-properties.yml

java -jar /user-task-api/user-tasks.jar "$@"
