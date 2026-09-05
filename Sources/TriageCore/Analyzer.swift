import Foundation

public enum Analyzer {
    public static func analyze(archive: URL, indicators: IndicatorSet, previous: Report, checkpoint: (Report) throws -> Void) throws -> Report {
        var report = previous
        report.completed = false; report.errors = []
        let digest = try Archive.hash(archive)
        guard report.archiveSHA256.isEmpty || report.archiveSHA256 == digest else { throw TriageError.invalid("Evidence changed since previous run") }
        report.archiveSHA256 = digest
        let done = Set(report.analyzed)
        try checkpoint(report)
        do {
            try Archive.walk(archive) { path, data, reason in
                if done.contains(path) { return }
                guard let data else {
                    let note = "\(path): \(reason ?? "unavailable")"
                    if !report.skipped.contains(note) { report.skipped.append(note) }
                    return
                }
                guard let text = String(data: data, encoding: .utf8) else {
                    report.skipped.append("\(path): non-UTF8 data"); return
                }
                report.findings += try scan(text, source: path, indicators: indicators.indicators)
                report.analyzed.append(path)
                guard report.findings.count <= 10000 else { throw TriageError.invalid("Finding limit reached; narrow the indicator set") }
                try checkpoint(report)
            }
            if report.analyzed.isEmpty { report.errors.append("No supported text or structured records were analyzed") }
            if indicators.indicators.isEmpty { report.errors.append("No supported indicators available") }
            report.completed = true
        } catch {
            report.errors.append(error is CancellationError ? "Analysis interrupted; resume to continue" : error.localizedDescription)
            try checkpoint(report)
            return report
        }
        try checkpoint(report)
        return report
    }
    public static func scan(_ text: String, source: String, indicators: [Indicator]) throws -> [Finding] {
        var findings: [Finding] = []
        let lines = text.components(separatedBy: .newlines)
        // Apple .ips commonly consists of a metadata JSON line followed by a JSON body.
        var structured: [(String, String, String?)] = []
        func collect(_ value: Any, path: String, timestamp: String?) {
            if let object = value as? [String: Any] {
                let time = (object["timestamp"] as? String) ?? (object["captureTime"] as? String) ?? timestamp
                for key in object.keys.sorted() {
                    let item = object[key]!
                    if let string = item as? String { structured.append((path + "." + key, string, time)) }
                    else { collect(item, path: path + "." + key, timestamp: time) }
                }
            } else if let array = value as? [Any] {
                for (i, item) in array.enumerated() { collect(item, path: path + "[\(i)]", timestamp: timestamp) }
            }
        }
        if let json = try? JSONSerialization.jsonObject(with: Data(text.utf8)) { collect(json, path: "$", timestamp: nil) }
        else {
            if let first = lines.first, let json = try? JSONSerialization.jsonObject(with: Data(first.utf8)) { collect(json, path: "$header", timestamp: nil) }
            let remainder = lines.dropFirst().joined(separator: "\n")
            if let json = try? JSONSerialization.jsonObject(with: Data(remainder.utf8)) { collect(json, path: "$body", timestamp: nil) }
        }
        for indicator in indicators {
            try Task.checkCancellation()
            var structuredMatch = false
            for (path, value, timestamp) in structured {
                let field = path.components(separatedBy: ".").last?.lowercased() ?? ""
                let compatible: Bool
                switch indicator.kind {
                case "process:name": compatible = ["procname", "process", "processname", "app_name"].contains(field)
                case "file:path": compatible = ["procpath", "path", "executablepath"].contains(field)
                case "file:name": compatible = ["filename", "name"].contains(field)
                case "domain-name:value": compatible = ["domain", "hostname", "host"].contains(field)
                case "url:value": compatible = ["url", "uri"].contains(field)
                default: compatible = false
                }
                let equal = indicator.kind == "domain-name:value" ? value.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ".")) == indicator.value.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ".")) : value == indicator.value
                if compatible && equal {
                    findings.append(make(indicator, source, path, value, "structured", timestamp)); structuredMatch = true
                }
            }
            if structuredMatch { continue }
            let escaped = NSRegularExpression.escapedPattern(for: indicator.value)
            let boundary = indicator.kind == "domain-name:value" ? "A-Za-z0-9_.-" : "A-Za-z0-9_./:%?=&-"
            let regex = try NSRegularExpression(pattern: "(?<![\(boundary)])\(escaped)(?![\(boundary)])", options: indicator.kind == "domain-name:value" ? [.caseInsensitive] : [])
            for (index, line) in lines.enumerated() {
                if regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) != nil {
                    findings.append(make(indicator, source, "line \(index + 1)", String(line.prefix(600)), "raw-text", nil))
                    if findings.count >= 10000 { throw TriageError.invalid("Finding limit reached") }
                }
            }
        }
        return findings
    }
    private static func make(_ i: Indicator, _ source: String, _ record: String, _ excerpt: String, _ type: String, _ timestamp: String?) -> Finding {
        Finding(id: UUID().uuidString, rule: i.id, value: i.value, source: source, record: record, timestamp: timestamp, matchType: type, explanation: type == "structured" ? "Exact indicator match in a recognized field; review context before escalation." : "Indicator appears in text; this may be incidental and requires contextual review.", excerpt: excerpt)
    }
}
