#!/bin/sh

set -ex

while ! lhctl version >/dev/null 2>&1; do
  echo "Waiting for LittleHorse to be ready..."
  sleep 1
done

cd /lh/demo
gradle run
lhctl put tenant default
lhctl run user-tasks-demo
