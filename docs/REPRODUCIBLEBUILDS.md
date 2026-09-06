# Reproducible builds

## Current status — 2026-09-06

Local Verify does **not yet have verified byte-for-byte reproducible builds**. The repository has documented build commands, pinned inputs and successful build/test records. It has not demonstrated identical release artifacts from two independent clean builds.

A reproducibility claim must identify the source revision, build environment, instructions and exact artifacts another builder can reproduce. Passing tests or rebuilding successfully does not establish identical bytes. See the [Reproducible Builds definition](https://reproducible-builds.org/docs/definition/).

| Area | Status | Repository evidence |
|---|---|---|
| Build commands and validation records | ✅ Available | [Main README](../README.md), [shared module guide](../Shared/README.md), [shared validation](SHARED-RECORD-ENGINE.md) |
| Gradle distribution | ✅ Pinned and checksummed | [Wrapper configuration](../Android/gradle/wrapper/gradle-wrapper.properties) pins Gradle 8.11.1 and its SHA-256 |
| Direct Android/KMP dependencies | ✅ Explicit versions | [Android plugins](../Android/build.gradle.kts), [app dependencies](../Android/app/build.gradle.kts), [shared plugins](../Shared/build.gradle.kts), [shared library](../Shared/record-engine/build.gradle.kts) |
| Development dependency locks | ✅ Present for npm and Rust experiment | Root `package-lock.json` and experimental `Cargo.lock`; these do not lock the Android/KMP dependency graph |
| Publisher and Unicode inputs | ✅ Pinned and verified | [Publisher manifest](../Shared/ThreatData/threat-manifest.json) and [Unicode generator checksums](../tools/generate_unicode_lowercase.py) |
| Exact build environment | 🟡 Partially specified | Minimum Xcode/JDK versions and SDK platform are documented; exact host, compiler and SDK tool versions are not enforced as a complete release environment |
| Gradle transitive dependencies | ❌ Reproducibility controls missing | No committed dependency lock state or dependency-verification metadata found for Android or Shared |
| App version generation | ❌ Depends on the build clock | Android uses `LocalDateTime.now(ZoneOffset.UTC)`; the iOS generator uses local `datetime.now()` |
| Independent artifact comparison | ❌ Not established | No recorded two-clean-build release hash comparison or automated reproducibility gate |

The immediate timestamp sources are [Android's build configuration](../Android/app/build.gradle.kts) and [the iOS project generator](../tools/generate_project.py). Building Android later, or regenerating the iOS project later, can change the app version for the same source revision. The iOS generator also depends on the local timezone. Fixing these is necessary, but does not prove that all other output is deterministic.

The existing offline-policy checks concern application source behavior. They do not make the build environment hermetic or prove artifact reproducibility. Dependency downloads may still be needed to provision a builder.

## Proposed next steps

These steps are a roadmap, not implemented build guarantees. Before changing build behavior, create an OpenSpec change with scenarios, design decisions and verification tasks; keep `openspec/specs/` authoritative and archive only after verification.

1. **Define the artifact boundary.** Start with the unsigned Android release APK, the shared JVM library and static Apple release frameworks. Define separate Apple device and simulator targets. For the iOS app and share extension, specify the unsigned bundle files and metadata being compared. Track each artifact's result independently; shared-library success must not imply app reproducibility.

2. **Remove clock-dependent versioning.** Use one committed release version and explicit version/build numbers for both apps. If a date is needed, derive it from a fixed source revision or release input in UTC. Consider the standard [SOURCE_DATE_EPOCH convention](https://reproducible-builds.org/docs/source-date-epoch/), but verify that every relevant generator consumes it: merely exporting the variable does not fix the current scripts. Release builds should reject missing required inputs rather than silently using the current time.

3. **Pin and verify the full dependency graph.** Add reviewed [Gradle dependency locks](https://docs.gradle.org/current/userguide/dependency_locking.html) and [dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html) for Android and Shared, including relevant build/plugin dependencies. Locking fixes selected versions; verification checks downloaded artifacts. Review generated checksums before trusting them. Keep experiments isolated from production targets and preserve existing offline-policy enforcement.

4. **Specify an exact release environment.** Record and check host OS/architecture, Xcode build number, Apple SDK/Swift versions, JDK vendor/version, Android SDK Build Tools, Kotlin/Native and Gradle versions. Pin Python and Node when used by the recipe. Define locale, timezone and relevant environment inputs. Preserve a rebuildable environment description and dependency inventory; a record of the developer's current machine is only a starting point.

5. **Build twice independently and diagnose differences.** Use clean checkouts at the same commit, isolated output directories and no reused build-output cache. Provision identical verified dependencies for both builds. Start with the exact same declared environment, then vary checkout path and build time to expose hidden inputs. Compare SHA-256 hashes and, on mismatch, inspect archive ordering/timestamps, generated metadata, embedded paths and binary contents. Fix demonstrated causes; do not assume those differences are already present or mask them with undocumented filtering.

6. **Separate signing from the initial comparison.** Compare unsigned artifacts first. Document Android signing keys and Apple signing/provisioning inputs as a separate release step, recording public identifiers rather than secrets. Publish a distinct assessment for signed/distributed artifacts. Matching unsigned payloads does not establish identical signed APKs or IPAs.

7. **Automate and publish the verification.** Add a clean-build comparison job for each supported release environment. Retain artifact manifests, hashes and readable mismatch reports. Initially report which targets pass; only make reproducibility a release gate after the process is verified and repeatable by another builder.

## Acceptance and evidence

A target can be marked verified once two independent clean builds produce identical bytes for every artifact in its declared scope. Publish the source commit, exact environment, commands, non-secret build inputs, dependency verification state, artifact paths, sizes and SHA-256 hashes, plus any explicitly excluded signing or packaging step. Bundle comparisons must cover the declared file set and relevant metadata, not just the main executable.

If an archive container differs but its extracted payload matches, report that narrower result. Do not label the entire archive reproducible. Likewise, a simulator result does not establish device-target or release-package reproducibility.

Existing unit suites, synthetic native integration checks and offline policies remain required alongside artifact comparisons. Use synthetic fixtures and committed public publisher snapshots; no phone evidence, extracted diagnostics, case containers or exports are needed for this work.

Reproducibility work can proceed alongside the shared-engine migration. It must not activate the shared engine in production or bypass the archive-processing, report-compatibility and native-integration gates.
