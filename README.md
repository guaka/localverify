# Local Verify — native iOS investigator prototype

<img src="iOS/App/Assets.xcassets/AppIcon.appiconset/app-icon.png" alt="Local Verify app icon" width="128">

Swift/SwiftUI iOS 17+ application and import-only share extension. Processing is local; no backend or telemetry. Android remains a documented second phase in [docs/ANDROID.md](docs/ANDROID.md).

**Experimental:** this is a focused diagnostics parser, not full MVT parity. The app bundles 1.49 MB of Amnesty Pegasus and Predator/Cytrox indicators for offline use. It supports 1,862 definitions in the current snapshot and explicitly skips 30 unsupported definitions. Historical indicators do not provide comprehensive current-spyware coverage. Device installation and synthetic tests are documented separately from real-world detection validation.

## Build

Requires Xcode 16+ and Python 3 only for deterministic project generation (no Python in the app).

```
python3 tools/generate_project.py
swift test
xcodebuild -project LocalVerify.xcodeproj -scheme LocalVerify -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Open `LocalVerify.xcodeproj` in Xcode. For installation on a physical iPhone, select your development team for both targets, register the phone, and provision the App Group `group.org.mobiletriage.private`. If changing identifiers, update both Swift app-group strings and `iOS/LocalVerify.entitlements`, plus the generator's bundle identifiers. Build and run the LocalVerify scheme. Unsigned simulator/device builds cannot be installed as provisioned private device builds.

Distribute the source with private builds, including LICENSE and third-party notices. Source availability is part of distribution, not an in-app network dependency.

## Workflow

### Files-only iPhone build

`python3 tools/generate_project.py --local-only` creates `LocalVerifyLocal.xcodeproj`. This preserves the full project but omits App Groups and the share extension, allowing signing with an existing wildcard development profile. Use Save to Files → On My iPhone, then import in Local Verify. The full share extension requires an App Groups-enabled profile and a signed-in Xcode developer account to provision it.

The Files-only build was installed and launched on `ip2` using Trustroots Foundation (`SUJ594N47C`). Rebuild with automatic signing and `DEVELOPMENT_TEAM=SUJ594N47C`; the existing profile is Xcode-managed, so do not force manual signing. The signed app is under `build/iphone-local/Build/Products/Debug-iphoneos/LocalVerify.app`.

No network upload code is included. See [privacy details](docs/PRIVACY.md).

The home screen includes an offline **How to collect and export sysdiagnose** guide. Its local save destination is **On My iPhone → Local Verify → Imports**. Import opens the system file picker; it does not generate diagnostics. Archive and indicator selection intentionally use a single picker presentation to avoid competing SwiftUI import sheets.

UI regression checks can be generated with `python3 tools/generate_project.py --local-only --ui-tests` and run using the `LocalVerifyChecks` scheme on an iPhone simulator.

Confirm data-owner consent, follow the collection guide, and import a sysdiagnose archive. Imported cases freeze their indicator set so resumed analysis remains reproducible. Results distinguish structured matches from contextual raw-text matches; every lead points to a file and record. Share a report ZIP manually, optionally with original evidence. Delete cases in their detail screen.

Cases are protected and excluded from cloud backup. Exports stored in a case are deleted with it; copies already shared elsewhere are not. Shared inbox archives are retained until analyzed or deleted. Analysis resumes completed files, re-reading the archive from its beginning; background execution is not guaranteed.

## Limits

Supports UTF-8 `.ips`, `.json`, `.crash`, `.log`, and `.txt` regular tar entries. JSON field matching recognizes process/path/domain/URL fields; other occurrences are text-only leads. Binary unified logs, binary plists, PAX/GNU long-name entries, links, and other formats are explicitly skipped. Up to 8 GiB expanded, 100,000 entries, 16 MiB per parsed file, and 10,000 findings. Exports use standard ZIP, not ZIP64 (under 4 GiB).

STIX support is intentionally restricted to single equality expressions for domain-name:value, url:value, process:name, file:path, and file:name. Compound expressions, escaped literals, other indicator types, revoked indicators, and valid_until-qualified indicators are reported unsupported. Domain matching is case-insensitive; other matching is exact. No malicious-domain lookups or network requests occur.

See [verification](docs/VERIFICATION.md) and [report contract](docs/REPORT.md).

For a synthetic manual test, run `python3 tools/generate_fixture.py`, transfer `Fixtures/synthetic-sysdiagnose.tar.gz` and `Fixtures/synthetic-indicators.stix2` to Files, import those test indicators, and then import the archive. Expect two leads: one raw-text and one structured. Use bundled indicators restores the real definitions afterward. The synthetic archive contains no real device data.

See [indicator update tools](docs/INDICATOR_UPDATES.md) for checking, refreshing, and pinning bundled publisher revisions. Optional in-app updates download only definitions; no archives or findings are uploaded.
