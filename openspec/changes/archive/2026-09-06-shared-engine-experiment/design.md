## Context
See `docs/ENGINE-EXPERIMENT.md` for legacy differences, the fixed prototype boundary, and the reproducible comparison. Production remains unchanged.

## Goals / Non-Goals
Share the future processing pipeline while preserving native UIs. This experiment covers STIX and a bounded record scan only; it does not implement streaming archives, durable checkpoints, hashing, or case/report migrations.

## Decisions
Use the same JSON corpus and typed indicator/finding/result records in both candidates. Provide a per-run cancellation object and synchronous scan on a caller-owned worker. Return content-free counters, partial findings and explicit cancellation. Use strict UTF-8, byte budgets and recognized field aliases. Prototype findings use stable content fields for comparison; production UUIDs are outside the slice.

## Risks / Trade-offs
A small corpus cannot establish archive security or real-device performance. Cross-language allocation and framework costs must be measured, not inferred. Existing Android dependencies are JVM-specific; KMP requires a common JSON parser and separate build. Rust requires mobile targets, Android NDK and generated bindings.

## Migration Plan
Keep prototypes isolated. Select a foundation only after correctness and native integration checks. Follow with separate changes for archive processing, reports/storage compatibility, app adapters, then removal of superseded engine code.
