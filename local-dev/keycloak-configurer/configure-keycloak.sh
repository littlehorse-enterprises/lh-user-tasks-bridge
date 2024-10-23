#!/bin/bash

set -ex

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

#  Here we set the Keycloak URL as a variable with a default value, so that it can be used in both local-env and when
#  building the standalone docker image
    KEYCLOAK_URL=${1:-"http://keycloak:8888"}
    REALM_NAME="default"
    KEYCLOAK_ADMIN="admin"
    KEYCLOAK_ADMIN_PASSWORD="admin"
    KEYCLOAK_CLIENT_ID="user-tasks-client"
    KEYCLOAK_CLIENT_SECRET="any-secret"

    echo "Getting admin access token"

#   Here we fetch Keycloak's admin access token. This access token will be used in all of the subsequent Http requests
    KEYCLOAK_ADMIN_ACCESS_TOKEN=$(http --ignore-stdin --form "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
        client_id=admin-cli \
        username="$KEYCLOAK_ADMIN" \
        password="$KEYCLOAK_ADMIN_PASSWORD" \
        grant_type=password | jq -r ".access_token")

#   Here we create the realm which will simulate a client's realm.
    echo "Creating realm"
    http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" POST "$KEYCLOAK_URL/admin/realms" \
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
        bruteForceProtected:=true \
        accessTokenLifespan=86400 \
        ssoSessionIdleTimeout=86400 \
        ssoSessionMaxLifespan=90000

    echo "Realm '${REALM_NAME}' created"

#   Here we create a Keycloak's client. This client will be used to customize some properties and also provide clientId and
#   clientSecret required to perform some of the subsequent Http requests.
    echo "Creating Client"
    http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" \
          id="$KEYCLOAK_CLIENT_ID"  \
          protocol="openid-connect" \
          clientId="$KEYCLOAK_CLIENT_ID" \
          secret="${KEYCLOAK_CLIENT_SECRET}" \
          name="LH-Client" \
          description="LH-Client" \
          redirectUris[]="*" \
          directAccessGrantsEnabled:=true \
          serviceAccountsEnabled:=true \
          standardFlowEnabled:=true \
          implicitFlowEnabled:=false \
          publicClient:=false \
          authorizationServicesEnabled:=false \
          surrogateAuthRequired:=false \
          frontchannelLogout:=true

   echo "Client successfully created"

#  Here we create a custom claim that is also added to the accessToken. This custom claim is used by the lh-user-tasks-api
#  to verify that the users accessing its endpoints are allowed to see resources from a given tenant.
   echo "Creating tenant custom claim"
   http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$KEYCLOAK_CLIENT_ID/protocol-mappers/models" \
                 protocol=openid-connect \
                 protocolMapper=oidc-hardcoded-claim-mapper \
                 name=allowed_tenant \
                 config[claim.name]=allowed_tenant \
                 config[claim.value]=default \
                 config[jsonType.label]=String \
                 config[id.token.claim]:=true \
                 config[access.token.claim]:=true \
                 config[lightweight.claim]:=false \
                 config[userinfo.token.claim]:=true \
                 config[access.tokenResponse.claim]:=false \
                 config[introspection.token.claim]:=true

  SERVICE_ACCOUNT=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$KEYCLOAK_CLIENT_ID/service-account-user" | jq -r ".id")

  REAL_MANAGEMENT_CLIENT_ID=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "$KEYCLOAK_URL/admin/realms/$REALM_NAME/ui-ext/available-roles/users/$SERVICE_ACCOUNT?first=0&max=11&search=manage-user" | jq -r ".[0].clientId")

  MANAGE_USER_ROLE_ID=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "$KEYCLOAK_URL/admin/realms/$REALM_NAME/ui-ext/available-roles/users/$SERVICE_ACCOUNT?first=0&max=11&search=manage-user" | jq -r ".[0].id")

   echo "Adding manage-users role to Client"
   http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/$SERVICE_ACCOUNT/role-mappings/clients/$REAL_MANAGEMENT_CLIENT_ID" \
    [0][description]='${role_manage-users}' \
    [0][id]="${MANAGE_USER_ROLE_ID}" \
    [0][name]="manage-users"

   echo "Keycloak Client Id '${KEYCLOAK_CLIENT_ID}' created"
   echo "Keycloak Client Secret '${KEYCLOAK_CLIENT_SECRET}' created"

#  Here we create a role that will later on be used to identify admin users.
   echo "Creating UserTasks Admin Role"
   http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles" \
    name="lh-user-tasks-admin" \
    description="This role is used to let UserTasks API know about which users are allowed to perform ADMIN actions."

#  Here we create a user that will not have the admin role.
   echo "Creating UserTasks NonAdmin User"
   http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users" \
           emailVerified:=true \
           username="my-user" \
           email="someemailaddress@somedomain.com" \
           firstName="local" \
           lastName="dev" \
           enabled:=true \
           credentials[0][type]="password" \
           credentials[0][value]="1234" \
           credentials[0][temporary]:=false

#  Here we create a user that will have the admin role.
   echo "Creating UserTasks Admin User"
   http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users" \
               emailVerified:=true \
               username="my-admin-user" \
               email="someotheremailaddress@somedomain.com" \
               firstName="local-admin" \
               lastName="dev" \
               enabled:=true \
               credentials[0][type]="password" \
               credentials[0][value]="1234" \
               credentials[0][temporary]:=false \

   echo "Fetching Users' IDs"
   NON_ADMIN_USER_ID=$(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/?username=my-user" | jq -r ".[0].id")
   ADMIN_USER_ID=$(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/?username=my-admin-user" | jq -r ".[0].id")

   echo "Fetching Roles' IDs"
   VIEW_USERS_ROLE_ID=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "$KEYCLOAK_URL/admin/realms/default/ui-ext/available-roles/users/$NON_ADMIN_USER_ID?first=0&max=1&search=view-users" | jq -r ".[0].id")
   USER_TASKS_ADMIN_ROLE_ID=$(http --ignore-stdin -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles/lh-user-tasks-admin" | jq -r ".id")

#  Here we assign the view-users role to the nonAdmin user, and subsequently to the admin user as well. The view-users role
#  allows users to see their userInfo details.
   echo "Assigning View-Users Role to Non Admin User"
   http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/$NON_ADMIN_USER_ID/role-mappings/clients/$REAL_MANAGEMENT_CLIENT_ID" \
              [0][id]="$VIEW_USERS_ROLE_ID" \
              [0][name]="view-users"

   echo "Assigning View-Users Role to Admin User"
   http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/$ADMIN_USER_ID/role-mappings/clients/$REAL_MANAGEMENT_CLIENT_ID" \
              [0][id]="$VIEW_USERS_ROLE_ID" \
              [0][name]="view-users"

#  Here we assign the admin role to the admin user.
   echo "Assigning Admin Role to Admin User"
   http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/$ADMIN_USER_ID/role-mappings/realm" \
              [0][id]="$USER_TASKS_ADMIN_ROLE_ID" \
              [0][name]="lh-user-tasks-admin"

   echo "Roles successfully assigned to users!"

#  Here we make the created client public, and also disabled the serviceAccounts. This is done so that users can be properly
#  authenticated when using the created client's credential when fetching access tokens.
   echo "Making the client public"
   http --ignore-stdin -q -A bearer -a "$KEYCLOAK_ADMIN_ACCESS_TOKEN" PUT "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/user-tasks-client" \
             id="$KEYCLOAK_CLIENT_ID"  \
             enabled:=true \
             serviceAccountsEnabled:=false \
             publicClient:=true
}

#  Here we are allowing the configure_keycloak function to receive an optional param
configure_keycloak $1
