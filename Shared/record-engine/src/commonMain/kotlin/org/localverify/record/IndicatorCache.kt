package org.localverify.record
import kotlinx.serialization.json.*
import kotlin.math.roundToLong

enum class LegacyPlatform { SWIFT, ANDROID }
/** Reads indicator caches only. Frozen case snapshots must always pass frozenCase=true. */
class IndicatorCache {
    fun decode(data: ByteArray, platform: LegacyPlatform, cancel: Cancellation): ParseResult = try {
        val text = Limits.decode(data, 8 * 1024 * 1024, cancel)
        Limits.json(text, cancel)
        val root = Json.parseToJsonElement(text) as? JsonObject ?: fail("Invalid indicator cache")
        fun strings(value: JsonElement?): List<String> {
            if (value == null || value == JsonNull) return emptyList()
            val list = value as? JsonArray ?: fail("Invalid cache list")
            if (list.size > 10_000) fail("Cache list limit reached")
            return list.map { (it as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail("Invalid cache string") }
        }
        fun date(key: String): Long? {
            val raw = root[key]?.takeUnless { it == JsonNull } ?: return null
            val number = (raw as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: fail("Invalid cache date")
            val epoch = if (platform == LegacyPlatform.SWIFT) (number + 978307200.0) * 1000 else number
            if (!epoch.isFinite() || epoch < -62135596800000.0 || epoch > 253402300799999.0) fail("Cache date out of range")
            return epoch.roundToLong()
        }
        val values = root["indicators"] as? JsonArray ?: fail("Missing cache indicators")
        if (values.size > 10_000) fail("Indicator count limit reached")
        val indicators = values.map {
            cancel.check()
            val item = it as? JsonObject ?: fail("Invalid cached indicator")
            Indicator(item.string("id") ?: fail("Missing indicator id"), item.string("kind") ?: fail("Missing indicator kind"),
                item.string("value") ?: fail("Missing indicator value"), strings(item["campaigns"]))
        }
        Limits.indicators(indicators, cancel)
        val version = root.string("version") ?: fail("Missing cache version")
        val sources = strings(root["sources"])
        val unsupported = strings(root["unsupported"])
        if (utf8Size(version) > 4096 || sources.size > 100 || sources.any { utf8Size(it) > 4096 } ||
            unsupported.any { utf8Size(it) > 16384 }) fail("Cache metadata limit reached")
        val count = root["byteCount"]?.takeUnless { it == JsonNull }?.let {
            (it as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.takeIf { n -> n >= 0 } ?: fail("Invalid cache byte count")
        }
        // Legacy caches have no reliable origin flag; sources are evaluated against a reviewed bundled set later.
        ParseResult(IndicatorSet(version, indicators, unsupported, sources, date("checkedAt"), date("latestIndicatorDate"), count), null)
    } catch (e: EngineFailure) { ParseResult(null, e.message) }
      catch (_: IllegalArgumentException) { ParseResult(null, "Invalid indicator cache") }

    fun preferred(cached: IndicatorSet?, bundled: IndicatorSet, frozenCase: Boolean): IndicatorSet? {
        if (frozenCase) return cached
        if (cached == null) return bundled
        val known = bundled.origin == Origin.BUNDLED && cached.sources.isNotEmpty() && cached.sources.all { it in bundled.sources }
        if (!known || cached.origin == Origin.IMPORTED) return cached
        if (bundled.latestIndicatorDate != null && (cached.latestIndicatorDate == null || bundled.latestIndicatorDate > cached.latestIndicatorDate)) return bundled
        if (cached.version == bundled.version && cached.sources == bundled.sources) {
            val metadata = bundled.indicators.associateBy { Triple(it.id, it.kind, it.value) }
            return cached.copy(indicators = cached.indicators.map {
                if (it.campaigns.isEmpty()) metadata[Triple(it.id, it.kind, it.value)] ?: it else it
            })
        }
        return cached
    }
}
