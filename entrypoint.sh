#!/bin/sh

set -e

if [ ! "${AUTH_URL+x}" ]; then
    echo "Provide the AUTH_URL env variable"
    exit 1
fi

if [ ! "${AUTH_SECRET+x}" ]; then
    echo "Provide the AUTH_SECRET env variable"
    exit 1
fi

if [ ! "${AUTH_KEYCLOAK_HOST+x}" ]; then
    echo "Provide the AUTH_KEYCLOAK_HOST env variable"
    exit 1
fi

if [ ! "${AUTH_KEYCLOAK_CLIENT_ID+x}" ]; then
    echo "Provide the AUTH_KEYCLOAK_CLIENT_ID env variable"
    exit 1
fi

if [ ! "${AUTH_KEYCLOAK_CLIENT_SECRET+x}" ]; then
    echo "Provide the AUTH_KEYCLOAK_CLIENT_SECRET env variable"
    exit 1
fi

if [ ! "${AUTH_KEYCLOAK_REALM+x}" ]; then
    echo "Provide the AUTH_KEYCLOAK_REALM env variable"
    exit 1
fi

if [ ! "${LHUT_API_URL+x}" ]; then
    echo "Provide the LHUT_API_URL env variable"
    exit 1
fi

/entrypoint.sh
