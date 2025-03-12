#!/bin/bash

set -e

/lh/kafka-entrypoint.sh &

# 60s default timeout
if ! kafka-topics.sh --bootstrap-server=localhost:9092 --list >/dev/null 2>&1; then
    echo "Error trying to connect to kafka, exiting..."
    exit 1
fi

/lh/littlehorse-entrypoint.sh &
/lh/dashboard-entrypoint.sh &

while ! lhctl version >/dev/null 2>&1; do
    echo "Waiting for lh"
    sleep 1
done

echo "Configuring LittleHorse"
/lh/configure-littlehorse.sh &

/lh/keycloak-entrypoint.sh &

while ! curl --silent --fail --output /dev/null http://localhost:8888; do
    echo "Waiting for keycloak"
    sleep 1
done

/lh/configure-keycloak.sh http://localhost:8888

/lh/backend-entrypoint.sh &
/lh/console-entrypoint.sh &

tail -f /dev/null
