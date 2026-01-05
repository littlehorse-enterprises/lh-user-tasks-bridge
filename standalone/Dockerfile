FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:latest

ARG KEYCLOAK_VERSION="26.4.7"
ENV PATH=${PATH}:/keycloak/bin

COPY --from=ghcr.io/littlehorse-enterprises/littlehorse/lhctl:latest /usr/local/bin/lhctl /usr/local/bin/lhctl

RUN yum install -y jq unzip python3-pip
RUN yum clean all && rm -rf /var/cache/yum
RUN python3 -m pip install wheel httpie

WORKDIR /keycloak
RUN wget -q https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz -O /tmp/keycloak.tar.gz \
    && tar -xzf /tmp/keycloak.tar.gz --strip-components 1 -C /keycloak \
    && rm /tmp/keycloak.tar.gz

WORKDIR /lh
COPY ./standalone/keycloak-entrypoint.sh /lh
COPY ./standalone/docker-entrypoint.sh /lh
COPY ./standalone/backend-entrypoint.sh /lh
COPY ./standalone/console-entrypoint.sh /lh
COPY ./local-dev/keycloak-configurer/configure-keycloak.sh /lh

WORKDIR /lh-user-tasks-bridge-backend
COPY ./backend/build/libs/lh-user-tasks-bridge-backend.jar /lh-user-tasks-bridge-backend
COPY ./standalone/backend-properties.yml /lh-user-tasks-bridge-backend

WORKDIR /lh-user-tasks-bridge-console
COPY ./console/.next/standalone /lh-user-tasks-bridge-console
COPY ./console/.next/static /lh-user-tasks-bridge-console/console/.next/static
COPY ./node_modules /lh-user-tasks-bridge-console/node_modules

# Configure LittleHorse with Demo Workflow
COPY ./demo-workflow/build/libs/lh-user-tasks-bridge-demo-all.jar /lh/build/libs
COPY ./demo-workflow/configure-littlehorse.sh /lh
RUN chmod +x /lh/configure-littlehorse.sh

WORKDIR /
