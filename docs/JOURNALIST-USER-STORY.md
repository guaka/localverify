# User story: a journalist under surveillance risk

Status: proposed product requirements, not a description of shipped behavior.

This document defines the experience LocalVerify should deliver for a journalist with limited time, money, and technical support. [HARDEN.md](HARDEN.md) describes the corresponding security engineering work; [VERIFICATION.md](VERIFICATION.md) describes validation requirements.

## Scenario

A freelance journalist is investigating a government known to target journalists with spyware in a country affected by civil war. Their phone holds communications with confidential sources. They suspect surveillance but do not know whether their phone is infected.

They have one phone, little available storage, intermittent connectivity and electricity, and no reliable access to a laptop or paid forensic specialist. They may have only a few uninterrupted minutes. Buying another phone is not a realistic default instruction.

The app must help without requiring the journalist to identify themselves, describe their investigation, or enter source information. Their concern may be justified even when the app finds nothing.

## Primary user story

**As a journalist who may be targeted, I want to check the evidence available on my phone and understand my next practical step, so that I can make informed decisions about my work and sources without needing money, specialist knowledge, or a computer.**

The first-minute objective is to help the journalist choose between starting a supported check and seeking help. It is not a promise to collect diagnostics or finish analysis in one minute.

## P0 — Required for a journalist-facing release

### J1. Get help without completing a scan

As a journalist under time pressure, I can choose **Check this phone** or **I need help now** immediately.

Acceptance criteria:

- Both actions are available on first launch, without an account, payment, network connection, or diagnostic import.
- A brief explanation says checking may be observable and cannot establish that a compromised phone is safe for sensitive communication. Detailed limitations remain available without blocking the main flow.
- Help includes instructions usable from a separate trusted device when one is available, but does not require owning one.
- Contact details are readable offline. Opening another app or contacting a service requires a deliberate action; LocalVerify sends nothing automatically.

### J2. Collect evidence without knowing forensic terminology

As a journalist unfamiliar with diagnostics, I receive one instruction at a time for my supported phone.

Acceptance criteria:

- Instructions use plain language and illustrations, work offline, and explain when the journalist must leave LocalVerify for system settings or Files.
- Before collection/import, the app explains that diagnostics may contain sensitive information and shows known storage requirements. Unknown requirements are labeled as estimates or unavailable.
- Returning to the app preserves the instruction step. Cancellation and interruption leave a clear way to continue or remove an unfinished import.
- Unsupported collection paths are identified early, with a route to help. The user never reaches a misleading successful empty check.
- Bundled definitions allow analysis without obtaining a separate indicator file.

### J3. Understand what the check means

As a journalist, I receive an understandable outcome with an available next action.

| Outcome | Required explanation | Available action |
| --- | --- | --- |
| Something needs expert review | Describe the observation as a lead, not confirmed infection or attribution. | Review details or find free help. |
| No known signs found in what we checked | Explain that absence of matches does not establish safety; summarize coverage and missing evidence. | Review limitations or find help anyway. |
| We could not complete the check | Explain the interruption, failure, or coverage problem and whether retrying can help. | Resume/retry when appropriate, review available leads, or find help. |

Acceptance criteria:

- Incomplete analysis with findings shows both facts; an error never hides earlier leads.
- Unsupported evidence is never presented as analyzed. Coverage limitations remain visible even after successful processing.
- There is no green “safe phone” verdict, infection probability without validation, or claim that a scan certifies sensitive communication as safe.
- Detailed technical records are available for review without being required to understand the outcome.

### J4. Avoid unnecessary disclosure

As a journalist, I control when findings become visible and when information leaves the app.

Acceptance criteria:

- Progress and completion do not expose match counts, campaign names, or excerpts. Findings require an explicit reveal action.
- No finding triggers notifications, sounds, badges, network requests, exports, or changes to the phone automatically.
- Sensitive background previews are protected using supported platform facilities; returning from the background requires revealing findings again.
- Copying and sharing require explicit actions. The app explains that receiving apps or people control their own copies.
- The app never promises invisible installation, undetectable scanning, or protection against an observer controlling the operating system.

### J5. Reach affordable assistance

As a journalist without money for an investigator, I can find relevant free assistance and prepare a minimal request for help.

Acceptance criteria:

- The app includes reviewed contact information, supported languages where verified, and a last-reviewed date. Availability and response times are not guaranteed.
- A help request can be prepared without attaching diagnostic evidence. Before sharing, the journalist previews and edits its contents.
- A suggested initial summary contains the app version, check completion state, and broad coverage limitations. It excludes raw logs, source names, finding excerpts, and the original archive by default.
- Evidence sharing is a separate, explicit decision with a preview of what is included. Seeking help never silently uploads diagnostics.

Candidate resources, verified when drafting this document on 2026-09-06: [Access Now's free, 24/7 Digital Security Helpline](https://www.accessnow.org/help/) and [Amnesty Security Lab's free forensic support for at-risk civil society](https://securitylab.amnesty.org/contact-us/). These are external services, not committed LocalVerify partners; review eligibility and contact details before release.

## P1 — Reduce practical barriers

- **Installation:** provide a documented, authentic release path that does not require the journalist to build the app, configure developer provisioning, or weaken device security. Validate distribution constraints before claiming accessibility in a particular region.
- **Language and accessibility:** translate the complete critical flow for intended deployment communities; test text size, screen readers, and comprehension with representative users. Follow [Apple's Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) for iOS.
- **Limited resources:** measure storage, memory, battery use, and time on representative supported devices. Provide useful progress and resume behavior; publish measured limitations rather than universal performance promises.
- **Maintenance:** keep offline definitions usable and their age visible. Offer a verified update path without making connectivity a prerequisite for help or analysis.

## Platform distinctions

The same user story applies to both platforms; collection, coverage, and security guarantees must be tested separately.

- **iOS:** validate sysdiagnose instructions and supported formats on physical devices. Address the current private-build installation barrier before presenting the app as generally accessible to journalists.
- **Android:** validate bugreport instructions on selected inexpensive devices and manufacturer variants. Test low-memory process termination, storage restrictions, and permission-related observation risks. Do not claim iOS-equivalent forensic coverage based only on matching-engine parity.
- **Both:** explain unsupported devices early. Neither platform can provide a trustworthy secret verdict when the OS controls the evidence and execution.

## Release exercise

Give a representative participant a supported phone and synthetic evidence, without coaching. Include an offline run, an interruption, insufficient storage, and each outcome above.

The release gate is met when participants can:

1. Within one minute of first launch, locate help or begin the appropriate collection instructions and explain that the app cannot prove the phone is safe.
2. Complete the supported workflow without a computer or specialist terminology, with collection and analysis times measured separately.
3. Recover from interruption and distinguish a completed check from incomplete coverage.
4. Find free assistance and preview a help request without accidentally sharing evidence.
5. Explain that app use may be observable even when findings are hidden.

Record device/OS, language, accessibility setup, timing, mistakes, and where assistance was needed. Fix dangerous misunderstandings before release. Passing this exercise establishes usability, not real-world spyware detection effectiveness.

Use synthetic fixtures and non-content progress/timing information for development and testing. Actual phone diagnostics, extracted evidence, case containers, and exports stay on the phone unless the user explicitly authorizes an exception, as required by [AGENTS.md](../AGENTS.md).
