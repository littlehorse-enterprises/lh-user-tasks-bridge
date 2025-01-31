FROM gradle:latest

COPY --from=ghcr.io/littlehorse-enterprises/littlehorse/lhctl:latest /usr/local/bin/lhctl /usr/local/bin/lhctl

RUN mkdir /lh
COPY ./ /lh
RUN chmod +x /lh/configure-littlehorse.sh

CMD [ "/lh/configure-littlehorse.sh" ]
