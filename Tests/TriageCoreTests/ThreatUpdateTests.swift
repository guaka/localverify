import XCTest
@testable import TriageCore

final class ThreatUpdateTests: XCTestCase {
    private var payloads: [Data] {
        get throws {
            let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            return try ThreatUpdates.feeds.map { try Data(contentsOf: root.appendingPathComponent("iOS/App/ThreatData/\($0.resource).stix2")) }
        }
    }
    private func temporaryThreatBundle() throws -> URL {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let source = root.appendingPathComponent("iOS/App/ThreatData")
        let bundleURL = FileManager.default.temporaryDirectory.appendingPathComponent("ThreatUpdatesBundle-\(UUID().uuidString).bundle")
        try FileManager.default.createDirectory(at: bundleURL, withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: source.appendingPathComponent("threat-manifest.json"), to: bundleURL.appendingPathComponent("threat-manifest.json"))
        for feed in ThreatUpdates.feeds {
            try FileManager.default.copyItem(at: source.appendingPathComponent("\(feed.resource).stix2"), to: bundleURL.appendingPathComponent("\(feed.resource).stix2"))
        }
        let info = [
            "CFBundleIdentifier": "org.mobiletriage.localverify.tests.threatdata",
            "CFBundleName": "ThreatUpdatesTestBundle",
            "CFBundleVersion": "1",
            "CFBundleShortVersionString": "1.0"
        ]
        let infoData = try PropertyListSerialization.data(fromPropertyList: info, format: .xml, options: 0)
        try infoData.write(to: bundleURL.appendingPathComponent("Info.plist"))
        return bundleURL
    }
    func testBundledFeedsHaveSupportedIndicatorsAndProvenance() throws {
        let set = try ThreatUpdates.combine(payloads, checkedAt: nil)
        XCTAssertGreaterThan(set.indicators.count, 100)
        XCTAssertEqual(set.sources?.count, 5)
        XCTAssertNil(set.checkedAt)
        XCTAssertNotNil(set.latestIndicatorDate)
        XCTAssertEqual(set.byteCount, 2_331_191)
        XCTAssertGreaterThan(set.latestIndicatorDate ?? .distantPast, ISO8601DateFormatter().date(from: "2026-03-01T00:00:00Z")!)
        XCTAssertEqual(Set(set.indicators.map { $0.kind + ":" + $0.value }).count, set.indicators.count)
        XCTAssertEqual(Set(set.indicators.flatMap { $0.campaigns ?? [] }), Set(["Pegasus", "Predator", "Coruna", "DarkSword"]))
        for data in try payloads {
            XCTAssertFalse(try IndicatorSet.parse(data).indicators.isEmpty)
        }
        XCTAssertFalse(set.indicators.contains { $0.value == "triage-test.invalid" })
        print("Bundled indicators: \(set.indicators.count) supported, \(set.unsupported.count) skipped")
        let report = Report(caseID: "snapshot", indicators: set)
        XCTAssertEqual(report.indicatorSources, set.sources)
    }
    func testBundledThreatDataLoadsFromBundlePath() throws {
        let bundleURL = try temporaryThreatBundle()
        defer { try? FileManager.default.removeItem(at: bundleURL) }
        let bundle = try XCTUnwrap(Bundle(path: bundleURL.path))
        let set = try ThreatUpdates.bundled(in: bundle)
        XCTAssertEqual(set.sources?.count, ThreatUpdates.feeds.count)
        XCTAssertGreaterThan(set.indicators.count, 0)
        XCTAssertNotNil(set.checkedAt)
        XCTAssertNotNil(set.byteCount)
    }
    func testPreferredInstalledSetUsesBundledWhenCachedHasNoCampaignMetadata() throws {
        let bundled = try ThreatUpdates.combine(payloads, checkedAt: nil)
        var cached = IndicatorSet(version: bundled.version, indicators: [Indicator(id: "manual", kind: "process:name", value: "manual.invalid")], unsupported: ["demo"])
        cached.sources = bundled.sources
        let selected = ThreatUpdates.preferredInstalledSet(cached: cached, bundled: bundled)
        XCTAssertEqual(selected.version, bundled.version)
        XCTAssertEqual(selected.sources, bundled.sources)
        XCTAssertEqual(selected.indicators, bundled.indicators)
    }
    func test2026CollectionsProduceTraceableLeadsWithoutDomainSubstringMatches() throws {
        for data in try payloads.suffix(2) {
            let set = try IndicatorSet.parse(data)
            let indicator = try XCTUnwrap(set.indicators.first { $0.kind == "domain-name:value" })
            let findings = try Analyzer.scan("Connected to \(indicator.value)", source: "seeded-2026.log", indicators: set.indicators)
            XCTAssertTrue(findings.contains { $0.rule == indicator.id && $0.source == "seeded-2026.log" && $0.value == indicator.value })
            let benign = try Analyzer.scan("Connected to \(indicator.value).benign.invalid", source: "benign.log", indicators: [indicator])
            XCTAssertTrue(benign.isEmpty)
        }
    }
    func testCampaignMetadataFlowsIntoFindings() throws {
        let set = try ThreatUpdates.combine(payloads, checkedAt: nil)
        let indicator = try XCTUnwrap(set.indicators.first { $0.kind == "domain-name:value" && $0.campaigns == ["DarkSword"] })
        let findings = try Analyzer.scan("host \(indicator.value)", source: "synthetic.log", indicators: [indicator])
        XCTAssertEqual(findings.first?.campaigns, ["DarkSword"])
    }
    func testInvalidOrIncompleteUpdatesRejected() throws {
        let valid = try payloads
        XCTAssertThrowsError(try ThreatUpdates.combine([valid[0]], checkedAt: Date()))
        var malformed = valid; malformed[1] = Data("bad".utf8)
        XCTAssertThrowsError(try ThreatUpdates.combine(malformed, checkedAt: Date()))
        let empty = Data(#"{"type":"bundle","objects":[]}"#.utf8)
        malformed[1] = empty
        XCTAssertThrowsError(try ThreatUpdates.combine(malformed, checkedAt: Date()))
        malformed = valid; malformed[0] = Data(repeating: 0, count: ThreatUpdates.maximumBytes + 1)
        XCTAssertThrowsError(try ThreatUpdates.combine(malformed, checkedAt: nil))
    }
    func testNewerBundleUpgradesPublisherCacheButPreservesManualImports() throws {
        let current = try ThreatUpdates.combine(payloads, checkedAt: Date())
        var old = current
        old.version = "old"
        old.sources = Array(current.sources!.prefix(2))
        old.latestIndicatorDate = Date(timeIntervalSince1970: 0)
        XCTAssertEqual(ThreatUpdates.preferredInstalledSet(cached: old, bundled: current).version, current.version)
        old.sources = nil
        XCTAssertEqual(ThreatUpdates.preferredInstalledSet(cached: old, bundled: current).version, "old")
        XCTAssertEqual(ThreatUpdates.preferredInstalledSet(cached: current, bundled: old).version, current.version)
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
