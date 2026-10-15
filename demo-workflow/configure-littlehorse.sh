#!/bin/sh

set -ex

while ! lhctl version >/dev/null 2>&1; do
  echo "Waiting for LittleHorse to be ready..."
  sleep 1
done

cd /lh
java -jar build/libs/lh-user-tasks-bridge-demo-all.jar
# The sample bridge uses user-id-claim: EMAIL; seed a task for my-user.
lhctl run user-tasks-bridge-demo user someemailaddress@somedomain.com
