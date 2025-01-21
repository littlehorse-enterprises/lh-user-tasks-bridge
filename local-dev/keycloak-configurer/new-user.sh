#!/bin/bash

set -ex

KEYCLOAK_URL=${KEYCLOAK_URL:-"http://localhost:8888"}
REALM_NAME=${REALM_NAME:-"default"}
KEYCLOAK_ADMIN_USER="admin"
KEYCLOAK_ADMIN_PASSWORD="admin"
KEYCLOAK_CLIENT_ID="user-tasks-client"

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

create_user() {
    USERNAME="$(cat /dev/stdin | jq -r .username)"
    PASSWORD="$(cat /dev/stdin | jq -r .password)"
    EMAIL="$(cat /dev/stdin | jq -r .email)"
    FIRST_NAME="$(cat /dev/stdin | jq -r .firstName)"
    LAST_NAME="$(cat /dev/stdin | jq -r .lastName)"
    IS_ADMIN="$(cat /dev/stdin | jq -r .isAdmin)"
    KEYCLOAK_ADMIN_ACCESS_TOKEN="$(get_access_token)"

    echo "Creating user ${USERNAME}"
    http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users" \
        emailVerified:=true \
        username="${USERNAME}" \
        email="${EMAIL}" \
        firstName="${FIRST_NAME}" \
        lastName="${LAST_NAME}" \
        enabled:=true \
        credentials[0][type]="password" \
        credentials[0][value]="${PASSWORD}" \
        credentials[0][temporary]:=false

    echo "Fetching Users' IDs"
    USER_ID=$(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users/?username=${USERNAME}" | jq -r ".[0].id")

    echo "Fetching Roles' IDs"
    VIEW_USERS_ROLE_ID=$(http --ignore-stdin -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "${KEYCLOAK_URL}/admin/realms/default/ui-ext/available-roles/users/${USER_ID}?first=0&max=1&search=view-users" | jq -r ".[0].id")
    USER_TASKS_BRIDGE_ADMIN_ROLE_ID=$(http --ignore-stdin -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/roles/lh-user-tasks-admin" | jq -r ".id")

    echo "Fetching Realm Management Client (realm-management) ID"
    REALM_MANAGEMENT_CLIENT_ID=$(http --ignore-stdin -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?first=0&max=11&clientId=realm-management&search=true" | jq -r ".[0].id")

    echo "Assigning View-Users Role to ${USERNAME}"
    http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users/${USER_ID}/role-mappings/clients/${REALM_MANAGEMENT_CLIENT_ID}" \
        [0][id]="${VIEW_USERS_ROLE_ID}" \
        [0][name]="view-users"file

    if [ "${IS_ADMIN}" == "true" ]; then
        echo "Assigning Admin Role to ${USERNAME}"
        http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users/${USER_ID}/role-mappings/realm" \
            [0][id]="${USER_TASKS_BRIDGE_ADMIN_ROLE_ID}" \
            [0][name]="lh-user-tasks-admin"
    fi
}

verify_dependencies
wait_for_keycloak
create_user
