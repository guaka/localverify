import XCTest
@testable import TriageCore

final class CopyExportTests: XCTestCase {
    func testCopyStreamsFilesAndReportsProgress() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let source = folder.appendingPathComponent("source.bin")
        let destination = folder.appendingPathComponent("destination.bin")
        let payload = Data(repeating: 0x5A, count: 1024 * 1024)
        try payload.write(to: source)
        var seen: [Int] = []
        try Archive.copy(source, to: destination, progress: { seen.append($0) })
        XCTAssertEqual(try Data(contentsOf: destination), payload)
        XCTAssertEqual(seen.last, payload.count)
        XCTAssertGreaterThan(seen.count, 0)
        XCTAssertTrue(FileManager.default.fileExists(atPath: destination.path))
    }
    func testIndividualAndAllPayloadsKeepSourceContext() throws {
        var report = Report(caseID: "synthetic-copy", indicators: .demo)
        report.archiveSHA256 = "synthetic-hash"
        let first = Finding(id: "one", rule: "rule-one", value: "first.invalid", source: "first.log", record: "line 7", timestamp: "2026-09-05", matchType: "raw-text", explanation: "Review context", excerpt: "Synthetic first payload")
        var second = first; second.id = "two"; second.source = "second.log"; second.excerpt = "Synthetic second payload"; second.timestamp = nil
        report.findings = [first, second]
        let individual = Export.payloadText(first)
        XCTAssertTrue(individual.contains("first.log"))
        XCTAssertTrue(individual.contains("line 7"))
        XCTAssertTrue(individual.contains("2026-09-05"))
        XCTAssertTrue(individual.contains("Synthetic first payload"))
        XCTAssertFalse(individual.contains("Synthetic second payload"))
        let all = Export.payloadsText(report)
        for value in ["synthetic-copy", "synthetic-hash", "Analysis incomplete", "Synthetic first payload", "Synthetic second payload", "Timestamp: Not available", "not the complete source file"] { XCTAssertTrue(all.contains(value), value) }
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Export.json(report)) as? [String: Any])
        XCTAssertEqual((json["findings"] as? [[String: Any]])?.count, 2)
    }
    func testEmptyCaseHasNoInventedPayloads() {
        let report = Report(caseID: "empty", indicators: .demo)
        XCTAssertTrue(Export.payloadsText(report).contains("No finding payloads recorded."))
    }
    func testCaseMetadataAndLegacyDecoding() throws {
        var report = Report(caseID: "metadata", indicators: .demo)
        report.sysdiagnoseFilename = "sysdiagnose_synthetic.tar.gz"
        report.analysisStartedAt = Date(timeIntervalSince1970: 100)
        report.analysisFinishedAt = Date(timeIntervalSince1970: 200)
        let data = try JSONEncoder().encode(report)
        let restored = try JSONDecoder().decode(Report.self, from: data)
        XCTAssertEqual(restored.sysdiagnoseFilename, report.sysdiagnoseFilename)
        XCTAssertEqual(restored.analysisFinishedAt, report.analysisFinishedAt)
        var legacy = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        for key in ["sysdiagnoseFilename", "analysisStartedAt", "analysisFinishedAt"] { legacy.removeValue(forKey: key) }
        let old = try JSONDecoder().decode(Report.self, from: JSONSerialization.data(withJSONObject: legacy))
        XCTAssertNil(old.sysdiagnoseFilename); XCTAssertNil(old.analysisStartedAt); XCTAssertNil(old.analysisFinishedAt)
    }
}
