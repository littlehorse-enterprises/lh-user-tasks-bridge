#!/bin/sh

set -e

# node env variables
export PORT=3000
export HOSTNAME="0.0.0.0"

# app env variables
export AUTH_URL="http://localhost:3000"
export AUTH_SECRET="any-secret"
export AUTH_KEYCLOAK_HOST="http://localhost:8888"
export AUTH_KEYCLOAK_REALM="default"
export AUTH_KEYCLOAK_CLIENT_ID="user-tasks-client"
export AUTH_KEYCLOAK_CLIENT_SECRET="any-secret"
export AUTH_KEYCLOAK_ISSUER='http://localhost:8888/realms/default'
export LHUT_API_URL="http://localhost:8089"

node /sso-workflow-bridge-ui/ui/server.js
