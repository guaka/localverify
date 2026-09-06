# ADR-001: Kotlin Multiplatform for the next shared-engine stage

Date: 2026-09-06. Status: recommended foundation for staged migration; production cutover has not occurred.

## Decision

Use Kotlin Multiplatform for the next bounded migration stage and retain the Rust/UniFFI experiment as a tested alternative. Keep SwiftUI and Jetpack Compose native. Neither experiment is linked into production.

Both candidates passed 45 synthetic checks through Swift on an iOS simulator and Kotlin on an Android emulator. Both compiled for physical iOS and Android targets; neither was installed on a physical phone. The checks include STIX-to-matcher integration, the canonical legacy-independent corpus, malformed UTF-8/JSON, resource budgets, and cancellation after scan progress begins.

The recommendation is a maintenance judgment, not a claim that Kotlin is faster. Kotlin fits the existing Android language/toolchain and produced smaller harness artifacts. Rust performed better in the final timing samples and clean build. There is no product latency threshold that disqualifies either candidate. A workload dominated by archive streaming or sustained scanning could justify revisiting this decision.

## Measurements

One Apple M3 Pro / 18 GiB host; Xcode 26.6, iOS 26.5 simulator, API 36 ARM64 Android emulator, JDK 21.0.4. Rust 1.86.0 / UniFFI 0.29.4; Kotlin 2.2.21. Both scan the same 180,000-byte synthetic input and return 4,000 findings. One warm-up, five timed samples, including language-boundary conversion.

| Measurement | Rust / UniFFI | Kotlin Multiplatform |
| --- | ---: | ---: |
| iOS median scan, ms | 34.629 | 47.447 |
| Android median scan, ms | 8.735 | 22.116 |
| iOS active cancellation, ms | 0.068 | 0.097 |
| Android active cancellation, ms | 0.725 | 0.071 |
| Clean mobile-library build, seconds | 26.140 | 34.958 |
| No-change incremental build, seconds | 0.183 | 0.888 |
| iOS simulator executable, MiB | 4.10 | 2.62 |
| iOS executable increase over baseline, MiB | 4.05 | 2.57 |
| Android debug APK, MiB | 5.81 | 2.98 |
| Android APK increase over baseline, MiB | 3.47 | 0.64 |
| iOS whole-test-process peak RSS, MiB | 87.92 | 76.59 |
| Android PSS after benchmark, MiB | 92.13 | 110.43 |

Raw samples, environment, source hashes, and sizes: [recorded results](../experiments/engine/results/2026-09-06/). Reproduce with [the experiment runner](../experiments/engine/README.md).

## Interpretation and limits

- Five simulator/emulator samples on a shared host are descriptive, not statistically conclusive. Earlier exploratory timings changed ordering under load; the table uses the final recorded run. These are not physical-phone throughput measurements.
- Cancellation waits for an atomic progress counter before signalling; values measure signal-to-return, not the earlier input conversion or preflight time. All final runs returned an explicit cancelled result.
- iOS memory is peak RSS over the entire test process, including adversarial allocations; Android memory is a PSS sample around the benchmark, affected by GC and shared pages. They do not isolate engine heap use or support a cross-OS memory comparison.
- Sizes use clean debug APK packaging and optimized Swift simulator executables, including runtime/binding overhead. Native symbols are retained consistently regardless of host NDK availability. They do not predict production App Store/Play download size.
- Clean library timing removes all Rust target output and the KMP module output, with downloads cached. Incremental timing is a no-change build. Binding generation, native harness compilation, first-time downloads and signing are excluded. An earlier release-only Rust cleanup retained target artifacts; that measurement was discarded.
- The prototypes omit the production token-index optimization. Their benchmark is not a production-regression claim. Kotlin's JVM byte-array integration is direct; its Swift adapter currently copies bytes individually and should be replaced with a reviewed bulk bridge before large-input production use.
- The experiment needed disk recovery: an incomplete Kotlin compiler copy was removed/reused, the temporary NDK was cleaned up, and fresh sparse Android data storage was used. One subsequent emulator install stalled; restarting the disposable device and using non-streaming installation yielded the final passing run. No existing device evidence or containers were read or transferred.

## Validation and next steps

Production validation: **50 Swift tests**, **19 Android JVM tests**, Android debug assembly/lint, iOS app/share-extension simulator build, and offline-policy check/self-test passed. The canonical corpus records existing Swift/Kotlin differences rather than changing production behavior. Strict OpenSpec validation covers the specs and staged changes.

The next changes are deliberately unimplemented:

1. `promote-shared-record-engine`: production-quality common parser/matcher, token index, metadata, and one publisher-feed resource source.
2. `shared-archive-engine`: bounded streaming ZIP/gzip/tar, integrity checks, cancellation and coverage, with reviewed dependencies.
3. `shared-report-compatibility`: shared reports/exports and explicit readers for both historical storage formats.
4. `native-engine-integration`: native import/storage/lifecycle/share adapters, restart on both platforms, then removal of superseded engines after workflow acceptance.

Archive safety, protected storage behavior, case migrations, streaming memory, real-device lifecycle behavior, and detection effectiveness remain unproven by this matching slice. Do not promote the prototype directly into production.

## Implementation follow-up — 2026-09-06

The first promotion stage is verified in [SHARED-RECORD-ENGINE.md](SHARED-RECORD-ENGINE.md). It adds bounded indexing, metadata/cache handling, canonical publisher resources and pinned Unicode casing after native integration exposed a runtime difference. This does not alter the frozen comparison results above. Production activation still depends on shared archive processing, report compatibility and native adapter integration.
