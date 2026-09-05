import SwiftUI
import UniformTypeIdentifiers

@main
struct LocalVerifyApp: App {
    @StateObject private var model = CaseStore()
    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
                .tint(Color("AccentColor"))
                .onAppear { model.load() }
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in model.loadInbox() }
        }
    }
}

@MainActor final class CaseStore: ObservableObject {
    @Published var reports: [Report] = []
    @Published var message = ""
    @Published var busy = false
    @Published var canCancel = false
    @Published var progress = ""
    @Published var indicators = IndicatorSet.demo
    @Published var updatingIndicators = false
    @Published var inbox: [URL] = []
    private var job: Task<Void, Never>?
    let root: URL
    init() {
        root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Cases", isDirectory: true)
        do {
            try Self.protect(root)
            let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            try Self.protect(documents)
            try Self.protect(documents.appendingPathComponent("Imports", isDirectory: true))
            let cached = try? JSONDecoder().decode(IndicatorSet.self, from: Data(contentsOf: indicatorCache))
            indicators = try ThreatUpdates.preferredInstalledSet(cached: cached, bundled: ThreatUpdates.bundled(in: .main))
        } catch { message = error.localizedDescription }
    }
    private var indicatorCache: URL { root.deletingLastPathComponent().appendingPathComponent("active-indicators.json") }
    private func activateIndicators(_ set: IndicatorSet) throws {
        guard !set.indicators.isEmpty else { throw TriageError.invalid("No supported indicators; current set retained") }
        try LocalStorage.write(JSONEncoder().encode(set), to: indicatorCache)
        indicators = set
    }
    func updateIndicators() {
        guard !updatingIndicators, !busy else { return }
        updatingIndicators = true
        Task {
            defer { updatingIndicators = false }
            do {
                let updated = try await ThreatUpdates.download()
                try activateIndicators(updated)
                message = "Threat indicators updated. Existing cases keep their original definitions."
            } catch { message = "Update failed; previous indicators retained. \(error.localizedDescription)" }
        }
    }
    func useBundledIndicators() {
        do { try activateIndicators(ThreatUpdates.bundled(in: .main)); message = "Using bundled Amnesty and MVT indicators." }
        catch { message = error.localizedDescription }
    }
    nonisolated static func protect(_ url: URL) throws {
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
        var mutable = url; var values = URLResourceValues(); values.isExcludedFromBackup = true; try mutable.setResourceValues(values)
    }
    func folder(_ id: String) -> URL { root.appendingPathComponent(id) }
    func load() {
        reports = ((try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? []).compactMap { try? JSONDecoder().decode(Report.self, from: Data(contentsOf: $0.appendingPathComponent("checkpoint.json"))) }.sorted { $0.createdAt > $1.createdAt }
        loadInbox()
    }
    func loadInbox() {
        #if LOCAL_ONLY
        inbox = []
        #else
        guard let shared = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.org.mobiletriage.private") else { return }
        inbox = ((try? FileManager.default.contentsOfDirectory(at: shared.appendingPathComponent("Inbox"), includingPropertiesForKeys: nil)) ?? []).filter { $0.lastPathComponent.hasSuffix(".tar.gz") || $0.pathExtension == "partial" }
        #endif
    }
    func importIndicators(_ url: URL) {
        guard !busy else { return }
        busy = true; canCancel = false; progress = "Loading threat indicators…"
        let worker = Task.detached(priority: .userInitiated) {
            let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard size <= 5 * 1024 * 1024 else { throw TriageError.invalid("Indicator file exceeds 5 MiB") }
            return try IndicatorSet.parse(Data(contentsOf: url))
        }
        job = Task {
            do {
                try activateIndicators(try await worker.value)
                message = "Threat indicators loaded: \(indicators.indicators.count) supported; \(indicators.unsupported.count) unsupported."
            } catch { message = "Could not load threat indicators. \(error.localizedDescription)" }
            busy = false
        }
    }
    func startImport(_ url: URL) {
        guard !busy else { return }; busy = true; canCancel = true; progress = "Preparing archive import…"
        let selected = indicators; let root = root
        let worker = Task.detached(priority: .userInitiated) {
                    let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                    let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    let id = UUID().uuidString; let folder = root.appendingPathComponent(id)
                    try Self.protect(folder)
                    do {
                        let destination = folder.appendingPathComponent("original.tar.gz")
                        try Archive.copy(url, to: destination) { copied in
                            let detail = size > 0 ? " \(Int((Double(copied) / Double(size) * 100).rounded()))%" : ""
                            Task { @MainActor [weak self] in self?.progress = "Copying archive…\(detail)" }
                        }
                        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: destination.path)
                        var report = Report(caseID: id, indicators: selected)
                        let indicatorURL = folder.appendingPathComponent("indicators.json")
                        try LocalStorage.write(JSONEncoder().encode(selected), to: indicatorURL)
                        report.indicatorSHA256 = try Archive.hash(indicatorURL)
                        try LocalStorage.write(JSONEncoder().encode(report), to: folder.appendingPathComponent("checkpoint.json"))
                        return report
                    } catch { try? FileManager.default.removeItem(at: folder); throw error }
        }
        job = Task {
            do {
                let report = try await withTaskCancellationHandler(operation: { try await worker.value }, onCancel: { worker.cancel() })
                try Task.checkCancellation()
                if inbox.contains(url) { try? FileManager.default.removeItem(at: url) }
                busy = false; load(); run(report)
            } catch { busy = false; canCancel = false; message = error.localizedDescription; load() }
        }
    }
    func run(_ report: Report) {
        guard !busy else { return }; busy = true; canCancel = true; progress = "Hashing and analyzing…"
        let dir = folder(report.caseID)
        let worker = Task.detached(priority: .userInitiated) { [weak self] in
            let setURL = dir.appendingPathComponent("indicators.json")
            let set = try JSONDecoder().decode(IndicatorSet.self, from: Data(contentsOf: setURL))
            guard try Archive.hash(setURL) == report.indicatorSHA256 else { throw TriageError.invalid("Case indicator set changed") }
            return try Analyzer.analyze(archive: dir.appendingPathComponent("original.tar.gz"), indicators: set, previous: report, progress: { detail in
                Task { @MainActor [weak self] in self?.progress = detail }
            }) { checkpoint in
                try LocalStorage.write(JSONEncoder().encode(checkpoint), to: dir.appendingPathComponent("checkpoint.json"))
            }
        }
        job = Task {
            do { _ = try await withTaskCancellationHandler(operation: { try await worker.value }, onCancel: { worker.cancel() }) }
            catch { message = error.localizedDescription }
            busy = false; canCancel = false; load()
        }
    }
    func cancel() { job?.cancel() }
    func deleteInbox(_ url: URL) {
        guard inbox.contains(url), !busy else { return }
        do { try FileManager.default.removeItem(at: url); loadInbox() } catch { message = error.localizedDescription }
    }
    func delete(_ report: Report) {
        guard !busy else { return }
        do { try FileManager.default.removeItem(at: folder(report.caseID)); load() } catch { message = error.localizedDescription }
    }
    func export(_ report: Report, includeOriginal: Bool) async -> URL? {
        guard !busy else { return nil }; busy = true; canCancel = false; progress = "Preparing export…"; defer { busy = false }
        let dir = folder(report.caseID)
        do {
            return try await Task.detached {
                let out = dir.appendingPathComponent("escalation.zip")
                try Export.zip(report: report, original: includeOriginal ? dir.appendingPathComponent("original.tar.gz") : nil, destination: out)
                return out
            }.value
        } catch { message = error.localizedDescription; return nil }
    }
}

struct ContentView: View {
    @ObservedObject var model: CaseStore
    var body: some View {
        TabView {
            ScanView(model: model)
                .tabItem { Label("Scan", systemImage: "magnifyingglass") }
            CasesView(model: model)
                .tabItem { Label("Cases", systemImage: "tray.full") }
            IndicatorsView(model: model)
                .tabItem { Label("Indicators", systemImage: "shield.lefthalf.filled") }
            AboutView()
                .tabItem { Label("About", systemImage: "info.circle") }
        }
    }
}

struct ScanView: View {
    @ObservedObject var model: CaseStore
    @State private var importing = false
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label {
                        Text("Local evidence. Reviewable leads.")
                    } icon: {
                        Image(systemName: "magnifyingglass").foregroundStyle(Color("AccentColor"))
                    }
                    Text("Experimental investigator prototype. Diagnostics provide limited coverage; no matches does not establish that a device is uncompromised.").font(.footnote)
                }
                Section("1 · Collect diagnostics") {
                    NavigationLink { CollectionGuide() } label: {
                        Label("How to collect and export sysdiagnose", systemImage: "list.number")
                    }.accessibilityIdentifier("collectionGuide")
                    Text("Generate the archive in iOS, save it to On My iPhone → Local Verify → Imports, then import it below. No computer is needed.").font(.footnote)
                }
                Section("2 · Import") {
                    Text("Archives may contain sensitive personal data. Cases remain on this phone until deleted and are excluded from automatic backup. Exported copies are controlled by their recipient.").font(.footnote)
                    Button("Import sysdiagnose", systemImage: "square.and.arrow.down") { importing = true }.disabled(model.busy).accessibilityIdentifier("importArchive")
                    ForEach(model.inbox, id: \.self) { url in
                        Button(url.pathExtension == "partial" ? "Interrupted import — swipe to delete" : "Analyze shared archive \(url.lastPathComponent.prefix(8))") { model.startImport(url) }.disabled(model.busy || url.pathExtension == "partial")
                            .swipeActions { Button("Delete", role: .destructive) { model.deleteInbox(url) }.disabled(model.busy) }
                    }
                }
                if !model.message.isEmpty { Section { Text(model.message).foregroundStyle(.orange) } }
            }.navigationTitle("Scan")
            .safeAreaInset(edge: .bottom) {
                if model.busy {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text(model.progress).lineLimit(2)
                        Spacer(minLength: 0)
                        if model.canCancel { Button("Cancel") { model.cancel() } }
                    }
                    .padding(12)
                    .background(.regularMaterial)
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("importStatus")
                }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
                do {
                    let url = try result.get()
                    model.startImport(url)
                } catch { model.message = error.localizedDescription }
            }
        }
    }
}
struct IndicatorsView: View {
    @ObservedObject var model: CaseStore
    @State private var importing = false
    private func timestamp(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
    var body: some View {
        NavigationStack {
            List {
                Section("Active threat indicators") {
                    Text(model.indicators.version).font(.caption)
                    Text("\(model.indicators.indicators.count) supported · \(model.indicators.unsupported.count) skipped").font(.subheadline).accessibilityIdentifier("indicatorCount")
                    LabeledContent("Definitions dated", value: model.indicators.latestIndicatorDate.map(timestamp) ?? "Not supplied")
                    if let size = model.indicators.byteCount { LabeledContent("Size", value: String(format: "%.2f MB", Double(size) / 1_000_000)) }
                    if let checked = model.indicators.checkedAt { LabeledContent("Last checked", value: timestamp(checked)) }
                    Text("Dates shown in device local time.").font(.caption).foregroundStyle(.secondary)
                    Text("Includes Pegasus, Predator, Coruna and DarkSword indicators, including research published in 2026. Newest definition date does not mean every campaign was updated then, or that every current threat is covered.").font(.footnote).foregroundStyle(.secondary)
                }
                Section("Manage indicators") {
                    Button("Update threat indicators", systemImage: "arrow.triangle.2.circlepath") { model.updateIndicators() }.disabled(model.busy || model.updatingIndicators).accessibilityIdentifier("updateIndicators")
                    if model.updatingIndicators { ProgressView("Downloading public definitions…") }
                    Text("Downloads definitions from Amnesty and MVT's public GitHub repositories. No diagnostics or findings are sent. GitHub sees normal connection metadata, such as your IP address.").font(.caption).foregroundStyle(.secondary)
                    Button("Import threat indicators", systemImage: "doc.badge.plus") { importing = true }.disabled(model.busy || model.updatingIndicators).accessibilityIdentifier("importIndicators")
                    Text("Advanced: import a STIX2 JSON file supplied by an investigator.").font(.caption).foregroundStyle(.secondary)
                    Button("Use bundled indicators", systemImage: "shippingbox") { model.useBundledIndicators() }.disabled(model.busy || model.updatingIndicators)
                }
                if !model.indicators.unsupported.isEmpty {
                    Section { DisclosureGroup("Unsupported definitions") { ForEach(model.indicators.unsupported, id: \.self) { Text($0).font(.caption) } } }
                }
                if !model.message.isEmpty { Section { Text(model.message).foregroundStyle(.orange) } }
            }
            .navigationTitle("Indicators")
            .safeAreaInset(edge: .bottom) {
                if model.busy {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text(model.progress).lineLimit(2)
                        Spacer(minLength: 0)
                        if model.canCancel { Button("Cancel") { model.cancel() } }
                    }
                    .padding(12)
                    .background(.regularMaterial)
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("importStatus")
                }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
                do { model.importIndicators(try result.get()) }
                catch { model.message = "Could not select threat indicators. \(error.localizedDescription)" }
            }
        }
    }
}
struct CasesView: View {
    @ObservedObject var model: CaseStore
    var body: some View {
        NavigationStack {
            List {
                if model.busy {
                    Section {
                        ProgressView(model.progress)
                        if model.canCancel { Button("Cancel", systemImage: "xmark.circle") { model.cancel() } }
                    }
                }
                if model.reports.isEmpty {
                    ContentUnavailableView("No cases yet", systemImage: "tray", description: Text("Import diagnostics in Scan. Your local cases and findings will appear here."))
                } else {
                    ForEach(model.reports, id: \.caseID) { report in
                        NavigationLink { CaseView(model: model, id: report.caseID) } label: {
                            Label {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(report.status)
                                    Text(report.createdAt.formatted()).font(.subheadline).foregroundStyle(.secondary)
                                    Text("\(report.findings.count) leads · \(report.analyzed.count) files").font(.caption).foregroundStyle(.secondary)
                                }
                            } icon: { Image(systemName: "doc.text.magnifyingglass") }
                        }
                    }
                }
            }.navigationTitle("Cases")
        }
    }
}

struct AboutView: View {
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label {
                        Text("Local Verify")
                    } icon: {
                        Image(systemName: "checkmark").foregroundStyle(Color("AccentColor"))
                    }
                    .font(.title2.bold())
                    Text("Private, on-device diagnostic verification for investigators.")
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0")
                }
                Section("Privacy") {
                    Label("Analysis stays on this device", systemImage: "iphone")
                    Text("No uploads or telemetry. Case files are protected and excluded from automatic backup. Data leaves Local Verify only when you choose to share an export.")
                    Text("Copies you saved in Files or previously shared remain separate from a Local Verify case.").font(.footnote).foregroundStyle(.secondary)
                }
                Section("Experimental coverage") {
                    Text("Findings are leads for review, not proof of compromise. No matches does not establish that a device is uncompromised.")
                    Text("Bundled definitions cover selected Pegasus, Predator, Coruna and DarkSword campaigns; unsupported indicator patterns are listed in Scan.").font(.footnote).foregroundStyle(.secondary)
                }
                Section("Indicator sources") {
                    Text("Amnesty International — Pegasus and Predator/Cytrox. Unmodified source bundles, licensed CC BY 2.0. The app uses only supported patterns.")
                    Link("Source and attribution", destination: URL(string: "https://github.com/AmnestyTech/investigations")!)
                    Link("CC BY 2.0 license", destination: URL(string: "https://creativecommons.org/licenses/by/2.0/")!)
                    Text("MVT contributors — expanded Predator, Coruna and DarkSword collections, compiled from published research. MIT license; source references and license text accompany the bundled files.")
                    Link("MVT indicator sources and license", destination: URL(string: "https://github.com/mvt-project/mvt-indicators")!)
                    Text("Optional updates download public definitions only. No diagnostic uploads or telemetry.").font(.footnote)
                }
                Section("Legal") {
                    NavigationLink {
                        ScrollView {
                            Text((Bundle.main.url(forResource: "LICENSE", withExtension: nil).flatMap { try? String(contentsOf: $0) }) ?? "See LICENSE in the supplied source distribution.")
                                .font(.body).textSelection(.enabled).padding()
                        }.navigationTitle("MVT License 1.1").navigationBarTitleDisplayMode(.inline)
                    } label: { Label("License", systemImage: "doc.text") }
                    .accessibilityIdentifier("license")
                    Text("MVT License 1.1. Source and third-party notices accompany private builds.").font(.footnote).foregroundStyle(.secondary)
                }
            }.navigationTitle("About")
        }
    }
}

struct CaseView: View {
    @ObservedObject var model: CaseStore
    let id: String
    @State private var original = false
    @State private var exportURL: URL?
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        if let report = model.reports.first(where: { $0.caseID == id }) {
            List {
                Section(report.status) {
                    Text("Experimental triage; review all leads in context.")
                    Text("SHA-256: \(report.archiveSHA256)").font(.caption).textSelection(.enabled)
                    Text(report.indicatorVersion).font(.caption)
                    if !report.completed { Button("Resume analysis") { model.run(report) }.disabled(model.busy) }
                }
                Section("Leads") {
                    ForEach(report.findings) { finding in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(finding.value).font(.headline)
                            Text("\(finding.matchType) · \(finding.rule)").font(.caption)
                            Text("\(finding.source) — \(finding.record)").font(.caption)
                            Text(finding.explanation).font(.footnote)
                            Text(finding.excerpt).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                        }
                    }
                }
                Section("Coverage") { Text("\(report.analyzed.count) files analyzed"); ForEach(report.skipped, id: \.self) { Text($0).font(.caption) } }
                Section("Errors") { ForEach(report.errors, id: \.self) { Text($0).foregroundStyle(.orange) } }
                Section("Escalation") {
                    Toggle("Include original sensitive archive", isOn: $original).disabled(model.busy).onChange(of: original) { _, _ in exportURL = nil }
                    Button("Prepare export") { Task { exportURL = await model.export(report, includeOriginal: original) } }.disabled(model.busy)
                    if let exportURL { ShareLink("Share report ZIP", item: exportURL) }
                }
                Section { Button("Delete case and local export", role: .destructive) { confirmDelete = true }.disabled(model.busy) }
            }.navigationTitle("Case").confirmationDialog("Delete this case permanently? Exported copies elsewhere will remain.", isPresented: $confirmDelete) { Button("Delete", role: .destructive) { model.delete(report); dismiss() } }
        }
    }
}
