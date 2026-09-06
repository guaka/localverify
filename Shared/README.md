# Shared record engine

`record-engine` is the selected Kotlin Multiplatform record library. SwiftUI and Compose apps still use their existing engines. This module prepares a common parser, matcher and indicator metadata layer; it is not an archive pipeline or report serializer.

## API and ownership

- Common/JVM: `IndicatorParser.parseBundle`, `RecordEngine.scanRecord`, `ThreatData.combine`, `IndicatorCache.decode` and `IndicatorCache.preferred`.
- Swift: static `RecordEngine.framework`, with `AppleRecordEngine` accepting Foundation `Data` through a bounded bulk-copy bridge.
- Results carry typed definitions, campaign labels, source references, timestamps, findings, coverage gaps and cancellation. `complete` requires no cancellation and no coverage gaps.
- Run on an adapter-owned worker. Give each run a fresh `Cancellation`; another thread may poll its content-free progress and cancel it. Treat supplied arrays/lists as exclusively owned for the duration of a call. Progress counters are approximate snapshots; phase changes reset the byte counter.
- Cache dates use an explicit legacy platform. Call `preferred(..., frozenCase=true)` for saved case snapshots. Imported/unknown sources are preserved; only recognized publisher snapshots may upgrade. The API does not rewrite any cache, case or export.

## Contract and Unicode

Authoritative behavior is in [OpenSpec](../openspec/specs). Inputs are strict UTF-8, bounded before parsing. Raw domains use ASCII case folding and ASCII token boundaries; other raw values are exact literals. Recognized structured domains use pinned Unicode 16.0 default lowercase and ASCII-dot trimming. There is no locale tailoring, NFC/NFD normalization, full case folding, IDNA conversion or confusable expansion.

The pinned table fixes an observed Android/desktop-JVM disagreement on Greek final sigma. `tools/generate_unicode_lowercase.py` derives tables and exhaustive non-identity mapping fixtures from checksum-pinned [Unicode 16.0 data](https://www.unicode.org/Public/16.0.0/ucd/). Its Final_Sigma logic uses Cased and Case_Ignorable properties; supplementary characters and unconditional multi-code-point mappings are included. Unicode source downloads are development-only and are never used during analysis.

Budgets: STIX 5 MiB; record 16 MiB; line 1 MiB; 500,000 lines counted as one plus each CR or LF; JSON depth 64 / 200,000 lexical tokens; structured nodes 100,000 / paths 4,096 bytes; supplied indicators 10,000, values 8,192 bytes, IDs 1,024 bytes, at most 16 campaign labels of 128 bytes; STIX supported values 2,048 code points and 2,000 definitions per bundle; text work 128 MiB; findings 10,000 or a smaller caller budget; excerpt 600 and timestamp 256 code points. Metadata errors return typed failure. Record budget failures retain earlier findings and mark coverage incomplete. Strict JSON decoding is bounded but not interruptible inside the platform call; cancellation is cooperative at surrounding and inner-loop checkpoints.

## Canonical resources

`ThreatData/` contains the five unmodified publisher bundles, manifest, feed catalog and attribution. The legacy iOS project generator packages these directly. Shared JVM/Android test resources are generated from this directory and root `Fixtures/`; no fixture copies are maintained by hand. Android's production demo feed remains unchanged until native integration.

The loader checks all five byte counts and SHA-256 hashes before returning any set. Exact kind/value duplicates keep the first ID and union campaigns. The current reviewed snapshot contains 2,336 supported definitions, 55 skipped definitions and 2,331,191 bytes; combined version suffix `c5f1b12b5971`.

## Reproduce validation

From the repository root on an Apple Silicon Mac with Xcode, the Android SDK/API 34 build tools, API 36 ARM64 emulator image and JDK 17+:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
python3 Shared/run.py build
python3 tools/check_shared_offline.py --self-test
python3 tools/generate_unicode_lowercase.py --check
npm run spec:validate
```

`build` runs JVM contract and common budget tests, compiles common budget tests for Kotlin/Native, links release Apple simulator/device frameworks, builds the Android synthetic harness and compiles Swift simulator/device callers. Kotlin 2.2.21 and serialization 1.9.0 are pinned. The first build downloads development dependencies. Later Gradle invocations can use `--offline`. `tools/check_shared_offline.py` runs before shared compile/link tasks; existing app offline checks remain enforced separately.

Create a disposable simulator, run the native unit tests and Swift contract, then remove it:

```sh
simulator_id=$(xcrun simctl create 'LocalVerify Shared Engine Checks' com.apple.CoreSimulator.SimDeviceType.iPhone-17 com.apple.CoreSimulator.SimRuntime.iOS-26-5)
python3 Shared/run.py ios --simulator "$simulator_id"
xcrun simctl shutdown "$simulator_id"
xcrun simctl delete "$simulator_id"
```

Use a new Android AVD with empty storage; run it after the iOS simulator is shut down:

```sh
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n LocalVerify_Shared_Engine_Checks -k 'system-images;android-36;google_apis;arm64-v8a' -p "$PWD/Shared/build/avd"
"$ANDROID_HOME/emulator/emulator" -avd LocalVerify_Shared_Engine_Checks -no-window -no-audio -no-snapshot -read-only -memory 1536 -cores 2 -port 5580
# In another terminal, after sys.boot_completed is 1:
python3 Shared/run.py android --serial emulator-5580
"$ANDROID_HOME/platform-tools/adb" -s emulator-5580 emu kill
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" delete avd -n LocalVerify_Shared_Engine_Checks
```

The runner checks virtual-device names and rejects physical-device serials. It installs only the synthetic harness and never reads or pulls app evidence. On a low-free-space host, a new empty sparse ext4 userdata image can avoid the API 36 emulator's pre-copy disk check; recorded validation used the SDK's `mkfs.ext4 -F -b 4096 -m 0 -L data Shared/build/avd/userdata-qemu.img 1572864` **only before the newly created AVD's first boot**. Do not use that command on an existing AVD.

Logs and measurements go to ignored `Shared/build/`. Reviewed results are recorded in [SHARED-RECORD-ENGINE.md](../docs/SHARED-RECORD-ENGINE.md). These synthetic checks do not establish full archive throughput or physical-device readiness.

## Activation stages

| Stage | Dependency / outcome |
|---|---|
| Promote shared record engine | This module and its integration gates |
| Shared archive engine | Streaming/decompression, archive limits, hashing and interruption |
| Shared report compatibility | Saved-case readability, export schema and report metadata |
| Native engine integration | Switch both production apps to KMP through native adapters; retain SwiftUI/Compose |

See the corresponding active changes in `openspec/changes/`. The frozen Rust/KMP experiment is retained under `experiments/engine/` and is not modified by this promotion.
