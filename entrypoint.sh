#!/bin/sh

set -e

if [ ! "${NEXTAUTH_URL+x}" ]; then
    echo "Provide the NEXTAUTH_URL env variable"
    exit 1
fi

if [ ! "${NEXTAUTH_SECRET+x}" ]; then
    echo "Provide the NEXTAUTH_SECRET env variable"
    exit 1
fi

if [ ! "${KEYCLOAK_HOST+x}" ]; then
    echo "Provide the KEYCLOAK_HOST env variable"
    exit 1
fi

if [ ! "${KEYCLOAK_CLIENT_ID+x}" ]; then
    echo "Provide the KEYCLOAK_CLIENT_ID env variable"
    exit 1
fi

if [ ! "${KEYCLOAK_CLIENT_SECRET+x}" ]; then
    echo "Provide the KEYCLOAK_CLIENT_SECRET env variable"
    exit 1
fi

if [ ! "${KEYCLOAK_REALM+x}" ]; then
    echo "Provide the KEYCLOAK_REALM env variable"
    exit 1
fi

if [ ! "${LHUT_API_URL+x}" ]; then
    echo "Provide the LHUT_API_URL env variable"
    exit 1
fi

if [ ! "${LHUT_TENANT_ID+x}" ]; then
    echo "Provide the LHUT_TENANT_ID env variable"
    exit 1
fi

node ui/server.js
