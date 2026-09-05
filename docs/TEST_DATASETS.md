# Public mobile-forensics test data

This note lists reputable, lawful sources for exercising Local Verify's import and matching workflow. Use only data you are authorized to possess. Treat all device extractions as sensitive evidence: they can contain identifiers, location history, account details, messages, and network metadata.

## Important limitation: confirmed compromise samples

There is no widely available, reputable public corpus of raw sysdiagnose archives from iPhones *confirmed* to be compromised by Pegasus or similar spyware. Victim data is normally not released, for good reason. Do not treat a random archive labelled "infected" on a forum or code-hosting site as ground truth, and never execute files or apps from such an archive.

The practical alternative is a layered corpus:

1. Public, benign sysdiagnose archives to test archive handling and parsing.
2. Public forensic images and CTF cases to test real-world artifacts.
3. Small synthetic fixtures with deliberately seeded, documented indicators to test positive matching.
4. Vetted IOC bundles and a separately maintained expected-results manifest.

Synthetic fixtures should be labelled as synthetic and must never be reported as evidence of a real compromise.

This repository includes `Fixtures/derived-confirmed-pegasus-sysdiagnose.tar.gz`
and its companion fixture-only STIX2 file. They are minimal, synthetic
reconstructions of two public Amnesty forensic timeline entries, and exist only
to test structured matching. See `Fixtures/DERIVED_CONFIRMED_CASE.md`; do not
substitute this fixture for a real acquisition or production threat intelligence.

## iPhone and iPad sources

| Source | What it provides | Appropriate use |
| --- | --- | --- |
| [Sysdiagnose Analysis Framework (SAF)](https://github.com/EC-DIGIT-CSIRC/sysdiagnose) | A framework from EC-DIGIT/CERT-EU with test sysdiagnose material, including an iOS 12 example. | Archive ingestion and parser regression tests. |
| [iLEAPP public-corpus catalog](https://github.com/abrignoni/iLEAPP/blob/main/admin/docs/testing/public_corpus_images.md) | A documented list of public iOS images from forensic CTFs and Digital Corpora, with publisher hashes and download locations. Some contain sysdiagnose artifacts. | Broad artifact parsing and compatibility testing across iOS versions. |
| [The Evidence Locker](https://theevidencelocker.github.io/) | A single index of public forensic images, including file names, sizes, hashes, and download links. | Discovery and integrity verification of CTF/corpus downloads. |
| [NIST CFReDS](https://cfreds.nist.gov/all) | Documented reference images, including mobile datasets and Android/iOS entries. | Reproducible tool testing and training cases. |
| [MVT](https://docs.mvt.re/en/stable/) | An Amnesty-led forensic toolkit and publicly documented IOC/methodology ecosystem. | Comparing IOC matching semantics and analysis workflows. |

MVT can prepare an extracted folder or a gzip-compressed tar sysdiagnose archive, but its sysdiagnose command runs modules supplied by installed plugins; it does not itself ship built-in sysdiagnose analysis modules. See its [sysdiagnose documentation](https://docs.mvt.re/en/latest/ios/sysdiagnose/).

For this repository, the safest first test is the project-generated `Fixtures/synthetic-sysdiagnose.tar.gz`. It is intentionally non-sensitive and has documented expected leads. Add public archives only after recording their source URL, date retrieved, SHA-256, iOS version, device model (if known), and license/terms in a local evidence manifest.

## Android equivalents

Android does not have a direct equivalent to an iOS sysdiagnose archive. The closest useful diagnostic acquisition is usually a **bug report** (often a ZIP), together with any authorized backup, package inventory, and application logs. Formats and available evidence vary substantially by Android version and device manufacturer.

| Source | What it provides | Appropriate use |
| --- | --- | --- |
| [AndroidQF and MVT](https://github.com/mvt-project) | AndroidQF collects authorized artifacts such as a bug report, backup, system logs, and suspicious APKs; MVT can analyze its output. | Establishing a realistic Android collection-and-analysis workflow. |
| [MVT Android methodology](https://github.com/mvt-project/mvt/blob/main/docs/android/methodology.md) | Current guidance on AndroidQF, backups, intrusion logs, and limitations of Android forensic data. | Defining the supported input boundary and test coverage. |
| [NIST CFReDS Android collections](https://cfreds.nist.gov/all) | Reference Android images, including Android 10 and older physical/JTAG/chip-off images. | Parser validation, artifact discovery, and forensic-training exercises. |
| [Digital Corpora mobile-phone collection](https://digitalcorpora.org/corpora/cell-phones/) | Public mobile forensic images, including several Android and iOS devices. | Cross-version and extraction-format testing. |
| [CICMalDroid 2020](https://www.unb.ca/cic/datasets/maldroid-2020.html) | Labeled Android malware/benign research data: APKs plus collected dynamic-analysis logs and feature CSVs. | Detection-model or log/feature-pipeline evaluation, not routine mobile-forensics parsing. |

The APK portion of malware datasets is hazardous. Prefer their captured logs or feature exports when possible. If APK analysis is necessary, keep it in a disposable, isolated lab with no personal accounts, secrets, or network access to production systems; do not install samples on a personal phone or ordinary emulator.

## Suggested test matrix

Maintain an expected-results manifest and cover the following independently:

| Test class | iOS input | Android input | Expected outcome |
| --- | --- | --- | --- |
| Archive handling | Valid and malformed `sysdiagnose_*.tar.gz` | Valid and malformed bug-report ZIP | Clear accept/reject result; no crash or unbounded extraction. |
| Benign baseline | Public sysdiagnose across supported iOS releases | Authorized bug reports across Android vendors/releases | No unsupported claim of compromise. |
| Seeded positive | Synthetic log record matching a known test STIX indicator | Synthetic bug-report/log record matching the same test indicator | Exact, reproducible finding with source location. |
| Unsupported data | Binary unified logs, plists, links, huge entries | Vendor-specific/binary sections, huge entries | Explicitly skipped/unsupported, not silently misread. |
| Regression corpus | Hash-verified public CTF/forensic images | Hash-verified CFReDS/Digital Corpora images | Stable parser output reviewed against the manifest. |

Do not conflate an IOC match with proof of infection. It is a lead that requires context, validation of the indicator source, and where possible corroboration from independent artifacts.

## Handling and provenance

- Download only from the original publisher or a well-documented catalog that links to it.
- Verify the publisher-provided hash before adding an archive to a test corpus.
- Keep raw evidence outside the app repository; commit only synthetic fixtures and non-sensitive expected-result metadata.
- Record provenance, hashes, tool versions, selected IOC bundle revision, and expected findings so a result is reproducible.
- Do not upload device archives, screenshots, backups, or APKs to public issue trackers or third-party scanning services without the data owner's explicit consent and a privacy review.
