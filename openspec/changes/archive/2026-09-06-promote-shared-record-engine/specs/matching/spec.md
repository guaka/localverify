## MODIFIED Requirements

### Requirement: Record matching
The shared engine SHALL use ASCII-only domain case folding in raw text and pinned Unicode 16.0 default lowercase, including Final_Sigma and without locale tailoring, with leading/trailing ASCII-dot trimming for recognized structured domain fields. It SHALL compare other indicator values exactly, prefer recognized structured matches over text matches for each indicator, and preserve source, record path, timestamp and coverage information. It SHALL NOT perform NFC/NFD normalization, IDNA conversion, confusable expansion or full Unicode case folding. The canonical fixtures SHALL define shared behavior and named legacy differences.

#### Scenario: Structured process alias
- **WHEN** a UTF-8 record contains process_name with a matching process indicator
- **THEN** the shared engine returns a structured finding; the existing Swift implementation retains its explicitly recorded raw-text expectation until migration.

#### Scenario: Unicode domain distinctions
- **WHEN** raw text contains an accented uppercase domain, a Kelvin sign, a long s, a combining sequence, or a punycode equivalent
- **THEN** only the explicitly defined ASCII-folded literal and boundary rules apply; structured domain fields use Unicode lowercase but do not expand equivalent spellings.

#### Scenario: Unicode lengths and line numbering
- **WHEN** findings contain supplementary characters or Unicode line separators
- **THEN** excerpts contain at most 600 Unicode code points, timestamps at most 256 code points, resource limits count UTF-8 bytes, and raw record numbers advance at LF characters only.
