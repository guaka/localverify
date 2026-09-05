import Foundation
import CryptoKit

public struct ThreatFeed {
    public let name: String
    public let resource: String
    public let url: URL
    public var campaign: String {
        switch resource {
        case "pegasus": return "Pegasus"
        case "cytrox", "predator": return "Predator"
        case "coruna": return "Coruna"
        case "darksword": return "DarkSword"
        default: return "Uncategorized"
        }
    }
}

public enum ThreatUpdates {
    public static let feeds = [
        ThreatFeed(name: "Amnesty International — Pegasus", resource: "pegasus", url: URL(string: "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2021-07-18_nso/pegasus.stix2")!),
        ThreatFeed(name: "Amnesty International — Predator/Cytrox", resource: "cytrox", url: URL(string: "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2021-12-16_cytrox/cytrox.stix2")!),
        ThreatFeed(name: "MVT — Predator", resource: "predator", url: URL(string: "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/intellexa_predator/predator.stix2")!),
        ThreatFeed(name: "MVT — Coruna / CryptoWaters", resource: "coruna", url: URL(string: "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/2026-03-03_coruna_cryptowaters/coruna.stix2")!),
        ThreatFeed(name: "MVT — DarkSword", resource: "darksword", url: URL(string: "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/2026-03-30_darksword/darksword.stix2")!)
    ]
    public static let maximumBytes = 5 * 1024 * 1024

    /// Upgrade our cached publisher sets when a newer app includes newer definitions.
    /// Investigator-imported sets have no feed URLs and must never be replaced automatically.
    public static func preferredInstalledSet(cached: IndicatorSet?, bundled: IndicatorSet) -> IndicatorSet {
        guard let cached else { return bundled }
        if cached.version == bundled.version, cached.sources == bundled.sources,
           cached.indicators.allSatisfy({ $0.campaigns == nil }) { return bundled }
        let known = Set(feeds.map { $0.url.absoluteString })
        if let sources = cached.sources, !sources.isEmpty, Set(sources).isSubset(of: known),
           let newDate = bundled.latestIndicatorDate,
           newDate > (cached.latestIndicatorDate ?? .distantPast) {
            return bundled
        }
        return cached
    }

    public static func combine(_ payloads: [Data], checkedAt: Date?) throws -> IndicatorSet {
        guard payloads.count == feeds.count else { throw TriageError.invalid("Incomplete indicator update") }
        var hash = SHA256(); var indicators: [Indicator] = []; var unsupported: [String] = []; var dates: [Date] = []
        var seen: [String: Int] = [:]
        for (index, data) in payloads.enumerated() {
            guard data.count <= maximumBytes else { throw TriageError.invalid("Indicator download exceeds 5 MiB") }
            let parsed = try IndicatorSet.parse(data)
            guard !parsed.indicators.isEmpty else { throw TriageError.invalid("\(feeds[index].name) contains no supported indicators") }
            hash.update(data: data)
            for var indicator in parsed.indicators {
                let key = indicator.kind + "\u{0}" + indicator.value
                let campaign = feeds[index].campaign
                if let existing = seen[key] {
                    if !(indicators[existing].campaigns ?? []).contains(campaign) { indicators[existing].campaigns?.append(campaign) }
                } else {
                    indicator.campaigns = [campaign]
                    seen[key] = indicators.count
                    indicators.append(indicator)
                }
            }
            if let date = parsed.latestIndicatorDate { dates.append(date) }
            unsupported += parsed.unsupported.map { feeds[index].name + ": " + $0 }
        }
        let digest = hash.finalize().map { String(format: "%02x", $0) }.joined()
        var set = IndicatorSet(version: "Pegasus · Predator · Coruna · DarkSword · \(digest.prefix(12))", indicators: indicators, unsupported: unsupported)
        set.sources = feeds.map { $0.url.absoluteString }
        set.checkedAt = checkedAt
        set.latestIndicatorDate = dates.max()
        set.byteCount = payloads.reduce(0) { $0 + $1.count }
        return set
    }

    public static func bundled(in bundle: Bundle) throws -> IndicatorSet {
        let manifestURL = bundle.url(forResource: "threat-manifest", withExtension: "json") ?? bundle.url(forResource: "threat-manifest", withExtension: "json", subdirectory: "ThreatData")
        let metadata = manifestURL.flatMap { try? Data(contentsOf: $0) }.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
        let date = (metadata?["downloadedAt"] as? String).flatMap { ISO8601DateFormatter().date(from: $0) }
        return try combine(feeds.map { feed in
            guard let url = bundle.url(forResource: feed.resource, withExtension: "stix2") ?? bundle.url(forResource: feed.resource, withExtension: "stix2", subdirectory: "ThreatData") else { throw TriageError.invalid("Bundled threat indicators are unavailable") }
            return try Data(contentsOf: url)
        }, checkedAt: date)
    }

    public static func download() async throws -> IndicatorSet {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil; config.urlCache = nil
        config.timeoutIntervalForRequest = 30; config.timeoutIntervalForResource = 90
        let session = URLSession(configuration: config, delegate: NoRedirects(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var payloads: [Data] = []
        for feed in feeds {
            var request = URLRequest(url: feed.url)
            request.httpMethod = "GET"
            request.cachePolicy = .reloadIgnoringLocalCacheData
            let (bytes, response) = try await session.bytes(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200,
                  response.url == feed.url, response.expectedContentLength <= Int64(maximumBytes) else {
                throw TriageError.invalid("Could not download \(feed.name); previous indicators retained")
            }
            var data = Data()
            for try await byte in bytes {
                try Task.checkCancellation()
                guard data.count < maximumBytes else { throw TriageError.invalid("Indicator download exceeds 5 MiB") }
                data.append(byte)
            }
            payloads.append(data)
        }
        return try combine(payloads, checkedAt: Date())
    }
}

private final class NoRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
