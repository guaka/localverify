import Foundation
import CryptoKit
import CZlib

public enum Archive {
    public static func copy(_ source: URL, to destination: URL) throws {
        let input = try FileHandle(forReadingFrom: source); defer { try? input.close() }
        try LocalStorage.createFile(destination)
        let output = try FileHandle(forWritingTo: destination); defer { try? output.close() }
        var size = 0
        while let data = try input.read(upToCount: 1048576), !data.isEmpty {
            try Task.checkCancellation(); size += data.count
            guard size <= 8 * 1024 * 1024 * 1024 else { throw TriageError.invalid("Compressed archive exceeds 8 GiB import limit") }
            try output.write(contentsOf: data)
        }
    }
    public static func hash(_ url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        var hash = SHA256()
        while let chunk = try handle.read(upToCount: 1024 * 1024), !chunk.isEmpty { try Task.checkCancellation(); hash.update(data: chunk) }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }
    public static func safePath(_ path: String) -> Bool {
        !path.isEmpty && !path.hasPrefix("/") && !path.contains("\\") && !path.split(separator: "/", omittingEmptySubsequences: false).contains("..")
    }
    // No files are extracted: bounded regular-file payloads are passed directly to parsers.
    public static func walk(_ url: URL, visit: (String, Data?, String?) throws -> Void) throws {
        guard let gz = gzopen(url.path, "rb") else { throw TriageError.invalid("Cannot open archive") }
        defer { gzclose(gz) }
        var expanded: Int64 = 0; var entries = 0; var regularPaths = Set<String>()
        func read(_ count: Int) throws -> Data {
            try Task.checkCancellation()
            var result = Data(count: count)
            let n = result.withUnsafeMutableBytes { gzread(gz, $0.baseAddress, UInt32(count)) }
            guard n == count else { throw TriageError.invalid("Truncated or corrupt gzip/tar archive") }
            expanded += Int64(n)
            guard expanded <= 8 * 1024 * 1024 * 1024 else { throw TriageError.invalid("Archive exceeds 8 GiB expanded limit") }
            return result
        }
        func string(_ data: Data, _ range: Range<Int>) -> String {
            String(decoding: data[range].prefix { $0 != 0 }, as: UTF8.self)
        }
        let signature = try FileHandle(forReadingFrom: url)
        let magic = try signature.read(upToCount: 2); try signature.close()
        guard magic == Data([0x1f, 0x8b]) else { throw TriageError.invalid("Expected gzip-compressed tar") }
        while true {
            let header = try read(512)
            if header.allSatisfy({ $0 == 0 }) {
                guard try read(512).allSatisfy({ $0 == 0 }) else { throw TriageError.invalid("Invalid tar terminator") }
                // Drain gzip to validate its trailer/CRC; only tar padding is accepted.
                var buffer = [UInt8](repeating: 0, count: 65536)
                while true {
                    try Task.checkCancellation()
                    let n = gzread(gz, &buffer, UInt32(buffer.count))
                    guard n >= 0 else { throw TriageError.invalid("Invalid gzip checksum") }
                    if n == 0 { break }
                    expanded += Int64(n)
                    guard expanded <= 8 * 1024 * 1024 * 1024, buffer.prefix(Int(n)).allSatisfy({ $0 == 0 }) else { throw TriageError.invalid("Unexpected trailing archive data") }
                }
                break
            }
            entries += 1
            guard entries <= 100000 else { throw TriageError.invalid("Too many archive entries") }
            let checksumText = string(header, 148..<156).trimmingCharacters(in: .whitespacesAndNewlines)
            let sum = header.enumerated().reduce(0) { $0 + ((148..<156).contains($1.offset) ? 32 : Int($1.element)) }
            guard Int(checksumText, radix: 8) == sum else { throw TriageError.invalid("Invalid tar header checksum") }
            let prefix = string(header, 345..<500)
            let name = string(header, 0..<100)
            let path = prefix.isEmpty ? name : prefix + "/" + name
            guard safePath(path) else { throw TriageError.invalid("Unsafe archive path") }
            guard let size = Int(string(header, 124..<136).trimmingCharacters(in: .whitespacesAndNewlines), radix: 8), size >= 0, size <= 8 * 1024 * 1024 * 1024 else { throw TriageError.invalid("Invalid tar entry size") }
            let regular = header[156] == 0 || header[156] == 48
            if regular && !regularPaths.insert(path).inserted { throw TriageError.invalid("Duplicate archive path; cannot resume unambiguously") }
            let supported = ["ips", "json", "log", "txt", "crash"].contains(URL(fileURLWithPath: path).pathExtension.lowercased())
            let keep = regular && supported && size <= 16 * 1024 * 1024
            var payload = Data(); var left = size
            while left > 0 {
                let part = try read(min(left, 65536)); if keep { payload.append(part) }; left -= part.count
            }
            if size % 512 != 0 { _ = try read(512 - size % 512) }
            if header[156] == 53 { continue }
            try visit(path, keep ? payload : nil, keep ? nil : (!regular ? "unsupported tar entry type" : (supported ? "exceeds 16 MiB parser limit" : "unsupported format")))
        }
    }
}
