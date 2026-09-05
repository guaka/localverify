import SwiftUI

struct CollectionGuide: View {
    var body: some View {
        List {
            Section {
                Text("Collect a sysdiagnose on this iPhone").font(.title2.bold())
                Text("A sysdiagnose is an archive of iPhone diagnostic records. iOS creates it; Local Verify analyzes the file you choose. Allow several minutes and some free storage.")
                Label("These instructions work offline", systemImage: "iphone")
            }
            Section("1 · Generate the archive") {
                Text("Press Volume Up, Volume Down, and the Side button together for about 1–1.5 seconds, then release all three. You may feel a short vibration.")
                Text("Release promptly. Do not keep holding the buttons: a long hold can bring up Emergency SOS or the power screen.").font(.footnote)
                Text("Wait several minutes for collection to finish. Local Verify cannot start this collection automatically.")
            }
            Section("2 · Find it in Settings") {
                Text("Open the iPhone Settings app and follow:")
                Text("Privacy & Security\n→ Analytics & Improvements\n→ Analytics Data").font(.headline)
                Text("Find the newest entry beginning with sysdiagnose_. Check its date and time against the collection you just started. It should be a .tar.gz archive, not an individual .ips crash record.")
            }
            Section("3 · Save a local copy") {
                Text("Tap the sysdiagnose entry, then tap the Share icon (a square with an upward arrow).")
                Text("Choose Save to Files. Scroll down in the share sheet if that action is not immediately visible.")
                Text("Choose Browse or go back to Locations, then select:\nOn My iPhone → Local Verify → Imports\nTap Save.").font(.headline)
                Text("Choose On My iPhone rather than iCloud Drive or another cloud provider to keep this copy local. Do not unpack or rename the archive.").font(.footnote)
            }
            Section("4 · Import into Local Verify") {
                Text("Return to Local Verify. Confirm data-owner consent, then tap Import sysdiagnose.")
                Text("In the file picker, open Browse → On My iPhone → Local Verify → Imports and select the .tar.gz file you saved. Analysis starts after the copy completes.")
                Text("Import sysdiagnose opens a file picker; it does not generate diagnostics.").font(.footnote)
            }
            Section("If you cannot find the file") {
                Text("Wait a few more minutes and reopen Analytics Data. Look for sysdiagnose_ with today's date. If it is still absent, repeat the short button press once. Device and iOS versions can differ.")
                Text("If the Local Verify folder is missing in Files, open Local Verify once, return to Files → Browse, and reopen On My iPhone.")
                Link("Apple's diagnostic instructions (opens browser)", destination: URL(string: "https://developer.apple.com/feedback-assistant/profiles-and-logs/?name=sysdiagnose")!)
            }
            Section("After importing") {
                Text("Local Verify keeps its own protected case copy. The file you saved in Files remains separate; you can remove it there when you no longer need it. Deleting a Local Verify case does not delete the original in Settings or Files.")
                Text("Analysis runs locally. Nothing is uploaded by Local Verify. Findings are leads for review, not proof that a device is clean or compromised.")
            }
        }.navigationTitle("Collect diagnostics").navigationBarTitleDisplayMode(.inline)
    }
}
