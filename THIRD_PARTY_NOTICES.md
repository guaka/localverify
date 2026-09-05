# Third-party notices

Project-owned source is licensed under the unchanged MVT License 1.1 in LICENSE. No upstream MVT implementation files have been copied into this initial prototype; MVT's documented forensic workflow informs the design.

Apple SDK frameworks (Foundation, SwiftUI, UIKit, CryptoKit) and the system zlib are linked through their platform-provided interfaces. No third-party binaries are bundled. The demonstration `.invalid` domain used by tests is synthetic. Imported indicator sets remain subject to their providers' terms.

Amnesty International's Pegasus and Predator/Cytrox indicator bundles are redistributed unmodified under CC BY 2.0: https://creativecommons.org/licenses/by/2.0/ . Source: https://github.com/AmnestyTech/investigations . Exact commit, paths, hashes, and retrieval time are recorded in iOS/App/ThreatData/threat-manifest.json and ATTRIBUTION.txt, which ship in the app. The app parses only supported definitions; no Amnesty endorsement is implied.

Before adding any MVT source or external indicator dataset, retain its provenance, revision, copyright notices, and license here. MVT's license grants no project branding endorsement.

MVT contributors' Predator, Coruna and DarkSword STIX collections are redistributed unmodified from https://github.com/mvt-project/mvt-indicators under its MIT license, copyright (c) 2022 MVT. Full text ships as iOS/App/ThreatData/MVT-INDICATORS-LICENSE.txt. Each collection's upstream README ships as a *-SOURCES.md file, preserving research attribution. The manifest records the pinned revision and per-file hashes. Matching is limited to supported patterns and duplicate values are collapsed; no source endorsement is implied.
