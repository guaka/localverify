# Indicator refresh tools

> Shared-engine migration requirements and named legacy differences are maintained in [the engine contract guide](ENGINE-EXPERIMENT.md). Existing implementation and historical validation notes below remain platform-specific.

The app embeds 2.33 MB of STIX data: Amnesty International Pegasus and Predator/Cytrox (CC BY 2.0), plus MVT's expanded Predator, Coruna and DarkSword collections (MIT). Coruna and DarkSword incorporate published iOS research from March 2026. These selected collections are not comprehensive current-spyware coverage.

```
# Check without changing resources:
python3 tools/update_bundled_indicators.py --check

# Refresh from current publisher master, pinned to a resolved commit:
python3 tools/update_bundled_indicators.py

# Reproduce a reviewed revision:
python3 tools/update_bundled_indicators.py --revision 3d8f248a0d015f183724ae7d096a5c46a8bb5fc7 --mvt-revision b22ddf05e1a31e7732b8895676987c5c3482ef65

# Verify before rebuilding:
swift test --scratch-path build/review-tests
```

Review resource/manifest changes before committing. The manifest records the immutable commit, retrieval date, URLs, byte sizes, and SHA-256 hashes. All responses are downloaded and checked before replacement. No publisher code is executed. Rebuild the app to ship a refreshed snapshot.

The iOS app does not download indicators. Users can import a local STIX file or use the bundled snapshot; maintainers refresh that snapshot with the tools above and rebuild the app. Cases freeze their own indicator snapshots. An app upgrade prefers a newer bundled set over an older known publisher cache; manually imported sets are retained. Duplicate kind/value indicators are matched only once.

The snapshot contains 2,336 unique supported indicators and 55 explicitly skipped definitions. Unsupported hashes/IPs and other patterns are not counted as checked. The newest embedded indicator timestamp is 2026-03-30 00:00 UTC; the older Predator bundle still uses a 2023 timestamp, so this is not a statement that all feeds are equally recent. The developer manifest pins each repository and includes upstream research references and full license notices.

Definitions dated is the newest indicator modified/created timestamp in the active bundles. Last checked is the retrieval time, not the age of definitions. Times display as yyyy-MM-dd HH:mm in device local time. MB uses decimal bytes / 1,000,000.

## External research references

[Cryptware Apps — Malware-Database](https://github.com/cryptwareapps/Malware-Database) is described by its maintainers as a collection of malware samples and source code across multiple platforms. It is listed here for background research when evaluating future indicator sources and synthetic test scenarios. Local Verify does not bundle or fetch its contents; this reference adds no detection coverage. Any future integration would require a separate OpenSpec change and review of provenance, licensing and compatibility with the supported indicator contract.
