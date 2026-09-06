## 1. Design and implementation
- [ ] 1.1 Resolve this stage's implementation details and dependency review.
- [ ] 1.2 Connect the common engine to native app-owned import permissions, private storage, lifecycle cancellation and explicit sharing. Apply restart-from-beginning consistently. Remove superseded Swift/Kotlin parser, matcher, archive and exporter logic only after both workflows pass.

## 2. Acceptance
- [ ] 2.1 SwiftUI/Compose synthetic workflow suites, stop/background/restart, protected storage, report review/export and existing-case compatibility pass. Offline-policy coverage includes the promoted engine and its reviewed dependencies.
- [ ] 2.2 Record validation and update affected specs before archiving.
