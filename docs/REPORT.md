# Report contract, version 1

Exported `report.json` uses ISO-8601 dates and these fields:

- `schemaVersion`, `engineVersion`, `platform`, `caseID`, `createdAt`
- `archiveSHA256`, `indicatorVersion`, `indicatorSHA256`, `consentConfirmedAt`
- `completed`, `findings`, `analyzed` (source archive paths), `skipped` (paths/reasons and unsupported indicators), `errors`
- Each finding: `id`, `rule`, `value`, `source`, `record`, optional `timestamp`, `matchType` (`structured` or `raw-text`), `explanation`, `excerpt`.

Status derives from completion/errors first; incomplete analyses can still contain leads. Otherwise findings determine leads versus no matches in analyzed evidence. `completed` means the bounded supported workflow finished, not all archive contents were understood. Coverage must always accompany findings.

The export contains `report.json`, `report.html` with evidence excerpts, and optionally `original.tar.gz`. Never infer device cleanliness. Android will emit the same contract with platform `android` and its own engine version.
