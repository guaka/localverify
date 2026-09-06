## Context
See docs/ADR-001-shared-engine.md and openspec/specs/interruption/spec.md.

## Goals / Non-Goals
Connect the common engine to native app-owned import permissions, private storage, lifecycle cancellation and explicit sharing. Apply restart-from-beginning consistently. Remove superseded Swift/Kotlin parser, matcher, archive and exporter logic only after both workflows pass.

## Decisions
Use the selected Kotlin Multiplatform foundation, native platform adapters, and the canonical synthetic corpus.

## Risks / Trade-offs
Depends on all preceding changes. Preserve native interfaces and Apple HIG; physical-device release validation is a separate gate.

## Migration Plan
Implement this bounded stage only after its dependencies pass. Retain current app behavior until its explicit cutover stage.

## Open Questions
Detailed implementation and dependency choices belong to this future change and must be resolved before applying it.
