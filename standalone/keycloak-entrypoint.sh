#!/bin/bash

set -e

export KC_BOOTSTRAP_ADMIN_USERNAME=admin
export KC_BOOTSTRAP_ADMIN_PASSWORD=admin

kc.sh start-dev --http-port=8888 --hostname-strict=false
