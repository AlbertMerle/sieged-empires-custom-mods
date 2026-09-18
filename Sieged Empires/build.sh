#!/bin/bash
cd "$(dirname "$0")"
./gradlew compileJava compileClientJava --no-daemon --console=plain 2>&1
