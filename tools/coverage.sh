#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT_DIR/build/coverage-reports"
SWIFTPM_BUILD_DIR="$ROOT_DIR/.build/swiftpm"
IOS_PROJECT="$ROOT_DIR/LocalVerifyLocal.xcodeproj"
IOS_DERIVED_DATA="$ROOT_DIR/build/derived-data"

SWIFTPM_LOG="$REPORT_DIR/swiftpm-test.log"
SWIFTPM_REPORT="$REPORT_DIR/swiftpm-coverage.txt"
SWIFTPM_LCOV="$REPORT_DIR/swiftpm-coverage.lcov"
IOS_LOG="$REPORT_DIR/ios-ui-test.log"
IOS_REPORT="$REPORT_DIR/ios-ui-coverage.txt"
SWIFTPM_IGNORE_REGEX='Tests|TriageCorePackageTests\.derived/runner\.swift$'

print_help() {
  cat <<'USAGE'
Usage:
  ./tools/coverage.sh [swiftpm|ios] [--destination <xcode-destination>] [--scheme <xcode-scheme>]

Subcommands:
  swiftpm   Generate SwiftPM coverage for TriageCore package tests (default).
  ios       Generate Xcode coverage for iOS UI tests.

Options:
  --destination   Xcode destination for iOS runs.
                  Default: "platform=iOS Simulator,name=iPhone 17 Pro"
  --scheme        Xcode scheme for iOS runs.
                  Default: LocalVerifyChecks

Outputs:
  build/coverage-reports/swiftpm-coverage.txt
  build/coverage-reports/swiftpm-coverage.lcov
  build/coverage-reports/ios-ui-coverage.txt
  build/coverage-reports/ios-run.*/results.xcresult (a new bundle per run)
USAGE
}

run_swiftpm_coverage() {
  if [[ $# -ne 0 ]]; then
    echo "[coverage] swiftpm does not accept options" >&2
    exit 1
  fi
  mkdir -p "$REPORT_DIR"

  echo "[coverage] Running SwiftPM test suite with coverage..."
  swift test \
    --build-path "$SWIFTPM_BUILD_DIR" \
    --enable-code-coverage 2>&1 |
    tee "$SWIFTPM_LOG"

  local binpath
  binpath="$(swift build --build-path "$SWIFTPM_BUILD_DIR" --show-bin-path)"
  local profdata="$binpath/codecov/default.profdata"
  if [[ ! -f "$profdata" ]]; then
    echo "[coverage] Could not locate SwiftPM .profdata output." >&2
    exit 1
  fi

  local test_binary="$binpath/TriageCorePackageTests.xctest/Contents/MacOS/TriageCorePackageTests"

  if [[ -z "$test_binary" || ! -x "$test_binary" ]]; then
    echo "[coverage] Could not locate built SwiftPM test binary under $binpath" >&2
    exit 1
  fi

  echo "[coverage] Writing coverage summary to $SWIFTPM_REPORT"
  xcrun llvm-cov report "$test_binary" \
    -instr-profile "$profdata" \
    -ignore-filename-regex "$SWIFTPM_IGNORE_REGEX" \
    > "$SWIFTPM_REPORT"
  xcrun llvm-cov export "$test_binary" \
    -instr-profile "$profdata" \
    -ignore-filename-regex "$SWIFTPM_IGNORE_REGEX" \
    -format lcov > "$SWIFTPM_LCOV"

  echo ""
  echo "SwiftPM coverage summary:"
  grep -A1 "TOTAL" "$SWIFTPM_REPORT" | tail -n 20
}

run_ios_coverage() {
  local destination="platform=iOS Simulator,name=iPhone 17 Pro"
  local scheme="LocalVerifyChecks"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --destination)
        [[ $# -ge 2 && -n "$2" ]] || { echo "Missing --destination value" >&2; exit 1; }
        destination="$2"
        shift 2
        ;;
      --scheme)
        [[ $# -ge 2 && -n "$2" ]] || { echo "Missing --scheme value" >&2; exit 1; }
        scheme="$2"
        shift 2
        ;;
      *)
        echo "[coverage] Unknown option: $1" >&2
        print_help
        exit 1
        ;;
    esac
  done

  mkdir -p "$REPORT_DIR"
  python3 "$ROOT_DIR/tools/generate_project.py" --local-only --ui-tests
  local run_dir
  run_dir="$(mktemp -d "$REPORT_DIR/ios-run.XXXXXX")"
  local IOS_RESULT_BUNDLE="$run_dir/results.xcresult"

  echo "[coverage] Running iOS UI tests ($scheme) with code coverage enabled"
  xcodebuild \
    -project "$IOS_PROJECT" \
    -scheme "$scheme" \
    -destination "$destination" \
    -derivedDataPath "$IOS_DERIVED_DATA" \
    -enableCodeCoverage YES \
    -resultBundlePath "$IOS_RESULT_BUNDLE" \
    CODE_SIGNING_ALLOWED=NO \
    test 2>&1 |
    tee "$IOS_LOG"

  echo "[coverage] Writing coverage summary to $IOS_REPORT"
  xcrun xccov view --report "$IOS_RESULT_BUNDLE" > "$IOS_REPORT"

  echo ""
  echo "iOS coverage summary written to:"
  echo "  - $IOS_REPORT"
  echo "  - $IOS_RESULT_BUNDLE"
}

mode="swiftpm"
if [[ $# -gt 0 ]]; then
  mode="$1"
  shift
fi

case "$mode" in
  swiftpm)
    run_swiftpm_coverage "$@"
    ;;
  ios)
    run_ios_coverage "$@"
    ;;
  -h|--help|help)
    print_help
    ;;
  *)
    echo "[coverage] Unknown mode: $mode" >&2
    print_help
    exit 1
    ;;
esac
