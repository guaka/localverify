# Indicator parsing

## Purpose
Define the migration contract for indicator parsing. Requirements referring to the future/shared engine are targets, not claims about legacy production parity.

## Requirements

### Requirement: Indicator parsing
The shared engine SHALL accept bounded strict UTF-8 STIX2 bundles and support only single equality expressions for domain-name:value, url:value, process:name, file:path, and file:name. Compound, revoked, time-qualified and unsupported patterns SHALL be reported as unsupported. Syntax whitespace SHALL be ASCII space, tab, CR, LF, vertical tab or form feed. Supported values SHALL be limited to 2,048 Unicode code points within the existing 8,192-byte pattern budget. Valid campaign labels and latest indicator timestamps SHALL be retained.

#### Scenario: Supported and unsupported definitions
- **WHEN** a bundle contains supported equality patterns alongside a compound expression
- **THEN** supported indicators are returned with IDs and the compound expression is recorded as unsupported.

#### Scenario: Code-point and byte budgets
- **WHEN** a value contains combining sequences or supplementary characters
- **THEN** the value limit counts Unicode code points, the pattern limit counts UTF-8 bytes, and exceeding either is reported according to the canonical parser fixtures.

#### Scenario: Typed campaign metadata
- **WHEN** a supported definition includes x_mvt_campaigns
- **THEN** string labels are deduplicated and bounded; malformed or oversized campaign metadata causes a typed parse error without a partial set.

## Acceptance evidence

Canonical synthetic scenarios: `Fixtures/stix.json`. Both native test suites and both candidate integrations consume these vectors. Named legacy expectations characterize current behavior; candidates must meet the shared expectation. Archive-container acceptance remains in existing native archive tests until the archive migration.

The promoted module additionally consumes `Fixtures/record-engine-*.json` through JVM, Swift simulator and Android emulator checks. Pinned Unicode behavior and completed validation are recorded in `docs/SHARED-RECORD-ENGINE.md`. Legacy production engines and the frozen experiment retain their named differences until cutover.
