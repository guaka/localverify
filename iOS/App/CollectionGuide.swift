import SwiftUI
import Accessibility

struct CollectionGuide: View {
    @State private var settingsError = false
    @State private var openingSettings = false

    private func openAssistiveTouch() {
        guard !openingSettings else { return }
        if #available(iOS 26.0, *) {
            openingSettings = true
            Task { @MainActor in
                defer { openingSettings = false }
                do { try await AccessibilitySettings.openSettings(for: .assistiveTouch) }
                catch { settingsError = true }
            }
        }
    }

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
            Section("Alternative · Use AssistiveTouch") {
                Text("If pressing the three buttons is difficult, you can try the on-screen AssistiveTouch control instead.")
                if #available(iOS 26.0, *) {
                    Button("Open AssistiveTouch Settings", systemImage: "hand.tap") { openAssistiveTouch() }
                        .disabled(openingSettings)
                        .accessibilityIdentifier("openAssistiveTouchSettings")
                    Text("Opens Settings only. If iOS stops at Accessibility, continue to Touch → AssistiveTouch. You choose whether to enable it; Local Verify cannot change this setting.").font(.footnote)
                } else {
                    Text("The direct shortcut requires iOS 26 or later. On this version, open Settings and follow the path below.").font(.footnote)
                }
                Text("Settings → Accessibility → Touch → AssistiveTouch").font(.headline)
                Text("Turn on AssistiveTouch. Under Custom Actions, choose an action such as Double-Tap, and select Analytics if it appears. Remember the previous assignment so you can restore it afterward.")
                Text("Perform your chosen action on the floating AssistiveTouch button, then wait several minutes. If Analytics is not offered, use the physical-button method above. Available actions can vary by iOS version.")
                Text("This is a one-time diagnostic collection action, not continuous monitoring. It is separate from Share iPhone Analytics, which shares analytics with Apple. You do not need to enable that sharing setting for Local Verify.").font(.footnote)
            }
            Section("2 · Find it in Settings") {
                Text("Open the iPhone Settings app and follow:")
                Text("Privacy & Security\n→ Analytics & Improvements\n→ Analytics Data").font(.headline)
                Text("iOS does not provide a supported direct shortcut from Local Verify to this screen. Open Settings manually and follow the path above.").font(.footnote)
                Text("Find the newest entry beginning with sysdiagnose_. Check its date and time against the collection you just started. It should be a .tar.gz archive, not an individual .ips crash record.")
            }
            Section("3 · Save a local copy") {
                Text("Tap the sysdiagnose entry, then tap the Share icon (a square with an upward arrow).")
                Text("Choose Save to Files. Scroll down in the share sheet if that action is not immediately visible.")
                Text("Choose Browse or go back to Locations, then select:\nOn My iPhone → Local Verify → Imports\nTap Save.").font(.headline)
                Text("Choose On My iPhone rather than iCloud Drive or another cloud provider to keep this copy local. Do not unpack or rename the archive.").font(.footnote)
            }
            Section("4 · Import into Local Verify") {
                Text("Return to Local Verify, then tap Import sysdiagnose.")
                Text("In the file picker, open Browse → On My iPhone → Local Verify → Imports and select the .tar.gz file you saved. Analysis starts after the copy completes.")
                Text("Import sysdiagnose opens a file picker; it does not generate diagnostics.").font(.footnote)
            }
            Section("If you cannot find the file") {
                Text("Wait a few more minutes and reopen Analytics Data. Look for sysdiagnose_ with today's date. If it is still absent, repeat the short button press once. Device and iOS versions can differ.")
                Text("If the Local Verify folder is missing in Files, open Local Verify once, return to Files → Browse, and reopen On My iPhone.")
                Text(verbatim: "Apple's diagnostic reference\nhttps://developer.apple.com/feedback-assistant/profiles-and-logs/?name=sysdiagnose").font(.footnote).textSelection(.enabled)
            }
            Section("After importing") {
                Text("Local Verify keeps its own protected case copy. The file you saved in Files remains separate; you can remove it there when you no longer need it. Deleting a Local Verify case does not delete the original in Settings or Files.")
                Text("Analysis runs locally. Nothing is uploaded by Local Verify. Findings are leads for review, not proof that a device is clean or compromised.")
            }
        }.navigationTitle("Collect diagnostics").navigationBarTitleDisplayMode(.inline)
            .alert("Could not open AssistiveTouch", isPresented: $settingsError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text("Open Settings → Accessibility → Touch → AssistiveTouch manually. No settings have been changed by Local Verify.")
            }
    }
}
