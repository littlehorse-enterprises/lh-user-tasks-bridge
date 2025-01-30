#!/bin/sh

set -e

# node env variables
export PORT=3000
export HOSTNAME="0.0.0.0"

# app env variables
export AUTH_URL="http://localhost:3000"
export NEXTAUTH_URL="http://localhost:3000"

export AUTH_SECRET="any-secret"
export AUTH_KEYCLOAK_ID="user-tasks-bridge-client"
export AUTH_KEYCLOAK_SECRET="any-secret"
export AUTH_KEYCLOAK_ISSUER='http://localhost:8888/realms/default'

export LHUT_API_URL="http://localhost:8089"
export LHUT_AUTHORITIES='$.realm_access.roles,$.resource_access.*.roles'

node /lh-user-tasks-bridge-console/console/server.js
