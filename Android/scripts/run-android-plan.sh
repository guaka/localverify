#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "== Android plan runner =="

echo "== Bootstrapping Gradle wrapper =="
./scripts/bootstrap-gradle.sh

echo "== Verifying Android build environment =="
./scripts/check-android-env.sh

echo "== Building debug variant =="
./scripts/gradle-android.sh :app:assembleDebug

echo "== Running unit tests =="
./scripts/gradle-android.sh :app:testDebugUnitTest

echo "== Plan-run tasks complete =="
echo "Next step: install on device (./scripts/gradle-android.sh :app:installDebug) and follow docs/ANDROID-VALIDATION-MATRIX.md"
