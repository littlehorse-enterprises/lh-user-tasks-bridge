#!/bin/bash

set -e

export LHC_API_HOST=localhost
export LHC_API_PORT=2023
export LHC_TENANT_ID=default
export LHUT_OIDC_CONFIG_FILE_LOCATION=/lh-user-tasks-bridge-backend/backend-properties.yml

java -jar /lh-user-tasks-bridge-backend/lh-user-tasks-bridge-backend.jar
