#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

version_ge() {
  local actual="$1"
  local minimum="$2"
  local parsed_actual
  local parsed_min
  parsed_actual="$(echo "$actual" | awk -F. '{print $1"."$2"."$3}' | awk -F. '{printf "%d%03d%03d", $1, ($2+0), ($3+0)}')"
  parsed_min="$(echo "$minimum" | awk -F. '{print $1"."$2"."$3}' | awk -F. '{printf "%d%03d%03d", $1, ($2+0), ($3+0)}')"
  if [[ "$parsed_actual" -lt "$parsed_min" ]]; then
    return 1
  fi
  return 0
}

DEFAULT_GRADLE_VERSION="8.7"
VERSION="${LOCALVERIFY_GRADLE_VERSION:-$DEFAULT_GRADLE_VERSION}"

if [[ -x "./gradlew" && -f "./gradlew" ]]; then
  echo "Gradle wrapper already present at $ROOT_DIR/gradlew"
  exit 0
fi

if ! command -v gradle >/dev/null 2>&1; then
  cat <<'MSG'
Gradle is required to bootstrap Android tooling in this repository.

Install options:
- Use Android Studio's embedded Gradle wrapper bootstrap.
- Install a local Gradle 8.7+ binary on PATH.
- Set LOCALVERIFY_GRADLE_VERSION to a supported Gradle 8.x version and rerun.

Example:
  export LOCALVERIFY_GRADLE_VERSION=8.7
  ./scripts/bootstrap-gradle.sh
MSG
  exit 1
fi

LOCAL_GRADLE_VERSION="$(gradle --version | awk '/Gradle / {for (i=1;i<=NF;i++) if($i ~ /^[0-9]+\.[0-9]+(\.[0-9]+)?(-.+)?$/){print $i; exit}}')"
if ! version_ge "$LOCAL_GRADLE_VERSION" "8.7.0"; then
  echo "Bootstrap requires Gradle >= 8.7.0 (found: ${LOCAL_GRADLE_VERSION:-unknown})."
  echo "Set LOCALVERIFY_GRADLE_VERSION to a supported version or install a newer Gradle."
  exit 1
fi

echo "Generating Gradle wrapper with version ${VERSION}..."
gradle wrapper --gradle-version "$VERSION"

echo "Gradle wrapper generated."
echo "Run commands via ./gradlew from the Android/ directory."
