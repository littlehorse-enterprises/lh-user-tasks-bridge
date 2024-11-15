#!/bin/sh

set -e

if [ -z "${NEXTAUTH_URL}" ]; then
    echo "Provide the NEXTAUTH_URL env variable"
    exit 1
fi

if [ -z "${NEXTAUTH_SECRET}" ]; then
    echo "Provide the NEXTAUTH_SECRET env variable"
    exit 1
fi

if [ -z "${KEYCLOAK_HOST}" ]; then
    echo "Provide the KEYCLOAK_HOST env variable"
    exit 1
fi

if [ -z "${KEYCLOAK_CLIENT_ID}" ]; then
    echo "Provide the KEYCLOAK_CLIENT_ID env variable"
    exit 1
fi

if [ -z "${KEYCLOAK_CLIENT_SECRET}" ]; then
    echo "Provide the KEYCLOAK_CLIENT_SECRET env variable"
    exit 1
fi

if [ -z "${KEYCLOAK_REALM}" ]; then
    echo "Provide the KEYCLOAK_REALM env variable"
    exit 1
fi

if [ -z "${LHUT_API_URL}" ]; then
    echo "Provide the LHUT_API_URL env variable"
    exit 1
fi

if [ -z "${LHUT_TENANT_ID}" ]; then
    echo "Provide the LHUT_TENANT_ID env variable"
    exit 1
fi

/entrypoint.sh
