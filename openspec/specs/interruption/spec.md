# Analysis interruption

## Purpose
Define the migration contract for analysis interruption. Requirements referring to the future/shared engine are targets, not claims about legacy production parity.

## Requirements

### Requirement: Analysis interruption
The future shared engine SHALL respond to cancellation, retain incomplete results for review, and start each explicit new attempt from the beginning with fresh findings. It SHALL verify the retained archive identity and preserve the frozen indicator set. Platform adapters SHALL supply lifecycle cancellation and protected persistence.

#### Scenario: Restart after stop
- **WHEN** the user stops a run and later starts analysis again
- **THEN** the next attempt rescans every supported file without carrying over partial findings; no checkpoint cursor is resumed.
