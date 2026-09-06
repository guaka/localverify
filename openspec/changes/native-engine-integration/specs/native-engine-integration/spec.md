## ADDED Requirements

### Requirement: Switch native adapters and retire duplicate engines
The migration SHALL implement this stage using the selected common engine and retain the behavior in the interruption capability specification.

#### Scenario: Stage completion
- **WHEN** this migration stage is ready for cutover
- **THEN** SwiftUI/Compose synthetic workflow suites, stop/background/restart, protected storage, report review/export and existing-case compatibility pass. Offline-policy coverage includes the promoted engine and its reviewed dependencies.
