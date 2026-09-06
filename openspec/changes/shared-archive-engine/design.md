## Context
See docs/ADR-001-shared-engine.md and openspec/specs/archive-processing/spec.md.

## Goals / Non-Goals
Build streaming ZIP and gzip/tar processing around the selected common engine, with platform-owned input access and no directory extraction. Preserve integrity validation, byte/entry/finding budgets, skipped-file reporting and cancellation.

## Decisions
Use the selected Kotlin Multiplatform foundation, native platform adapters, and the canonical synthetic corpus.

## Risks / Trade-offs
Depends on promote-shared-record-engine. Select and review archive dependencies in this change before implementation.

## Migration Plan
Implement this bounded stage only after its dependencies pass. Retain current app behavior until its explicit cutover stage.

## Open Questions
Detailed implementation and dependency choices belong to this future change and must be resolved before applying it.
