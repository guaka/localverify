## Why
Swift and Kotlin duplicate analysis behavior and already differ in fixtures, limits, definitions, and interruption semantics. Establish durable requirements and measure a shared-engine slice before selecting its implementation language.

## What Changes
- Adopt project-local OpenSpec and one canonical synthetic contract corpus.
- Characterize legacy differences without changing production engines.
- Compare isolated Rust/UniFFI and Kotlin Multiplatform parser/scanner prototypes through Swift and Kotlin.
- Record measurements, limitations, and staged migration decisions.

## Capabilities
### New Capabilities
- `engine-experiment`: Reproducible cross-platform correctness and cost comparison.
### Modified Capabilities

## Impact
Development tooling, fixtures, tests, and documentation. Experimental packages are isolated from production app targets. No case migration, device evidence transfer, UI redesign, or production rollout.
