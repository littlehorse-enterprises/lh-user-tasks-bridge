FROM alpine:latest
RUN mkdir /lh
COPY ./configure-keycloak.sh /lh
RUN apk add --no-cache httpie curl jq bash

CMD ["./lh/configure-keycloak.sh"]
