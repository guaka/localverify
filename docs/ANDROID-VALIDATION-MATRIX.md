# Android validation matrix and release checklist

## Current hardening and workflow validation — 2026-09-06

This entry supersedes earlier emulator results below. No consent gate was added; Start runs directly, Stop remains, and restarting scans the retained archive from the beginning. Original inclusion remains opt-in. Other contributors' restart and no-consent changes are preserved.

- Environment: Android Studio JDK 21.0.4, checksum-pinned Gradle 8.11.1, SDK Platform/Build Tools 34; disposable `Trustroots_Pixel_8_API_36`, Android 16/API 36 arm64, serial `emulator-5584`, launched read-only/headless without snapshot saving. The emulator had no Local Verify installation before testing.
- Final command: `:app:connectedDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain`, using the handoff Java/SDK environment and `ANDROID_SERIAL=emulator-5584`.
- Result: **BUILD SUCCESSFUL**. All **9 instrumentation tests** passed, zero failures/errors/skips. All **17 JVM tests** across six suites passed on the current core; the final unchanged JVM task was up-to-date. Lint: **0 errors, 18 nonblocking warnings** (including dependency/resource recommendations).
- APK: `Android/app/build/outputs/apk/debug/localverify-debug.apk`, debug-signed. SHA-256: `1e1c5c1b85ed2b2bb73583793face53e445fbd2519bc9f6d878738efccc9801f`.
- Current emulator coverage: package-scoped implicit `ACTION_SEND`/`ACTION_VIEW` resolution and confirmation; direct Start without consent; ZIP and tar.gz intake; actual document-picker import from Downloads for ZIP and STIX; replacing indicators resets analysis; unsupported input and malformed archives remain failures; skipped entries are not counted as analyzed; Stop and fresh restart; Home/background and screen-lock interruption; completed-case rotation and recreation without replaying imports; explicit reveal and hidden findings after recreation; secure-window flag and absent INTERNET permission; report-only and opt-in original exports with byte equality; forced export-write failure preserves the case and does not launch sharing.
- Fixes during validation: incoming imports are not replayed on activity recreation; native dialogs are dismissed on destruction; provider metadata is read off the UI thread; local atomic reads/writes are synchronized; failure status is retained. Content-only VIEW filtering resolved the app-link lint error. Tests were adapted to native button identifiers, real foreground return after Home/lock, and the current status text.
- Reports: `Android/app/build/reports/androidTests/connected/debug/index.html`, `Android/app/build/outputs/androidTest-results/connected/debug/`, `Android/app/build/reports/tests/testDebugUnitTest/index.html`, and `Android/app/build/reports/lint-results-debug.html`.
- Fixtures, report contents, and exports were generated and inspected inside the disposable emulator. No actual phone diagnostics or evidence were transferred. The test emulator is stopped after this run.

Limits: these are emulator/software results, not physical-device or OEM validation. API 35 remains untested because its AVD image is absent. Real disk-full behavior, hostile provider timeouts, process-death interruption during writes, hardware capture resistance, full upstream Android-MVT parity, and release signing remain outstanding. No export recipient was selected and nothing was published externally.

## Current run log template

Keep one entry per device and test set:

- Date:
- Device model:
- Android version:
- Manufacturer flow used (`Developer options → Bug report` path):
- App build:
- Release artifact path:
- Test case:
- Test bundle:
- Outcome:
- SHA-256:
- Notes:

## Current build and implementation evidence

### Latest local execution notes (2026-09-06)

- Final consent-free build: 17 JVM tests passed across six suites, zero failures/errors/skips; `:app:assembleDebug` succeeded. Current hardening suite has seven tests. Instrumentation was not rerun for this UI.
- Current APK SHA-256: `f2ecd7caa53c3a7593e58ad2b16787cc946cd3cca32f031e57af9e98429d9956`; older hashes and results below are historical.
- Next-agent instructions: [ANDROID-HANDOFF.md](./ANDROID-HANDOFF.md).
- Current product policy: no consent checkbox or gate for local analysis. Start is explicit; export/sharing remains explicit, and including the original remains opt-in. New analysis does not populate `consentConfirmedAt`; older values remain schema-compatible.
- Latest restart-policy validation: 15 JVM tests passed across six suites with zero failures/errors/skips, plus successful debug assembly. `TriageAnalyzerTest` now has three tests; `HardeningTest` contributes five.
- New tests verify immediate Stop and Stop after one matching file, incomplete status, restart from the first file after report reload, no duplicate findings, retained consent, and unchanged archive bytes.
- Latest APK SHA-256: `271b0a4571e25c9c82633d1c7627d33cece56c4d076a663026af90f2607a8b6a`.
- Product decision: keep Stop; defer checkpoint resume. Restart replaces partial results and scans the retained input from the beginning. The UI explains stop-on-background and uses `Start analysis`.
- Emulator results below apply to the earlier UI. Current instrumentation needs adaptation to newer import confirmation/privacy defaults and rerunning; Stop/background/rotation testing remains pending.

Earlier execution evidence:

- `:app:testDebugUnitTest :app:assembleDebug --console=plain`: **BUILD SUCCESSFUL**, exit 0, using Android Studio OpenJDK 21.0.4 and cached Gradle 8.11.1.
- SDK: `/Users/k/Library/Android/sdk`, Platform 34 and Build Tools 34.0.0.
- Eight tests passed across five suites; zero failures, errors, or skips. Matching coverage includes nine synthetic fixture rows.
- APK: `Android/app/build/outputs/apk/debug/localverify-debug.apk` (debug-signed; permanent debug output filename).
- Earlier APK SHA-256: `bd39b85e807c8e24576d1db8716b4ab6ffe52258220d02c54d6a90664f50e3b6` (consent-persistence milestone; superseded above).
- Emulator follow-up: two instrumentation tests passed on Android 16/API 36, alongside all eight JVM tests and debug assembly.
- The successful cached-Gradle builds are confirmed by a new test/build execution. Default-PATH preflight failures are a separate environment-discovery issue, not evidence that builds never ran.

### Execution log entries

- Date: 2026-09-06; final test XML timestamp: `2026-09-06T00:53:34` (as emitted by Gradle).
- Command: cached Gradle 8.11.1 with `:app:testDebugUnitTest :app:assembleDebug --console=plain`; exact environment documented in [ANDROID.md](./ANDROID.md#current-environment-note).
- Initial test blockers: Kotlin compilation errors in fixture loading, archive creation, and nullable source-list access; Android stub JSON methods; missing `process_name` alias; nullable timestamp handling; unsupported-kind fixture not reaching its intended branch.
- Fixes: explicit Gson generic type, `archive.outputStream()`, full-list assertion, test-only Android-compatible JSON library, process alias and timestamp handling, separate unsupported software-kind fixture.
- Final outcome: all eight tests passed and debug APK assembly succeeded.
- Suite results: `ArchivePolicyTest` 2/2, `ArchiveWalkerTest` 3/3, `IndicatorParserParityTest` 1/1, `MatchingParityTest` 1/1 (nine fixture rows), `TriageAnalyzerTest` 1/1.
- Test inputs: generated synthetic archives, inline STIX bundle, and `Android/app/src/test/resources/fixtures/matching.json`; no phone evidence transferred.
- HTML report: `Android/app/build/reports/tests/testDebugUnitTest/index.html`.
- XML reports: `Android/app/build/test-results/testDebugUnitTest/TEST-*.xml`.
- APK hash above was recorded using `openssl dgst -sha256`; the sandbox blocked the Perl runtime used by `shasum`.
- Device validation and signed release packaging were not performed during the initial JVM-only run; see the emulator follow-up below.
- Follow-up tooling run on 2026-09-06: generated the Gradle 8.11.1 wrapper with the published distribution checksum, then ran `./scripts/run-android-plan.sh` successfully with no standalone Gradle on PATH. Preflight verified the wrapper, Java/javac 21.0.4, SDK path, and ADB 1.0.41 (37.0.0-14910828). Debug assembly and unit-test tasks were up-to-date from the passing run; this was a wrapper/workflow validation, not a new test execution.

---

### Emulator execution (2026-09-06)

- AVD: `Trustroots_Pixel_8_API_36`, Android 16/API 36, arm64; serial `emulator-5554`. Started read-only/headless and stopped after the run. No physical device was connected.
- API 35 AVD was unavailable due to a missing system image; API 35 is not validated.
- Command: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug --console=plain` with the documented Java/SDK environment.
- Tests: `SyntheticWorkflowTest.viewImportConsentAnalysisRecreateAndExportWithOriginal` and `SyntheticWorkflowTest.shareImportAnalysisAndReportOnlyExport`.
- First run: both tests failed because the analysis checkpoint overwrote the saved consent timestamp. Fixed by updating the same report instance passed into analysis.
- Final result: two instrumentation tests passed, zero failures/errors/skips, plus eight passing JVM tests; debug APK rebuilt successfully.
- Synthetic fixture: locally generated ZIP with a matching hostname JSON record and a skipped binary entry. Tested content URI intake, consent gate/persistence, structured finding, two analyzed paths, completed-case recreation, and export ZIP contents with/without original bytes.
- Report: `Android/app/build/reports/androidTests/connected/debug/index.html`; XML: `Android/app/build/outputs/androidTest-results/connected/debug/`.
- Tests inspect exports in the emulator and remove their generated cases/fixtures. No actual diagnostic evidence was used or transferred.
- Limits: explicit intents target the activity; this does not verify document-picker or file-manager UI, implicit intent resolution, interrupted analysis, rotation/background, OEM guidance, or physical hardware.

- [x] Archive walker unit test coverage added:
  - `Android/app/src/test/kotlin/org/mobiletriage/localverify/ArchiveWalkerTest.kt`
  - `./scripts/gradle-android.sh :app:testDebugUnitTest` should execute this on CI/local runner.
- [x] Analyzer skip-path coverage added:
  - `Android/app/src/test/kotlin/org/mobiletriage/localverify/TriageAnalyzerTest.kt`
  - Verifies unsupported archive entries are surfaced in report `skipped`.
- [x] Archive safety coverage added:
  - `Android/app/src/test/kotlin/org/mobiletriage/localverify/ArchiveWalkerTest.kt`
  - Verifies zip parser rejects unsafe paths (directory traversal) and malformed magic.
- [x] Archive intake policy coverage added:
  - `Android/app/src/test/kotlin/org/mobiletriage/localverify/ArchivePolicyTest.kt`
  - Verifies extension and MIME allowlist checks used by picker/share intake and manifest-aligned runtime validation.
  - Includes MIME suffix normalization coverage (e.g., `application/zip; charset=UTF-8`).
- [x] Share/picker intake coverage exists in code:
  - `Android/app/src/main/java/org/mobiletriage/localverify/ui/MainActivity.kt` includes picker and `ACTION_SEND`/`ACTION_VIEW` intake.
  - `Android/app/src/main/AndroidManifest.xml` declares `LAUNCHER`, `SEND`, and `VIEW` filters for archive payloads.
- [x] Stream intake robustness improved:
  - `MainActivity` and manifest now validate archive acceptance by filename extension and expanded MIME coverage (`application/zip`, `application/gzip`, `application/x-gzip-compressed`, `application/x-zip-compressed`, `application/x-tar`, `application/tar`, etc.), so shared URIs without filenames can still import supported streams.
- [x] Android Gradle bootstrap and execution scripts added:
  - `Android/scripts/bootstrap-gradle.sh`
  - `Android/scripts/gradle-android.sh`
- [x] Android release packaging helper added:
  - `Android/scripts/package-apk.sh`
- [x] Android environment preflight helper added:
  - `Android/scripts/check-android-env.sh`
- [x] Android plan runner helper added:
  - `Android/scripts/run-android-plan.sh`
  - Current behavior: `run-android-plan.sh` runs `bootstrap-gradle.sh` first, then `check-android-env.sh`.
- [x] Synthetic explicit-intent analysis/export workflow validated on API 36 emulator.
- [ ] Full physical-device and lifecycle validation completed.

## Environment and reproducibility notes

- The default PATH did not expose the working Android toolchain. Set `JAVA_HOME`, `ANDROID_HOME`, and PATH as shown in [ANDROID.md](./ANDROID.md#current-environment-note).
- Repository-local Gradle 8.11.1 wrapper generated with distribution checksum `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`; includes Unix/Windows launchers, JAR, and properties.
- Preflight and full plan runner passed with the configured environment and no standalone Gradle on PATH; unit tests and APK assembly are verified.
- SDK/cache access restrictions required additional access for the successful runs. Do not describe a permission or PATH failure as proof that the installed tools are absent.
- ADB connected to the API 36 emulator and instrumentation passed; physical-device validation remains pending.

`./scripts/check-android-env.sh` enforces required tooling for local build/test:
- fails when both `gradlew` and local `gradle` are missing,
- fails when `java` or `javac` are unavailable or failing,
- treats `adb` as optional until install/debug validation is run,
- reports Android SDK env presence (`ANDROID_HOME` / `ANDROID_SDK_ROOT`) for context.

## Validation tracks

### 0) Tooling/attestation prereq

- [x] `Android/` directory contains a usable Gradle runner (`./scripts/gradle-android.sh` delegates to pinned `./gradlew`)
- [x] `Android/scripts/bootstrap-gradle.sh` and `Android/scripts/gradle-android.sh` are available and point to same execution path.
- [x] JDK 17+ active for Android build tasks (Android Studio OpenJDK 21.0.4)
- [x] API 36 emulator available and used for integration checks; no physical device connected
- [x] `./scripts/check-android-env.sh` baseline checks pass (`gradle >= 8.7`, `java/javac >= 17`, `gradlew` status; `adb` optional for now)
- [x] `./scripts/run-android-plan.sh` executed successfully on branch

### 1) Intake and capture

- [x] ZIP document-picker import (API 36 emulator); tar.gz validated through content-URI open
- [ ] Import via `Share` (`ACTION_SEND`) flow
- [ ] Import via direct open (`ACTION_VIEW`) from file manager
- [x] Explicit `ACTION_SEND` and `ACTION_VIEW` ZIP/content-URI intake into the activity (API 36 emulator)
- [x] Rejected unsupported formats show explicit status (current API 36 emulator)
- [ ] Manufacturer note visibility checks

### 2) Parsing and matching

- [x] `matching.json` parity run against checked-in synthetic fixture matrix (`domain`, `process`, `file`, `url`), nine rows
- [x] Structured matches detected where expected in the checked-in fixtures
- [x] Raw-text matches detected where expected in the checked-in fixtures
- [x] Unsupported archive type listed in `skipped` by `TriageAnalyzerTest`; unsafe paths rejected by `ArchiveWalkerTest`
- [ ] Pinned Android-MVT check list is executed and compared field-by-field
- [x] `:app:testDebugUnitTest` via cached Gradle (all eight tests passed, including `MatchingParityTest` and `IndicatorParserParityTest`)

### 2a) Pinned Android-MVT check list

- [ ] Bug-report intake extension checks (`.zip`, `.gz`, `.tgz`, `.tar.gz`) are explicitly exercised
- [ ] STIX `pattern` types/unsupported kinds are reported consistently in import `skipped`
- [ ] `file:name` and `file:path` aliases resolve from structured JSON records
- [ ] `process` aliases resolve from structured JSON records
- [ ] `url` tokens with protocol/param boundaries are matched in raw text

### 3) Workflow behavior

- [x] Core cancellation retains incomplete results and the archive (synthetic JVM regression tests)
- [x] Starting again clears partial results and rescans every file without duplicate findings (synthetic JVM regression test after report reload)
- [x] Stop button/background/screen-lock interruption and fresh restart validated on current API 36 UI
- Checkpoint resume: deferred, not a release requirement for this version.
- [x] Rotation/background recovery validated on API 36 emulator
- [x] Core analysis runs without a consent timestamp (JVM assertion); consent-gate requirement removed by product decision
- [x] Direct Start without a checkbox validated on current API 36 emulator UI
- [x] Completed-case activity recreation preserves findings and consent timestamp (not an interrupted-run or rotation test)
- [x] Export ZIP includes `report.json` and `report.html` (API 36 emulator)
- [x] Optional original archive inclusion toggles correctly; included bytes equal synthetic input (API 36 emulator)

### 4) Release packaging

- [x] `:app:assembleDebug` passes; `app/build/outputs/apk/debug/localverify-debug.apk` rebuilt with matching fixes
- [x] Helper-script build path verified with configured PATH and repository wrapper
- [ ] `./scripts/gradle-android.sh :app:assembleRelease` passes
- [ ] `./scripts/package-apk.sh app/build/outputs/apk/release/app-release.apk` records SHA-256 for handoff
- [ ] Keystore/signing flow documented for release artifact
- [ ] Final signed APK/hash and changelog retained for handoff

### 5) Release sign-off artifacts

- [ ] Signed release APK exists at a tracked path
- [ ] SHA-256 of release APK recorded in matrix row
- [ ] Test bundle used for parity/unit validation recorded

## Evidence links

- Attach one completed matrix entry per execution cycle.
- Attach a signed artifact path plus test bundle used for the run.
- Keep actual diagnostic archives and extracted evidence on the phone. Use generated synthetic fixtures and non-content progress/timing data for debugging; transferring evidence containers or case exports to the Mac requires explicit permission.

## Next execution order

1. Complete physical-device/OEM validation and remaining fault-injection checks. The current API 36 workflow suite passes; checkpoint resume remains deferred.
2. Complete upstream/OEM validation and release signing, then record the release artifact hash for handoff.
