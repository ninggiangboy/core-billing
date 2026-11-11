#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../../billing"
exec ./gradlew :apps:app-boot:loadTest
