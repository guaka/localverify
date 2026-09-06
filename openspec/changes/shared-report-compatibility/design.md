## Context
See docs/ADR-001-shared-engine.md and openspec/specs/report-compatibility/spec.md.

## Goals / Non-Goals
Add a shared report model, version-1 ISO-8601 export serializer, escaped HTML and ZIP exports. Keep explicit adapters for existing Swift and Android saved-case encodings, optional metadata and opt-in originals.

## Decisions
Use the selected Kotlin Multiplatform foundation, native platform adapters, and the canonical synthetic corpus.

## Risks / Trade-offs
Depends on the shared record and archive contracts. No destructive in-place case migration.

## Migration Plan
Implement this bounded stage only after its dependencies pass. Retain current app behavior until its explicit cutover stage.

## Open Questions
Detailed implementation and dependency choices belong to this future change and must be resolved before applying it.
