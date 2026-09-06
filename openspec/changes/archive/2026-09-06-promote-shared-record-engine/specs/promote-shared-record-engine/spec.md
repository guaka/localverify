## Purpose
Define the bounded shared record-analysis and indicator-metadata contract that native adapters will consume in the subsequent migration stages.

## ADDED Requirements

### Requirement: Promote the KMP record engine
The shared record module SHALL expose bounded byte input, typed indicator definitions and metadata, structured findings, coverage gaps, progress and cancellation through JVM and static Apple bindings. It SHALL remain separate from the frozen architecture experiment and the active production engines until native integration.

#### Scenario: Native binding acceptance
- **WHEN** the module is ready for subsequent archive integration
- **THEN** canonical matching, STIX, Unicode, cache and budget checks pass through Swift on a disposable iOS simulator and Kotlin on a disposable Android emulator, and Apple device-target compilation succeeds without a phone installation.

### Requirement: Bounded indexing and partial results
The shared matcher SHALL index only sought ASCII token presence in at most two text passes and recognized structured fields once. It SHALL bound inputs, metadata, JSON complexity, retained records, literal matching work and findings. Per-run cancellation SHALL be safe to signal across threads and retain findings already appended.

#### Scenario: Representative publisher workload
- **WHEN** 248,000-byte and 15,500,000-byte generated benign logs are scanned against the reviewed 2,336 publisher definitions
- **THEN** scans complete without findings or coverage gaps and timings are recorded for both native bindings.

#### Scenario: Live interruption retains earlier findings
- **WHEN** cancellation is signalled after the first indicator produced a finding and the second indicator started
- **THEN** the result is cancelled and incomplete, retains the first finding, and returns within the synthetic test deadline of five seconds.

### Requirement: Reviewed publisher resource source
The five publisher bundles, manifest and attribution SHALL have one canonical source at Shared/ThreatData. Build tasks SHALL package resources from this source. The shared loader SHALL verify each manifest byte count and SHA-256 before returning any combined set, preserve first indicator identity for exact kind/value duplicates, and union campaign labels in feed order.

#### Scenario: Missing or tampered publisher
- **WHEN** a required feed is missing or its bytes disagree with the manifest
- **THEN** combination returns an error and no partial indicator set.

#### Scenario: Duplicate across campaigns
- **WHEN** identical kind/value definitions appear in multiple reviewed feeds
- **THEN** one definition retains the first ID and all distinct campaign labels; source URLs, download time, latest indicator time, byte count and combined version remain available.

### Requirement: Conservative legacy indicator cache handling
Legacy cache decoding SHALL require an explicit platform, interpret Swift dates as seconds since 2001 and Android dates as epoch milliseconds, validate budgets and types, and preserve manual or unknown-provenance sets. Only known publisher snapshots may upgrade automatically. Frozen case sets SHALL never change automatically.

#### Scenario: Manual or frozen cache
- **WHEN** an imported cache or a case-frozen snapshot is considered against a newer publisher set
- **THEN** the cached definitions and metadata are retained.

#### Scenario: Same-version campaign enrichment
- **WHEN** a known same-version publisher cache lacks campaign labels
- **THEN** labels may be enriched only for definitions with identical ID, kind and value.
