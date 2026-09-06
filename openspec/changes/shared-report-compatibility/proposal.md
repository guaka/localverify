## Why
Share reports and legacy readers is a staged follow-up to the measured shared-engine experiment.

## What Changes
Add a shared report model, version-1 ISO-8601 export serializer, escaped HTML and ZIP exports. Keep explicit adapters for existing Swift and Android saved-case encodings, optional metadata and opt-in originals.

## Capabilities
### New Capabilities
- `shared-report-compatibility`: Share reports and legacy readers.
### Modified Capabilities

## Impact
Depends on the shared record and archive contracts. No destructive in-place case migration.

Not implemented by the experiment. See ADR-001 for the foundation decision.
