# Local Verify

<img src="iOS/App/Assets.xcassets/AppIcon.appiconset/app-icon.png" alt="Local Verify app icon" width="128">

Local Verify is an experimental, local-only iOS 17+ app for importing and reviewing sysdiagnose archives. It has no backend, telemetry, or evidence upload. Android support is planned separately in [docs/ANDROID.md](docs/ANDROID.md).

The app bundles a snapshot of Amnesty/MVT indicators for offline analysis. It is not full MVT parity and does not provide comprehensive current-spyware coverage. Results are leads for investigation, not proof of compromise.

## Build

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

## Tests and coverage

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

## Using the app

1. Follow the in-app guide to collect a sysdiagnose using Apple-supported settings.
2. Save it to `On My iPhone > Local Verify > Imports`.
3. Use bundled indicators, or import a local STIX indicator file before importing the archive.
4. Review findings and export a report ZIP manually.

Imported cases keep their indicator set so resumed analysis is reproducible. Cases are protected from cloud backup, and deleting a case deletes its stored exports. The app never imports or uploads evidence automatically.

For a safe end-to-end smoke test, generate the synthetic fixtures:

```sh
python3 tools/generate_fixture.py
```

The generated archive contains no real device data. Transfer `Fixtures/synthetic-indicators.stix2` and `Fixtures/synthetic-sysdiagnose.tar.gz` to Files. Import the synthetic indicators first, then the archive, and expect one raw-text lead and one structured lead. Use “Use bundled indicators” afterward to restore the bundled definitions.

## Supported data and limits

The parser supports UTF-8 `.ips`, `.json`, `.crash`, `.log`, and `.txt` regular tar entries. It skips binary unified logs, binary plists, links, unsupported tar formats, and other non-text entries.

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
