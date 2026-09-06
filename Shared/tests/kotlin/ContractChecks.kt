package org.localverify.record.checks
import org.localverify.record.*
import kotlinx.serialization.json.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Test-only runner shared by desktop JVM and Android instrumentation. All input is synthetic/public. */
object ContractChecks {
    fun run(read: (String) -> ByteArray): String {
        var cases = 0
        fun rows(name: String) = Json.parseToJsonElement(read("fixtures/$name.json").decodeToString()).jsonArray
        fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
        fun JsonObject.n(key: String) = getValue(key).jsonPrimitive.int
        fun JsonObject.b(key: String) = getValue(key).jsonPrimitive.boolean
        for (name in listOf("matching", "record-engine-unicode", "record-engine-unicode-mappings")) for (entry in rows(name)) {
            val r = entry.jsonObject; val id = r.s("id")
            val indicator = Indicator("test-indicator", r.s("indicatorKind"), r.s("indicatorValue"), listOf("Synthetic"))
            val bundle = buildJsonObject { put("type", "bundle"); putJsonArray("objects") {
                addJsonObject { put("type", "indicator"); put("id", indicator.id); put("pattern", "[${indicator.kind} = '${indicator.value}']") }
            } }.toString().encodeToByteArray()
            val parsed = IndicatorParser().parseBundle(bundle, Cancellation())
            check(parsed.error == null && parsed.set!!.indicators.size == 1) { "$id parse: ${parsed.error}" }
            val result = RecordEngine().scanRecord(r.s("text").encodeToByteArray(), "fixture.txt", listOf(indicator), Cancellation())
            check(result.complete && result.findings.size == r.n("expected")) { "$id: $result" }
            check(result.findings.all { it.rule == indicator.id && it.value == indicator.value && it.source == "fixture.txt" && it.campaigns == listOf("Synthetic") })
            r["matchType"]?.takeUnless { it == JsonNull }?.let { type -> check(result.findings.all { it.matchType == type.jsonPrimitive.content }) { id } }
            r["timestamp"]?.let { check(result.findings.first().timestamp == it.jsonPrimitive.content) { id } }
            cases++
        }
        for (entry in rows("stix") + rows("record-engine-stix")) {
            val r = entry.jsonObject
            val result = IndicatorParser().parseBundle((r["raw"]?.jsonPrimitive?.content ?: r.getValue("bundle").toString()).encodeToByteArray(), Cancellation())
            check((result.error != null) == r.b("error") && (result.set?.indicators?.size ?: 0) == r.n("supported") && (result.set?.unsupported?.size ?: 0) == r.n("unsupported")) { r.s("id") + result }
            cases++
        }
        val indicator = Indicator("test", "domain-name:value", "triage-test.invalid")
        for (entry in rows("budgets")) {
            val r = entry.jsonObject
            val text = (r["prefix"]?.jsonPrimitive?.content ?: "") + r.s("unit").repeat(r.n("repeat")) + (r["suffix"]?.jsonPrimitive?.content ?: "")
            val result = RecordEngine().scanRecord(text.encodeToByteArray(), "fixture.txt", listOf(indicator), Cancellation())
            check(result.coverageGaps.isNotEmpty() == r.b("error")) { r.s("id") }; cases++
        }
        val manifest = read("threat-data/threat-manifest.json")
        val catalog = read("threat-data/feed-catalog.json")
        val feeds = listOf("pegasus", "cytrox", "predator", "coruna", "darksword").map { FeedPayload(it, read("threat-data/$it.stix2")) }
        val combined = ThreatData().combine(manifest, catalog, feeds, Cancellation())
        val set = combined.set ?: error(combined.error ?: "No feed set")
        check(set.indicators.size == 2336 && set.unsupported.size == 55 && set.byteCount == 2331191L) { "Feed totals: ${set.indicators.size}/${set.unsupported.size}/${set.byteCount}" }
        check(set.indicators.map { it.kind to it.value }.distinct().size == 2336)
        check(set.indicators.all { it.campaigns.isNotEmpty() } && set.sources.size == 5 && set.checkedAt == 1788631772000L && set.latestIndicatorDate == 1774828800000L) { "Feed metadata: ${set.checkedAt}/${set.latestIndicatorDate}" }
        val altered = feeds.toMutableList(); altered[0] = altered[0].copy(data = altered[0].data.clone().also { it[0] = 32 })
        check(ThreatData().combine(manifest, catalog, altered, Cancellation()).error != null)
        check(ThreatData().combine(manifest, catalog, feeds.dropLast(1), Cancellation()).error != null)
        cases += 3
        val merge = Json.parseToJsonElement(read("fixtures/record-engine-feed-merge.json").decodeToString()).jsonObject
        val merged = ThreatData().combine(merge.getValue("manifest").toString().encodeToByteArray(), catalog,
            merge.getValue("feeds").jsonArray.map { FeedPayload(it.jsonObject.s("name"), it.jsonObject.s("text").encodeToByteArray()) }, Cancellation())
        check(merged.error == null && merged.set!!.indicators.single().id == merge.s("expectedId"))
        check(merged.set!!.indicators.single().campaigns == merge.getValue("expectedCampaigns").jsonArray.map { it.jsonPrimitive.content })
        cases++
        val cache = IndicatorCache()
        for (entry in rows("record-engine-cache")) {
            val r = entry.jsonObject
            val result = cache.decode(r.getValue("cache").toString().encodeToByteArray(), LegacyPlatform.valueOf(r.s("platform")), Cancellation())
            check((result.error != null) == r.b("error")) { r.s("id") + result }
            if (result.set != null) {
                check(result.set!!.checkedAt == r["checkedAt"]?.jsonPrimitive?.longOrNull) { r.s("id") }
                check(cache.preferred(result.set, set, true) === result.set)
                check(cache.preferred(result.set, set, false) === result.set) { "Imported cache preserved" }
            }
            cases++
        }
        val stale = set.copy(version = "old", latestIndicatorDate = 0, origin = Origin.UNKNOWN)
        check(cache.preferred(stale, set, false) === set)
        check(cache.preferred(stale, set, true) === stale)
        check(cache.preferred(stale.copy(origin = Origin.IMPORTED), set, false)?.version == "old")
        val emptyLabels = set.copy(indicators = set.indicators.map { it.copy(campaigns = emptyList()) })
        check(cache.preferred(emptyLabels, set, false)!!.indicators == set.indicators)
        val changed = emptyLabels.copy(indicators = listOf(emptyLabels.indicators[0].copy(id = "unknown")))
        check(cache.preferred(changed, set, false)!!.indicators.single().campaigns.isEmpty())
        check(cache.preferred(null, set, true) == null); cases += 6
        val invalid = byteArrayOf(-1)
        check(IndicatorParser().parseBundle(invalid, Cancellation()).error != null)
        check(!RecordEngine().scanRecord(invalid, "x", listOf(indicator), Cancellation()).complete)
        check(IndicatorParser().parseBundle(ByteArray(5*1024*1024+1), Cancellation()).error != null)
        check(!RecordEngine().scanRecord(ByteArray(16*1024*1024+1), "x", listOf(indicator), Cancellation()).complete)
        val pre = Cancellation().also { it.cancel() }
        check(RecordEngine().scanRecord(byteArrayOf(), "x", listOf(indicator), pre).cancelled)
        check(IndicatorParser().parseBundle("{}".encodeToByteArray(), pre).error == "cancelled"); cases += 6
        val dense = "triage-test.invalid ".repeat(40_000).encodeToByteArray()
        val denseResult = RecordEngine().scanRecord(dense, "x", listOf(indicator), Cancellation())
        check(denseResult.complete && denseResult.findings.size == 1)
        val partial = RecordEngine().scanRecord("triage-test.invalid\n".repeat(20).encodeToByteArray(), "x", listOf(indicator), Cancellation(), 3)
        check(partial.findings.size == 3 && !partial.complete)
        val unicodeExcerpt = RecordEngine().scanRecord(("😀".repeat(601) + " triage-test.invalid").encodeToByteArray(), "x", listOf(indicator), Cancellation()).findings.single().excerpt
        check(unicodeExcerpt.codePointCount(0, unicodeExcerpt.length) == 600 && !unicodeExcerpt.last().isHighSurrogate()); cases += 3
        // Independent straightforward reference checks the token prefilter and literal matcher.
        val random = java.util.Random(42)
        val definitions = listOf("a.invalid", "ab", "a.b", "a/a", "é", "a a", "😀").flatMap { value ->
            listOf("domain-name:value", "process:name").map { kind -> Indicator("$kind:$value", kind, value) }
        }
        fun boundary(c: Char, domain: Boolean) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in (if (domain) "_.-" else "_./:%?=&-")
        repeat(100) {
            val text = (0..100).joinToString("") { listOf("a", "b", ".", "/", " ", "é", "😀", "A", "\n")[random.nextInt(9)] }
            val expected = definitions.flatMap { d -> text.split('\n').mapIndexedNotNull { line, content ->
                val domain = d.kind == "domain-name:value"
                fun fold(s: String) = if (domain) s.map { if (it in 'A'..'Z') it + 32 else it }.joinToString("") else s
                val input = fold(content); val value = fold(d.value)
                val match = input.indices.any { pos -> input.startsWith(value, pos) && (pos == 0 || !boundary(input[pos-1], domain)) && (pos+value.length == input.length || !boundary(input[pos+value.length], domain)) }
                if (match) d.id to "line ${line+1}" else null
            } }
            val actual = RecordEngine().scanRecord(text.encodeToByteArray(), "x", definitions, Cancellation())
            check(actual.complete && actual.findings.map { it.rule to it.record } == expected) { "Reference mismatch" }
        }; cases += 100
        val workload = Json.parseToJsonElement(read("fixtures/record-engine-workload.json").decodeToString()).jsonObject
        val unit = workload.s("unit")
        val times = mutableListOf<Double>()
        for (lines in listOf(workload.n("smallLines"), workload.n("largeLines"))) {
            val data = unit.repeat(lines).encodeToByteArray()
            val start = System.nanoTime()
            val result = RecordEngine().scanRecord(data, "synthetic.log", set.indicators, Cancellation())
            val elapsed = (System.nanoTime()-start)/1e6
            check(result.complete && result.findings.isEmpty()) { "Large workload: $result" }
            check(elapsed < workload.n("maximumScanMs")) { "Large workload exceeds 30s: $elapsed" }
            times.add(elapsed); cases++
        }
        // Cancel after text indexing starts, so this is a live run, not a pre-cancelled call.
        val data = unit.repeat(workload.n("largeLines")).encodeToByteArray(); val live = Cancellation(); val done = CountDownLatch(1)
        var result: ScanResult? = null
        val worker = thread { try { result = RecordEngine().scanRecord(data, "synthetic.log", set.indicators, live) } finally { done.countDown() } }
        val wait = System.nanoTime()
        while (live.progress().phase != Phase.INDEX_TEXT && done.count > 0 && System.nanoTime()-wait < 5_000_000_000) Thread.sleep(0, 100000)
        check(live.progress().phase == Phase.INDEX_TEXT) { "Worker reached indexing before cancel" }
        val signalled = System.nanoTime(); live.cancel()
        check(done.await(5, TimeUnit.SECONDS)); worker.join()
        val cancelMs = (System.nanoTime()-signalled)/1e6
        check(result?.cancelled == true); cases++
        val retain = Cancellation(); val retainedDone = CountDownLatch(1); var retained: ScanResult? = null
        val retainedData = ("{\"process\":\"present\"}\n" + unit.repeat(workload.n("largeLines"))).encodeToByteArray()
        val retainedDefinitions = listOf(Indicator("first", "process:name", "present"), Indicator("second", "process:name", "missing 😀"))
        val retainedWorker = thread { try { retained = RecordEngine().scanRecord(retainedData, "synthetic.log", retainedDefinitions, retain) } finally { retainedDone.countDown() } }
        val retainedWait = System.nanoTime()
        while (retain.progress().definitionsChecked < 2 && retainedDone.count > 0 && System.nanoTime()-retainedWait < 5_000_000_000) Thread.sleep(0, 100000)
        check(retain.progress().phase == Phase.MATCH && retain.progress().definitionsChecked == 2)
        retain.cancel(); check(retainedDone.await(5, TimeUnit.SECONDS)); retainedWorker.join()
        check(retained!!.cancelled && retained!!.findings.single().rule == "first")
        cases++
        return buildJsonObject { put("cases", cases); put("publisherIndicators", set.indicators.size); put("version", set.version); put("smallScanMs", times[0]); put("largeScanMs", times[1]); put("cancelMs", cancelMs); put("largeInputBytes", data.size) }.toString()
    }
}
