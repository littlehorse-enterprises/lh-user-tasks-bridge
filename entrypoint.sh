#!/bin/bash

set -e

exec java -jar /lh/lh-user-tasks-bridge-backend.jar "$@"
