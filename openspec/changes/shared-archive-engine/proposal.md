## Why
Share bounded archive processing is a staged follow-up to the measured shared-engine experiment.

## What Changes
Build streaming ZIP and gzip/tar processing around the selected common engine, with platform-owned input access and no directory extraction. Preserve integrity validation, byte/entry/finding budgets, skipped-file reporting and cancellation.

## Capabilities
### New Capabilities
- `shared-archive-engine`: Share bounded archive processing.
### Modified Capabilities

## Impact
Depends on promote-shared-record-engine. Select and review archive dependencies in this change before implementation.

Not implemented by the experiment. See ADR-001 for the foundation decision.
