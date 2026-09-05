# Verification and release gate

Automated core tests cover equality/unsupported STIX patterns, structured versus raw matches, text boundaries, path safety, HTML escaping, archive analysis and resume, unchanged original hashes, ZIP integrity, and truncated archives. Fixtures are synthetic and establish software behavior only.

## Recorded checks — 2026-09-05

- Native iOS app and share extension compile with Xcode 26.6 for the iOS simulator.
- App installed and launched on the iPhone 17 Pro / iOS 26.5 simulator; initial screen visually inspected.
- Swift core tests pass, including cancellation and refusal to resume changed evidence.
- Unsigned iPhone Release archive builds at `build/Triage.xcarchive`; it requires provisioning/signing before installation on a phone.
- Files-only build signed with Trustroots Foundation, installed and launched on the paired iPhone 15 Pro Max (`ip2`) on 2026-09-05.
- 10 Swift core tests pass, including explicit backup exclusion for local files. Device installation/launch does not yet establish real sysdiagnose analysis coverage.
- Real sysdiagnose sample evaluation and confirmed-positive validation remain outstanding.

Before investigator deployment, complete and record:

- Physical iPhone import through Files and share extension, airplane-mode analysis/export.
- Consented or sanitized sysdiagnose archives from each supported OS version; document supported and missing formats.
- A representative large archive, low storage, locked-device data protection, cancellation, termination/relaunch, and resume.
- Benign corpus false-match review; consented confirmed-positive evidence where available.
- Case/inbox deletion and backup exclusion; exported copies remain under recipient control.
- Registered-device signing/provisioning and source delivery.

No real-world detection effectiveness is claimed before this gate. Unknown formats are reported as skipped, never counted as checked. Android has its own equivalent gate in ANDROID.md.
