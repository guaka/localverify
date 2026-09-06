package org.mobiletriage.localverify.core

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.regex.Pattern

private val STIX_KINDS = setOf("domain-name:value", "url:value", "process:name", "file:path", "file:name")
private val STIX_PATTERN = Pattern.compile("^\\s*\\[\\s*([a-z-]+:[a-z]+)\\s*=\\s*'([^'\\\\]+)'\\s*\\]\\s*$")

object IndicatorParser {
    fun parse(data: ByteArray): IndicatorSet {
        val text = data.toString(StandardCharsets.UTF_8)
        val root = try { JSONObject(text) } catch (_: Exception) { throw IllegalArgumentException("Expected a STIX2 bundle") }
        if (root.optString("type") != "bundle") throw IllegalArgumentException("Expected a STIX2 bundle")

        val indicators = ArrayList<Indicator>()
        val skipped = ArrayList<String>()
        var latestIndicatorDate: Long? = null

        val objects = root.optJSONArray("objects") ?: JSONArray()
        for (index in 0 until objects.length()) {
            val item = objects.optJSONObject(index) ?: continue
            if (item.optString("type") != "indicator") continue
            val id = item.optString("id", "unnamed")

            if (item.optBoolean("revoked", false)) {
                skipped.add("$id: revoked")
                continue
            }
            val pattern = item.optString("pattern", "")
            val matcher = STIX_PATTERN.matcher(pattern)
            if (!matcher.find()) {
                skipped.add("$id: unsupported pattern")
                continue
            }

            val kind = matcher.group(1).lowercase()
            val value = matcher.group(2)

            if (item.has("valid_until")) {
                skipped.add("$id: valid_until is not supported")
                continue
            }
            if (item.optString("pattern_type", "stix") != "stix") {
                skipped.add("$id: unsupported pattern_type")
                continue
            }
            if (!STIX_KINDS.contains(kind)) {
                skipped.add("$id: unsupported kind $kind")
                continue
            }
            if (value.isBlank() || value.length > 2048 || indicators.size >= 2_000) {
                skipped.add("$id: indicator size/count limit")
                continue
            }

            val campaigns = item.optJSONArray("x_mvt_campaigns")?.let {
                List(it.length()) { idx -> it.optString(idx) }.filter { it.isNotBlank() }
            }

            indicators.add(Indicator(id = id, kind = kind, value = value, campaigns = campaigns?.ifEmpty { null }))

            val modified = item.optString("modified", item.optString("created", ""))
            parseIsoDate(modified)?.let {
                latestIndicatorDate = if (latestIndicatorDate == null || it > latestIndicatorDate!!) it else latestIndicatorDate
            }
        }

        val source = root.optString("id", "imported")
        return IndicatorSet(
            version = source,
            indicators = indicators,
            unsupported = skipped,
            sources = listOf(source),
            checkedAt = System.currentTimeMillis(),
            latestIndicatorDate = latestIndicatorDate,
            byteCount = data.size
        )
    }

    private fun parseIsoDate(text: String): Long? = if (text.isBlank()) null else runCatching {
        Instant.parse(text).toEpochMilli()
    }.getOrNull()
}
