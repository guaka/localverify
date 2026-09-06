import Foundation

/// Budgets are checked before Foundation parsing or allocation-heavy indexing.
public enum InputLimits {
    public enum ResourceLimit: LocalizedError {
        case exceeded(String)
        public var errorDescription: String? {
            switch self { case .exceeded(let message): return message }
        }
    }
    public static let indicatorBytes = 5 * 1024 * 1024
    public static let textBytes = 16 * 1024 * 1024
    public static func read(_ url: URL, maximum: Int) throws -> Data {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        var data = Data()
        while let chunk = try file.read(upToCount: min(65536, maximum - data.count + 1)), !chunk.isEmpty {
            try Task.checkCancellation()
            guard chunk.count <= maximum - data.count else { throw TriageError.invalid("Input exceeds byte limit") }
            data.append(chunk)
        }
        return data
    }
    public static func text(_ text: String) throws {
        guard text.utf8.count <= textBytes else { throw ResourceLimit.exceeded("Text exceeds byte limit") }
        var lines = 1; var length = 0
        for byte in text.utf8 {
            if byte == 10 || byte == 13 { lines += 1; length = 0 } else { length += 1 }
            guard lines <= 500_000, length <= 1024 * 1024 else { throw ResourceLimit.exceeded("Text line limit reached") }
        }
    }
    /// A conservative lexical budget, not a JSON validator. Run before JSONSerialization.
    public static func json(_ data: Data) throws {
        var depth = 0; var tokens = 0; var quoted = false; var escaped = false
        for (index, byte) in data.enumerated() {
            if index % 65536 == 0 { try Task.checkCancellation() }
            if quoted {
                if escaped { escaped = false }
                else if byte == 92 { escaped = true }
                else if byte == 34 { quoted = false }
            } else {
                if byte == 34 { quoted = true; tokens += 1 }
                if byte == 123 || byte == 91 { depth += 1; tokens += 1 }
                if byte == 125 || byte == 93 { depth = max(0, depth - 1) }
                if byte == 44 { tokens += 1 }
                guard depth <= 64, tokens <= 200_000 else { throw ResourceLimit.exceeded("JSON complexity limit reached") }
            }
        }
    }
    public static func indicators(_ values: [Indicator]) throws {
        guard values.count <= 10_000, values.allSatisfy({ !$0.value.isEmpty && $0.value.utf8.count <= 8192 && $0.id.utf8.count <= 1024 && ($0.campaigns ?? []).count <= 16 && ($0.campaigns ?? []).allSatisfy { $0.utf8.count <= 128 } }) else {
            throw TriageError.invalid("Indicator size/count limit reached")
        }
    }
}
