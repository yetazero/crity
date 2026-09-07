#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
bash rebuild-crity.sh
HYTALE_JAR="${HYTALE_SERVER_JAR:-$HOME/.local/share/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar}"
mkdir -p build/manual/test-classes
mapfile -d '' tests < <(find src/test/kotlin -name '*.kt' -print0)
"${KOTLINC:-/usr/bin/kotlinc}" -cp "build/manual/classes:$HYTALE_JAR" \
    -Xfriend-paths="$PWD/build/manual/classes" -jvm-target "${JVM_TARGET:-21}" \
    -d build/manual/test-classes "${tests[@]}"
java --enable-native-access=ALL-UNNAMED -ea -cp "build/manual/test-classes:build/manual/classes:$HYTALE_JAR" com.yetazero.crity.CombatDisplayCheckKt
