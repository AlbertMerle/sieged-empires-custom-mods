#!/usr/bin/env bash
# Bootstrap Sodium + Iris + BSL for ./gradlew runClient shader testing.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="$ROOT/../client"

mkdir -p "$ROOT/libs" "$ROOT/run/mods" "$ROOT/run/shaderpacks" "$ROOT/run/config"

cp -f "$PACK/mods/sodium-fabric-0.9.1+mc26.2.jar" "$ROOT/libs/"
cp -f "$PACK/mods/iris-fabric-1.11.2+mc26.2.jar" "$ROOT/libs/"
cp -f "$PACK/mods/sodium-fabric-0.9.1+mc26.2.jar" "$PACK/mods/iris-fabric-1.11.2+mc26.2.jar" "$ROOT/run/mods/"
cp -f "$PACK/shaderpacks/BSL_v10.1.3.zip" "$PACK/shaderpacks/BSL_v10.1.3.zip.txt" "$ROOT/run/shaderpacks/"

if [[ ! -f "$ROOT/run/config/iris.properties" ]]; then
  cp -f "$ROOT/src/client/resources/data/siegedempires/performance_presets/medium/iris.properties" "$ROOT/run/config/iris.properties"
fi

echo "Dev shader stack ready:"
ls -1 "$ROOT/libs"/sodium*.jar "$ROOT/libs"/iris*.jar
ls -1 "$ROOT/run/shaderpacks"/BSL_v10.1.3.zip
