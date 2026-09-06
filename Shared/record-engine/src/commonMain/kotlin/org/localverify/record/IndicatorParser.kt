@file:OptIn(kotlin.time.ExperimentalTime::class)
package org.localverify.record

import kotlin.time.Instant
import kotlinx.serialization.json.*

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun instant(text: String?): Long? = text?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
internal fun jsonObject(text: String): JsonObject? = try { Json.parseToJsonElement(text) as? JsonObject } catch (_: IllegalArgumentException) { null }

class IndicatorParser {
    fun parseBundle(data: ByteArray, cancel: Cancellation): ParseResult = try {
        val text = Limits.decode(data, Limits.INDICATOR_BYTES, cancel)
        Limits.json(text, cancel)
        val root = jsonObject(text) ?: fail("Expected a STIX2 bundle")
        if (root.string("type") != "bundle") fail("Expected a STIX2 bundle")
        val objects = root["objects"] as? JsonArray ?: fail("Expected objects array")
        val found = mutableListOf<Indicator>()
        val skipped = mutableListOf<String>()
        var latest: Long? = null
        for (element in objects) {
            cancel.check()
            val item = element as? JsonObject ?: fail("Invalid STIX object")
            if (item.string("type") != "indicator") continue
            instant(item.string("modified") ?: item.string("created"))?.let { latest = maxOf(latest ?: it, it) }
            val id = item.string("id") ?: "unnamed"
            val pattern = item.string("pattern") ?: ""
            if (utf8Size(id) > 1024 || utf8Size(pattern) > 8192) fail("Indicator metadata limit reached")
            val match = PATTERN.matchEntire(pattern)
            val reason = when {
                item["revoked"] == JsonPrimitive(true) -> "revoked"
                item.containsKey("valid_until") -> "valid_until is not supported"
                item.containsKey("pattern_type") && item.string("pattern_type") != "stix" -> "unsupported pattern_type"
                match == null || match.groupValues[1] !in Limits.kinds -> "unsupported pattern"
                match.groupValues[2].unicodeBlank() || match.groupValues[2].codePointCount() > 2048 || found.size >= 2000 -> "indicator size/count limit"
                else -> null
            }
            if (reason != null) { skipped.add("$id: $reason"); continue }
            val campaigns = item["x_mvt_campaigns"]?.let { value ->
                if (value !is JsonArray) fail("Invalid campaign metadata")
                value.map { (it as? JsonPrimitive)?.takeIf { label -> label.isString }?.content ?: fail("Invalid campaign label") }.filter { !it.unicodeBlank() }.distinct()
            } ?: emptyList()
            val indicator = Indicator(id, match!!.groupValues[1], match.groupValues[2], campaigns)
            Limits.indicators(listOf(indicator), cancel)
            found.add(indicator)
        }
        ParseResult(IndicatorSet(root.string("id") ?: "imported", found, skipped,
            latestIndicatorDate = latest, byteCount = data.size.toLong(), origin = Origin.IMPORTED), null)
    } catch (e: EngineFailure) { ParseResult(null, e.message) }

    private companion object {
        val PATTERN = Regex("^[ \t\r\n\u000B\u000C]*\\[[ \t\r\n\u000B\u000C]*([a-z-]+:[a-z]+)[ \t\r\n\u000B\u000C]*=[ \t\r\n\u000B\u000C]*'([^'\\\\]+)'[ \t\r\n\u000B\u000C]*\\][ \t\r\n\u000B\u000C]*$")
    }
}
