# Android validation matrix and release checklist

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

- `:app:testDebugUnitTest :app:assembleDebug --console=plain`: **BUILD SUCCESSFUL**, exit 0, using Android Studio OpenJDK 21.0.4 and cached Gradle 8.11.1.
- SDK: `/Users/k/Library/Android/sdk`, Platform 34 and Build Tools 34.0.0.
- Eight tests passed across five suites; zero failures, errors, or skips. Matching coverage includes nine synthetic fixture rows.
- APK: `Android/app/build/outputs/apk/debug/localverify-debug.apk` (debug-signed; permanent debug output filename).
- Latest APK SHA-256: `bd39b85e807c8e24576d1db8716b4ab6ffe52258220d02c54d6a90664f50e3b6` (consent-persistence fix included).
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

- [ ] Import via document picker with `.zip`/`.tar.gz`
- [ ] Import via `Share` (`ACTION_SEND`) flow
- [ ] Import via direct open (`ACTION_VIEW`) from file manager
- [x] Explicit `ACTION_SEND` and `ACTION_VIEW` ZIP/content-URI intake into the activity (API 36 emulator)
- [ ] Rejected unsupported formats show explicit status/toast
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

- [ ] Cancel request interrupts and persists checkpoint
- [ ] Resume from interrupted checkpoint continues from last analyzed entry
- [ ] Rotation/background does not crash; state remains recoverable
- [x] `consentConfirmedAt` is populated on first analysis start and survives checkpointing (API 36 emulator)
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

1. Complete the remaining intake/lifecycle checks: picker UI, implicit intents, other formats, cancellation/resume, rotation/background, and export failures. Two API 36 synthetic workflow tests now pass; physical-device checks remain pending.
2. Complete upstream/OEM validation and release signing, then record the release artifact hash for handoff.
