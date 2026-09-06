# Local data handling

The app contains no upload client, remote analysis service, analytics SDK, telemetry, or indicator downloader. Analysis reads only user-imported files and local indicator data. Analysis works offline with bundled or locally imported definitions. New definitions can be imported as a local STIX file or shipped in an updated app bundle. Source, license, and help references are selectable text; the app does not open websites. The collection guide can still open Apple's AssistiveTouch settings.

Project generation and iOS app/share-extension builds run `tools/check_offline.py`. This conservative source check flags common networking APIs, browser-opening controls, unreviewed framework imports, package dependencies, and ATS exceptions. Run it directly with `python3 tools/check_offline.py`; exercise synthetic checks with `--self-test`. It is a regression guard, not a complete static analyzer: indirect calls, Foundation URL-based reads, lower-level APIs, and dynamically loaded code still require review. It cannot prevent network activity at runtime. ATS keeps its default secure-connection requirements; it does not deny HTTPS. Browser activity after copying a reference, file-provider downloads, and explicit sharing remain outside this check.

App-owned cases, originals, indicator snapshots, checkpoints, and exports are excluded from automatic backup. iOS files use complete file protection. Share-extension inbox files are protected and their directory excluded from backup; interrupted copies are listed for explicit deletion.

Case copy buttons write only after a user tap: either the JSON report or finding excerpts with source context. Clipboard entries are marked local-only (Universal Clipboard disabled) and expire after five minutes. A user can paste into another app; that destination's copies and network behavior are outside Local Verify's control. Copying payloads does not extract complete archived files.

The `Documents/Imports` folder is deliberately visible as On My iPhone → Local Verify → Imports in Files (and through local Finder file sharing). It is a local staging area, also excluded from backup. Private cases and reports remain in Application Support and are not exposed through that folder.

An export leaves the sandbox only when the user invokes sharing and chooses a destination. Export ZIPs are not independently encrypted: protect them when choosing recipients or storage. Original evidence is included only when explicitly selected. Copies exported to another app or cloud provider are outside Local Verify's control.

Choosing an input from iCloud Drive may download it via Apple's file provider; Local Verify neither uploads it nor removes the existing cloud copy. For a wholly local collection workflow, save diagnostics under On My iPhone. Apple diagnostic collection and system analytics settings operate independently of this app.

Deleting a case removes its local original, checkpoints, indicator snapshot, and prepared export. It does not remove the source file in Settings/Files or any previously shared copies. Deletion is filesystem removal, not a forensic secure-erase guarantee.

Reviewed 2026-09-06: the indicator network client has been removed. No upload APIs or third-party telemetry dependencies are included. This describes the app's source; it is not an operating-system network block or a full external security audit.


Hardening update — 2026-09-06: iOS progress no longer includes live match counts or evidence filenames, and completion leaves review to the user's Cases action. The app covers its window synchronously on foreground exit to reduce app-switcher exposure. This is not a universal screenshot block or protection against privileged screen capture. Generated HTML reports prohibit remote resources and scripts through a content security policy in addition to escaping evidence text.
