import Foundation

public extension Finding {
    var isAmbiguousTextMatch: Bool { matchType == "raw-text" && value.count <= 3 }

    var reviewTitle: String {
        matchType == "raw-text" ? "Unverified text match" : "Structured indicator match"
    }

    var reviewGuidance: String {
        if matchType == "raw-text" {
            let caution = isAmbiguousTextMatch ? "This short value is especially ambiguous and may be an unrelated abbreviation. " : ""
            return caution + "The indicator appeared in text. This does not establish that a process ran, a file existed, or a network connection occurred. Check what the source record actually describes, its timestamp and any executable path. Look for separate corroborating evidence before escalation; repeated text mentions are not independent confirmation."
        }
        return "A recognized field matched an indicator. Check the field's meaning, executable path or network context, timestamp, and independent corroborating records. A structured match alone is not proof of compromise."
    }

    var isPegasusBridgeheadReference: Bool {
        value == "bh" && rule == "indicator--984deb09-2850-48fb-8df3-e7b72fe96ed3"
    }
}

public extension Report {
    var matchReviewSummary: String {
        let raw = findings.filter { $0.matchType == "raw-text" }.count
        let structured = findings.filter { $0.matchType == "structured" }.count
        let distinct = Set(findings.map { $0.rule }).count
        return "\(distinct) distinct indicators · \(raw) text occurrences · \(structured) structured occurrences. Repeated occurrences are not independent evidence of compromise."
    }
}
