# Engine boundary

## Purpose
Define the migration contract for engine boundary. Requirements referring to the future/shared engine are targets, not claims about legacy production parity.

## Requirements

### Requirement: Engine boundary
The experimental engine SHALL accept bounded UTF-8 bytes and parsed indicator definitions and return typed findings, coverage gaps, cancellation state, and content-free progress counts. It SHALL expose a cancellation object safe to signal from another thread. Native adapters SHALL retain file-picker permissions, private storage, lifecycle integration, and explicit sharing.

#### Scenario: Cross-language cancellation
- **WHEN** a Swift or Kotlin caller cancels an active scan
- **THEN** the engine returns an explicitly incomplete result with bounded partial findings and does not present cancellation as a clean scan.
