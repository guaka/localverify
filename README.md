# Local Verify

> Shared-engine migration requirements and named legacy differences are maintained in [the engine contract guide](docs/ENGINE-EXPERIMENT.md). Existing implementation and historical validation notes below remain platform-specific.

<img src="iOS/App/Assets.xcassets/AppIcon.appiconset/app-icon.png" alt="Local Verify app icon" width="128">

Local Verify is an experimental, local-only app for iOS 17+ and Android 11+ for importing and reviewing diagnostic archives: iOS sysdiagnose files and Android bug reports. It has no backend, telemetry, or evidence upload.

The app bundles a snapshot of Amnesty/MVT indicators for offline analysis. It is not full MVT parity and does not provide comprehensive current-spyware coverage. Results are leads for investigation, not proof of compromise.

## Build

Byte-for-byte reproducible builds are not yet verified. See [current status and next steps](docs/REPRODUCIBLEBUILDS.md).

### iOS

Requires Xcode 16+ and Python 3 for project generation and offline source checks. Python is not included in the app.

```sh
python3 tools/generate_project.py
swift test
xcodebuild -project LocalVerify.xcodeproj \
  -scheme LocalVerify \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO build
```

Open `LocalVerify.xcodeproj` in Xcode to run the app. Physical-device builds need a development team, a registered device, and the App Group `group.org.mobiletriage.private` configured for both targets.

For a Files-only build without App Groups or the share extension, run `python3 tools/generate_project.py --local-only` and open `LocalVerifyLocal.xcodeproj`. Use Save to Files → On My iPhone, then import in Local Verify. Physical-device installation still requires signing.

Distribute the source with private builds, including LICENSE and third-party notices.

### Android

The native Android app lives in `Android/` and uses Kotlin and Jetpack Compose. It supports Android 11+ (API 30+) and has Scan, Cases, Indicators, and About tabs.

Requires JDK 17+, Android SDK Platform 34, and an SDK location configured through `ANDROID_HOME` or `Android/local.properties`. Android Studio can provide the JDK and SDK. The repository includes the Gradle wrapper; build dependencies may require internet access even though the app processes evidence offline.

From the repository root:

```sh
cd Android
./gradlew :app:assembleDebug
```

The debug APK is written to `Android/app/build/outputs/apk/debug/localverify-debug.apk` (relative to the repository root).

To install on a connected Android 11+ device with USB debugging enabled, run from `Android/`:

```sh
./gradlew :app:installDebug
```

Android supports archive selection through the system document picker and incoming share/open intents. Cases and incomplete results are stored locally; report ZIP exports include JSON and HTML, with the original archive included only when selected.

Physical-device and manufacturer-specific collection guidance still need validation, and full upstream Android-MVT parity is not established. See the [Android implementation notes](docs/ANDROID.md) for setup and release-signing details, and the [Android validation matrix](docs/ANDROID-VALIDATION-MATRIX.md) for recorded test coverage and remaining gaps.

## Tests and coverage

### iOS and shared Swift core

Run the Swift package tests with:

```sh
swift test
```

Generate a SwiftPM coverage summary and LCOV file with:

```sh
./tools/coverage.sh
```

Reports are written to:

- `build/coverage-reports/swiftpm-coverage.txt`
- `build/coverage-reports/swiftpm-coverage.lcov`

The SwiftPM report excludes generated SwiftPM test harness files and test-source files so the totals focus on production coverage.

Generate coverage for the iOS UI tests on a simulator with:

```sh
./tools/coverage.sh ios \
  --destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

The script generates the Files-only UI-test project. The iOS report is written to `build/coverage-reports/ios-ui-coverage.txt`; each run keeps a separate raw result bundle under `build/coverage-reports/ios-run.*/results.xcresult`.

UI regression checks are generated with:

```sh
python3 tools/generate_project.py --local-only --ui-tests
```

Run the generated checks with the `LocalVerifyChecks` scheme in Xcode.

### Android

From `Android/`, run JVM tests and lint with:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Run workflow tests only on a disposable emulator without existing Local Verify cases. Select its serial from `adb devices`:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

These tests use synthetic evidence. Do not use a phone containing real cases as the instrumentation test target. JVM reports are written under `Android/app/build/reports/tests/`; emulator reports are under `Android/app/build/reports/androidTests/`.

## Using the app

### iOS

1. Follow the in-app guide to collect a sysdiagnose using Apple-supported settings.
2. Save it to `On My iPhone > Local Verify > Imports`.
3. Use bundled indicators, or import a local STIX indicator file before importing the archive.
4. Review findings and export a report ZIP manually.

Imported iOS cases keep their indicator set so resumed analysis is reproducible.

### Android

1. Follow the in-app collection guidance to create a bug report. The available settings and collection steps vary by manufacturer.
2. Save the archive locally, then select it in Scan or explicitly share/open it with Local Verify. ZIP and gzip-compressed tar archives are supported.
3. Use the bundled indicators, or import a local STIX2 bundle in Indicators before creating the case.
4. Start analysis and keep the app in the foreground with the screen unlocked. Switching apps or locking the screen stops analysis; incomplete results remain available, but starting again scans from the beginning.
5. Open Cases to review findings, errors, and skipped files. Prepare a report ZIP and share it explicitly; including the original archive is optional.

On both platforms, case storage is excluded from automatic backup, and deleting a case deletes its stored exports. Evidence upload is never automatic. Copies saved or shared outside the app remain separate.

### Synthetic smoke test

For a safe end-to-end smoke test, generate the synthetic fixtures:

```sh
python3 tools/generate_fixture.py
```

The generated archive contains no real device data. Transfer `Fixtures/synthetic-indicators.stix2` and `Fixtures/synthetic-sysdiagnose.tar.gz` to Files on iOS or local storage accessible to Android's document picker. Import the synthetic indicators first, then the archive, and expect one raw-text lead and one structured lead. Use “Use bundled indicators” afterward to restore the bundled definitions.

## Supported data and limits

Both apps scan supported UTF-8 `.ips`, `.json`, `.crash`, `.log`, and `.txt` archive entries. iOS accepts gzip-compressed tar archives; Android also accepts ZIP containers. Binary unified logs, binary plists, links, and other unsupported entries are not analyzed as text.

Limits include 8 GiB expanded input, 100,000 archive entries, 16 MiB per parsed file, and 10,000 findings. Exports use standard ZIP and must remain under 4 GiB.

STIX matching is intentionally limited to single equality expressions for domains, URLs, processes, and file paths/names. Matching is case-insensitive for domains and exact for other values. No malicious-domain lookups or network requests are performed.

See the detailed [verification](docs/VERIFICATION.md), [report contract](docs/REPORT.md), [privacy](docs/PRIVACY.md), and [indicator update](docs/INDICATOR_UPDATES.md) documentation for more detail.

## License

Local Verify's project-owned source is licensed under the GNU Affero General Public
License, version 3 or (at your option) any later version (`AGPL-3.0-or-later`).
See [LICENSE](LICENSE) for the full terms. The software is provided without warranty.

Third-party components and indicator datasets retain their own licenses and notices;
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Distributions must provide
recipients access to the corresponding source as required by the AGPL.

## Shared-engine development

Install the pinned development CLI with `npm ci`, then use `npm run spec --` and `npm run spec:validate`. The wrapper disables OpenSpec telemetry. Repository-local assistant skills are in `.agents/skills/`.

The [engine contract guide](docs/ENGINE-EXPERIMENT.md) records shared requirements and legacy differences. The [architecture decision](docs/ADR-001-shared-engine.md) compares the isolated Rust and Kotlin prototypes and stages the future migration. Production apps continue to use their existing engines.

The selected KMP engine is promoted in [Shared](Shared/README.md), with typed results, bounded token indexing, publisher metadata and legacy cache handling. The original experiment remains frozen. Archive processing, report compatibility and activation in both native apps remain separate OpenSpec stages.
