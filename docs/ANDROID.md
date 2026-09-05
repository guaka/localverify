# Phase 2: basic native Android (deferred)

Begin after iOS physical-device acceptance. Implement Kotlin/Jetpack Compose for Android 11+, with a native Kotlin engine. Share the report contract and matching fixtures, not a cross-platform runtime.

`Fixtures/matching.json` contains language-neutral matching vectors already exercised by the Swift tests. Kotlin must pass those same vectors.

1. Guide Developer options → Take bug report → Share; accept ZIPs via document picker/share intents. Document manufacturer differences.
2. Parse package/process information and supported diagnostic text sections. Port selected MVT bug-report checks against a pinned upstream revision and record the selected checks in the coverage matrix.
3. Implement the same consent, retention, progress/cancellation, interrupted-job recovery, result states, and manual ZIP escalation workflow.
4. Apply streaming archive limits and private backup-excluded storage. Freeze indicators per case.
5. Validate on physical devices in airplane mode, benign and seeded fixtures, representative bug reports, and confirmed positives when available. Compare selected checks with upstream MVT. Deliver signed APK plus source.

No direct ADB acquisition, root, AndroidQF acquisition, server, telemetry, automated escalation, or store publication in this phase. iOS implementation does not imply Android has been implemented or validated.
