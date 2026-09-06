package org.localverify.record

import kotlinx.serialization.json.*

/** Stateless, bounded record analysis. Run on an adapter-owned worker, never the UI thread. */
class RecordEngine {
    fun scanRecord(
        data: ByteArray, source: String, indicators: List<Indicator>, cancel: Cancellation,
        findingLimit: Int = Limits.FINDINGS,
    ): ScanResult {
        val findings = mutableListOf<Finding>()
        val gaps = mutableListOf<String>()
        var checked = 0
        var work = 0L
        try {
            if (findingLimit !in 0..Limits.FINDINGS) fail("Invalid finding budget")
            if (source.length > 4096 || utf8Size(source) > 4096) fail("Source path limit reached")
            Limits.indicators(indicators, cancel)
            cancel.stage(Phase.PREFLIGHT)
            val text = Limits.decode(data, Limits.TEXT_BYTES, cancel)
            Limits.text(data, cancel)
            cancel.stage(Phase.INDEX_TEXT)
            val tokens = tokenPresence(text, indicators, cancel)
            cancel.stage(Phase.INDEX_RECORDS)
            val records = try { structured(text, cancel) } catch (e: EngineFailure) {
                if (cancel.isCancelled()) throw e
                gaps.add(e.message ?: "Structured analysis unavailable")
                emptyMap()
            }
            cancel.stage(Phase.MATCH)
            for (indicator in indicators) {
                cancel.checked(++checked)
                fun append(record: String, excerpt: String, timestamp: String?, type: String) {
                    cancel.check()
                    if (findings.size >= findingLimit) fail("Finding limit reached")
                    findings.add(Finding(indicator.id, indicator.value, source, record, type, timestamp,
                        excerpt.takeCodePoints(600), indicator.campaigns,
                        if (type == "structured") "Exact indicator match in a recognized field; review context before escalation."
                        else "Indicator appears in text; this may be incidental and requires contextual review."))
                }
                val key = indicator.kind + '\u0000' + normalized(indicator.value, indicator.kind)
                val selected = records[key].orEmpty()
                if (selected.isNotEmpty()) {
                    selected.forEach { append(it.path, it.value, it.time, "structured") }
                    continue
                }
                val domain = indicator.kind == "domain-name:value"
                val token = indicator.value.all { tokenChar(it, domain) }
                val value = if (domain) asciiLower(indicator.value) else indicator.value
                if (token && value !in tokens[if (domain) 0 else 1]) continue
                val literal = Literal(value, domain)
                var start = 0
                var lineNumber = 1
                while (start <= text.length) {
                    cancel.check()
                    val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
                    val line = text.substring(start, end)
                    work += utf8Size(line) + 1L
                    if (work > Limits.TEXT_WORK_BYTES) fail("Text matching work limit reached")
                    if (literal.matches(line, cancel)) append("line $lineNumber", line, null, "raw-text")
                    if (end == text.length) break
                    start = end + 1; lineNumber++
                }
            }
            cancel.check()
            cancel.stage(Phase.FINISHED)
        } catch (e: EngineFailure) {
            if (!cancel.isCancelled()) gaps.add(e.message ?: "Analysis incomplete")
        }
        return ScanResult(findings, gaps, cancel.isCancelled(), checked, work)
    }
}

/** Bounded sets only: never retain one index entry per occurrence in a dense log. */
private fun tokenPresence(text: String, indicators: List<Indicator>, cancel: Cancellation): List<Set<String>> {
    val sought = listOf(mutableSetOf<String>(), mutableSetOf<String>())
    for (indicator in indicators) {
        cancel.check()
        val group = if (indicator.kind == "domain-name:value") 0 else 1
        if (indicator.value.all { tokenChar(it, group == 0) }) {
            sought[group].add(if (group == 0) asciiLower(indicator.value) else indicator.value)
        }
    }
    return (0..1).map { group ->
        val found = mutableSetOf<String>()
        if (sought[group].isNotEmpty()) {
            var start = -1
            var bytes = 0L
            for (i in 0..text.length) {
                if (i % 4096 == 0) cancel.visited(bytes)
                if (i < text.length) bytes += when {
                    text[i].code < 128 -> 1
                    text[i].code < 2048 -> 2
                    text[i].isHighSurrogate() -> 4
                    text[i].isLowSurrogate() -> 0
                    else -> 3
                }
                if (i < text.length && tokenChar(text[i], group == 0)) {
                    if (start < 0) start = i
                } else if (start >= 0) {
                    if (i - start <= 8192) {
                        val token = text.substring(start, i).let { if (group == 0) asciiLower(it) else it }
                        if (token in sought[group]) found.add(token)
                    }
                    start = -1
                }
            }
        }
        found
    }
}

/** Linear literal search, including overlapping candidates; avoids regex backtracking. */
internal class Literal(private val value: String, private val domain: Boolean) {
    private val prefix = IntArray(value.length).also { table ->
        var j = 0
        for (i in 1 until value.length) {
            while (j > 0 && value[i] != value[j]) j = table[j - 1]
            if (value[i] == value[j]) j++
            table[i] = j
        }
    }
    fun matches(line: String, cancel: Cancellation): Boolean {
        var matched = 0
        line.forEachIndexed { i, raw ->
            if (i % 4096 == 0) cancel.check()
            val c = if (domain && raw in 'A'..'Z') raw + 32 else raw
            while (matched > 0 && c != value[matched]) matched = prefix[matched - 1]
            if (c == value[matched]) matched++
            if (matched == value.length) {
                val start = i + 1 - value.length
                if ((start == 0 || !tokenChar(line[start - 1], domain)) &&
                    (i + 1 == line.length || !tokenChar(line[i + 1], domain))) return true
                matched = prefix[matched - 1]
            }
        }
        return false
    }
}

private data class Record(val path: String, val value: String, val time: String?)
private val fields = mapOf(
    "procname" to "process:name", "process" to "process:name", "processname" to "process:name",
    "process_name" to "process:name", "app_name" to "process:name", "procpath" to "file:path",
    "path" to "file:path", "executablepath" to "file:path", "filename" to "file:name", "name" to "file:name",
    "domain" to "domain-name:value", "hostname" to "domain-name:value", "host" to "domain-name:value",
    "url" to "url:value", "uri" to "url:value",
)
private fun structured(text: String, cancel: Cancellation): Map<String, List<Record>> {
    val result = mutableMapOf<String, MutableList<Record>>()
    var nodes = 0
    fun collect(value: JsonElement, path: String, timestamp: String?) {
        cancel.check()
        if (++nodes > 100_000 || utf8Size(path) > 4096) fail("Structured record limit reached")
        when (value) {
            is JsonObject -> {
                val time = (value.string("timestamp") ?: value.string("captureTime") ?: timestamp)?.takeCodePoints(256)
                for (field in value.keys.sorted()) {
                    val child = value.getValue(field)
                    val childPath = "$path.$field"
                    if (utf8Size(childPath) > 4096) fail("Structured path limit reached")
                    val string = (child as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (string != null) {
                        if (++nodes > 100_000) fail("Structured record limit reached")
                        fields[asciiLower(field)]?.let { kind ->
                            if (utf8Size(string) <= 8192) result.getOrPut(kind + '\u0000' + normalized(string, kind)) { mutableListOf() }
                                .add(Record(childPath, string, time))
                        }
                    } else collect(child, childPath, time)
                }
            }
            is JsonArray -> value.forEachIndexed { i, child -> collect(child, "$path[$i]", timestamp) }
            else -> Unit
        }
    }
    fun decode(candidate: String, path: String): Boolean {
        if (candidate.firstOrNull { it !in " \t\r\n" } !in listOf('{', '[')) return false
        Limits.json(candidate, cancel)
        val json = try { Json.parseToJsonElement(candidate) } catch (_: IllegalArgumentException) { return false }
        collect(json, path, null)
        return true
    }
    if (!decode(text, "$")) {
        val newline = text.indexOf('\n')
        if (newline >= 0) {
            decode(text.substring(0, newline), "\$header")
            decode(text.substring(newline + 1), "\$body")
        }
    }
    return result
}
