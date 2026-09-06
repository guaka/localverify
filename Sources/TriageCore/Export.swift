import Foundation
import CZlib

public enum Export {
    public static func payloadText(_ finding: Finding) -> String {
        """
        Rule: \(finding.rule)
        Match: \(finding.matchType)
        Campaign: \((finding.campaigns ?? ["Uncategorized"]).joined(separator: ", "))
        Value: \(finding.value)
        Source: \(finding.source)
        Record: \(finding.record)
        Timestamp: \(finding.timestamp ?? "Not available")
        Review: \(finding.explanation)

        Evidence excerpt (not the complete source file):
        \(finding.excerpt)
        """
    }

    public static func payloadsText(_ report: Report) -> String {
        let header = "Case: \(report.caseID)\nStatus: \(report.status)\nOriginal SHA-256: \(report.archiveSHA256)\n\nExperimental triage. These are leads requiring review, not proof of compromise.\n\n"
        return header + (report.findings.isEmpty ? "No finding payloads recorded." : report.findings.map(payloadText).joined(separator: "\n\n---\n\n"))
    }

    public static func json(_ report: Report) throws -> Data {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]; encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(report)
    }
    public static func html(_ report: Report) -> String {
        func esc(_ s: String) -> String { s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;") }
        let findings = report.findings.map { "<article><h2>\(esc($0.value))</h2><p>\(esc($0.matchType)) · \(esc($0.rule))</p><p>\(esc($0.source)) — \(esc($0.record))</p><p>\(esc($0.explanation))</p><pre>\(esc($0.excerpt))</pre></article>" }.joined()
        return "<!doctype html><meta charset='utf-8'><meta http-equiv='Content-Security-Policy' content=\"default-src &apos;none&apos;; style-src &apos;unsafe-inline&apos;\"><meta name='viewport' content='width=device-width'><title>Local Verify report</title><style>body{font:16px system-ui;max-width:900px;margin:40px auto;padding:20px}pre{white-space:pre-wrap;overflow-wrap:anywhere}article{border-top:1px solid #aaa}</style><h1>\(esc(report.status))</h1><p>Experimental triage. Absence of matches does not establish that a device is uncompromised.</p><p>Case: \(esc(report.caseID))</p><p>SHA-256: \(esc(report.archiveSHA256))</p><p>Indicators: \(esc(report.indicatorVersion))</p>\(findings)<h2>Coverage</h2><pre>\(esc(report.analyzed.joined(separator: "\n")))</pre><h2>Skipped / unsupported</h2><pre>\(esc(report.skipped.joined(separator: "\n")))</pre><h2>Errors</h2><pre>\(esc(report.errors.joined(separator: "\n")))</pre>"
    }
    // Streaming ZIP (stored entries with data descriptors); ZIP64 intentionally rejected.
    public static func zip(report: Report, original: URL?, destination: URL) throws {
        if let original, try Archive.hash(original) != report.archiveSHA256 { throw TriageError.invalid("Original evidence hash changed; export stopped") }
        try LocalStorage.createFile(destination)
        let output = try FileHandle(forWritingTo: destination); defer { try? output.close() }
        var central = Data(); var count: UInt16 = 0
        func u16(_ n: UInt16) -> Data { var x = n.littleEndian; return withUnsafeBytes(of: &x) { Data($0) } }
        func u32(_ n: UInt32) -> Data { var x = n.littleEndian; return withUnsafeBytes(of: &x) { Data($0) } }
        func entry(_ name: String, data: Data?, file: URL?) throws {
            let start = try output.offset(); guard start < UInt32.max else { throw TriageError.invalid("Export exceeds ZIP size limit") }
            let nameData = Data(name.utf8)
            var header = Data()
            header.append(u32(0x04034b50))
            for value: UInt16 in [20, 8, 0, 0, 0] { header.append(u16(value)) }
            for _ in 0..<3 { header.append(u32(0)) }
            header.append(u16(UInt16(nameData.count))); header.append(u16(0)); header.append(nameData)
            try output.write(contentsOf: header)
            var crc: uLong = 0; var size: UInt64 = 0
            func write(_ chunk: Data) throws {
                try Task.checkCancellation(); size += UInt64(chunk.count)
                guard size < UInt32.max else { throw TriageError.invalid("Evidence exceeds 4 GiB ZIP entry limit") }
                crc = chunk.withUnsafeBytes { crc32(crc, $0.bindMemory(to: Bytef.self).baseAddress, uInt(chunk.count)) }
                try output.write(contentsOf: chunk)
            }
            if let data { try write(data) }
            if let file {
                let input = try FileHandle(forReadingFrom: file); defer { try? input.close() }
                while let chunk = try input.read(upToCount: 1048576), !chunk.isEmpty { try write(chunk) }
            }
            try output.write(contentsOf: u32(0x08074b50) + u32(UInt32(crc)) + u32(UInt32(size)) + u32(UInt32(size)))
            central.append(u32(0x02014b50))
            for value: UInt16 in [20, 20, 8, 0, 0, 0] { central.append(u16(value)) }
            for value in [UInt32(crc), UInt32(size), UInt32(size)] { central.append(u32(value)) }
            central.append(u16(UInt16(nameData.count)))
            for _ in 0..<4 { central.append(u16(0)) }
            central.append(u32(0)); central.append(u32(UInt32(start))); central.append(nameData)
            count += 1
        }
        do {
            try entry("report.json", data: json(report), file: nil)
            try entry("report.html", data: Data(html(report).utf8), file: nil)
            if let original { try entry("original.tar.gz", data: nil, file: original) }
            let offset = try output.offset(); guard offset + UInt64(central.count) < UInt32.max else { throw TriageError.invalid("Export exceeds ZIP size limit") }
            try output.write(contentsOf: central)
            var end = u32(0x06054b50)
            for value: UInt16 in [0, 0, count, count] { end.append(u16(value)) }
            end.append(u32(UInt32(central.count))); end.append(u32(UInt32(offset))); end.append(u16(0))
            try output.write(contentsOf: end)
        } catch { try? FileManager.default.removeItem(at: destination); throw error }
    }
}
