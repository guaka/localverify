# Shared engine experiment

Two isolated parser/scanner candidates: Rust 1.86+/UniFFI 0.29.4 and Kotlin Multiplatform 2.2.21. Neither is a production engine or a dependency of either app. Both consume the canonical JSON corpus in `Fixtures/`; archive files are never packaged into the harness.

The [contract guide](../../docs/ENGINE-EXPERIMENT.md) describes legacy differences and the shared target. [ADR-001](../../docs/ADR-001-shared-engine.md) records results and the migration decision.

## Prerequisites (Apple Silicon Mac)

- Xcode and an installed iOS simulator runtime; Android Studio JDK 17+, SDK platform 34, and an API 30+ ARM64 emulator image.
- Rust/Cargo with `aarch64-apple-ios`, `aarch64-apple-ios-sim`, and `aarch64-linux-android` targets.
- Android NDK `27.2.12479018`. Install with the SDK's `sdkmanager 'ndk;27.2.12479018'`.
- Allow roughly 12 GiB of free temporary space for compiler downloads, intermediates, and disposable devices. Initial runs download development dependencies; the engines have no network APIs.
- The repository's checksum-pinned Android Gradle wrapper is reused. Cargo dependencies are locked. Kotlin and direct Android harness dependencies are pinned separately from production.

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$HOME/.cargo/bin:$PATH"
rustup target add aarch64-apple-ios aarch64-apple-ios-sim aarch64-linux-android
python3 experiments/engine/run.py build
```

The build generates Swift/Kotlin bindings, mobile libraries, unsigned Swift device executables (compile checks only), simulator executables, and isolated Android test APKs. Generated outputs stay under ignored build/target directories. Production project generation and the offline policy remain unchanged.

## Run integrations

Create/boot a disposable simulator named **LocalVerify Engine Experiment** with `simctl` or Xcode. Pass its UUID explicitly:

```sh
python3 experiments/engine/run.py ios --simulator YOUR_DISPOSABLE_SIMULATOR_UUID
```

The Swift command-line runner executes inside the simulator and links each mobile framework/library. This tests language bindings, not SwiftUI workflows or physical-device runtime behavior.

Create/boot a disposable Android emulator with Android Studio or `avdmanager`. Use the serial from `adb devices`:

```sh
python3 experiments/engine/run.py android --serial emulator-5580
```

Only `org.localverify.experiment.rust`, `.kmp`, and their instrumentation packages are installed. The runner rejects physical-device serials, performs no evidence/container pulls, and reads only synthetic test output. It does not clear other applications or emulator logs.

## Measurements

```sh
python3 experiments/engine/run.py sizes
python3 experiments/engine/run.py cost
```

`cost` removes all Rust target outputs and the KMP module outputs and rebuilds all three mobile library targets, retaining downloaded dependencies. It measures a clean output build and a subsequent no-change incremental build. It does not measure an edit/recompile workload or include first-time downloads, binding generation, app compilation, or signing in the library comparison.

Outputs are in `experiments/engine/build/`: per-command logs, build costs, artifact sizes, and `ios-{rust,kmp}.json` / `android-{rust,kmp}.json`. Copy only these synthetic measurement summaries into `results/` when updating the decision. There are no real case results in this directory.

Timing includes input conversion and result conversion across bindings. The canonical 180,000-byte benchmark returns 4,000 findings; one warm-up is followed by five samples. Active cancellation waits for the engine's atomic progress counter to advance before signalling cancellation. Memory readings are process-level: iOS peak RSS includes adversarial allocations from the whole test run; Android PSS is sampled around the benchmark. They are not isolated engine heap measurements and are not comparable between operating systems.

Artifact sizes compare otherwise equivalent harnesses: iOS optimized simulator executables against an empty Swift executable; Android debug APKs against an empty Kotlin application. Debug packaging, JNA, retained native symbols, and runtime overhead are included. No production-app download-size claim follows from these numbers.

## Scope and remaining work

The prototypes return typed findings, explicit coverage gaps, a cancellation flag, and content-free progress units. They deliberately omit archive streaming, file protection, hashing, report rendering/serialization, UUID generation, campaign enrichment, and persisted case compatibility. They do not retain the production engines' optimized token index. They do not establish detection effectiveness, archive safety, large real-world throughput, or full Unicode/IDNA equivalence.

The production Swift and Android tests separately consume the same matching/STIX/budget corpus with explicitly named legacy expectations. Candidate integrations must pass the shared expectations without those overrides.
