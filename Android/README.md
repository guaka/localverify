# Local Verify Android

> Shared-engine migration requirements and named legacy differences are maintained in [the engine contract guide](../docs/ENGINE-EXPERIMENT.md). Existing implementation and historical validation notes below remain platform-specific.

This is a Kotlin/Jetpack Compose native Android implementation started from
`docs/ANDROID.md`.

## Current scope implemented

- Android 11+ native import path for bug-report exports (document picker + shared `ACTION_SEND/ACTION_VIEW` intake).
- Manual import of STIX indicator bundles (plus bundled demo indicators).
- Streaming parser for `.zip` archives and `.tar.gz` streams with limits and skip reasons:
  - max expanded size 8 GiB
  - max entries 100,000
  - max per-entry parser size 16 MiB
  - supported text extensions: `ips`, `json`, `log`, `txt`, `crash`
  - skipped unsupported entries and unsafe paths
- Cancellation-aware analysis with on-disk checkpoint after each analyzed file.
- Stop retains an incomplete report and the imported archive. Starting again rescans from the beginning and replaces partial results; checkpoint resume is deferred.
- Case report model aligned with the iOS report contract (`platform = android`).
- Export ZIP (`report.json`, `report.html`, optional original archive).
- Internal backup exclusion via manifest/private storage policy.
- Local analysis starts directly when the user taps Start; no consent checkbox. Sharing requires an explicit export action, with original archive inclusion opt-in.
- Coverage matrix persistence (canonical `../Fixtures/matching.json` is packaged for parity testing).

## Run in Android Studio

1. Open `Android/` as a project.
2. Install Android Studio and SDK Platform 34 / Build Tools 34.0.0.
3. Configure a JDK 17+ and SDK path. Android Studio's bundled JDK 21 is verified on this Mac.
4. Use the included checksum-pinned Gradle 8.11.1 wrapper. No separate Gradle installation is needed; the first run downloads its distribution if it is not cached.
5. Optional preflight sanity check:
   - `./scripts/check-android-env.sh`
6. Run the full execution path end-to-end:
   - `./scripts/run-android-plan.sh`
7. Or run parity checks directly:
   - `./scripts/gradle-android.sh :app:testDebugUnitTest`
8. Install on an Android 11+ device for manual workflow testing.
9. Capture each validation cycle in `docs/ANDROID-VALIDATION-MATRIX.md`.
10. Export signed release artifacts via Gradle assemble tasks for handoff.
   - Set `LOCALVERIFY_RELEASE_KEYSTORE`, `LOCALVERIFY_RELEASE_STORE_PASSWORD`, `LOCALVERIFY_RELEASE_KEY_ALIAS`, and `LOCALVERIFY_RELEASE_KEY_PASSWORD` for signed release output.
   - Optional: capture release hash and size:
     - `./scripts/package-apk.sh app/build/outputs/apk/release/app-release.apk`

## Notes

From `Android/` on this Mac:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./scripts/run-android-plan.sh
```

Debug APK: `app/build/outputs/apk/debug/localverify-debug.apk`.
For direct build and test execution, use `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
The bootstrap helper is retained for wrapper recovery; its default version is 8.11.1.

Synthetic UI tests: on a disposable emulator without existing Local Verify cases, set
`ANDROID_SERIAL` to its serial and run `./gradlew :app:connectedDebugAndroidTest`.
Two tests target explicit ZIP share/open intake, direct analysis, activity recreation,
and export with/without the original. They generate and inspect fixtures inside the
emulator. Do not run them on a phone containing actual evidence.

This implementation is intentionally conservative and intentionally mirrors iOS behavior,
so there is no telemetry and no cloud uploads.
