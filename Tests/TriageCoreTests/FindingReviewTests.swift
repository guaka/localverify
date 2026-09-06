import XCTest
@testable import TriageCore

final class FindingReviewTests: XCTestCase {
    private func finding(_ type: String = "raw-text") -> Finding {
        Finding(id: "synthetic", rule: "indicator--984deb09-2850-48fb-8df3-e7b72fe96ed3", value: "bh", source: "synthetic.log", record: "line 1", matchType: type, explanation: "Synthetic fixture", excerpt: "abbreviation bh")
    }

    func testShortTextIsUnverifiedNotConfirmedExecution() {
        let result = finding()
        XCTAssertEqual(result.reviewTitle, "Unverified text match")
        XCTAssertTrue(result.isAmbiguousTextMatch)
        XCTAssertTrue(result.reviewGuidance.contains("does not establish that a process ran"))
        XCTAssertTrue(result.isPegasusBridgeheadReference)
        XCTAssertFalse(finding("structured").isAmbiguousTextMatch)
    }

    func testRepeatedTextDoesNotBecomeIndependentEvidence() {
        var report = Report(caseID: "synthetic-review", indicators: .demo)
        report.findings = [finding(), finding()]
        XCTAssertEqual(report.status, "Analysis incomplete")
        report.completed = true
        XCTAssertEqual(report.status, "Unverified text matches")
        XCTAssertTrue(report.matchReviewSummary.contains("1 distinct indicators · 2 text occurrences"))
        report.findings.append(finding("structured"))
        XCTAssertEqual(report.status, "Leads requiring review")
        report.errors = ["Synthetic interruption"]
        XCTAssertEqual(report.status, "Analysis incomplete")
    }

    func testStructuredGuidanceMentionsRecognizedFieldSemantics() {
        XCTAssertTrue(finding("structured").reviewGuidance.hasPrefix("A recognized field matched"))
    }
}
