import SwiftUI
import UniformTypeIdentifiers

@main
struct TriageApp: App {
    @StateObject private var model = CaseStore()
    var body: some Scene {
        WindowGroup { ContentView(model: model).tint(Color("AccentColor")).onAppear { model.load() }.onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in model.loadInbox() } }
    }
}

@MainActor final class CaseStore: ObservableObject {
    @Published var reports: [Report] = []
    @Published var message = ""
    @Published var busy = false
    @Published var canCancel = false
    @Published var progress = ""
    @Published var indicators = IndicatorSet.demo
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
        } catch { message = error.localizedDescription }
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
        let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
        do {
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard size <= 5 * 1024 * 1024 else { throw TriageError.invalid("Indicator file exceeds 5 MiB") }
            indicators = try IndicatorSet.parse(Data(contentsOf: url))
            message = "Loaded \(indicators.indicators.count) indicators; \(indicators.unsupported.count) unsupported."
        } catch { message = error.localizedDescription }
    }
    func startImport(_ url: URL) {
        guard !busy else { return }; busy = true; canCancel = true; progress = "Copying evidence…"
        let selected = indicators; let root = root
        let worker = Task.detached(priority: .userInitiated) {
                    let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                    let id = UUID().uuidString; let folder = root.appendingPathComponent(id)
                    try Self.protect(folder)
                    do {
                        let destination = folder.appendingPathComponent("original.tar.gz")
                        try Archive.copy(url, to: destination)
                        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: destination.path)
                        var report = Report(caseID: id, indicators: selected, consent: Date())
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
            return try Analyzer.analyze(archive: dir.appendingPathComponent("original.tar.gz"), indicators: set, previous: report) { checkpoint in
                try LocalStorage.write(JSONEncoder().encode(checkpoint), to: dir.appendingPathComponent("checkpoint.json"))
                Task { @MainActor [weak self] in self?.progress = "\(checkpoint.analyzed.count) files analyzed · \(checkpoint.findings.count) leads" }
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
    @State private var consent = false
    @State private var importing = false
    private enum ImportKind { case archive, indicators }
    @State private var importKind: ImportKind = .archive
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label("Local evidence. Reviewable leads.", systemImage: "magnifyingglass")
                    Text("Experimental investigator prototype. Diagnostics provide limited coverage; no matches does not establish that a device is uncompromised.").font(.footnote)
                }
                Section("1 · Collect diagnostics") {
                    NavigationLink { CollectionGuide() } label: {
                        Label("How to collect and export sysdiagnose", systemImage: "list.number")
                    }.accessibilityIdentifier("collectionGuide")
                    Text("Generate the archive in iOS, save it to On My iPhone → Local Verify → Imports, then import it below. No computer is needed.").font(.footnote)
                }
                Section("2 · Consent and import") {
                    Text("Archives may contain sensitive personal data. Cases remain on this phone until deleted and are excluded from automatic backup. Exported copies are controlled by their recipient.").font(.footnote)
                    Toggle("I confirm the data owner gave informed, uncoerced consent to this analysis and retention.", isOn: $consent).accessibilityIdentifier("consent")
                    Button("Import sysdiagnose", systemImage: "square.and.arrow.down") { importKind = .archive; importing = true }.disabled(!consent || model.busy).accessibilityIdentifier("importArchive")
                    ForEach(model.inbox, id: \.self) { url in
                        Button(url.pathExtension == "partial" ? "Interrupted import — swipe to delete" : "Analyze shared archive \(url.lastPathComponent.prefix(8))") { model.startImport(url) }.disabled(!consent || model.busy || url.pathExtension == "partial")
                            .swipeActions { Button("Delete", role: .destructive) { model.deleteInbox(url) }.disabled(model.busy) }
                    }
                }
                Section("Indicators for new cases") {
                    Text(model.indicators.version).font(.caption)
                    Text("Bundled demonstration indicator: triage-test.invalid. Import trusted threat indicators for investigative use.").font(.footnote)
                    Button("Import STIX2 bundle") { importKind = .indicators; importing = true }.disabled(model.busy).accessibilityIdentifier("importIndicators")
                    ForEach(model.indicators.unsupported, id: \.self) { Text($0).font(.caption) }
                }
                if model.busy { Section { ProgressView(model.progress); if model.canCancel { Button("Cancel") { model.cancel() } } } }
                if !model.message.isEmpty { Section { Text(model.message).foregroundStyle(.orange) } }
                Section("Cases") {
                    ForEach(model.reports, id: \.caseID) { report in
                        NavigationLink { CaseView(model: model, id: report.caseID) } label: {
                            VStack(alignment: .leading) { Text(report.status); Text(report.createdAt.formatted()).font(.caption); Text("\(report.findings.count) leads · \(report.analyzed.count) files").font(.caption) }
                        }
                    }
                }
                Section {
                    Text("MVT License 1.1 · Experimental\nSource and third-party notices accompany private builds.").font(.footnote)
                    NavigationLink("License") {
                        ScrollView { Text((Bundle.main.url(forResource: "LICENSE", withExtension: nil).flatMap { try? String(contentsOf: $0) }) ?? "See LICENSE in the supplied source distribution.").font(.caption).padding() }.navigationTitle("MVT License 1.1")
                    }
                }
            }.navigationTitle("Local Verify")
            .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
                do {
                    let url = try result.get()
                    switch importKind {
                    case .archive: model.startImport(url)
                    case .indicators: model.importIndicators(url)
                    }
                } catch { model.message = error.localizedDescription }
            }
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
