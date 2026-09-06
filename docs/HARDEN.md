# LocalVerify hardening plan

Status: implementation in progress; the plan below includes work still outstanding. Scope: iOS and the developing Android module. The baseline below describes iOS; Android parity must be verified separately against [ANDROID.md](ANDROID.md) and its [validation matrix](ANDROID-VALIDATION-MATRIX.md).

LocalVerify should remain useful when inputs are malicious, report incomplete analysis honestly, and avoid unnecessarily revealing findings. It cannot guarantee correct or secret execution when the attacker controls the OS or LocalVerify's process. “No matches” must never become “this phone is safe.”

## 1. Define the adversary by capability

These levels describe access, not particular malware families. An attacker may gain capabilities during a scan.

| Level | Attacker capability | Achievable objective |
| --- | --- | --- |
| 1: External observer | Observes network traffic, cloud copies, or shared artifacts. | No analysis traffic or automatic disclosure of results; explicit, local workflows. |
| 2: Confined malicious app or input producer | Supplies crafted archives/logs/indicators or manipulates records it can legitimately write; platform isolation still holds. | Resist parser attacks and resource exhaustion; protect private cases through platform isolation; distinguish matches from planted evidence. |
| 3: Privileged observer or targeted tampering | Reads screens/files beyond normal sandbox access, hooks LocalVerify, alters diagnostics, or terminates it. | Reduce avoidable exposure and detect some inconsistencies. Correctness, availability, and result confidentiality become conditional. |
| 4: OS or kernel control | Falsifies file reads, clocks, APIs, execution, and displayed results. | No trustworthy on-device verdict or stealth guarantee. Independent analysis on a trusted system can check computation, but cannot recover evidence already suppressed at collection. |

Apple's [runtime security model](https://support.apple.com/en-gb/guide/security/sec15bfe098e/web) provides the sandbox and entitlement baseline. Our higher levels explicitly assume those boundaries have been bypassed.

On Android, a malicious app with user-granted accessibility or other special access may gain some level-3 observation/control without a kernel exploit. Treat effective capabilities as the boundary; neither a rooted phone nor an enabled accessibility service alone proves infection.

## 2. Preserve the existing baseline

The current app has no network analysis client or telemetry; it uses bundled or locally imported indicators, freezes case indicator snapshots, applies complete file protection and backup exclusion, and checks original hashes on resume/export. Archive parsing streams bounded payloads without extracting files. Clipboard and export actions require user interaction.

Keep these properties as release requirements. They reduce exposure under an intact OS; hashes establish byte consistency, not authentic diagnostics, and file protection does not protect plaintext inside a compromised running process. The source-only offline check is a regression guard, not a runtime firewall. See [PRIVACY.md](PRIVACY.md) and [VERIFICATION.md](VERIFICATION.md).

## 3. P0 — Make hostile input and interruption safe

Primary targets: `Archive.swift`, `Analyzer.swift`, `Models.swift`, `LocalStorage.swift`, and both import entry points.

- **Bound every amplification step.** Review compressed input size, decompression, JSON depth/node count, line/token length, indicator count/value size, accumulated findings, and report/checkpoint size. Existing archive limits alone do not bound parser memory or CPU. Enforce budgets while accumulating data; a pathological file must end as a recorded limit failure, not a crash or successful empty scan.
- **Treat every imported byte as attacker-controlled.** Fuzz gzip/tar, STIX, structured logs, resume state, and export rendering with synthetic inputs. Cover corrupt trailers, duplicate/ambiguous paths, Unicode/control characters, deep JSON, huge lines, and dense matches. Keep evidence text inert: no active links, remote resources, or executable report content.
- **Bind analysis to one stable input.** Finish a private copy before parsing; prevent concurrent replacement, and review the gap between hashing and reopening a file. Validate checkpoint schema, case identity, input/indicator digests, and analyzer version before trusting saved progress. A same-device checksum cannot defeat an attacker who can rewrite both data and checksums.
- **Make interruption explicit.** Exercise storage exhaustion, lock/unlock, cancellation, process death, and failed writes at each stage. Publish checkpoints atomically and mark results complete only after successful final validation and persistence. Preserve earlier leads when later parsing fails, with an unmistakable partial status. Never treat skipped, absent, corrupt, or unsupported evidence as checked.

Acceptance: a repeatable hostile synthetic corpus produces bounded completion or explicit partial/error results; interrupted/resumed runs agree with uninterrupted runs on coverage and findings, excluding generated IDs/times.

## 4. P0 — Reduce signals that disclose a detection

Distinguish **hiding the result** from **hiding use of the app**. Installation, sysdiagnose collection, Files activity, foreground execution, battery use, and user behavior may reveal an investigation. No icon change or “stealth mode” can promise invisibility.

- **Keep outward behavior independent of matches.** Never introduce match-triggered network requests, indicator lookups, notifications, badges, sounds, haptics, automatic exports, or remediation. Test positive and negative scans for the same absence of these signals. A finding must not contact the suspected infrastructure to verify itself.
- **Make result disclosure deliberate.** Replace live match counts and automatic result navigation with neutral progress/completion and an explicit reveal action. Keep incomplete-analysis warnings visible. Hide excerpts and campaign names until review; do not put them in filenames or generic error messages. Follow [Apple's Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) and retain accessible, understandable controls.
- **Protect incidental surfaces.** Cover sensitive views before background snapshots, including during transitions; Apple documents [why app-switcher snapshots need protection](https://developer.apple.com/library/archive/qa/qa1838/_index.html). Audit restoration data, logs, crash breadcrumbs, previews, temporary exports, and the shared inbox. Retain explicit copy/share actions and explain their disclosure boundary. Snapshot protection cannot stop a privileged screen observer.
- **Measure residual side channels.** Compare equal-sized synthetic positive/negative cases for duration, checkpoint writes, and output size. Remove cheap match-dependent signals where practical. Do not claim constant-time scanning or hidden results: finding storage and computation still differ, and padding cannot defeat process/OS inspection.

Acceptance: synthetic device checks show no automatic outward result signal or sensitive background preview. Record remaining observable differences rather than labeling the app undetectable.

## 5. P1 — Strengthen evidence and release trust

- **Expose coverage and provenance.** Record analyzer/build version, indicator digest/source/age, input digest, and skipped/error counts. Flag unexpected missing evidence categories as coverage gaps, without presenting them as proof of tampering. Preserve structured versus raw-text distinctions: attackers can plant indicator strings as well as remove them.
- **Authenticate definitions.** Keep reviewed, pinned bundled snapshots. Design signed offline update bundles with a versioned manifest, pinned verification key, rollback policy, and key-rotation procedure. Clearly label arbitrary manual STIX imports as unverified. Signatures identify an approved publisher; they do not establish rule quality or complete spyware coverage. A fully compromised OS can bypass verification.
- **Minimize the release surface.** Review entitlements, file sharing, and the App Group/share extension; retain the Files-only build where sufficient. Pin build inputs, review indicator/dependency changes, verify release signing and absence of debug entitlements, and publish build provenance. Avoid third-party SDKs added solely for integrity checks.
- **Treat integrity checks as advisory.** Debugger, jailbreak, hook, or self-hash checks may detect some interference but are bypassable and can produce false alarms. Never use a passed check as a trust certificate, or destroy evidence/block export when a check fails. Prioritize the safeguards above before anti-tamper heuristics or obfuscation.

Acceptance: stale/unverified definitions and coverage gaps are visible; tampered signed bundles are rejected in controlled tests; each release has a reviewed provenance and entitlement record.

## 6. P2 — Provide a path beyond the compromised phone

For high-assurance investigations, plan an explicitly authorized workflow to verify an app/indicator release and independently re-analyze evidence on a separate trusted system. This can expose disagreement with the phone's computation, but agreement still does not prove the source logs were truthful or complete. Encryption to a recipient's key can protect an export after creation; it cannot hide plaintext already visible to compromised iOS.

Contacting an investigator or planning sensitive next steps should happen from a separate trusted device when observation is a concern. Do not automatically uninstall malware, reboot, erase evidence, or change connectivity after a match: these can reveal detection or alter evidence. Offline operation avoids app-originated traffic but does not prevent malware from recording activity and reporting it later.

This is a future user-controlled investigation path, not permission to transfer evidence during development. Under repository policy, actual phone sysdiagnose archives, extracted contents, app evidence containers, and case exports stay on the phone unless the user explicitly authorizes an exception. Use synthetic fixtures and non-content progress/timing data for hardening tests.

## 7. Platform-specific work

Apply P0 to both engines independently; matching parity does not establish security parity.

| Area | iOS | Android |
| --- | --- | --- |
| Offline enforcement | Retain source/release review and runtime traffic checks; ATS is not a network deny policy. | Require the merged release manifest to omit `INTERNET`; check dependencies and runtime behavior. Providers and external share destinations can still use the network. |
| Screen exposure | Shield background snapshots; do not promise universal screenshot prevention. | Apply `FLAG_SECURE` to sensitive windows; evaluate Android 12+ overlay hiding and obscured-touch defenses for reveal/export. These do not defeat all accessibility observation or privileged capture. Test supported OS/OEM combinations and legitimate assistive use. |
| Storage and sharing | Verify complete file protection on temporary files and App Group contents, including locked-device interruption. | Keep cases in credential-encrypted private storage; verify backup/device-transfer exclusions. Restrict FileProvider paths and temporary URI grants to explicitly selected exports. Device encryption is not equivalent to iOS complete protection on each lock. |
| Import surface | Review document-provider and share-extension copies as untrusted inputs. | Review exported activities, `ACTION_SEND`/`ACTION_VIEW`, URI grants, and hostile content providers. Copy bounded streams privately; never trust MIME, display names, reported sizes, or provider stability. Fuzz ZIP as well as tar/gzip. |
| Parser containment | Keep parsing minimal and bounded within available public iOS execution facilities. | Evaluate an isolated parser service with narrowly passed file descriptors and bounded messages. A separate ordinary process alone is not a new security boundary; isolation can contain parser damage while the OS remains trustworthy. |
| Execution and coverage | Test suspension, device locking, and sysdiagnose format differences. | Test process killing, OEM battery restrictions, and bugreport format differences. If a foreground service becomes necessary, its required notification exposes activity: use neutral text and disclose that tradeoff. |

Android references: [secure sensitive activities](https://developer.android.com/security/fraud-prevention/activities) and [isolated-process policy](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/README.apps.md). These are mitigations under an intact platform, not evidence that the platform is intact. Do not add online attestation as a prerequisite for offline analysis or present an attestation success as proof that malware is absent.

## Delivery order

1. Complete the two P0 tracks and add their synthetic acceptance checks to the release gate.
2. Complete P1 provenance, definition trust, and release review; document remaining coverage limits.
3. Design P2 with investigator input and commission an independent adversarial review. Report tested attacker capabilities and residual risks, rather than a blanket claim that LocalVerify works secretly on any infected phone.


## Implementation record — 2026-09-06

Implemented in this hardening pass:

- Both engines preflight JSON complexity and bound text lines, indicator metadata, and fallback matching work. Dense-match limits preserve earlier findings and leave analysis incomplete; originals are checked again before completion.
- iOS uses bounded indicator reads and checkpoint loading, validates loaded case identity, removes sensitive live progress and automatic finding navigation, and synchronously covers its window when leaving the foreground.
- Android distinguishes skipped/invalid UTF-8 entries from analyzed files, checks ZIP central-directory presence, uses atomic bounded local records, verifies actual indicator snapshot hashes, and streams original evidence into a temporary export before publishing it. Failed/replaced cases cannot reuse old analysis; deletion also removes prepared exports.
- Android hides results until reveal, sets secure-window/overlay protection, excludes originals from exports by default, and removes unnecessary browser intent handling. Both HTML exporters add a restrictive content security policy.
- Android follows its current product decisions: no consent gate, Stop remains, and every new attempt starts from the beginning. Checkpoints preserve incomplete reports, not resume cursors. See [ANDROID-HANDOFF.md](ANDROID-HANDOFF.md).

Still outstanding: comprehensive fuzzing/resource measurements on physical devices; stronger input-handle binding; full disk/lock/fault-injection coverage; signed offline definition packages and key lifecycle; independent release/security review; parser-process isolation; and the separately authorized trusted-system investigation workflow. These changes do not establish detection effectiveness, constant-time behavior, or secrecy against a compromised OS. Current test evidence is recorded in the platform validation docs.
