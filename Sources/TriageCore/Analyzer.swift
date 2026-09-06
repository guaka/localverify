import Foundation

public enum Analyzer {
    public static func analyze(archive: URL, indicators: IndicatorSet, previous: Report, progress: ((String) -> Void)? = nil, checkpoint: (Report) throws -> Void) throws -> Report {
        guard previous.schemaVersion == 1, previous.findings.count <= 10000, previous.analyzed.count <= 100000 else { throw TriageError.invalid("Invalid checkpoint") }
        try InputLimits.indicators(indicators.indicators)
        var report = previous
        if report.engineVersion != "0.2.0-hardened" {
            report.engineVersion = "0.2.0-hardened"
            report.analyzed = []; report.findings = []; report.skipped = indicators.unsupported
        }
        report.analysisStartedAt = Date()
        report.analysisFinishedAt = nil
        report.completed = false; report.errors = []
        var lastUpdate = Date.distantPast
        func update(_ detail: String, force: Bool = false) {
            let now = Date()
            guard force || now.timeIntervalSince(lastUpdate) >= 0.25 else { return }
            lastUpdate = now
            let count = report.analyzed.count
            progress?("\(detail) · \(count) \(count == 1 ? "file" : "files") checked")
        }
        update("Verifying original archive", force: true)
        let digest = try Archive.hash(archive) { bytes in update("Verifying original · \(bytes / 1_000_000) MB") }
        guard report.archiveSHA256.isEmpty || report.archiveSHA256 == digest else { throw TriageError.invalid("Evidence changed since previous run") }
        report.archiveSHA256 = digest
        let done = Set(report.analyzed)
        try checkpoint(report)
        do {
            update("Reading archive", force: true)
            try Archive.walk(archive, progress: { bytes, _ in
                update("Reading archive · \(bytes / 1_000_000) MB unpacked")
            }) { path, data, reason in
                if done.contains(path) { return }
                guard let data else {
                    let note = "\(path): \(reason ?? "unavailable")"
                    if !report.skipped.contains(note) { report.skipped.append(note) }
                    return
                }
                guard let text = String(data: data, encoding: .utf8) else {
                    report.skipped.append("\(path): non-UTF8 data"); return
                }
                update("Checking file")
                // Replace partial findings for this uncompleted file when resuming.
                report.findings.removeAll { $0.source == path }
                _ = try scan(text, source: path, indicators: indicators.indicators, progress: { stage in
                    update(stage)
                }, findingLimit: 10000 - report.findings.count, onFinding: { report.findings.append($0) })
                report.analyzed.append(path)
                try checkpoint(report)
            }
            if report.analyzed.isEmpty { report.errors.append("No supported text or structured records were analyzed") }
            if indicators.indicators.isEmpty { report.errors.append("No supported indicators available") }
            guard try Archive.hash(archive) == digest else { throw TriageError.invalid("Evidence changed during analysis") }
            report.completed = true
            report.analysisFinishedAt = Date()
        } catch {
            report.errors.append(error is CancellationError ? "Analysis interrupted; resume to continue" : error.localizedDescription)
            try checkpoint(report)
            return report
        }
        try checkpoint(report)
        return report
    }
    public static func scan(_ text: String, source: String, indicators: [Indicator], progress: ((String) -> Void)? = nil, findingLimit: Int = 10000, onFinding: ((Finding) -> Void)? = nil) throws -> [Finding] {
        try InputLimits.text(text)
        try InputLimits.indicators(indicators)
        var findings: [Finding] = []
        func append(_ finding: Finding) throws {
            guard findings.count < findingLimit else { throw TriageError.invalid("Finding limit reached") }
            findings.append(finding); onFinding?(finding)
        }
        var workBytes = 0

        try Task.checkCancellation()
        progress?("Indexing text")
        let lines = text.components(separatedBy: .newlines)
        // Most indicators are whole ASCII tokens. Index only sought tokens in two
        // passes, instead of running thousands of regexes against every log line.
        // Non-token literals retain the original regex path and its semantics.
        let boundaries = ["A-Za-z0-9_.-", "A-Za-z0-9_./:%?=&-"]
        let alphabets = [CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-"), CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_./:%?=&-")]
        var sought = [Set<String>(), Set<String>()]
        var tokenKeys: [String?] = []
        for indicator in indicators {
            let group = indicator.kind == "domain-name:value" ? 0 : 1
            let token = !indicator.value.isEmpty && indicator.value.unicodeScalars.allSatisfy { alphabets[group].contains($0) }
            let key = token ? (group == 0 ? indicator.value.lowercased() : indicator.value) : nil
            tokenKeys.append(key)
            if let key { sought[group].insert(key) }
        }
        var present = [Set<String>(), Set<String>()]
        let nsText = text as NSString
        for group in 0..<2 where !sought[group].isEmpty {
            let tokenizer = try NSRegularExpression(pattern: "[\(boundaries[group])]+", options: group == 0 ? [.caseInsensitive] : [])
            var cancelled = false
            var tokens = 0
            tokenizer.enumerateMatches(in: text, range: NSRange(location: 0, length: nsText.length)) { match, _, stop in
                if Task.isCancelled { cancelled = true; stop.pointee = true; return }
                guard let match, match.range.length <= 2048 else { return }
                let value = nsText.substring(with: match.range)
                let key = group == 0 ? value.folding(options: .caseInsensitive, locale: Locale(identifier: "en_US_POSIX")) : value
                if sought[group].contains(key) { present[group].insert(key) }
                tokens += 1
                if tokens % 1024 == 0 { progress?("Indexing text") }
            }
            if cancelled { throw CancellationError() }
        }
        // Apple .ips commonly consists of a metadata JSON line followed by a JSON body.
        var structured: [(String, String, String?)] = []
        var nodes = 0
        func collect(_ value: Any, path: String, timestamp: String?) throws {
            nodes += 1
            guard nodes <= 100000, path.utf8.count <= 4096 else { throw TriageError.invalid("Structured record limit reached") }
            try Task.checkCancellation()
            if let object = value as? [String: Any] {
                let time = (object["timestamp"] as? String) ?? (object["captureTime"] as? String) ?? timestamp
                for key in object.keys.sorted() {
                    let item = object[key]!
                    if let string = item as? String {
                        guard (path + "." + key).utf8.count <= 4096 else { throw TriageError.invalid("Structured path limit reached") }
                        if string.utf8.count <= 8192 { structured.append((path + "." + key, string, time.map { String($0.prefix(256)) })) }
                    }
                    else { try collect(item, path: path + "." + key, timestamp: time) }
                }
            } else if let array = value as? [Any] {
                for (i, item) in array.enumerated() { try collect(item, path: path + "[\(i)]", timestamp: timestamp) }
            }
        }
        // Budget JSON-looking input before entering the platform parser.
        func json(_ candidate: String) throws -> Any? {
            guard let first = candidate.first(where: { !$0.isWhitespace }), first == "{" || first == "[" else { return nil }
            let data = Data(candidate.utf8)
            try InputLimits.json(data)
            return try? JSONSerialization.jsonObject(with: data)
        }
        if let json = try json(text) { try collect(json, path: "$", timestamp: nil) }
        else {
            if let first = lines.first, let json = try json(first) { try collect(json, path: "$header", timestamp: nil) }
            let remainder = lines.dropFirst().joined(separator: "\n")
            if let json = try json(remainder) { try collect(json, path: "$body", timestamp: nil) }
        }
        // Index recognized structured fields once; unrelated JSON strings never
        // need to be compared against every indicator.
        let fields = ["procname": "process:name", "process": "process:name", "processname": "process:name", "app_name": "process:name", "procpath": "file:path", "path": "file:path", "executablepath": "file:path", "filename": "file:name", "name": "file:name", "domain": "domain-name:value", "hostname": "domain-name:value", "host": "domain-name:value", "url": "url:value", "uri": "url:value"]
        func normalized(_ value: String, kind: String) -> String {
            kind == "domain-name:value" ? value.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ".")) : value
        }
        var indexed: [String: [(String, String, String?)]] = [:]
        for record in structured {
            try Task.checkCancellation()
            let field = record.0.components(separatedBy: ".").last?.lowercased() ?? ""
            if let kind = fields[field] { indexed[kind + "\u{0}" + normalized(record.1, kind: kind), default: []].append(record) }
        }
        for (indicatorIndex, indicator) in indicators.enumerated() {
            try Task.checkCancellation()
            progress?("Checking definitions \(indicatorIndex + 1)/\(indicators.count)")
            var structuredMatch = false
            for (path, value, timestamp) in indexed[indicator.kind + "\u{0}" + normalized(indicator.value, kind: indicator.kind)] ?? [] {
                    try append(make(indicator, source, path, value, "structured", timestamp)); structuredMatch = true

            }
            if structuredMatch { continue }
            let group = indicator.kind == "domain-name:value" ? 0 : 1
            if let key = tokenKeys[indicatorIndex], !present[group].contains(key) { continue }
            let escaped = NSRegularExpression.escapedPattern(for: indicator.value)
            let boundary = indicator.kind == "domain-name:value" ? "A-Za-z0-9_.-" : "A-Za-z0-9_./:%?=&-"
            let regex = try NSRegularExpression(pattern: "(?<![\(boundary)])\(escaped)(?![\(boundary)])", options: indicator.kind == "domain-name:value" ? [.caseInsensitive] : [])
            for (index, line) in lines.enumerated() {
                if index % 256 == 0 { try Task.checkCancellation(); progress?("Checking text line \(index + 1)/\(lines.count)") }
                workBytes += line.utf8.count + 1
                guard workBytes <= 128 * 1024 * 1024 else { throw TriageError.invalid("Text matching work limit reached") }
                if regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) != nil {
                    try append(make(indicator, source, "line \(index + 1)", String(line.prefix(600)), "raw-text", nil))

                }
            }
        }
        return findings
    }
    private static func make(_ i: Indicator, _ source: String, _ record: String, _ excerpt: String, _ type: String, _ timestamp: String?) -> Finding {
        Finding(id: UUID().uuidString, rule: i.id, value: i.value, source: source, record: record, timestamp: timestamp, matchType: type, explanation: type == "structured" ? "Exact indicator match in a recognized field; review context before escalation." : "Indicator appears in text; this may be incidental and requires contextual review.", excerpt: String(excerpt.prefix(600)), campaigns: i.campaigns)
    }
}
