# Verification and release gate

> Shared-engine migration requirements and named legacy differences are maintained in [the engine contract guide](ENGINE-EXPERIMENT.md). Existing implementation and historical validation notes below remain platform-specific.

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

### Analysis stall regression — 2026-09-05

- Reproduced slow scanning with a generated 248,000-byte log and all 2,336 indicators: approximately 7.61 seconds before the change, 0.04 seconds afterward on the development Mac (debug build). A generated 15,500,000-byte log completed in approximately 2.10 seconds. These are synthetic software timings, not measured iPhone throughput.
- Matching now indexes sought ASCII tokens and recognized structured fields, retaining regex matching for candidate and non-token literals. Regression tests compare Unicode/case/boundary behavior and cover cancellation while indexing.
- Progress reports hashing, decompression (including skipped entries), and work inside a file, throttled to four updates per second. Checkpoint writes remain separate from visual progress.
- 23 core tests executed, zero failures; optional live network and real-archive tests skipped. No actual phone sysdiagnose archives, extracted evidence, case containers, or exports were copied to the MacBook. This restriction is recorded in AGENTS.md.

### Case copying, campaign filters and progress — 2026-09-05

- Added individual excerpt and all-payload copy actions with source context, plus JSON case copying. Clipboard items are local-only and expire after five minutes; pasted copies are independent.
- Added campaign filtering, a larger progress panel, automatic case navigation after analysis, and recorded archive filename and analysis timestamps. Older missing metadata is shown as unavailable.
- 29 core tests executed with zero failures and two optional tests skipped. Synthetic simulator UI checks passed for automatic case navigation, filename display, case/all/individual copying, DarkSword filtering, and a progress panel taller than 200 points with a reachable Cancel button.
- Signed iPhone build succeeded. These checks used synthetic fixtures only; no phone evidence was transferred to the MacBook. Physical-device workflow and detection-effectiveness gates below remain outstanding.

### Remaining physical-device checks

Before investigator deployment, complete and record:

- Physical iPhone import through Files and share extension, airplane-mode analysis/export.
- Consented or sanitized sysdiagnose archives from each supported OS version; document supported and missing formats.
- A representative large archive, low storage, locked-device data protection, cancellation, termination/relaunch, and resume.
- Benign corpus false-match review; consented confirmed-positive evidence where available.
- Case/inbox deletion and backup exclusion; exported copies remain under recipient control.
- Registered-device signing/provisioning and source delivery.

No real-world detection effectiveness is claimed before this gate. Unknown formats are reported as skipped, never counted as checked. Android has its own equivalent gate in ANDROID.md.


### Hardening regression checks — 2026-09-06

- Swift synthetic suite: 46 tests passed with no failures after parser/indicator budgets, partial finding retention, bounded local reads, and integrity rechecking. New adversarial cases cover deep JSON (including an IPS body), excessive lines/bytes, dense-match limits with repeated analysis, neutral progress, and final checkpoint write failure.
- iPhone 17 Pro / iOS 26.5 simulator: app build succeeded; two targeted UI tests passed for explicit Cases review/copy/filter behavior and the neutral progress panel. The privacy shield is included in this build. Physical-device background snapshots, locked storage, and capture behavior still require validation.
- Offline source policy passed. No actual phone diagnostics, extracted evidence, case containers, or exports were transferred.
- This is software hardening evidence, not real-world spyware detection validation. Android has separate current results in [ANDROID-VALIDATION-MATRIX.md](ANDROID-VALIDATION-MATRIX.md).


### Shared contract adoption — 2026-09-06

Canonical matching, STIX and budget vectors are now consumed by both production test suites. Named legacy expectations preserve current behavior. Experiment measurements and their limits are recorded separately in [ADR-001](ADR-001-shared-engine.md); they do not supersede physical-device release gates above.

### Promoted shared record engine — 2026-09-06

The standalone KMP module passed both native binding suites, common budget tests and legacy app regressions. Detailed counts, synthetic timings, the pinned Unicode integration finding and migration activation stages are recorded in [SHARED-RECORD-ENGINE.md](SHARED-RECORD-ENGINE.md). These results leave the existing physical-device and full-pipeline gates intact.
