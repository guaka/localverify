## Why
Switch native adapters and retire duplicate engines is a staged follow-up to the measured shared-engine experiment.

## What Changes
Connect the common engine to native app-owned import permissions, private storage, lifecycle cancellation and explicit sharing. Apply restart-from-beginning consistently. Remove superseded Swift/Kotlin parser, matcher, archive and exporter logic only after both workflows pass.

## Capabilities
### New Capabilities
- `native-engine-integration`: Switch native adapters and retire duplicate engines.
### Modified Capabilities

## Impact
Depends on all preceding changes. Preserve native interfaces and Apple HIG; physical-device release validation is a separate gate.

Not implemented by the experiment. See ADR-001 for the foundation decision.
