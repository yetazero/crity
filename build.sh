#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
case "${1:-}" in
    install) exec bash rebuild-crity.sh install ;;
    "") ;;
    *) echo "Usage: $0 [install]" >&2; exit 2 ;;
esac
GRADLE_CMD="gradle"
if [[ -x ./gradlew ]]; then
    GRADLE_CMD="./gradlew"
fi
"$GRADLE_CMD" build --console=plain
