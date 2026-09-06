package org.mobiletriage.localverify.core

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

private val FIELD_ALIASES = mapOf(
    "procname" to "process:name",
    "process" to "process:name",
    "processname" to "process:name",
    "process_name" to "process:name",
    "app_name" to "process:name",
    "procpath" to "file:path",
    "path" to "file:path",
    "executablepath" to "file:path",
    "filename" to "file:name",
    "name" to "file:name",
    "domain" to "domain-name:value",
    "hostname" to "domain-name:value",
    "host" to "domain-name:value",
    "url" to "url:value",
    "uri" to "url:value",
)

object TriageAnalyzer {
    fun analyze(
        archive: java.io.File,
        indicators: IndicatorSet,
        report: Report,
        isCancelled: () -> Boolean,
        onProgress: (String) -> Unit,
        onCheckpoint: (Report) -> Unit,
    ): Report {
        require(report.schemaVersion == 1 && report.findings.size <= MAX_FINDINGS && report.analyzed.size <= 100_000) { "Invalid report" }
        InputLimits.indicators(indicators.indicators)
        report.engineVersion = "0.4.0-android-hardened"
        // Checkpoints retain incomplete results for review, never a resume cursor.
        report.analyzed.clear()
        report.findings.clear()
        report.skipped = indicators.unsupported.toMutableList()
        report.analysisStartedAt = System.currentTimeMillis()
        report.analysisFinishedAt = null
        report.completed = false
        report.errors.clear()

        try {
            onCheckpoint(report)
            if (isCancelled()) throw java.util.concurrent.CancellationException("Analysis interrupted")
            onProgress("Verifying original archive")
            val digest = ArchiveUtil.hashFile(archive)
            if (report.archiveSHA256.isNotEmpty() && report.archiveSHA256 != digest) {
                report.errors.add("Evidence changed since previous run")
                onCheckpoint(report)
                return report
            }
            report.archiveSHA256 = digest
            onCheckpoint(report)

            onProgress("Reading archive")
            ArchiveUtil.walkArchive(
                file = archive,
                onProgress = { _, _ ->
                    if (isCancelled()) throw java.util.concurrent.CancellationException("Analysis interrupted")
                    onProgress("Reading archive")
                },
                onVisit = { path, payload, reason ->
                    if (isCancelled()) throw java.util.concurrent.CancellationException("Analysis interrupted")

                    if (payload == null) {
                        val note = "$path: ${reason ?: "unsupported"}"
                        if (!report.skipped.contains(note)) report.skipped.add(note)
                        onCheckpoint(report)
                        return@walkArchive
                    } else {
                        val text = try {
                            StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(payload)).toString()
                        } catch (_: java.nio.charset.CharacterCodingException) {
                            val note = "$path: non-UTF8 data"
                            if (!report.skipped.contains(note)) report.skipped.add(note)
                            onCheckpoint(report)
                            return@walkArchive
                        }
                        if (text.isNotBlank()) {
                            report.findings.removeAll { it.source == path }
                            scanText(
                                text = text,
                                source = path,
                                indicators = indicators.indicators,
                                progressHint = { onProgress("Checking file") },
                                findingLimit = MAX_FINDINGS - report.findings.size,
                                onFinding = { report.findings.add(it) },
                                isCancelled = isCancelled
                            )
                            if (report.findings.size > MAX_FINDINGS) {
                                throw IllegalStateException("Finding limit reached")
                            }
                        }
                    }
                    report.analyzed.add(path)
                    onCheckpoint(report)
                }
            )
            if (report.analyzed.isEmpty()) report.errors.add("No supported text or structured records were analyzed")
            if (indicators.indicators.isEmpty()) report.errors.add("No supported indicators available")
            check(ArchiveUtil.hashFile(archive) == digest) { "Evidence changed during analysis" }
            report.completed = true
            report.analysisFinishedAt = System.currentTimeMillis()
        } catch (error: Exception) {
            report.errors.add(
                if (error is java.util.concurrent.CancellationException || isCancelled() || Thread.currentThread().isInterrupted)
                    "Analysis stopped; results are incomplete. Start again to scan the archive from the beginning."
                else error.message ?: "Analysis failed"
            )
            onCheckpoint(report)
            return report
        }
        onCheckpoint(report)
        return report
    }

    fun scanText(
        text: String,
        source: String,
        indicators: List<Indicator>,
        progressHint: (String) -> Unit,
        findingLimit: Int = MAX_FINDINGS,
        onFinding: (Finding) -> Unit = {},
        isCancelled: () -> Boolean = { Thread.currentThread().isInterrupted },
    ): List<Finding> {
        InputLimits.text(text)
        InputLimits.indicators(indicators)
        fun checkCancelled() {
            if (isCancelled() || Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Analysis interrupted")
        }
        checkCancelled()
        val findings = ArrayList<Finding>()
        fun append(finding: Finding) {
            check(findings.size < findingLimit) { "Finding limit reached" }
            findings.add(finding)
            onFinding(finding)
        }
        var workBytes = 0L
        val lines = text.split('\n')

        val boundaries = arrayOf("A-Za-z0-9_.-", "A-Za-z0-9_./:%?=&-")
        val tokenKeys = ArrayList<String?>()
        val sought = arrayOf(HashSet<String>(), HashSet<String>())

        for (indicator in indicators) {
            checkCancelled()
            val group = if (indicator.kind == "domain-name:value") 0 else 1
            val token = indicator.value.all { ch ->
                when (group) {
                    0 -> (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9') || ch in listOf('.', '_', '-')
                    else -> (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9') || ch in listOf('.', '_', '/', ':', '%', '?', '&', '=')
                }
            }
            val key = if (token) {
                if (group == 0) indicator.value.lowercase(Locale.ROOT) else indicator.value
            } else null
            tokenKeys.add(key)
            if (key != null) sought[group].add(key)
        }

        val present = arrayOf(HashSet<String>(), HashSet<String>())
        for (group in 0..1) {
            if (sought[group].isEmpty()) continue
            val tokenPattern = Pattern.compile("[${boundaries[group]}]+", if (group == 0) Pattern.CASE_INSENSITIVE else 0)
            val matcher = tokenPattern.matcher(text)
            while (matcher.find()) {
                checkCancelled()
                val tokenValue = matcher.group()
                if (tokenValue.length > 2048) continue
                val normalized = if (group == 0) tokenValue.lowercase(Locale.ROOT) else tokenValue
                if (sought[group].contains(normalized)) {
                    present[group].add(normalized)
                }
            }
        }

        val structured = collectStructuredRecords(text)
        val index = HashMap<String, MutableList<Triple<String, String, String?>>>()
        for ((fieldPath, observed, ts) in structured) {
            val leaf = fieldPath.substringAfterLast('.').lowercase(Locale.ROOT)
            val kind = FIELD_ALIASES[leaf] ?: continue
            val normalizedValue = if (kind == "domain-name:value") observed.lowercase(Locale.ROOT).trim('.') else observed
            val key = "$kind\u0000$normalizedValue"
            index.getOrPut(key) { ArrayList() }.add(Triple(fieldPath, observed, ts))
        }

        for ((indicatorIndex, indicator) in indicators.withIndex()) {
            checkCancelled()
            progressHint("Checking definitions")

            val structuredKey = "${indicator.kind}\u0000${normalize(indicator.kind, indicator.value)}"
            val structuredMatches = index[structuredKey]
            if (!structuredMatches.isNullOrEmpty()) {
                for ((fieldPath, observed, ts) in structuredMatches) {
                    append(makeFinding(indicator, source, fieldPath, observed, ts, "structured"))

                }
                continue
            }

            val key = tokenKeys[indicatorIndex]
            val group = if (indicator.kind == "domain-name:value") 0 else 1
            if (key != null && !present[group].contains(key)) continue

            val boundary = boundaries[group]
            val tokenRegex = Pattern.compile("(?<![$boundary])${Pattern.quote(indicator.value)}(?![$boundary])",
                if (group == 0) Pattern.CASE_INSENSITIVE else 0)
            for (lineIndex in lines.indices) {
                if (lineIndex % 256 == 0) checkCancelled()
                val line = lines[lineIndex]
                workBytes += line.length + 1
                check(workBytes <= 128L * 1024 * 1024) { "Text matching work limit reached" }
                if (tokenRegex.matcher(line).find()) {
                    append(makeFinding(indicator, source, "line ${lineIndex + 1}", line.take(600), null, "raw-text"))

                }
            }
        }

        return findings
    }

    private fun normalize(kind: String, value: String): String {
        return if (kind == "domain-name:value") value.lowercase(Locale.ROOT).trim('.') else value
    }

    private fun makeFinding(
        indicator: Indicator,
        source: String,
        record: String,
        excerpt: String,
        timestamp: String?,
        type: String,
    ): Finding {
        return Finding(
            id = UUID.randomUUID().toString(),
            rule = indicator.id,
            value = indicator.value,
            source = source,
            record = record,
            timestamp = timestamp,
            matchType = type,
            explanation = if (type == "structured") {
                "Exact indicator match in a recognized field; review context before escalation."
            } else {
                "Indicator appears in text; this may be incidental and requires contextual review."
            },
            excerpt = excerpt.take(600),
            campaigns = indicator.campaigns,
        )
    }

    private fun collectStructuredRecords(text: String): List<Triple<String, String, String?>> {
        var nodes = 0
        fun parse(value: Any?, path: String, timestamp: String?, out: MutableList<Triple<String, String, String?>>) {
            nodes++
            check(nodes <= 100_000 && path.length <= 4096) { "Structured record limit reached" }
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Analysis interrupted")
            when (value) {
                is JSONObject -> {
                    val time = value.optString("timestamp", value.optString("captureTime", timestamp))
                    for (key in value.keys()) parse(value.get(key), "$path.$key", time?.takeIf { it.isNotBlank() }, out)
                }
                is JSONArray -> for (i in 0 until value.length()) parse(value.get(i), "$path[$i]", timestamp, out)
                is String -> if (value.length <= 8192) out.add(Triple(path, value, timestamp?.take(256)))
            }
        }
        fun decode(candidate: String): Any? {
            val trimmed = candidate.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
            InputLimits.json(candidate)
            return try {
                if (trimmed.startsWith("[")) JSONArray(candidate) else JSONObject(candidate)
            } catch (_: org.json.JSONException) { null }
        }
        val result = ArrayList<Triple<String, String, String?>>()
        val full = decode(text)
        if (full != null) { parse(full, "$", null, result); return result }
        val newline = text.indexOf('\n')
        if (newline >= 0) {
            decode(text.substring(0, newline))?.let { parse(it, "\$header", null, result) }
            decode(text.substring(newline + 1))?.let { parse(it, "\$body", null, result) }
        }
        return result
    }
}
