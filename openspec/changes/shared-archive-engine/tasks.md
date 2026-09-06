## 1. Design and implementation
- [ ] 1.1 Resolve this stage's implementation details and dependency review.
- [ ] 1.2 Build streaming ZIP and gzip/tar processing around the selected common engine, with platform-owned input access and no directory extraction. Preserve integrity validation, byte/entry/finding budgets, skipped-file reporting and cancellation.

## 2. Acceptance
- [ ] 2.1 Synthetic corrupt/truncated archives, duplicate and unsafe paths, large expansion, unsupported entries and cancellation pass on both platforms with bounded memory.
- [ ] 2.2 Record validation and update affected specs before archiving.
