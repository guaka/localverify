# Android implementation plan (local module status update)

Last updated: 2026-09-06

Current validation: 9 API 36 emulator tests and 17 JVM tests pass; debug assembly and lint succeed. See the [current validation record](ANDROID-VALIDATION-MATRIX.md#current-hardening-and-workflow-validation--2026-09-06) for coverage, limitations, and the APK hash.

## Scope

Implement a native Android 11+ (API 30+) triage flow that mirrors the same local-only workflow as iOS:

- Import bug-report evidence (`.tar.gz` stream from Android device exports).
- Parse indicators from STIX2 bundles.
- Stream and scan supported text sections with limits and checkpointing.
- Persist per-case reports, checkpoint state, and coverage artifacts.
- Export a shareable ZIP (`report.json`, `report.html`, optional original archive).
- Preserve full local processing with no uploads, no telemetry, and no server calls.

Validation evidence file: [docs/ANDROID-VALIDATION-MATRIX.md](./ANDROID-VALIDATION-MATRIX.md)

## Current implementation status

The Android module exists at `Android/` and currently includes:

- Compose shell and workflow UI in `Android/app/src/main/java/org/mobiletriage/localverify/ui/MainActivity.kt`
  - Direct user-initiated local analysis; no consent checkbox
  - Archive picker
  - Indicators picker
  - Start/stop analysis
  - Export/share ZIP
  - Delete/reload actions
  - Manufacturer note for Samsung guidance in UI text
- Core engine in Kotlin:
  - Report contract in `core/TriageContracts.kt`
  - Indicator parser in `core/IndicatorParser.kt`
  - Archive reader and caps in `core/ArchiveWalker.kt`
  - Analyzer/checkpoint scan logic in `core/TriageAnalyzer.kt`
  - Exporter in `core/Exporter.kt`
  - File-based per-case storage in `storage/CaseStore.kt`
- Resources/assets:
  - bundled indicators: `app/src/main/assets/bundled-indicators.stix2`
  - matching fixtures: `app/src/main/assets/fixtures/matching.json`
  - file provider and backup policy XMLs under `app/src/main/res/xml/`
  - local Android tooling scripts under `Android/scripts/` (`bootstrap-gradle.sh`, `gradle-android.sh`, `check-android-env.sh`, `run-android-plan.sh`, `package-apk.sh`)

## What is currently working in code

- ✅ Per-case report schema aligned to shared contract fields (`platform`, `status`, `findings`, `analyzed`, `errors`, `skipped`, etc.).
- ✅ STIX parser supports supported indicator kinds:
  - `domain-name:value`
  - `url:value`
  - `process:name`
  - `file:path`
  - `file:name`
- ✅ Archive streaming + limits implemented:
  - 8 GiB expanded bytes hard cap
  - 100,000 entry cap
  - 16 MiB per-text-entry parser cap
  - duplicate-path and unsafe-path checks
  - tar checksum/trailer validation
  - gzip magic-byte verification
- ✅ Archive intake supports `.zip` containers and `.tar.gz` streams through the same parser pipeline.
- ✅ Extension + MIME gate validates `.zip`, `.gz`, `.tgz`, `.tar.gz`; shared/imported streams also pass when MIME is `application/zip`, `application/gzip`, `application/x-gzip`, `application/x-gzip-compressed`, `application/x-zip-compressed`, `application/x-tar`, `application/tar`, `application/x-compressed`, or `application/octet-stream`.
  - MIME values are normalized case-insensitively and tolerate common parameter suffixes (for example `application/zip; charset=utf-8`).
- ✅ `Indicator`/analysis checkpointing model present:
  - Report is checkpointed during analysis (`onCheckpoint` every visited path)
  - Incomplete results are retained for review; a new attempt clears scan progress and partial findings and scans from the beginning.
- ✅ Export zip includes report JSON, report HTML, optional original archive.
- ✅ Manifest sets backup exclusion (`allowBackup="false"` and `dataExtractionRules`) and includes FileProvider.
- ✅ Foreground/background interruption and intent hardening in UI layer:
  - Activity uses `singleTop` with `onNewIntent` for shared intake (`ACTION_SEND`/`ACTION_VIEW`)
  - `onStop`/`onDestroy` interrupts active analysis. The UI explains that switching apps or locking the screen stops the run; the imported archive remains available for a fresh attempt.
- ✅ Matching parity coverage now has fixture-driven unit coverage for:
  - domain-name indicators (raw-text + structured)
  - process indicators
  - file-path/file-name indicators
  - URL indicators
  - `Android/app/src/test/kotlin/org/mobiletriage/localverify/MatchingParityTest.kt`
  - `Android/app/src/test/resources/fixtures/matching.json`
  - `Android/app/src/main/assets/fixtures/matching.json` (same matrix copy)
- ✅ STIX parser skip/supported parity test coverage via:
  - `Android/app/src/test/kotlin/org/mobiletriage/localverify/IndicatorParserParityTest.kt`
  - `Android/app/src/test/kotlin/org/mobiletriage/localverify/TriageAnalyzerTest.kt`
- ✅ Archive intake policy is centralized:
  - `Android/app/src/main/java/org/mobiletriage/localverify/core/ArchivePolicy.kt`
  - `ArchivePolicyTest` validates shared extension + MIME allowlist behavior for picker/share flows.
- ✅ Archive import name resolution is hardened:
  - `MainActivity` resolves intake filenames from `OpenableColumns.DISPLAY_NAME` and URI path fallbacks before MIME/extension checks.

## What still needs completion

- Manufacturer and collection-path guidance is present but not yet validated for real devices.
- Shared evidence parsing parity against upstream Android-MVT checks is not yet fully validated.
- Current API 36 emulator tests cover document-picker archive/indicator imports, package-scoped implicit share/open resolution, ZIP/tar.gz inputs, malformed input, lifecycle interruption/restart, rotation/recreation, and exports. Physical-device validation remains pending.
- Android `ACTION_VIEW` intent handling is configured with `DEFAULT` + `BROWSABLE` and needs device-path coverage.
- Signed APK/release packaging pipeline not completed (release Gradle signing hook is now documented).
- Keystore-based release signing is optional via env vars in `Android/app/build.gradle.kts`; unsigned release builds still allowed for local CI parity checks.
- Checkpoint resume is deferred by product decision. Stop and restart-from-beginning are implemented; physical-device interruption checks remain pending.

## Android parity table (current)

Legend: `✅` implemented, `🟠` implemented but not validated on physical devices, `⚪` not implemented.

### Evidence capture & intake

| Area | Status | Notes |
| --- | --- | --- |
| Bug-report path documentation in-app | 🟠 | Generic flow exists; OEM-specific instructions still need verification. |
| Archive intake via picker | 🟠 | Implemented with document picker; extension and format checks include `.zip`, `.gz`, `.tgz`, `.tar.gz`. |
| Shared incoming report intents | 🟠 | `ACTION_SEND` and `ACTION_VIEW` are wired into manifest (including `BROWSABLE`) and activity `onNewIntent`; in-app re-entry uses `singleTop`. |
| Private staging + backup exclusion | ✅ | allowBackup/dataExtractionRules present. |

### Archive parsing & limits

| Area | Status | Notes |
| --- | --- | --- |
| Gzip/tar boundary checks | ✅ | Checks include expanded bytes, duplicate entries, trailer, checksum, unsafe paths, type/size checks. |
| Streaming and cap enforcement | ✅ | Enforced during import and walk stages. |
| Unsupported entries surfaced | ✅ | Parser reports unsupported reasons and persists in `skipped`. |
| Supported extension policy | ✅ | `ips|json|txt|log|crash` in `ArchiveWalker`. |

### Matching parity

| Area | Status | Notes |
| --- | --- | --- |
| STIX parse parity surface | 🟠 | Core parser is present; `IndicatorParserParityTest` covers supported/unsupported vectors and skip reasons, full matrix parity still to validate. |
| `Fixtures/matching.json` available | ✅ | Vendored in assets. |
| Structured vs raw-text matching behavior | 🟠 | Fixture-backed unit verification now spans domain/process/file/url indicator paths; full matrix parity still pending. |

### Workflow behavior

| Area | Status | Notes |
| --- | --- | --- |
| Start local analysis | ✅ | Explicit Start action with no consent checkbox or timestamp requirement. Sharing requires the export action. |
| Progress + cancellation | 🟠 | Implemented via thread/callback model; app-stop/onDestroy interruption flips stop flag and interrupts worker thread. |
| Restart after interruption | 🟠 | Each attempt clears previous progress/findings and rescans the retained archive; JVM regression tests pass. API 36 Stop/background/lock tests pass; physical-device checks remain. Checkpoint resume is deferred. |
| Freeze indicators per-case | ✅ | Indicators written under case directory per run. |
| Export with optional original archive | 🟠 | API 36 report-only and opt-in original export checks pass, including byte equality and forced write failure. Originals stream through a temporary ZIP; physical-device checks remain. |

### Delivery hardening

| Area | Status | Notes |
| --- | --- | --- |
| Coverage matrix persistence | ✅ | Written/read in `CaseStore`. |
| Debug APK artifact | ✅ | Built successfully on 2026-09-06: `Android/app/build/outputs/apk/debug/localverify-debug.apk`. Future debug builds use this filename. |
| Signed release APK artifact | ⚪ | Release build/signing not yet validated; release signing env hook is enabled for CI/local signing. |
| Local-only processing compliance | ✅ | No network client paths in Android code path. |

## Build/run commands

### Verified local execution status (2026-09-06)

- Historical consent-free build before the current full emulator run: `:app:testDebugUnitTest :app:assembleDebug` passed, with 17 JVM tests across six suites (including seven current hardening tests). No failures/errors/skips. Instrumentation was not rerun.
- Current APK SHA-256: `f2ecd7caa53c3a7593e58ad2b16787cc946cd3cca32f031e57af9e98429d9956`. Earlier hashes below identify earlier milestones only.
- Continuation instructions: [ANDROID-HANDOFF.md](./ANDROID-HANDOFF.md).
- Current product policy: no consent gate for local analysis. `Start analysis` runs directly; no new consent timestamp is generated. The optional legacy report field remains for compatibility. Sharing is user-initiated through export; original-archive inclusion remains opt-in.
- Latest restart-policy run: 15 JVM tests passed across six suites, zero failures/errors/skips, and debug assembly succeeded. This includes five newer hardening tests and two new stop/restart regression tests.
- Regression evidence: cancellation after the first matching file preserves incomplete results; reloading the report and starting again rescans both files with fresh finding IDs, no duplicates, retained consent, and unchanged archive bytes. Immediate cancellation also leaves the report incomplete.
- UI now says `Start analysis`, explains stop-on-background and restart behavior, and scrolls so the guidance and controls remain reachable.
- Latest restart-build APK SHA-256: `271b0a4571e25c9c82633d1c7627d33cece56c4d076a663026af90f2607a8b6a`.
- The earlier two emulator test results below are historical. Instrumentation has not been rerun for the restart policy or newer import/privacy UI; those tests need adaptation to the confirmation dialog and default report-only export before claiming current device coverage.

Earlier build and emulator milestones:

- `:app:testDebugUnitTest :app:assembleDebug --console=plain`: **BUILD SUCCESSFUL**, exit 0.
- Follow-up: `./scripts/run-android-plan.sh` passed using the included checksum-pinned Gradle 8.11.1 wrapper with no standalone Gradle on PATH. Preflight passed; build and test outputs were up-to-date from the successful run below.
- Eight JVM tests passed across five suites, with zero failures, errors, or skips. `MatchingParityTest` exercises nine synthetic fixture rows.
- Suites: `ArchivePolicyTest` (2), `ArchiveWalkerTest` (3), `IndicatorParserParityTest` (1), `MatchingParityTest` (1), `TriageAnalyzerTest` (1).
- Debug-signed APK: `Android/app/build/outputs/apk/debug/localverify-debug.apk`; the filename is configured for subsequent debug builds.
- Earlier APK SHA-256: `bd39b85e807c8e24576d1db8716b4ab6ffe52258220d02c54d6a90664f50e3b6` (consent-persistence milestone, superseded by the restart build above).
- Test report: `Android/app/build/reports/tests/testDebugUnitTest/index.html`; XML results: `Android/app/build/test-results/testDebugUnitTest/`.
- Build fixes include the Material Components XML theme dependency, Compose compiler 1.5.14 alignment with Kotlin 1.9.24, and nullable-value/activity callback corrections.
- Test-run fixes include explicit Gson fixture typing, archive output stream selection, a nullable sources assertion, and a test-only Android-compatible JSON implementation (`com.vaadin.external.google:android-json:0.0.20131108.vaadin1`).
- Matching now recognizes `process_name`, and structured records without timestamps are handled safely. The STIX test includes a separate unsupported `software:name` fixture in addition to the escaped registry pattern.
- Follow-up: two `SyntheticWorkflowTest` instrumentation tests passed on `Trustroots_Pixel_8_API_36` (Android 16/API 36), together with all eight JVM tests and debug assembly.
- Emulator coverage: explicit `ACTION_VIEW`/`ACTION_SEND` ZIP intake via content URI, consent gate, persisted consent timestamp, structured match, skipped binary entry, completed-case activity recreation, and ZIP export with/without original bytes.
- The tests caught and fixed consent loss: `startAnalysis` now stamps the same report object passed to the analyzer instead of writing a separate snapshot that checkpoints overwrite.
- Instrumentation report: `Android/app/build/reports/androidTests/connected/debug/index.html`. Synthetic inputs and export assertions run inside the emulator; no phone evidence was used or transferred.
- This does not establish full upstream Android-MVT parity, interrupted-run recovery, document-picker navigation, rotation/background behavior, or physical-device readiness. Release signing remains pending.

### Current environment note

Successful runs used Android Studio's OpenJDK 21.0.4, cached Gradle 8.11.1, and `/Users/k/Library/Android/sdk`. SDK Platform 34 was installed during the first build; Build Tools 34.0.0 were available. Earlier default-PATH preflight failures do not invalidate these successful runs. SDK and Gradle-cache access was required; a sandbox access failure must be reported separately from missing tools.

Reproduce the verified command from the repository root on this Mac:

```sh
cd Android
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./scripts/run-android-plan.sh
```

The original successful run invoked cached Gradle by absolute path. The repository now includes `gradlew`, `gradlew.bat`, and the wrapper JAR/properties under `Android/gradle/wrapper/`. Gradle 8.11.1 is pinned with its published distribution SHA-256 (`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`). The full plan runner and helper-script preflight passed with the wrapper and no standalone Gradle on PATH. ADB 1.0.41 (37.0.0-14910828) was used for API 36 emulator testing. No physical device was connected. The API 35 AVD could not start because its system image was missing; the API 36 AVD ran read-only and was stopped after testing.

Run instrumentation only on a disposable emulator without existing Local Verify cases. The tests refuse to run against existing cases and generate their fixtures locally:

```sh
export ANDROID_SERIAL=emulator-5554
./gradlew :app:connectedDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Use the emulator serial shown by `adb devices`; select only the intended test emulator.

`./scripts/check-android-env.sh` is strict for required dependencies and will fail fast when:
- `gradlew` is absent and no usable local `gradle` binary is on PATH,
- Gradle is older than 8.7,
- Java/JDK runtime or `javac` are unavailable or older than 17.
- `adb` is optional until install/debug validation is run.
- It also reports Android SDK env detection (`ANDROID_HOME`/`ANDROID_SDK_ROOT`) for context when build discovery depends on it.

Requirements:

- Android Studio (or local SDK/NDK toolchain)
- JDK 17+
- Included Gradle 8.11.1 wrapper and writeable Android module cache; no separate Gradle installation required
- Kotlin and Android plugin alignment for JDK 17

Steps:

1. From repo root:
   - `cd Android`
2. Ensure wrapper path is ready:
   - Optional preflight check first:
     - `./scripts/check-android-env.sh`
   - Full plan run (bootstrap + preflight + build + tests):
     - `./scripts/run-android-plan.sh`
3. Or run explicit build/test steps:
   - `./scripts/gradle-android.sh :app:assembleDebug`
   - `./scripts/gradle-android.sh :app:testDebugUnitTest`
4. Install/debug on a connected Android 11+ device:
   - `./scripts/gradle-android.sh :app:installDebug`
5. Open app and validate:
   - Import a generated synthetic bug-report zip and indicators bundle.
   - Validate picker and share/open intent intake (`ACTION_SEND` and `ACTION_VIEW`) for `.zip`, `.gz`, `.tgz`, `.tar.gz`.
   - Start local analysis directly; no consent checkbox is required.
   - Export report ZIP with and without original archive.
6. Track each validation run in:
  - `docs/ANDROID-VALIDATION-MATRIX.md`

## Release packaging baseline

- Configure release signing in `app/build.gradle.kts` (or via signingConfig/environment variables in CI).
- Supported release signing environment variables:
  - `LOCALVERIFY_RELEASE_KEYSTORE`
  - `LOCALVERIFY_RELEASE_STORE_PASSWORD`
  - `LOCALVERIFY_RELEASE_KEY_ALIAS`
  - `LOCALVERIFY_RELEASE_KEY_PASSWORD`
- Build release artifact:
  - `./scripts/gradle-android.sh :app:assembleRelease`
- Record release artifact hash:
  - `./scripts/package-apk.sh`
- Record for handoff:
  - Release artifact path
  - SHA-256 of APK
  - Validation matrix entries that ran against this build

## Next build-phase tasks

1. Complete physical-device/OEM and remaining fault-injection validation. Current API 36 workflow tests pass; checkpoint resume is not required for this version.
2. Complete upstream parity and OEM guidance validation, then build/sign a release APK and record its hash and validation evidence.

Keep actual device evidence on the phone. Do not copy diagnostic archives, extracted contents, evidence containers, or case exports to the Mac for debugging without explicit permission. Use synthetic fixtures and non-content timing/progress information.

Remaining validation details:

- Complete lifecycle/rotation validation matrix with explicit unsupported-input and export-failure checks.
- Validate parser behavior with:
  - benign corpus
  - seeded positives
  - representative bug-report layouts from multiple OEMs
- Finalize lifecycle behavior (stop/rotation/background restart) against `docs/ANDROID-VALIDATION-MATRIX.md`.
- Add a reproducible physical-device verification logbook and attach signed APK output.
- Execute the pinned upstream Android-MVT task list against:
  - `docs/ANDROID-VALIDATION-MATRIX.md` section 2a
  - `IndicatorParserParityTest`
  - `MatchingParityTest`
- Keep `docs/ANDROID-VALIDATION-MATRIX.md` as the canonical release readiness evidence artifact.
