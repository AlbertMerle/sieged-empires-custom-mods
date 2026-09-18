#!/bin/sh
cd "$(dirname "$0")"
./gradlew genSources --no-daemon 2>&1
