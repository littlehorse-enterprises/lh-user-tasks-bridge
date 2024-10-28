#!/bin/bash

set -ex

KEYCLOAK_URL=${KEYCLOAK_URL:-"http://localhost:8888"}
REALM_NAME=${REALM_NAME:-"default"}
KEYCLOAK_ADMIN_USER="admin"
KEYCLOAK_ADMIN_PASSWORD="admin"

wait_for_keycloak() {
    while ! curl --silent --fail --output /dev/null "${KEYCLOAK_URL}"; do
        echo "Waiting for keycloak"
        sleep 5
    done
}

verify_dependencies() {
    if ! command -v http &>/dev/null; then
        echo "'http' command not found. Install httpie https://httpie.io/cli"
        exit 1
    fi

    if ! command -v jq &>/dev/null; then
        echo "'jq' command not found. Install jq https://jqlang.github.io/jq/"
        exit 1
    fi
}

get_access_token() {
    http --ignore-stdin --form "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
        client_id=admin-cli \
        username="${KEYCLOAK_ADMIN_USER}" \
        password="${KEYCLOAK_ADMIN_PASSWORD}" \
        grant_type=password | jq -r ".access_token"
}

create_group() {
    echo "Creating group"
    http --ignore-stdin -q -A bearer -a "$(get_access_token)" POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/groups" name="${1}"
}

verify_dependencies
wait_for_keycloak
create_group $1
