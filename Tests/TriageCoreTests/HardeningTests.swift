import XCTest
@testable import TriageCore

final class HardeningTests: XCTestCase {
    func testJSONBudgetRejectsDeepInputIncludingIPSBody() throws {
        let deep = String(repeating: "[", count: 65) + "0" + String(repeating: "]", count: 65)
        XCTAssertThrowsError(try Analyzer.scan(deep, source: "deep.json", indicators: IndicatorSet.demo.indicators))
        XCTAssertThrowsError(try Analyzer.scan("not json\n" + deep, source: "deep.ips", indicators: IndicatorSet.demo.indicators))
        XCTAssertNoThrow(try InputLimits.json(Data("{\"text\":\"[[[[\\\"[[\"}".utf8)))
    }
    func testSizeAndLineBudgets() throws {
        XCTAssertThrowsError(try IndicatorSet.parse(Data(repeating: 32, count: InputLimits.indicatorBytes + 1)))
        XCTAssertThrowsError(try Analyzer.scan(String(repeating: "x", count: 1024 * 1024 + 1), source: "long.log", indicators: IndicatorSet.demo.indicators))
        XCTAssertThrowsError(try Analyzer.scan(String(repeating: "\n", count: 500_001), source: "lines.log", indicators: IndicatorSet.demo.indicators))
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: file) }
        try Data(repeating: 0, count: 5).write(to: file)
        XCTAssertEqual(try InputLimits.read(file, maximum: 5).count, 5)
        XCTAssertThrowsError(try InputLimits.read(file, maximum: 4))
    }
    func testDenseMatchesPreservePartialLeadsAndResumeWithoutDuplicates() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        try String(repeating: "triage-test.invalid\n", count: 10001).write(to: folder.appendingPathComponent("dense.log"), atomically: true, encoding: .utf8)
        let archive = folder.appendingPathComponent("synthetic.tar.gz")
        let tar = Process(); tar.executableURL = URL(fileURLWithPath: "/usr/bin/tar")
        tar.arguments = ["-czf", archive.path, "-C", folder.path, "dense.log"]
        try tar.run(); tar.waitUntilExit(); XCTAssertEqual(tar.terminationStatus, 0)
        var events: [String] = []
        let report = try Analyzer.analyze(archive: archive, indicators: .demo, previous: Report(caseID: "synthetic", indicators: .demo), progress: { events.append($0) }) { _ in }
        XCTAssertEqual(report.findings.count, 10000)
        XCTAssertFalse(report.completed)
        XCTAssertTrue(report.analyzed.isEmpty)
        XCTAssertTrue(report.errors.contains { $0.contains("Finding limit") })
        XCTAssertFalse(events.contains { $0.contains("matches") || $0.contains("dense.log") || $0.contains("triage-test.invalid") })
        let resumed = try Analyzer.analyze(archive: archive, indicators: .demo, previous: report) { _ in }
        XCTAssertEqual(resumed.findings.count, 10000)
        XCTAssertEqual(Set(resumed.findings.map(\.record)).count, 10000)
        XCTAssertEqual(resumed.status, "Analysis incomplete")
    }
    func testFinalCheckpointFailureDoesNotReturnSuccess() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let archive = root.appendingPathComponent("Fixtures/synthetic-sysdiagnose.tar.gz")
        XCTAssertTrue(FileManager.default.fileExists(atPath: archive.path))
        XCTAssertThrowsError(try Analyzer.analyze(archive: archive, indicators: .demo, previous: Report(caseID: "synthetic", indicators: .demo)) { report in
            if report.completed { throw TriageError.invalid("Synthetic storage failure") }
        }) { error in XCTAssertEqual(error.localizedDescription, "Synthetic storage failure") }
    }
}
