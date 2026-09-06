## ADDED Requirements

### Requirement: Share bounded archive processing
The migration SHALL implement this stage using the selected common engine and retain the behavior in the archive-processing capability specification.

#### Scenario: Stage completion
- **WHEN** this migration stage is ready for cutover
- **THEN** Synthetic corrupt/truncated archives, duplicate and unsafe paths, large expansion, unsupported entries and cancellation pass on both platforms with bounded memory.
