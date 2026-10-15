#!/bin/sh

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONTEXT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

cd "${CONTEXT_DIR}"

# Forward each argument as a Gradle task option, preserving spaces and flags.
for compose_arg do
    shift
    set -- "$@" "--compose-arg=${compose_arg}"
done

exec ./gradlew -q --console=plain compose "$@"
