import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let label = UILabel()
    private var saving = false
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .systemBackground
        label.numberOfLines = 0; label.textAlignment = .center
        label.text = "Import this diagnostic archive? It will be stored locally until you analyze or delete it in Local Verify."
        let button = UIButton(type: .system); button.setTitle("Save to Local Verify", for: .normal); button.addTarget(self, action: #selector(save), for: .touchUpInside)
        let cancel = UIButton(type: .system); cancel.setTitle("Close", for: .normal); cancel.addTarget(self, action: #selector(close), for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [label, button, cancel]); stack.axis = .vertical; stack.spacing = 24; stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack); NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24), stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24), stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)])
    }
    @objc private func close() { extensionContext?.completeRequest(returningItems: nil) }
    @objc private func save() {
        guard !saving else { return }
        guard let provider = (extensionContext?.inputItems as? [NSExtensionItem])?.flatMap({ $0.attachments ?? [] }).first,
              let type = provider.registeredTypeIdentifiers.first else { label.text = "No file attached."; return }
        saving = true; label.text = "Copying archive…"
        provider.loadFileRepresentation(forTypeIdentifier: type) { [weak self] url, error in
            do {
                guard let url else { throw error ?? NSError(domain: "Import", code: 1) }
                guard url.lastPathComponent.hasSuffix(".tar.gz") else { throw NSError(domain: "Expected a .tar.gz archive", code: 2) }
                guard let group = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.org.mobiletriage.private") else { throw NSError(domain: "App Group is not provisioned", code: 3) }
                var inbox = group.appendingPathComponent("Inbox")
                try FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
                var values = URLResourceValues(); values.isExcludedFromBackup = true; try inbox.setResourceValues(values)
                let target = inbox.appendingPathComponent(UUID().uuidString + ".tar.gz")
                let partial = target.appendingPathExtension("partial")
                defer { try? FileManager.default.removeItem(at: partial) }
                let input = try FileHandle(forReadingFrom: url); defer { try? input.close() }
                guard try input.read(upToCount: 2) == Data([0x1f, 0x8b]) else { throw NSError(domain: "Invalid gzip archive", code: 4) }
                try input.seek(toOffset: 0)
                FileManager.default.createFile(atPath: partial.path, contents: nil, attributes: [.protectionKey: FileProtectionType.complete])
                let output = try FileHandle(forWritingTo: partial); defer { try? output.close() }
                var size = 0
                while let data = try input.read(upToCount: 1048576), !data.isEmpty {
                    size += data.count
                    guard size <= 8 * 1024 * 1024 * 1024 else { throw NSError(domain: "Archive exceeds 8 GiB import limit", code: 5) }
                    try output.write(contentsOf: data)
                }
                try output.close()
                try FileManager.default.moveItem(at: partial, to: target)
                DispatchQueue.main.async { self?.label.text = "Saved. Open Local Verify to analyze." }
            } catch { DispatchQueue.main.async { self?.saving = false; self?.label.text = "Import failed: \(error.localizedDescription)" } }
        }
    }
}
