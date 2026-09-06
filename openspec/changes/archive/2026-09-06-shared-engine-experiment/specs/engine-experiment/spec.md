## ADDED Requirements

### Requirement: Reproducible architecture comparison
Both candidates SHALL consume the canonical synthetic corpus through Swift on an iOS simulator and Kotlin on an Android emulator. Device targets SHALL compile without physical-phone installation. The report SHALL record correctness, cancellation, timing, memory, binary size, build cost and unimplemented archive/report work.

#### Scenario: Architecture selection
- **WHEN** measurements from both candidate integrations are available
- **THEN** the decision records passing correctness/integration gates and compares costs; untested capabilities are not claimed as ready.
