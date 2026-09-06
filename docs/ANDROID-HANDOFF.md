# Android continuation handoff

> Shared-engine migration requirements and named legacy differences are maintained in [the engine contract guide](ENGINE-EXPERIMENT.md). Existing implementation and historical validation notes below remain platform-specific.

Updated: 2026-09-06

## Continuation completed — current result

The hardening continuation completed the current API 36 workflow suite: 9 instrumentation tests and 17 JVM tests pass; debug assembly and lint succeed (0 lint errors, 18 warnings). APK SHA-256: `1e1c5c1b85ed2b2bb73583793face53e445fbd2519bc9f6d878738efccc9801f`. The disposable emulator used serial `emulator-5584` and is stopped after validation. See [the current validation record](ANDROID-VALIDATION-MATRIX.md#current-hardening-and-workflow-validation--2026-09-06) for exact coverage and remaining limits.

The no-consent and restart-without-resume decisions below are unchanged. Picker navigation, implicit content-URI resolution, import recreation, Stop/background/lock, rotation, and export-failure checks are now covered on the emulator. Physical hardware/OEM checks, full upstream parity, broader fault injection, and release signing remain later work. Changes remain uncommitted in the shared workspace; preserve other contributors' edits when preparing commits.

The original handoff below is retained as historical context; its earlier APK hashes and pending emulator steps are superseded by this record.

## Product decisions

- No consent checkbox or consent gate for local analysis. The explicit Start action is sufficient. Do not reintroduce the gate or synthesize consent timestamps. The optional legacy report field remains for compatibility.
- Keep Stop. Switching apps or locking the screen stops analysis through activity lifecycle handling. The UI explains this.
- Do not implement checkpoint resume for this version. A new attempt scans from the beginning, replaces partial findings/progress, retains the imported archive, and preserves integrity checks. Interrupted reports remain incomplete.
- Sharing requires explicit export. Original archive inclusion is opt-in.
- Debug APK filename remains `localverify-debug.apk`.

## Verified state

- `:app:testDebugUnitTest :app:assembleDebug` passed: 17 JVM tests, six suites, no failures/errors/skips.
- Stop/restart regressions verify incomplete status, reprocessing every file after report reload, no duplicate findings, and unchanged archive bytes.
- APK: `Android/app/build/outputs/apk/debug/localverify-debug.apk`.
- SHA-256: `f2ecd7caa53c3a7593e58ad2b16787cc946cd3cca32f031e57af9e98429d9956`.
- Earlier API 36 instrumentation tests passed on an older UI, not the current confirmation/privacy/no-consent UI. Do not describe those passes as current UI validation.
- Latest restart/no-consent changes have not been committed by this thread. Other Android hardening and unrelated iOS/Swift edits have been made in the shared workspace. Inspect current changes, preserve them, and coordinate overlapping edits before committing.

## Working build commands

From the repository root on this Mac:

```sh
cd Android
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

The repository includes checksum-pinned Gradle 8.11.1. No standalone Gradle install is needed. Android Studio supplies JDK 21.0.4; SDK Platform 34 and Build Tools 34.0.0 are installed. The full `./scripts/run-android-plan.sh` also passed previously. If the sandbox denies SDK/cache access, report an access restriction, not missing tools.

## Next work

1. Adapt `SyntheticWorkflowTest` to the current UI: handle the native import confirmation, direct Start without consent, opt-in original export, skipped-versus-analyzed counts, scrolling, and the current export storage behavior. Check activity recreation does not replay the incoming import and replace results. Keep assertions meaningful rather than deleting failing checks.
2. Run instrumentation on a disposable API 36 emulator without existing Local Verify cases. `Trustroots_Pixel_8_API_36` worked with `-read-only -no-snapshot-save -no-window -no-audio`; stop only the emulator you start when finished. The API 35 AVD has a missing system image.
3. Validate picker navigation, implicit share/open resolution, unsupported and malformed inputs, Stop then fresh restart, screen lock/background, rotation, and export failures with synthetic fixtures. Fix issues and add focused regression coverage.
4. Update `docs/ANDROID.md` and `docs/ANDROID-VALIDATION-MATRIX.md` with exact results, clearly separating JVM, emulator, physical-device, and historical evidence.
5. Physical-device/OEM instructions, upstream parity, and release signing remain later work. Do not publish or share artifacts externally without authorization.

For emulator tests, select the intended emulator serial explicitly:

```sh
adb devices -l
export ANDROID_SERIAL=emulator-5554
./gradlew :app:connectedDebugAndroidTest --console=plain
```

Use the actual serial from the device listing. Generate fixtures in the disposable emulator; never transfer actual phone diagnostic archives, extracted evidence, evidence containers, or case exports to the Mac without explicit permission. Ordinary debugging or installation does not authorize that transfer.
