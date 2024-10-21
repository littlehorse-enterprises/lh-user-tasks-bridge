ARG LH_VERSION="0.11.2"
ARG KEYCLOAK_VERSION="26.0.1"

FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:${LH_VERSION}

ARG LH_VERSION
ARG KEYCLOAK_VERSION
ENV PATH=${PATH}:/keycloak/bin:/lhctl

RUN apt update && apt install -y httpie jq openjdk-21-jdk

WORKDIR /keycloak
RUN wget -q https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz -O /tmp/keycloak.tar.gz \
    && tar -xzf /tmp/keycloak.tar.gz --strip-components 1 -C /keycloak \
    && rm /tmp/keycloak.tar.gz

WORKDIR /lhctl
# https://github.com/littlehorse-enterprises/littlehorse/blob/master/local-dev/Dockerfile
RUN wget -q https://github.com/littlehorse-enterprises/littlehorse/releases/download/v${LH_VERSION}/lhctl_Linux_x86_64.tar.gz -O /tmp/lhctl.tar.gz \
    && tar -xzf /tmp/lhctl.tar.gz -C /lhctl \
    && rm /tmp/lhctl.tar.gz

WORKDIR /lh
COPY ./standalone/keycloak-entrypoint.sh /lh
COPY ./standalone/docker-entrypoint.sh /lh
COPY ./standalone/api-entrypoint.sh /lh
COPY ./standalone/ui-entrypoint.sh /lh

WORKDIR /user-task-api
COPY ./build/libs/user-tasks.jar /user-task-api
COPY ./standalone/api-properties.yml /user-task-api

WORKDIR /user-task-ui
COPY ./.next/standalone /user-task-ui
COPY ./.next/static /user-task-ui/.next/static
COPY ./public /user-task-ui/public

WORKDIR /
