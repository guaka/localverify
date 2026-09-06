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
        report.analysisStartedAt = System.currentTimeMillis()
        report.analysisFinishedAt = null
        report.completed = false
        report.errors.clear()

        onProgress("Verifying original archive")
        val digest = ArchiveUtil.hashFile(archive)
        if (report.archiveSHA256.isNotEmpty() && report.archiveSHA256 != digest) {
            report.errors.add("Evidence changed since previous run")
            return report
        }
        report.archiveSHA256 = digest
        onCheckpoint(report)

        val done = report.analyzed.toSet()
        onProgress("Reading archive")
        try {
            ArchiveUtil.walkArchive(
                file = archive,
                onProgress = { _, path -> path?.let { onProgress("Checking $it") } ?: onProgress("Reading archive") },
                onVisit = { path, payload, reason ->
                    if (isCancelled()) throw java.util.concurrent.CancellationException("Analysis interrupted")
                    if (done.contains(path) || report.analyzed.contains(path)) return@walkArchive

                    if (payload == null) {
                        val note = "$path: ${reason ?: "unsupported"}"
                        if (!report.skipped.contains(note)) report.skipped.add(note)
                    } else {
                        val text = String(payload, StandardCharsets.UTF_8)
                        if (text.isNotBlank()) {
                            report.findings += scanText(
                                text = text,
                                source = path,
                                indicators = indicators.indicators,
                                progressHint = { onProgress("Checking $path") }
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
            report.completed = true
            report.analysisFinishedAt = System.currentTimeMillis()
        } catch (error: Exception) {
            report.errors.add(
                if (error is java.util.concurrent.CancellationException) "Analysis interrupted; resume to continue"
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
    ): List<Finding> {
        val findings = ArrayList<Finding>()
        val lines = text.split('\n')

        val boundaries = arrayOf("A-Za-z0-9_.-", "A-Za-z0-9_./:%?=&-")
        val tokenKeys = ArrayList<String?>()
        val sought = arrayOf(HashSet<String>(), HashSet<String>())

        for (indicator in indicators) {
            val group = if (indicator.kind == "domain-name:value") 0 else 1
            val token = indicator.value.all { ch ->
                when (group) {
                    0 -> ch.isLetterOrDigit() || ch in listOf('.', '_', '-')
                    else -> ch.isLetterOrDigit() || ch in listOf('.', '_', '/', ':', '%', '?', '&', '=')
                }
            }
            val key = if (token) {
                if (group == 0) indicator.value.lowercase(Locale.getDefault()) else indicator.value
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
                val tokenValue = matcher.group()
                if (tokenValue.length > 2048) continue
                val normalized = if (group == 0) tokenValue.lowercase(Locale.getDefault()) else tokenValue
                if (sought[group].contains(normalized)) {
                    present[group].add(normalized)
                }
            }
        }

        val structured = collectStructuredRecords(text)
        val index = HashMap<String, MutableList<Triple<String, String, String?>>>()
        for ((fieldPath, observed, ts) in structured) {
            val leaf = fieldPath.substringAfterLast('.').lowercase(Locale.getDefault())
            val kind = FIELD_ALIASES[leaf] ?: continue
            val normalizedValue = if (kind == "domain-name:value") observed.lowercase(Locale.getDefault()).trim('.') else observed
            val key = "$kind\u0000$normalizedValue"
            index.getOrPut(key) { ArrayList() }.add(Triple(fieldPath, observed, ts))
        }

        for ((indicatorIndex, indicator) in indicators.withIndex()) {
            progressHint("Checking ${indicator.id}")

            val structuredKey = "${indicator.kind}\u0000${normalize(indicator.kind, indicator.value)}"
            val structuredMatches = index[structuredKey]
            if (!structuredMatches.isNullOrEmpty()) {
                for ((fieldPath, observed, ts) in structuredMatches) {
                    findings.add(makeFinding(indicator, source, fieldPath, observed, ts, "structured"))
                    if (findings.size >= MAX_FINDINGS) throw IllegalStateException("Finding limit reached")
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
                if (lineIndex % 256 == 0 && (lineIndex > 0) && Thread.interrupted()) {
                    throw java.util.concurrent.CancellationException("Analysis interrupted")
                }
                val line = lines[lineIndex]
                if (tokenRegex.matcher(line).find()) {
                    findings.add(makeFinding(indicator, source, "line ${lineIndex + 1}", line.take(600), null, "raw-text"))
                    if (findings.size >= MAX_FINDINGS) throw IllegalStateException("Finding limit reached")
                }
            }
        }

        return findings
    }

    private fun normalize(kind: String, value: String): String {
        return if (kind == "domain-name:value") value.lowercase(Locale.getDefault()).trim('.') else value
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
            excerpt = excerpt,
            campaigns = indicator.campaigns,
        )
    }

    private fun collectStructuredRecords(text: String): List<Triple<String, String, String?>> {
        fun parse(value: Any?, path: String, timestamp: String?, out: MutableList<Triple<String, String, String?>>) {
            when (value) {
                is JSONObject -> {
                    val time = value.optString("timestamp", value.optString("captureTime", timestamp))
                    for (key in value.keys()) {
                        parse(value.get(key), "$path.$key", time?.takeIf { it.isNotBlank() }, out)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        parse(value.get(i), "$path[$i]", timestamp, out)
                    }
                }
                is String -> out.add(Triple(path, value, timestamp))
            }
        }

        val result = ArrayList<Triple<String, String, String?>>()
        val full = try {
            val root = JSONObject(text)
            parse(root, "$", null, result)
            true
        } catch (_: Exception) {
            false
        }

        if (full) return result

        val lines = text.lines()
        if (lines.isEmpty()) return result
        runCatching {
            val header = JSONObject(lines.first())
            parse(header, "\$header", null, result)
        }
        if (lines.size > 1) {
            val body = lines.drop(1).joinToString("\n")
            runCatching {
                val bodyJson = JSONObject(body)
                parse(bodyJson, "\$body", null, result)
            }
        }
        return result
    }
}
