import XCTest
@testable import TriageCore

final class ContractTests: XCTestCase {
    private func rows(_ name: String) throws -> [[String: Any]] {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("Fixtures/\(name).json"))) as? [[String: Any]])
    }
    func testCanonicalSTIX() throws {
        for row in try rows("stix") {
            let data = try (row["raw"] as? String).map { Data($0.utf8) } ?? JSONSerialization.data(withJSONObject: row["bundle"]!)
            let id = row["id"] as! String
            if row["error"] as! Bool { XCTAssertThrowsError(try IndicatorSet.parse(data), id) }
            else {
                let result = try IndicatorSet.parse(data)
                XCTAssertEqual(result.indicators.count, row["supported"] as? Int, id)
                XCTAssertEqual(result.unsupported.count, row["unsupported"] as? Int, id)
            }
        }
    }
    func testCanonicalBudgets() throws {
        for row in try rows("budgets") {
            let text = (row["prefix"] as? String ?? "") + String(repeating: row["unit"] as! String, count: row["repeat"] as! Int) + (row["suffix"] as? String ?? "")
            if row["error"] as! Bool { XCTAssertThrowsError(try Analyzer.scan(text, source: "fixture.txt", indicators: IndicatorSet.demo.indicators), row["id"] as! String) }
            else { XCTAssertNoThrow(try Analyzer.scan(text, source: "fixture.txt", indicators: IndicatorSet.demo.indicators), row["id"] as! String) }
        }
    }
}
