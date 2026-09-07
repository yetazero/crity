#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

KOTLINC="${KOTLINC:-/usr/bin/kotlinc}"
JAR="${JAR:-jar}"
HYTALE_JAR="${HYTALE_SERVER_JAR:-$HOME/.local/share/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-/usr/share/kotlin/lib/kotlin-stdlib.jar}"
JVM_TARGET="${JVM_TARGET:-21}"
VERSION="$(python3 -c 'import json; print(json.load(open("src/main/resources/manifest.json"))["Version"])')"

case "${1:-}" in
    ""|install) ;;
    *) echo "Usage: $0 [install]" >&2; exit 2 ;;
esac
[[ -f "$HYTALE_JAR" ]] || { echo "Missing server JAR: $HYTALE_JAR" >&2; exit 1; }
[[ -f "$KOTLIN_STDLIB" ]] || { echo "Missing Kotlin stdlib: $KOTLIN_STDLIB" >&2; exit 1; }

rm -rf build/manual/classes
mkdir -p build/manual/classes target
mapfile -d '' sources < <(find src/main/kotlin -name '*.kt' -print0)
"$KOTLINC" -cp "$HYTALE_JAR" -jvm-target "$JVM_TARGET" -d build/manual/classes "${sources[@]}"
cp -r src/main/resources/. build/manual/classes/
(cd build/manual/classes && "$JAR" xf "$KOTLIN_STDLIB")
rm -f build/manual/classes/META-INF/MANIFEST.MF
"$JAR" cf "target/Crity-$VERSION.jar" -C build/manual/classes .
echo "Built: $PWD/target/Crity-$VERSION.jar"

if [[ "${1:-}" == install ]]; then
    MODS_DIR="${HYTALE_MODS_DIR:-$HOME/.local/share/Hytale/data/pre-release/Mods}"
    mkdir -p "$MODS_DIR"
    if [[ -f "$MODS_DIR/Crity.jar" ]]; then
        cp -p "$MODS_DIR/Crity.jar" "$MODS_DIR/Crity.jar.backup-$(date +%Y%m%d-%H%M%S)"
    fi
    cp "target/Crity-$VERSION.jar" "$MODS_DIR/Crity.jar"
    echo "Installed: $MODS_DIR/Crity.jar — restart the server to load it."
fi
