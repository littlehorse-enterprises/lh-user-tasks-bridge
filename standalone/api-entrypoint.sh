#!/bin/bash

set -e

export LHC_API_HOST=localhost
export LHC_API_PORT=2023
export LHC_TENANT_ID=default
export LHUT_OIDC_CONFIG_FILE_LOCATION=/sso-workflow-bridge-api/api-properties.yml

java -jar /sso-workflow-bridge-api/sso-workflow-bridge-api.jar
