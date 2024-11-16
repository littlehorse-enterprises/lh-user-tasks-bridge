#!/bin/sh

set -e

# node env variables
export PORT=3000
export HOSTNAME="0.0.0.0"

# app env variables
export NEXTAUTH_URL="http://localhost:3000"
export NEXTAUTH_SECRET="any-secret"
export KEYCLOAK_HOST="http://localhost:8888"
export KEYCLOAK_REALM="default"
export KEYCLOAK_CLIENT_ID="user-tasks-client"
export KEYCLOAK_CLIENT_SECRET="any-secret"
export LHUT_API_URL="http://localhost:8089"
export LHUT_TENANT_ID="default"

node /user-task-ui/ui/server.js
