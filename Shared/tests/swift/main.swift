import Foundation
import Dispatch
import Darwin
import RecordEngine
private let engine = AppleRecordEngine()
private func parse(_ data: Data, _ c: Cancellation) -> ParseResult { engine.parseBundle(data: data, cancel: c) }
private func scan(_ data: Data, _ indicators: [Indicator], _ c: Cancellation, limit: Int32 = 10000) -> ScanResult { engine.scanRecord(data: data, source: "fixture.txt", indicators: indicators, cancel: c, findingLimit: limit) }
private func require(_ value: Bool, _ name: String) { if !value { fputs("FAIL: \(name)\n", stderr); exit(1) } }
private func rss() -> Int64 { var r = rusage(); getrusage(RUSAGE_SELF, &r); return Int64(r.ru_maxrss) }
private func now() -> Double { ProcessInfo.processInfo.systemUptime }
let root = URL(fileURLWithPath: CommandLine.arguments[1])
func rows(_ name: String) throws -> [[String: Any]] { try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("\(name).json"))) as! [[String: Any]] }
var checked = 0
for row in try rows("matching") + rows("record-engine-unicode") + rows("record-engine-unicode-mappings") {
    let i = Indicator(id: "test-indicator", kind: row["indicatorKind"] as! String, value: row["indicatorValue"] as! String, campaigns: ["Synthetic"])
    let bundle: [String: Any] = ["type": "bundle", "objects": [["type": "indicator", "id": i.id, "pattern": "[\(i.kind) = '\(i.value)']"]]]
    let parsed = parse(try JSONSerialization.data(withJSONObject: bundle), Cancellation())
    require(parsed.error == nil && parsed.set!.indicators.count == 1, "matching definition parsed")
    let result = scan(Data((row["text"] as! String).utf8), parsed.set!.indicators, Cancellation())
    require(result.findings.allSatisfy { $0.rule == i.id && $0.value == i.value && $0.source == "fixture.txt" }, "finding context")
    let id = row["id"] as! String
    require(result.findings.count == row["expected"] as! Int, id + " count")
    require(result.coverageGaps.isEmpty && !result.cancelled, id + " completion")
    if let type = row["matchType"] as? String { require(result.findings.allSatisfy { $0.matchType == type }, id + " type") }
    if let timestamp = row["timestamp"] as? String { require(result.findings.first?.timestamp == timestamp, id + " timestamp") }
    checked += 1
}
for row in try rows("stix") + rows("record-engine-stix") {
    let data = try (row["raw"] as? String).map { Data($0.utf8) } ?? JSONSerialization.data(withJSONObject: row["bundle"]!)
    let result = parse(data, Cancellation())
    let id = row["id"] as! String
    require((result.error != nil) == row["error"] as! Bool, id + " parse error")
    require((result.set?.indicators.count ?? 0) == row["supported"] as! Int, id + " supported")
    require((result.set?.unsupported.count ?? 0) == row["unsupported"] as! Int, id + " unsupported")
    checked += 1
}
let indicator = Indicator(id: "test", kind: "domain-name:value", value: "triage-test.invalid", campaigns: [])
for row in try rows("budgets") {
    let text = (row["prefix"] as? String ?? "") + String(repeating: row["unit"] as! String, count: row["repeat"] as! Int) + (row["suffix"] as? String ?? "")
    let result = scan(Data(text.utf8), [indicator], Cancellation())
    require(!result.coverageGaps.isEmpty == row["error"] as! Bool, row["id"] as! String)
    checked += 1
}
require(parse(Data([0xff]), Cancellation()).error != nil, "invalid UTF8 STIX")
require(!scan(Data([0xff]), [indicator], Cancellation()).coverageGaps.isEmpty, "invalid UTF8 record")
require(parse(Data(repeating: 32, count: 5*1024*1024+1), Cancellation()).error != nil, "STIX byte cap")
require(!scan(Data(repeating: 32, count: 16*1024*1024+1), [indicator], Cancellation()).coverageGaps.isEmpty, "text byte cap")
let cancelled = Cancellation(); cancelled.cancel()
require(scan(Data("benign".utf8), [indicator], cancelled).cancelled, "pre-cancelled")
require(parse(Data("{}".utf8), cancelled).error == "cancelled", "pre-cancelled parser")

let threatRoot = URL(fileURLWithPath: CommandLine.arguments[2])
func resource(_ name: String) throws -> Data { try Data(contentsOf: threatRoot.appendingPathComponent(name)) }
let manifest = try resource("threat-manifest.json"), catalog = try resource("feed-catalog.json")
let payloads = try ["pegasus", "cytrox", "predator", "coruna", "darksword"].map { name in engine.payload(name: name, data: try resource(name + ".stix2"))! }
let combined = engine.combine(manifest: manifest, catalog: catalog, payloads: payloads, cancel: Cancellation())
require(combined.error == nil, "publisher combination: \(combined.error ?? "")")
let publishers = combined.set!
require(publishers.indicators.count == 2336 && publishers.unsupported.count == 55 && publishers.byteCount?.int64Value == 2331191, "publisher totals")
require(publishers.sources.count == 5 && publishers.indicators.allSatisfy { !$0.campaigns.isEmpty }, "publisher metadata")
require(publishers.checkedAt?.int64Value == 1788631772000 && publishers.latestIndicatorDate?.int64Value == 1774828800000, "publisher dates")
require(engine.combine(manifest: manifest, catalog: catalog, payloads: Array(payloads.dropLast()), cancel: Cancellation()).error != nil, "missing publisher")
var tampered = try resource("pegasus.stix2"); tampered[0] = 32
var changedPayloads = payloads; changedPayloads[0] = engine.payload(name: "pegasus", data: tampered)!
require(engine.combine(manifest: manifest, catalog: catalog, payloads: changedPayloads, cancel: Cancellation()).error != nil, "publisher integrity")
checked += 3
let merge = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("record-engine-feed-merge.json"))) as! [String: Any]
let mergePayloads = (merge["feeds"] as! [[String: String]]).map { engine.payload(name: $0["name"]!, data: Data($0["text"]!.utf8))! }
let merged = engine.combine(manifest: try JSONSerialization.data(withJSONObject: merge["manifest"]!), catalog: catalog, payloads: mergePayloads, cancel: Cancellation())
require(merged.error == nil && merged.set!.indicators.count == 1 && merged.set!.indicators[0].id == merge["expectedId"] as! String, "dedup first id")
require(merged.set!.indicators[0].campaigns == merge["expectedCampaigns"] as! [String], "dedup campaign union")
checked += 1
let cache = IndicatorCache()
for row in try rows("record-engine-cache") {
    let decoded = engine.decodeCache(data: try JSONSerialization.data(withJSONObject: row["cache"]!), platform: row["platform"] as! String == "SWIFT" ? .swift : .android, cancel: Cancellation())
    require((decoded.error != nil) == row["error"] as! Bool, row["id"] as! String)
    if let set = decoded.set {
        require(set.checkedAt?.int64Value == (row["checkedAt"] as? NSNumber)?.int64Value, "cache date")
        require(cache.preferred(cached: set, bundled: publishers, frozenCase: true) == set, "frozen cache preserved")
        require(cache.preferred(cached: set, bundled: publishers, frozenCase: false) == set, "manual cache preserved")
    }
    checked += 1
}
let dense = scan(Data(String(repeating: "triage-test.invalid ", count: 40000).utf8), [indicator], Cancellation())
require(dense.complete && dense.findings.count == 1, "bounded dense tokens")
let partial = scan(Data(String(repeating: "triage-test.invalid\n", count: 20).utf8), [indicator], Cancellation(), limit: 3)
require(partial.findings.count == 3 && !partial.complete, "partial results at finding limit")
let excerpt = scan(Data((String(repeating: "😀", count: 601) + " triage-test.invalid").utf8), [indicator], Cancellation()).findings.first!.excerpt
require(excerpt.unicodeScalars.count == 600, "code point excerpt")
checked += 3
let config = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("record-engine-workload.json"))) as! [String: Any]
let unit = config["unit"] as! String
var times: [Double] = []
for lines in [config["smallLines"] as! Int, config["largeLines"] as! Int] {
    let data = Data(String(repeating: unit, count: lines).utf8)
    let start = now(), result = scan(data, publishers.indicators, Cancellation())
    let elapsed = (now()-start)*1000
    require(result.complete && result.findings.isEmpty, "large benign workload")
    require(elapsed < config["maximumScanMs"] as! Double, "large scan deadline")
    times.append(elapsed); checked += 1
}
let workload = Data(String(repeating: unit, count: config["largeLines"] as! Int).utf8)
let live = Cancellation(), done = DispatchSemaphore(value: 0)
var stopped = false
DispatchQueue.global().async { stopped = scan(workload, publishers.indicators, live).cancelled; done.signal() }
let waiting = now()
while live.progress().phase != .indexText && now()-waiting < 5 { Thread.sleep(forTimeInterval: 0.0001) }
require(live.progress().phase == .indexText, "indexing started before cancellation")
let signalled = now(); live.cancel()
require(done.wait(timeout: .now()+5) == .success && stopped, "active cancellation")
let cancelMs = (now()-signalled)*1000
let retain = Cancellation(), retainedDone = DispatchSemaphore(value: 0)
var retained: ScanResult?
let retainedData = Data(("{\"process\":\"present\"}\n" + String(repeating: unit, count: config["largeLines"] as! Int)).utf8)
let retainedDefinitions = [Indicator(id: "first", kind: "process:name", value: "present", campaigns: []), Indicator(id: "second", kind: "process:name", value: "missing 😀", campaigns: [])]
DispatchQueue.global().async { retained = scan(retainedData, retainedDefinitions, retain); retainedDone.signal() }
let retainedWait = now()
while retain.progress().definitionsChecked < 2 && now()-retainedWait < 5 { Thread.sleep(forTimeInterval: 0.0001) }
require(retain.progress().phase == .match && retain.progress().definitionsChecked == 2, "second indicator started")
retain.cancel()
require(retainedDone.wait(timeout: .now()+5) == .success, "retained cancellation deadline")
require(retained!.cancelled && retained!.findings.count == 1 && retained!.findings[0].rule == "first", "cancel retains findings")
checked += 1
let result: [String: Any] = ["platform":"ios-simulator", "cases":checked+7, "publisherIndicators":publishers.indicators.count, "version":publishers.version, "smallScanMs":times[0], "largeScanMs":times[1], "largeInputBytes":workload.count, "cancelMs":cancelMs, "peakRssBytes":rss()]
print(String(data: try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys]), encoding: .utf8)!)
