#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

MISSING=0

require_version_ge() {
  local label="$1"
  local actual="$2"
  local minimum="$3"

  if [[ -z "$actual" ]]; then
    echo "  -> unable to parse version"
    return 1
  fi

  # Compare only the first three numeric components numerically via sort version.
  local parsed_actual
  local parsed_min
  parsed_actual="$(echo "$actual" | awk -F. '{print $1"."$2"."$3}' | awk -F. '{printf "%d%03d%03d", $1, ($2+0), ($3+0)}')"
  parsed_min="$(echo "$minimum" | awk -F. '{print $1"."$2"."$3}' | awk -F. '{printf "%d%03d%03d", $1, ($2+0), ($3+0)}')"
  if [[ "$parsed_actual" -lt "$parsed_min" ]]; then
    echo "  -> ${label} is too old (got $actual, need >= $minimum)"
    return 1
  fi
  echo "  -> ${label} meets minimum $minimum ($actual)"
  return 0
}

echo "Android module preflight"

echo -n "gradlew: "
if [[ -x ./gradlew ]]; then
  echo "present"
elif [[ -f ./gradlew ]]; then
  echo "present (not executable)"
  MISSING=1
else
  echo "missing"
fi

echo -n "local gradle CLI: "
if command -v gradle >/dev/null 2>&1; then
  gradle --version >/tmp/localverify-gradle.out 2>&1
  sed -n '1,3p' /tmp/localverify-gradle.out
  GRADLE_VERSION="$(awk '/Gradle / {for (i=1;i<=NF;i++) if($i ~ /^[0-9]+\.[0-9]+(\.[0-9]+)?(-.+)?$/){print $i; exit}}' /tmp/localverify-gradle.out)"
  if ! require_version_ge "Gradle" "$GRADLE_VERSION" "8.7.0"; then
    MISSING=1
  fi
else
  echo "missing"
  if [[ ! -x ./gradlew ]]; then
    MISSING=1
  fi
fi

echo -n "java runtime: "
if command -v java >/dev/null 2>&1; then
  if java -version >/tmp/localverify-java.out 2>&1; then
    sed -n '1p' /tmp/localverify-java.out
    JAVA_VERSION="$(awk -F[\"\"] '/version/ {print $2; exit}' /tmp/localverify-java.out)"
    if ! require_version_ge "Java runtime" "$JAVA_VERSION" "17.0.0"; then
      MISSING=1
    fi
  else
    sed -n '1,3p' /tmp/localverify-java.out
    MISSING=1
  fi
else
  echo "missing"
  if [[ -n "${JAVA_HOME:-}" ]]; then
    echo "  note: JAVA_HOME is set to ${JAVA_HOME} but java was not invokable on PATH"
  fi
  MISSING=1
fi

echo -n "javac: "
if command -v javac >/dev/null 2>&1; then
  if javac -version >/tmp/localverify-javac.out 2>&1; then
    sed -n '1p' /tmp/localverify-javac.out
    JAVAC_VERSION="$(awk '{print $2}' /tmp/localverify-javac.out | awk -F. '{print $1"."$2"."$3}')"
    if ! require_version_ge "javac" "$JAVAC_VERSION" "17.0.0"; then
      MISSING=1
    fi
  else
    sed -n '1,3p' /tmp/localverify-javac.out
    MISSING=1
  fi
else
  echo "missing"
  MISSING=1
fi

echo -n "adb: "
if command -v adb >/dev/null 2>&1; then
  adb --version >/tmp/localverify-adb.out 2>&1
  sed -n '1,2p' /tmp/localverify-adb.out
else
  echo "missing"
  echo "  note: optional for on-device steps; required only for install/debug validation"
fi

echo -n "android SDK env: "
if [[ -n "${ANDROID_HOME:-}" || -n "${ANDROID_SDK_ROOT:-}" ]]; then
  ANDROID_SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  echo "$ANDROID_SDK_PATH"
  if [[ ! -d "$ANDROID_SDK_PATH" ]]; then
    echo "  note: configured Android SDK path does not exist"
  fi
else
  echo "not set"
  echo "  note: set ANDROID_HOME or ANDROID_SDK_ROOT if Gradle/adb cannot locate SDK"
fi

if [[ "$MISSING" -ne 0 ]]; then
  echo
  echo "Preflight failed: required tooling is missing."
  exit 1
fi
