# Report compatibility

## Purpose
Define the migration contract for report compatibility. Requirements referring to the future/shared engine are targets, not claims about legacy production parity.

## Requirements

### Requirement: Report compatibility
The migration SHALL preserve readability of existing iOS and Android cases, optional legacy fields, finding identity and evidence hashes. Production exported report behavior SHALL remain unchanged during this experiment. The future shared serializer SHALL use the documented version-1 fields and ISO-8601 timestamps, with explicit adapters for legacy storage encodings.

#### Scenario: Read historical case
- **WHEN** a stored report lacks optional analysis timestamps or campaign labels
- **THEN** the application opens it without inventing timestamps or attribution and preserves stored findings.

### Requirement: Version 1 report meaning
The shared exported report SHALL include schemaVersion, engineVersion, platform, caseID, createdAt, archiveSHA256, indicatorVersion, indicatorSHA256, completed, findings, analyzed source paths, skipped paths/reasons, and errors. Optional fields SHALL remain optional: sysdiagnoseFilename, analysisStartedAt, analysisFinishedAt, indicatorSources, indicatorsCheckedAt, and legacy consentConfirmedAt. Exported dates SHALL use ISO-8601.

Each finding SHALL preserve id, rule, value, source, record, optional timestamp, matchType (structured or raw-text), explanation, and excerpt. Optional campaigns SHALL describe published indicator groups, not confirmed attribution. Older findings may receive display labels only when exact rule ID and matched value agree with bundled metadata. Filtering SHALL NOT remove stored findings or findings from Copy all payloads.

Status SHALL prioritize incomplete analysis/errors, then findings. Completion SHALL mean the bounded supported workflow finished, never device cleanliness. Exports SHALL contain report.json and escaped report.html; original evidence inclusion SHALL require an explicit opt-in and retain the actual archive format.

#### Scenario: Incomplete report with leads
- **WHEN** a bounded scan records findings but stops early or records coverage errors
- **THEN** the report retains its findings and identifies analysis as incomplete, without inferring device cleanliness.

#### Scenario: Missing historical metadata
- **WHEN** a report lacks an original filename or analysis timestamp
- **THEN** readers show that information as unrecorded rather than inferring it.
