# Local data handling

The app contains no upload client, remote analysis service, analytics SDK, telemetry, or background indicator download. Analysis reads only user-imported files and local indicator data. Update threat indicators downloads five fixed public Amnesty/MVT bundles from raw.githubusercontent.com only when tapped. GET requests contain no evidence, findings, cookies, or account credentials. Redirects are rejected. GitHub sees connection metadata such as IP address. Analysis works offline with bundled or cached definitions. Source/license/help links open a browser only when tapped and pass no evidence.

App-owned cases, originals, indicator snapshots, checkpoints, and exports are excluded from automatic backup. iOS files use complete file protection. Share-extension inbox files are protected and their directory excluded from backup; interrupted copies are listed for explicit deletion.

The `Documents/Imports` folder is deliberately visible as On My iPhone → Local Verify → Imports in Files (and through local Finder file sharing). It is a local staging area, also excluded from backup. Private cases and reports remain in Application Support and are not exposed through that folder.

An export leaves the sandbox only when the user invokes sharing and chooses a destination. Export ZIPs are not independently encrypted: protect them when choosing recipients or storage. Original evidence is included only when explicitly selected. Copies exported to another app or cloud provider are outside Local Verify's control.

Choosing an input from iCloud Drive may download it via Apple's file provider; Local Verify neither uploads it nor removes the existing cloud copy. For a wholly local collection workflow, save diagnostics under On My iPhone. Apple diagnostic collection and system analytics settings operate independently of this app.

Deleting a case removes its local original, checkpoints, indicator snapshot, and prepared export. It does not remove the source file in Settings/Files or any previously shared copies. Deletion is filesystem removal, not a forensic secure-erase guarantee.

Reviewed 2026-09-05: the sole app network client downloads public indicators using bounded, fixed-URL GET requests. It receives no case/archive/report parameters. No upload APIs or third-party telemetry dependencies are included. This is source review, not a full external security audit.
