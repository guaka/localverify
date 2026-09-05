import XCTest
@testable import TriageCore

final class ThreatUpdateTests: XCTestCase {
    private var payloads: [Data] {
        get throws {
            let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            return try ThreatUpdates.feeds.map { try Data(contentsOf: root.appendingPathComponent("iOS/App/ThreatData/\($0.resource).stix2")) }
        }
    }
    func testBundledFeedsHaveSupportedIndicatorsAndProvenance() throws {
        let set = try ThreatUpdates.combine(payloads, checkedAt: nil)
        XCTAssertGreaterThan(set.indicators.count, 100)
        XCTAssertEqual(set.sources?.count, 2)
        XCTAssertNil(set.checkedAt)
        XCTAssertNotNil(set.latestIndicatorDate)
        XCTAssertEqual(set.byteCount, 1_486_428)
        XCTAssertFalse(set.indicators.contains { $0.value == "triage-test.invalid" })
        print("Bundled indicators: \(set.indicators.count) supported, \(set.unsupported.count) skipped")
        let report = Report(caseID: "snapshot", indicators: set, consent: Date())
        XCTAssertEqual(report.indicatorSources, set.sources)
    }
    func testLiveDownloadWhenRequested() async throws {
        guard ProcessInfo.processInfo.environment["TRIAGE_TEST_LIVE_UPDATES"] == "1" else { throw XCTSkip("Set TRIAGE_TEST_LIVE_UPDATES=1 for the public-feed integration check") }
        let set = try await ThreatUpdates.download()
        XCTAssertGreaterThan(set.indicators.count, 100)
        XCTAssertNotNil(set.checkedAt)
        XCTAssertNotNil(set.latestIndicatorDate)
        XCTAssertGreaterThan(set.byteCount ?? 0, 0)
    }
    func testInvalidOrIncompleteUpdatesRejected() throws {
        let valid = try payloads
        XCTAssertThrowsError(try ThreatUpdates.combine([valid[0]], checkedAt: Date()))
        XCTAssertThrowsError(try ThreatUpdates.combine([valid[0], Data("bad".utf8)], checkedAt: Date()))
        let empty = Data(#"{"type":"bundle","objects":[]}"#.utf8)
        XCTAssertThrowsError(try ThreatUpdates.combine([valid[0], empty], checkedAt: Date()))
        XCTAssertThrowsError(try ThreatUpdates.combine([Data(repeating: 0, count: ThreatUpdates.maximumBytes + 1), valid[1]], checkedAt: nil))
    }
    func testUpdateTimestampAndStableFingerprint() throws {
        let data = try payloads
        let before = try ThreatUpdates.combine(data, checkedAt: nil)
        let now = Date()
        let after = try ThreatUpdates.combine(data, checkedAt: now)
        XCTAssertEqual(before.version, after.version)
        XCTAssertEqual(after.checkedAt, now)
        for feed in ThreatUpdates.feeds {
            XCTAssertEqual(feed.url.scheme, "https")
            XCTAssertEqual(feed.url.host, "raw.githubusercontent.com")
            XCTAssertNil(feed.url.query)
        }
    }
}
