# Indicator refresh tools

The app embeds ~1.5 MB of Amnesty International Pegasus and Predator/Cytrox STIX data under CC BY 2.0. Selected historical campaign indicators are not comprehensive current-spyware coverage.

```
# Check without changing resources:
python3 tools/update_bundled_indicators.py --check

# Refresh from current publisher master, pinned to a resolved commit:
python3 tools/update_bundled_indicators.py

# Reproduce a reviewed revision:
python3 tools/update_bundled_indicators.py --revision 3d8f248a0d015f183724ae7d096a5c46a8bb5fc7

# Verify before rebuilding:
swift test --scratch-path build/review-tests
```

Review resource/manifest changes before committing. The manifest records the immutable commit, retrieval date, URLs, byte sizes, and SHA-256 hashes. All responses are downloaded and checked before replacement. No publisher code is executed. Rebuild the app to ship a refreshed snapshot.

In-app updates fetch only two fixed HTTPS URLs from raw.githubusercontent.com, refuse redirects, use no cookies/credentials, cap each response at 5 MiB, and require a supported nonempty set from both feeds. The previous active set remains if download or validation fails. No background request runs at launch; analysis remains offline. Cases freeze their own indicator snapshots.

Definitions dated is the newest indicator modified/created timestamp in the active bundles. Last checked is the retrieval time, not the age of definitions. Times display as yyyy-MM-dd HH:mm in device local time. MB uses decimal bytes / 1,000,000.
