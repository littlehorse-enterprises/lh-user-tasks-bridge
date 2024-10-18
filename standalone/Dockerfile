ARG LH_VERSION="0.11.2"
ARG KEYCLOAK_VERSION="26.0.1"

FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:${LH_VERSION}

ARG LH_VERSION
ARG KEYCLOAK_VERSION
ENV PATH=${PATH}:/keycloak/bin:/lhctl

RUN mkdir /keycloak \
    && wget -q https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz -O /tmp/keycloak.tar.gz \
    && tar -xzf /tmp/keycloak.tar.gz --strip-components 1 -C /keycloak \
    && rm /tmp/keycloak.tar.gz

RUN mkdir /lhctl \
    && wget -q https://github.com/littlehorse-enterprises/littlehorse/releases/download/v${LH_VERSION}/lhctl_Linux_x86_64.tar.gz -O /tmp/lhctl.tar.gz \
    && tar -xzf /tmp/lhctl.tar.gz -C /lhctl \
    && rm /tmp/lhctl.tar.gz

COPY ./standalone/keycloak-entrypoint.sh /lh
COPY ./standalone/docker-entrypoint.sh /lh