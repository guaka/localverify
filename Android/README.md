# Local Verify Android

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
- Resume behavior using checkpoints when re-running incomplete cases.
- Case report model aligned with the iOS report contract (`platform = android`).
- Export ZIP (`report.json`, `report.html`, optional original archive).
- Internal backup exclusion via manifest/private storage policy.
- Coverage matrix persistence (`app/src/main/assets/fixtures/matching.json` is vendored for parity testing).

## Run in Android Studio

1. Open `Android/` as a project.
2. Install Android Studio/SDK toolchain with Gradle 8.7+ available locally.
3. Ensure Java 17 is active on PATH.
   - macOS: `brew install --cask temurin` (or another JDK 17 distribution), then `brew install gradle`
   - Windows/macOS/Linux: install Java 17 and Gradle 8.7+ via your preferred package manager
   - Or use Android Studio's bundled/managed Gradle setup.
4. Bootstrap the Gradle wrapper once (if needed):
   - `./scripts/bootstrap-gradle.sh`
   - The script requires Gradle >= 8.7 and will validate wrapper generation inputs.
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

This implementation is intentionally conservative and intentionally mirrors iOS behavior,
so there is no telemetry and no cloud uploads.
