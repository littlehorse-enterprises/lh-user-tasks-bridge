#!/bin/sh

set -e

# Env variables to translate here
# LHUT_OAUTH_ENCRYPT_SECRET
# LHUT_OAUTH_CLIENT_ID
# LHUT_OAUTH_CLIENT_SECRET
# LHUT_OAUTH_ISSUER_URI

missing_vars=""

[ -z "${LHUT_API_URL}" ] && missing_vars="${missing_vars}LHUT_API_URL "
[ -z "${LHUT_OAUTH_ENCRYPT_SECRET}" ] && missing_vars="${missing_vars}LHUT_OAUTH_ENCRYPT_SECRET "
[ -z "${LHUT_OAUTH_CLIENT_ID}" ] && missing_vars="${missing_vars}LHUT_OAUTH_CLIENT_ID "
[ -z "${LHUT_OAUTH_CLIENT_SECRET}" ] && missing_vars="${missing_vars}LHUT_OAUTH_CLIENT_SECRET "
[ -z "${LHUT_OAUTH_ISSUER_URI}" ] && missing_vars="${missing_vars}LHUT_OAUTH_ISSUER_URI "

if [ -n "${missing_vars}" ]; then
    echo "The following environment variables are missing: ${missing_vars}"
    echo "Please refer to our documentation https://littlehorse.io/docs"
    exit 1
fi

export AUTH_TRUST_HOST=true # https://authjs.dev/getting-started/deployment#docker
export AUTH_SECRET=${LHUT_OAUTH_ENCRYPT_SECRET}
export AUTH_KEYCLOAK_ID=${LHUT_OAUTH_CLIENT_ID}
export AUTH_KEYCLOAK_SECRET=${LHUT_OAUTH_CLIENT_SECRET}
export AUTH_KEYCLOAK_ISSUER=${LHUT_OAUTH_ISSUER_URI}

if [ -n "${LHUT_OAUTH_CALLBACK_URL}" ]; then
    export AUTH_URL=${LHUT_OAUTH_CALLBACK_URL} # https://authjs.dev/getting-started/deployment#auth_url
fi

/entrypoint.sh
