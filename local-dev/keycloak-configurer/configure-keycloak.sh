#!/bin/bash

set -e

configure_keycloak() {
    echo "Proceeding to create lh realm in keycloak and the client for user-tasks as sample OIDC provider"

    if ! command -v http &>/dev/null; then
        echo "'http' command not found. Install httpie https://httpie.io/cli"
        exit 1
    fi

    if ! command -v jq &>/dev/null; then
        echo "'jq' command not found. Install jq https://jqlang.github.io/jq/"
        exit 1
    fi

    REALM_NAME="lh"
    KEYCLOAK_ADMIN="admin"
    KEYCLOAK_ADMIN_PASSWORD="admin"
    KEYCLOAK_PORT="8888"
    KEYCLOAK_CLIENT_ID="user-tasks-client"
    KEYCLOAK_CLIENT_SECRET="any-secret"

    echo "Getting admin access token"

    KEYCLOAK_ADMIN_ACCESS_TOKEN=$(http --ignore-stdin --form "http://user-tasks-keycloak:${KEYCLOAK_PORT}/realms/master/protocol/openid-connect/token" \
        client_id=admin-cli \
        username="$KEYCLOAK_ADMIN" \
        password="$KEYCLOAK_ADMIN_PASSWORD" \
        grant_type=password | jq -r ".access_token")

    echo "Creating realm"
    http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" POST "http://user-tasks-keycloak:${KEYCLOAK_PORT}/admin/realms" \
        id="$REALM_NAME" \
        realm="$REALM_NAME" \
        displayName="$REALM_NAME" \
        sslRequired=external \
        enabled:=true \
        registrationAllowed:=false \
        loginWithEmailAllowed:=true \
        duplicateEmailsAllowed:=false \
        resetPasswordAllowed:=false \
        editUsernameAllowed:=false \
        bruteForceProtected:=true

    echo "Keycloak url: http://user-tasks-keycloak:${KEYCLOAK_PORT}"
    echo "Keycloak admin username: ${KEYCLOAK_ADMIN}"
    echo "Keycloak admin password: ${KEYCLOAK_ADMIN_PASSWORD}"
    echo "Realm '${REALM_NAME}' created"

    echo "Creating Client"
    http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" POST "http://user-tasks-keycloak:${KEYCLOAK_PORT}/admin/realms/$REALM_NAME/clients" \
          id="$KEYCLOAK_CLIENT_ID"  \
          protocol="openid-connect" \
          clientId="$KEYCLOAK_CLIENT_ID" \
          secret="${KEYCLOAK_CLIENT_SECRET}" \
          name="LH-Client" \
          description="LH-Client" \
          directAccessGrantsEnabled:=true \
          serviceAccountsEnabled:=true \
          standardFlowEnabled:=true \
          implicitFlowEnabled:=false \
          publicClient:=false \
          authorizationServicesEnabled:=false \
          surrogateAuthRequired:=false \
          frontchannelLogout:=true


#  KEYCLOAK_CLIENT_SECRET=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "http://user-tasks-keycloak:8888/admin/realms/lh/clients/$KEYCLOAK_CLIENT_ID/client-secret" | jq -r ".value")

  SERVICE_ACCOUNT=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "http://user-tasks-keycloak:8888/admin/realms/lh/clients/$KEYCLOAK_CLIENT_ID/service-account-user" | jq -r ".id")

  REAL_MANAGEMENT_CLIENT_ID=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "http://user-tasks-keycloak:8888/admin/realms/lh/ui-ext/available-roles/users/$SERVICE_ACCOUNT?first=0&max=11&search=manage-user" | jq -r ".[0].clientId")

  MANAGE_USER_ROLE_ID=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "http://user-tasks-keycloak:8888/admin/realms/lh/ui-ext/available-roles/users/$SERVICE_ACCOUNT?first=0&max=11&search=manage-user" | jq -r ".[0].id")

  echo "Adding manage-users role to Client"
   http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" POST "http://user-tasks-keycloak:8888/admin/realms/lh/users/$SERVICE_ACCOUNT/role-mappings/clients/$REAL_MANAGEMENT_CLIENT_ID" \
    [0][description]='${role_manage-users}' \
    [0][id]="${MANAGE_USER_ROLE_ID}" \
    [0][name]="manage-users"

   echo "Keycloak Client Id '${KEYCLOAK_CLIENT_ID}' created"
   echo "Keycloak Client Secret '${KEYCLOAK_CLIENT_SECRET}' created"

#   TODO: It's probably a good idea to create the test user here as well
}

configure_keycloak
