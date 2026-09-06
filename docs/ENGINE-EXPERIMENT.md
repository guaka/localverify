# Shared engine experiment

The [OpenSpec capabilities](../openspec/specs/) are the authoritative migration requirements. Existing native engines remain active. Future-engine requirements do not claim production parity.

## Current differences, captured before migration

| Behavior | Swift production | Kotlin production | Shared target |
| --- | --- | --- | --- |
| Matching corpus | Previously 5 domain-only rows | Previously 9 rows in two copies | One `Fixtures/matching.json`, explicit kinds and named legacy expectations |
| `process_name` | Raw-text fallback | Structured match | Structured match |
| Two-object `.ips` | Header and body parsed separately | Platform parser can accept header and ignore trailing body | Strict full parse, then header/body fallback |
| Text/line/work limits | UTF-8 byte counts | UTF-16 string lengths | UTF-8 byte counts |
| Missing STIX objects | Rejects | Empty list accepted | Rejects |
| Imported metadata | Does not read `x_mvt_campaigns`; latest date includes unsupported indicators | Reads campaigns; latest date from accepted indicators; sets sources/check time | Specify metadata migration separately; prototype tests supported IDs/kinds/values only |
| Bundled definitions | Five publisher feeds, merged metadata | Demo STIX bundle | One reviewed canonical feed collection during migration; unchanged now |
| Interrupted run | Resumes completed files | Restarts retained archive | Restart, retaining incomplete report for review |
| Persisted dates | Swift Codable dates; export ISO-8601 | Gson epoch milliseconds | Preserve legacy readers; shared export ISO-8601 |
| Archive intake | gzip/tar | gzip/tar and ZIP | Streaming support for both |
| JSON coverage budget | Reports reduced coverage and can continue raw matching | Throws and stops current analysis | Explicit coverage gaps; never claim a complete structured scan |

## Fixed prototype boundary

Both candidates expose `Indicator(id, kind, value)`, `Parsed(indicators, unsupported, error)`, `Finding(rule, value, source, record, matchType, timestamp, excerpt)`, `ScanResult(findings, coverageGaps, cancelled, visitedUnits)`, and a thread-safe per-run `Cancellation`.

`parseBundle(bytes, cancellation)` accepts up to 5 MiB of strict UTF-8 STIX. `scanRecord(bytes, source, indicators, cancellation)` accepts up to 16 MiB of strict UTF-8 text, enforces line/JSON/record/finding/work budgets, and returns partial results with explicit gaps or cancellation. Callers execute synchronously on their own worker. `visitedUnits` is a content-free definition counter; the cancellation object exposes an atomic `progressUnits` counter for live polling. Callback-based progress remains future work.

Both implement the five supported equality kinds and the union of recognized field aliases. Raw matching uses ASCII boundary characters and ASCII domain case folding; structured domain values are lowercased and trimmed of dots. No new IDNA policy is implied. Results have no generated UUID or current-time fields, enabling deterministic comparison. Campaign enrichment and report serialization are outside this slice.

## Reproduction and measurements

See [the experiment README](../experiments/engine/README.md) for build/run commands and [the architecture decision](ADR-001-shared-engine.md) for measured results and remaining work. Only synthetic fixture JSON is packaged. No original archives or phone containers are accessed.

## Ongoing workflow

1. Install the pinned development dependency with `npm ci` (Node 20.19+).
2. Use `npm run spec --` for CLI operations; the wrapper disables telemetry. Repository-local skills live in `.agents/skills/` and use the same wrapper.
3. For a behavioral change, create a proposal, scenarios, design and tasks; reference canonical fixture IDs. Internal cleanups need no separate proposal.
4. Run `npm run spec:validate` plus affected production and experiment tests. Record unavailable checks explicitly.
5. Archive a change only after its acceptance checks pass. Specs describe behavior; validation history records what actually ran.

OpenSpec is development tooling only and is not linked into either app. Regenerating its skills may restore direct CLI invocations; retain the wrapper when updating integrations.
