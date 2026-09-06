#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -x "./gradlew" && -f "./gradlew" ]]; then
  ./gradlew "$@"
  exit 0
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "Neither ./gradlew nor a local gradle binary is available."
  echo "Run ./scripts/bootstrap-gradle.sh to generate the wrapper."
  exit 1
fi

gradle "$@"
