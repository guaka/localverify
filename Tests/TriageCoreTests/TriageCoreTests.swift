import XCTest
import Foundation
@testable import TriageCore

final class TriageCoreTests: XCTestCase {
    func testLocalFilesExcludedFromBackup() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let report = dir.appendingPathComponent("report.json")
        try LocalStorage.write(Data("test".utf8), to: report)
        XCTAssertEqual(try report.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
        let archive = dir.appendingPathComponent("export.zip")
        try LocalStorage.createFile(archive)
        XCTAssertEqual(try archive.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    }
    func testSharedAndroidMatchingVectors() throws {
        struct Vector: Decodable { let text: String; let expected: Int; let matchType: String? }
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let vectors = try JSONDecoder().decode([Vector].self, from: Data(contentsOf: root.appendingPathComponent("Fixtures/matching.json")))
        for vector in vectors {
            let result = try Analyzer.scan(vector.text, source: "fixture.txt", indicators: IndicatorSet.demo.indicators)
            XCTAssertEqual(result.count, vector.expected)
            if let type = vector.matchType { XCTAssertEqual(result.first?.matchType, type) }
        }
    }
    func testSTIXRejectsCompoundPatterns() throws {
        let data = Data(#"{"type":"bundle","id":"test","objects":[{"type":"indicator","id":"a","pattern":"[domain-name:value = 'evil.example']"},{"type":"indicator","id":"b","pattern":"[process:name = 'x' OR process:name = 'y']"}]}"#.utf8)
        let set = try IndicatorSet.parse(data)
        XCTAssertEqual(set.indicators.count, 1); XCTAssertEqual(set.unsupported.count, 1)
    }
    func testStructuredAndTextBoundaries() throws {
        let indicators = [Indicator(id: "p", kind: "process:name", value: "badproc"), Indicator(id: "d", kind: "domain-name:value", value: "evil.example")]
        let found = try Analyzer.scan(#"{"procName":"badproc","timestamp":"2026-01-01"}"#, source: "sample.ips", indicators: indicators)
        XCTAssertEqual(found.count, 1); XCTAssertEqual(found[0].matchType, "structured"); XCTAssertEqual(found[0].timestamp, "2026-01-01")
        XCTAssertEqual(try Analyzer.scan("not-evil.example evil.example.safe badprocess", source: "x.log", indicators: indicators).count, 0)
        XCTAssertEqual(try Analyzer.scan("host evil.example connected", source: "x.log", indicators: indicators).count, 1)
    }
    func testUnsafePaths() {
        for path in ["../x", "/etc/a", "a/../../x", "a\\b"] { XCTAssertFalse(Archive.safePath(path)) }
        XCTAssertTrue(Archive.safePath("sysdiagnose/logs/a.ips"))
    }
    func testHTMLAndIncompleteStatus() {
        var report = Report(caseID: "<script>", indicators: .demo, consent: Date())
        XCTAssertEqual(report.status, "Analysis incomplete")
        XCTAssertTrue(Export.html(report).contains("&lt;script&gt;"))
        report.completed = true; report.errors = ["corrupt"]
        XCTAssertEqual(report.status, "Analysis incomplete")
    }
    func fixture(_ name: String, text: String) throws -> URL {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data(text.utf8).write(to: dir.appendingPathComponent(name))
        let task = Process(); task.executableURL = URL(fileURLWithPath: "/usr/bin/tar")
        task.arguments = ["-czf", dir.appendingPathComponent("fixture.tar.gz").path, "-C", dir.path, name]; try task.run(); task.waitUntilExit()
        XCTAssertEqual(task.terminationStatus, 0)
        return dir
    }
    func testArchiveAnalysisResumeAndExport() throws {
        let dir = try fixture("test.log", text: "connected triage-test.invalid\nbenign.example\n")
        defer { try? FileManager.default.removeItem(at: dir) }
        let archive = dir.appendingPathComponent("fixture.tar.gz")
        let digest = try Archive.hash(archive)
        let initial = Report(caseID: "fixture", indicators: .demo, consent: Date())
        let report = try Analyzer.analyze(archive: archive, indicators: .demo, previous: initial) { _ in }
        XCTAssertTrue(report.completed, report.errors.joined()); XCTAssertEqual(report.findings.count, 1)
        let resumed = try Analyzer.analyze(archive: archive, indicators: .demo, previous: report) { _ in }
        XCTAssertEqual(resumed.findings.count, 1); XCTAssertEqual(try Archive.hash(archive), digest)
        let zip = dir.appendingPathComponent("export.zip")
        try Export.zip(report: report, original: archive, destination: zip)
        let check = Process(); check.executableURL = URL(fileURLWithPath: "/usr/bin/unzip"); check.arguments = ["-t", zip.path]; try check.run(); check.waitUntilExit()
        XCTAssertEqual(check.terminationStatus, 0)
    }
    func testCorruptArchiveNeverCompletes() throws {
        let dir = try fixture("test.log", text: "benign")
        defer { try? FileManager.default.removeItem(at: dir) }
        let archive = dir.appendingPathComponent("fixture.tar.gz")
        var bytes = try Data(contentsOf: archive); bytes.removeLast(12); try bytes.write(to: archive)
        let report = try Analyzer.analyze(archive: archive, indicators: .demo, previous: Report(caseID: "bad", indicators: .demo, consent: Date())) { _ in }
        XCTAssertFalse(report.completed); XCTAssertFalse(report.errors.isEmpty)
    }
    func testCancelledAnalysisDoesNotComplete() async throws {
        let dir = try fixture("test.log", text: "triage-test.invalid")
        defer { try? FileManager.default.removeItem(at: dir) }
        let task = Task {
            while !Task.isCancelled { await Task.yield() }
            return try Analyzer.analyze(archive: dir.appendingPathComponent("fixture.tar.gz"), indicators: .demo, previous: Report(caseID: "cancel", indicators: .demo, consent: Date())) { _ in }
        }
        task.cancel()
        do { let report = try await task.value; XCTAssertFalse(report.completed) }
        catch { XCTAssertTrue(error is CancellationError) }
    }
    func testChangedEvidenceRejectedOnResume() throws {
        let dir = try fixture("test.log", text: "benign")
        defer { try? FileManager.default.removeItem(at: dir) }
        var previous = Report(caseID: "changed", indicators: .demo, consent: Date()); previous.archiveSHA256 = "wrong"
        XCTAssertThrowsError(try Analyzer.analyze(archive: dir.appendingPathComponent("fixture.tar.gz"), indicators: .demo, previous: previous) { _ in })
    }
}
