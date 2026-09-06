# Bounded archive processing

## Purpose
Define the migration contract for bounded archive processing. Requirements referring to the future/shared engine are targets, not claims about legacy production parity.

## Requirements

### Requirement: Bounded archive processing
The future shared engine SHALL stream ZIP and gzip/tar archives without extracting evidence into a directory. It SHALL enforce 8 GiB expanded input, 100,000 entries, 16 MiB per parsed file and 10,000 findings; reject unsafe or duplicate file paths and corrupt containers; and report unsupported entries and reduced coverage. Limits measured in bytes SHALL use UTF-8 bytes.

#### Scenario: Unsupported evidence
- **WHEN** an archive contains supported UTF-8 text and binary entries
- **THEN** supported entries are scanned and binary entries are recorded as skipped; completion never implies all evidence was understood.

## Acceptance evidence

Canonical synthetic scenarios: `Fixtures/budgets.json`. Both native test suites and both candidate integrations consume these vectors. Named legacy expectations characterize current behavior; candidates must meet the shared expectation. Archive-container acceptance remains in existing native archive tests until the archive migration.
