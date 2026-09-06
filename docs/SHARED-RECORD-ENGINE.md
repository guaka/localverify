# Shared record-engine validation — 2026-09-06

The `promote-shared-record-engine` stage is implemented and verified. Kotlin Multiplatform now provides a standalone bounded record parser/matcher, publisher metadata and legacy indicator-cache handling. Production iOS and Android apps continue using their existing engines; activation follows archive processing and report compatibility.

| Gate | Result |
|---|---|
| Shared JVM contract | 1,670 checks passed; four additional common budget unit tests passed |
| Swift → Kotlin/Native, iPhone 17 / iOS 26.5 simulator | 1,564 checks passed |
| Kotlin/Native common budget tests, same disposable simulator | Four passed |
| Kotlin → shared JVM library, API 36 ARM64 Android emulator | 1,670 checks passed |
| Apple device compilation | Release framework and Swift caller compiled for arm64 iPhone target; no installation |
| Existing Swift suite | 50 tests passed |
| Existing Android suite | 19 tests passed; app build and lint passed |
| Existing iOS app + share extension | Simulator build passed; all five bundled feeds and manifest match canonical bytes |
| Offline source policies | Existing production policy retained; shared compile/link policy and both policy self-tests passed |
| Unicode generation | Checksum-pinned Unicode 16.0 tables and 1,460 non-identity mapping fixtures reproduce exactly |

Swift and Kotlin runners consume the same matching, STIX, budget, Unicode, cache, publisher-merge and workload JSON. Kotlin adds 100 independently generated reference-matcher comparisons and six cache-preference checks, accounting for the count difference. Cases also cover malformed UTF-8, byte/code-point limits, supplementary characters, all five indicator kinds, typed campaigns, source timestamps, dense tokens, partial finding limits, active cancellation during indexing and cancellation after a first finding.

## Measurements

Single synthetic acceptance runs; these are not statistical benchmarks or device-throughput predictions. Both workloads use the same 2,336 reviewed publisher definitions. Android ran headless with 1,536 MiB emulator RAM and two virtual cores. Apple frameworks and Swift callers used optimized release builds; Android used a debug harness.

| Workload | Desktop JVM | Swift / iOS simulator | Android emulator |
|---|---:|---:|---:|
| 248,000-byte benign log | 9.05 ms | 52.10 ms | 42.38 ms |
| 15,500,000-byte benign log | 286.47 ms | 1,028.87 ms | 1,711.01 ms |
| Cancellation after indexing began | 0.130 ms | 0.169 ms | 0.036 ms |

The Swift process peak RSS was 215,531,520 bytes across the whole suite, including fixture loading and large input buffers; this is not engine-only memory. The separate unoptimized Kotlin/Native adversarial work-budget unit test intentionally scans up to the 128 MiB work cap and took about 13.65 seconds. No full-archive latency, mobile memory ceiling, battery use or physical-device responsiveness is established here.

## Integration findings and decisions

Android and desktop Java disagreed on default lowercase conversion for `ΟΣ.INVALID`. The promoted module now uses generated [Unicode 16.0 mappings](https://www.unicode.org/Public/16.0.0/ucd/SpecialCasing.txt) and versioned Cased/Case_Ignorable properties, including Final_Sigma, rather than delegating casing to platform libraries. Raw ASCII domain folding is unchanged. The new fixtures characterize this difference; the old architecture experiment was not rerun or rewritten to claim coverage it did not originally have.

Publisher snapshots moved byte-for-byte to `Shared/ThreatData`. The shared loader validates all five manifest hashes and byte counts before returning a set, retains first IDs for exact duplicates, unions campaign labels and preserves source/date/version metadata. Synthetic tampering and missing-feed tests return no partial set. The reviewed snapshot remains 2,331,191 bytes, 2,336 supported definitions, 55 unsupported definitions, latest indicator date 2026-03-30, version suffix `c5f1b12b5971`.

Legacy cache decoding explicitly selects Swift reference-date seconds or Android epoch milliseconds. Manual imports, unknown sources and frozen case snapshots are preserved. Known publisher caches may upgrade; same-version label enrichment requires identical indicator ID, kind and value. No saved cases or export files are rewritten by this stage.

## Reproduction and scope

Commands are in [Shared/README.md](../Shared/README.md). Machine-readable [results and source hashes](../Shared/results/2026-09-06/) identify the verified source and canonical inputs. The shared policy permits only the reviewed serialization library and platform byte hashing; it runs before compile/link tasks. Unicode downloads are development-only. Original production offline checks remain unchanged and enforced. Disposable simulators/emulators were removed after validation. No phone evidence, extracted archive contents, case containers or exports were transferred.

| Migration stage | State |
|---|---|
| OpenSpec and engine comparison | Complete; KMP selected in ADR-001 |
| Shared record engine | Complete; specifications synchronized and change archived |
| Shared archive engine | Next: streaming/decompression, hashing, limits and interruption |
| Shared report compatibility | Pending: saved cases and export compatibility |
| Activate KMP in both native apps | Pending native-engine-integration, after the preceding gates |

SwiftUI, Compose, document access, protected storage, lifecycle handling and explicit sharing remain native adapter responsibilities. The next archive stage consumes this library; the production switch and removal of duplicate engines happen only in the final native-integration stage.
