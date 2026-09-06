## ADDED Requirements

### Requirement: Share reports and legacy readers
The migration SHALL implement this stage using the selected common engine and retain the behavior in the report-compatibility capability specification.

#### Scenario: Stage completion
- **WHEN** this migration stage is ready for cutover
- **THEN** Synthetic historical cases from both serializers reopen without lost findings or invented metadata; export content, hashes, failure cleanup and original opt-in pass.
