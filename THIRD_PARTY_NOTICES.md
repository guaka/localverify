# Third-party notices

Project-owned source is licensed under the GNU Affero General Public License, version 3 or (at your option) any later version (SPDX: AGPL-3.0-or-later); see LICENSE. Third-party components and datasets retain their respective licenses below. No upstream MVT implementation files have been copied into this initial prototype; MVT's documented forensic workflow informs the design.

Apple SDK frameworks (Foundation, SwiftUI, UIKit, CryptoKit) and the system zlib are linked through their platform-provided interfaces. No third-party binaries are bundled. The demonstration `.invalid` domain used by tests is synthetic. Imported indicator sets remain subject to their providers' terms.

Amnesty International's Pegasus and Predator/Cytrox indicator bundles are redistributed unmodified under CC BY 2.0: https://creativecommons.org/licenses/by/2.0/ . Source: https://github.com/AmnestyTech/investigations . Exact commit, paths, hashes, and retrieval time are recorded in Shared/ThreatData/threat-manifest.json and ATTRIBUTION.txt, which ship in the app. The app parses only supported definitions; no Amnesty endorsement is implied.

Before adding any MVT source or external indicator dataset, retain its provenance, revision, copyright notices, and license here. MVT's license grants no project branding endorsement.

MVT contributors' Predator, Coruna and DarkSword STIX collections are redistributed unmodified from https://github.com/mvt-project/mvt-indicators under its MIT license, copyright (c) 2022 MVT. Full text ships as Shared/ThreatData/MVT-INDICATORS-LICENSE.txt. Each collection's upstream README ships as a *-SOURCES.md file, preserving research attribution. The manifest records the pinned revision and per-file hashes. Matching is limited to supported patterns and duplicate values are collapsed; no source endorsement is implied.


## Development-only engine experiment

The isolated `experiments/engine/` harness is not included in production app targets. It uses Mozilla UniFFI (MPL-2.0), Rust serde_json and regex (MIT OR Apache-2.0), Kotlin and kotlinx.serialization (Apache-2.0), AndroidX testing tools (Apache-2.0), and JNA (LGPL-2.1-or-later OR Apache-2.0). Cargo.lock and the experimental Gradle manifests identify versions. Review and include the relevant dependency notices before distributing a promoted engine.

The repository-local OpenSpec development CLI is MIT-licensed and pinned in package-lock.json. It is not an app runtime dependency.

## Shared record module (not yet linked into production apps)

`Shared/record-engine` uses Kotlin 2.2.21 and kotlinx.serialization 1.9.0 under Apache-2.0; the license is retained in `Shared/licenses/Kotlin-Apache-2.0.txt`. Its generated Unicode 16.0 casing data is derived from Unicode, Inc.'s Unicode Character Database under Unicode License v3 (`Shared/licenses/Unicode-3.0.txt`). The generator pins upstream file checksums and records their version. Apple CommonCrypto and Java MessageDigest provide platform SHA-256 implementations. Native integration must retain these notices when packaging the module for distribution.
