# Verification and release gate

Automated core tests cover equality/unsupported STIX patterns, structured versus raw matches, text boundaries, path safety, HTML escaping, archive analysis and resume, unchanged original hashes, ZIP integrity, and truncated archives. Fixtures are synthetic and establish software behavior only.

An optional real-archive smoke test is available for a locally stored, authorized sysdiagnose archive. It is skipped by default so the repository neither distributes nor requires device evidence:

```
TRIAGE_REAL_SYSDIAGNOSE=/absolute/path/to/sysdiagnose.tar.gz swift test --filter TriageCoreTests/testRealSysdiagnoseWhenProvided
```

The local SAF iOS 15 sample used during initial verification was obtained from [EC-DIGIT's sysdiagnose test-data repository](https://github.com/EC-DIGIT-CSIRC/sysdiagnose-testdata), verified against its Git LFS SHA-256 `4491d5e4b6f4349311df3b3fc671f1dd040c8ccda9f97e3a0debef151e613114`, and kept under ignored `TestData/`. Download it with `tools/download_saf_sysdiagnose.sh`.

## Recorded checks — 2026-09-05

- Native iOS app and share extension compile with Xcode 26.6 for the iOS simulator.
- App installed and launched on the iPhone 17 Pro / iOS 26.5 simulator; initial screen visually inspected.
- Swift core tests pass, including cancellation and refusal to resume changed evidence.
- Unsigned iPhone Release archive builds at `build/LocalVerify.xcarchive`; it requires provisioning/signing before installation on a phone.
- Files-only build signed with Trustroots Foundation, installed and launched on the paired iPhone 15 Pro Max (`ip2`) on 2026-09-05.
- 10 Swift core tests pass, including explicit backup exclusion for local files. Device installation/launch does not yet establish real sysdiagnose analysis coverage.
- Real sysdiagnose sample evaluation and confirmed-positive validation remain outstanding.
- Collection-guide/import update installed and launched on `ip2`. Two simulator UI tests pass: the consent-gated archive picker and indicator picker both open, and the offline collection guide is reachable.
- Fixed competing SwiftUI fileImporter presenters by using one routed picker. The Files staging folder is exposed separately from private cases.
- Bundled-indicator update: 16 core tests executed with zero failures (one optional real-archive test skipped), including a live public-feed download. Three simulator UI tests pass, covering the bottom tabs, license screen, bundled indicator count, date format and MB size, both import pickers, and the offline collection guide.
- Publisher snapshot check confirmed revision `3d8f248a0d015f183724ae7d096a5c46a8bb5fc7`. Bundled definitions total 1,486,428 bytes, with 1,862 supported and 30 explicitly skipped indicators. These checks establish parsing and update behavior, not detection effectiveness.
- This bundled-indicator/tab update was signed with Trustroots Foundation, reinstalled on `ip2`, and successfully launched. This remains the Files-only build; physical-device airplane-mode analysis/export validation is still a separate release gate.

### Newer definitions and Settings shortcut — 2026-09-05

- Added pinned MVT Predator, Coruna and DarkSword collections from revision `b22ddf05e1a31e7732b8895676987c5c3482ef65`, preserving the existing Amnesty bundles. Total: 2,331,191 bytes, 2,336 unique supported indicators, 55 skipped. Newest STIX indicator date: 2026-03-30 00:00 UTC. Publisher timestamps are not replaced with download dates.
- 18 core tests run, zero failures, one optional real-archive test skipped. Includes traceable seeded matches from both 2026 collections, benign domain-suffix checks, all-or-nothing five-feed live download, de-duplication and cache upgrade while preserving manual imports. This is behavior validation, not confirmed-positive detection validation.
- Collection-guide and Settings-navigation simulator tests pass. The simulator omits AssistiveTouch and lands on the Accessibility root; the test explicitly checks this fallback. The app uses Apple's public iOS 26+ AssistiveTouch API, retains manual instructions, and does not toggle any settings. Exact physical-device destination still needs a user check. Analytics Data has no documented public deep link.

### Remaining release gate

Before investigator deployment, complete and record:

- Physical iPhone import through Files and share extension, airplane-mode analysis/export.
- Consented or sanitized sysdiagnose archives from each supported OS version; document supported and missing formats.
- A representative large archive, low storage, locked-device data protection, cancellation, termination/relaunch, and resume.
- Benign corpus false-match review; consented confirmed-positive evidence where available.
- Case/inbox deletion and backup exclusion; exported copies remain under recipient control.
- Registered-device signing/provisioning and source delivery.

No real-world detection effectiveness is claimed before this gate. Unknown formats are reported as skipped, never counted as checked. Android has its own equivalent gate in ANDROID.md.
