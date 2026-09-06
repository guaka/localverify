## Why
Promote the KMP record engine is a staged follow-up to the measured shared-engine experiment.

## What Changes
Implement the selected common Kotlin parser and matcher as a production module, preserving validated record semantics and adding the production token index, metadata, and full Unicode characterization. Move the five publisher bundles and manifest to one reviewed resource source, with legacy indicator cache handling.

## Capabilities
### New Capabilities
- `promote-shared-record-engine`: Promote the KMP record engine.
### Modified Capabilities
- `matching`: Pin Unicode behavior across platforms and characterize bounded record semantics.
- `indicator-parsing`: Specify code-point/byte limits, deterministic whitespace and typed campaign metadata.

## Impact
Archive, report and app cutover remain separate dependent changes.

Not implemented by the experiment. See ADR-001 for the foundation decision.
