import Foundation
import Dispatch
import Darwin
#if KMP
import KmpEngine
private let engine = Engine()
private func bytes(_ data: Data) -> KotlinByteArray {
    let result = KotlinByteArray(size: Int32(data.count))
    for (i,b) in data.enumerated() { result.set(index: Int32(i), value: Int8(bitPattern: b)) }
    return result
}
private func parse(_ data: Data, _ c: Cancellation) -> Parsed { engine.parseBundle(data: bytes(data), cancel: c) }
private func scan(_ data: Data, _ indicators: [Indicator], _ c: Cancellation) -> ScanResult { engine.scanRecord(data: bytes(data), source: "fixture.txt", indicators: indicators, cancel: c) }
#else
private func parse(_ data: Data, _ c: Cancellation) -> Parsed { parseBundle(data: data, cancel: c) }
private func scan(_ data: Data, _ indicators: [Indicator], _ c: Cancellation) -> ScanResult { scanRecord(data: data, source: "fixture.txt", indicators: indicators, cancel: c) }
#endif
private func require(_ value: Bool, _ name: String) { if !value { fputs("FAIL: \(name)\n", stderr); exit(1) } }
private func rss() -> Int64 { var r = rusage(); getrusage(RUSAGE_SELF, &r); return Int64(r.ru_maxrss) }
private func now() -> Double { ProcessInfo.processInfo.systemUptime }
let root = URL(fileURLWithPath: CommandLine.arguments[1])
func rows(_ name: String) throws -> [[String: Any]] { try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("\(name).json"))) as! [[String: Any]] }
var checked = 0
for row in try rows("matching") {
    let i = Indicator(id: "test-indicator", kind: row["indicatorKind"] as! String, value: row["indicatorValue"] as! String)
    let bundle: [String: Any] = ["type": "bundle", "objects": [["type": "indicator", "id": i.id, "pattern": "[\(i.kind) = '\(i.value)']"]]]
    let parsed = parse(try JSONSerialization.data(withJSONObject: bundle), Cancellation())
    require(parsed.error == nil && parsed.indicators.count == 1, "matching definition parsed")
    let result = scan(Data((row["text"] as! String).utf8), parsed.indicators, Cancellation())
    require(result.findings.allSatisfy { $0.rule == i.id && $0.value == i.value && $0.source == "fixture.txt" }, "finding context")
    let id = row["id"] as! String
    require(result.findings.count == row["expected"] as! Int, id + " count")
    require(result.coverageGaps.isEmpty && !result.cancelled, id + " completion")
    if let type = row["matchType"] as? String { require(result.findings.allSatisfy { $0.matchType == type }, id + " type") }
    if let timestamp = row["timestamp"] as? String { require(result.findings.first?.timestamp == timestamp, id + " timestamp") }
    checked += 1
}
for row in try rows("stix") {
    let data = try (row["raw"] as? String).map { Data($0.utf8) } ?? JSONSerialization.data(withJSONObject: row["bundle"]!)
    let result = parse(data, Cancellation())
    let id = row["id"] as! String
    require((result.error != nil) == row["error"] as! Bool, id + " parse error")
    require(result.indicators.count == row["supported"] as! Int, id + " supported")
    require(result.unsupported.count == row["unsupported"] as! Int, id + " unsupported")
    checked += 1
}
let indicator = Indicator(id: "test", kind: "domain-name:value", value: "triage-test.invalid")
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
let benchmark = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("benchmark.json"))) as! [String: [String: Any]]
let cancelConfig = benchmark["cancel"]!
let scanConfig = benchmark["scan"]!
let workload = Data(String(repeating: cancelConfig["unit"] as! String, count: cancelConfig["repeat"] as! Int).utf8)
let many = (0..<(cancelConfig["definitions"] as! Int)).map { Indicator(id: "\($0)", kind: "process:name", value: "missing-\($0)") }
let liveCancel = Cancellation()
let semaphore = DispatchSemaphore(value: 0)
var stopped = false
let started = now()
DispatchQueue.global().async {
    stopped = scan(workload, many, liveCancel).cancelled
    semaphore.signal()
}
let waiting = now()
while liveCancel.progressUnits() == 0 && now() - waiting < 5 { Thread.sleep(forTimeInterval: 0.0001) }
require(liveCancel.progressUnits() > 0, "scan started before cancellation")
let signalled = now(); liveCancel.cancel()
require(semaphore.wait(timeout: .now() + 5) == .success, "cancellation deadline")
require(stopped, "active cancellation")
let cancellationMs = (now() - signalled)*1000
let bench = Data(String(repeating: scanConfig["unit"] as! String, count: scanConfig["repeat"] as! Int).utf8)
_ = scan(bench, [indicator], Cancellation())
let rssBefore = rss()
var times: [Double] = []
for _ in 0..<(scanConfig["iterations"] as! Int) {
    let t = now(); let r = scan(bench, [indicator], Cancellation())
    require(r.findings.count == scanConfig["expectedFindings"] as! Int && r.coverageGaps.isEmpty, "benchmark correctness")
    times.append((now()-t)*1000)
}
let result: [String: Any] = ["platform":"ios-simulator", "cases": checked+7, "scanMs": times, "inputBytes": bench.count, "peakRssBytes": rss(), "peakRssBeforeBenchmarkBytes": rssBefore, "cancelMs": cancellationMs, "cancelRunMs":(signalled-started)*1000 + cancellationMs]
print(String(data: try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys]), encoding: .utf8)!)
