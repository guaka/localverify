#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

APK_PATH="${1:-app/build/outputs/apk/release/app-release.apk}"

if [[ ! -f "$APK_PATH" ]]; then
  echo "Release APK not found at: $APK_PATH"
  echo "Hint: run ./scripts/gradle-android.sh :app:assembleRelease first, or pass an explicit APK path."
  echo "Example: ./scripts/package-apk.sh app/build/outputs/apk/debug/app-debug.apk"
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  DIGEST="$(sha256sum "$APK_PATH" | awk '{print $1}')"
else
  DIGEST="$(shasum -a 256 "$APK_PATH" | awk '{print $1}')"
fi

SIZE_BYTES="$(wc -c <"$APK_PATH")"

echo "APK: $APK_PATH"
echo "SHA-256: $DIGEST"
echo "size: $SIZE_BYTES"
