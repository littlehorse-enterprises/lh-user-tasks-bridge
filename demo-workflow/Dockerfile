ARG LH_VERSION=latest
FROM ghcr.io/littlehorse-enterprises/littlehorse/lhctl:${LH_VERSION} AS lhctl

FROM gradle:latest
ARG LH_VERSION

COPY --from=lhctl /usr/local/bin/lhctl /usr/local/bin/lhctl

RUN mkdir /lh
COPY ./ /lh
RUN chmod +x /lh/configure-littlehorse.sh

CMD [ "/lh/configure-littlehorse.sh" ]
