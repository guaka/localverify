import Foundation

public struct Indicator: Codable, Equatable {
    public var id: String
    public var kind: String
    public var value: String
    public init(id: String, kind: String, value: String) { self.id = id; self.kind = kind; self.value = value }
}
public struct IndicatorSet: Codable {
    public var version: String
    public var indicators: [Indicator]
    public var unsupported: [String]
    public var sources: [String]? = nil
    public var checkedAt: Date? = nil
    public var latestIndicatorDate: Date? = nil
    public var byteCount: Int? = nil
    public static let demo = IndicatorSet(version: "demo-1 — NOT threat intelligence", indicators: [.init(id: "demo-domain", kind: "domain-name:value", value: "triage-test.invalid")], unsupported: [])
    public static func parse(_ data: Data) throws -> IndicatorSet {
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let objects = root?["objects"] as? [[String: Any]], root?["type"] as? String == "bundle" else { throw TriageError.invalid("Expected a STIX2 bundle") }
        let regex = try NSRegularExpression(pattern: "^\\s*\\[\\s*([a-z-]+:[a-z]+)\\s*=\\s*'([^'\\\\]+)'\\s*\\]\\s*$")
        let supported = ["domain-name:value", "url:value", "process:name", "file:path", "file:name"]
        var found: [Indicator] = []; var skipped: [String] = []
        for item in objects where item["type"] as? String == "indicator" {
            let id = item["id"] as? String ?? "unnamed"
            let pattern = item["pattern"] as? String ?? ""
            guard item["revoked"] as? Bool != true else { skipped.append("\(id): revoked"); continue }
            guard item["pattern_type"] == nil || item["pattern_type"] as? String == "stix",
                  let match = regex.firstMatch(in: pattern, range: NSRange(pattern.startIndex..., in: pattern)),
                  let kr = Range(match.range(at: 1), in: pattern), let vr = Range(match.range(at: 2), in: pattern), supported.contains(String(pattern[kr])) else { skipped.append("\(id): unsupported pattern \(pattern)"); continue }
            // Time-qualified indicators need validity evaluation, not silent broadening.
            if item["valid_until"] != nil { skipped.append("\(id): valid_until is not supported"); continue }
            guard !pattern[vr].isEmpty, pattern[vr].count <= 2048, found.count < 2000 else { skipped.append("\(id): indicator size/count limit"); continue }
            found.append(.init(id: id, kind: String(pattern[kr]), value: String(pattern[vr])))
        }
        var result = IndicatorSet(version: root?["id"] as? String ?? "imported", indicators: found, unsupported: skipped)
        let fractional = ISO8601DateFormatter(); fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let plain = ISO8601DateFormatter()
        result.latestIndicatorDate = objects.filter { $0["type"] as? String == "indicator" }.compactMap { object -> Date? in
            guard let text = object["modified"] as? String ?? object["created"] as? String else { return nil }
            return fractional.date(from: text) ?? plain.date(from: text)
        }.max()
        result.byteCount = data.count
        return result
    }
}
public struct Finding: Codable, Identifiable {
    public var id: String
    public var rule: String
    public var value: String
    public var source: String
    public var record: String
    public var timestamp: String?
    public var matchType: String
    public var explanation: String
    public var excerpt: String
}
public struct Report: Codable {
    public var schemaVersion = 1
    public var engineVersion = "0.1.0-experimental"
    public var platform = "ios"
    public var caseID: String
    public var createdAt = Date()
    public var archiveSHA256 = ""
    public var indicatorVersion: String
    public var indicatorSHA256 = ""
    public var indicatorSources: [String]? = nil
    public var indicatorsCheckedAt: Date? = nil
    // Retained as optional legacy metadata so existing saved cases can still be opened.
    public var consentConfirmedAt: Date? = nil
    public var completed = false
    public var findings: [Finding] = []
    public var analyzed: [String] = []
    public var skipped: [String] = []
    public var errors: [String] = []
    public var status: String {
        if !completed || !errors.isEmpty { return "Analysis incomplete" }
        return findings.isEmpty ? "No matches in analyzed evidence" : "Leads requiring review"
    }
    public init(caseID: String, indicators: IndicatorSet) {
        self.caseID = caseID; indicatorVersion = indicators.version
        skipped = indicators.unsupported
        indicatorSources = indicators.sources
        indicatorsCheckedAt = indicators.checkedAt
    }
}
public enum TriageError: LocalizedError {
    case invalid(String)
    public var errorDescription: String? { if case .invalid(let text) = self { return text }; return nil }
}
