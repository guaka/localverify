package org.localverify.record
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FeedDefinition(val resource: String, val name: String, val campaign: String, val url: String)
@Serializable
private data class Catalog(val feeds: List<FeedDefinition>)
@Serializable
private data class ManifestFile(val name: String, val bytes: Int, val sha256: String)
@Serializable
private data class Manifest(val downloadedAt: String? = null, val files: List<ManifestFile>)
data class FeedPayload(val name: String, val data: ByteArray)
internal val metadataJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

class ThreatData {
    fun combine(manifest: ByteArray, catalog: ByteArray, payloads: List<FeedPayload>, cancel: Cancellation): ParseResult = try {
        val manifestText = Limits.decode(manifest, Limits.INDICATOR_BYTES, cancel)
        val catalogText = Limits.decode(catalog, Limits.INDICATOR_BYTES, cancel)
        Limits.json(manifestText, cancel); Limits.json(catalogText, cancel)
        val recorded = metadataJson.decodeFromString<Manifest>(manifestText)
        val feeds = metadataJson.decodeFromString<Catalog>(catalogText).feeds
        if (feeds.size != 5 || feeds.map { it.resource }.toSet().size != 5 ||
            recorded.files.size != 5 || recorded.files.map { it.name }.toSet().size != 5 ||
            payloads.size != 5 || payloads.map { it.name }.toSet().size != 5) fail("Incomplete indicator collection")
        val found = mutableListOf<Indicator>()
        val skipped = mutableListOf<String>()
        val seen = mutableMapOf<Pair<String, String>, Int>()
        var latest: Long? = null
        var byteCount = 0L
        // Validate everything before constructing the combined digest or returning a set.
        val ordered = feeds.map { feed ->
            cancel.check()
            if (feed.campaign.unicodeBlank() || utf8Size(feed.campaign) > 128 || utf8Size(feed.name) > 1024 || utf8Size(feed.url) > 4096) fail("Feed metadata limit reached")
            val file = recorded.files.singleOrNull { it.name == feed.resource } ?: fail("Manifest feed mismatch")
            val data = payloads.singleOrNull { it.name == feed.resource }?.data ?: fail("Missing feed")
            if (data.size > Limits.INDICATOR_BYTES || data.size != file.bytes || sha256(data) != file.sha256) fail("Feed integrity mismatch: ${feed.resource}")
            feed to data
        }
        for ((feed, data) in ordered) {
            cancel.check()
            val parsed = IndicatorParser().parseBundle(data, cancel)
            val set = parsed.set ?: fail(parsed.error ?: "Invalid feed")
            if (set.indicators.isEmpty()) fail("No supported indicators in ${feed.name}")
            for (indicator in set.indicators) {
                cancel.check()
                val key = indicator.kind to indicator.value
                val campaigns = (listOf(feed.campaign) + indicator.campaigns).distinct()
                val index = seen[key]
                if (index == null) {
                    seen[key] = found.size
                    found.add(indicator.copy(campaigns = campaigns))
                } else found[index] = found[index].copy(campaigns = (found[index].campaigns + campaigns).distinct())
            }
            skipped += set.unsupported.map { "${feed.name}: $it" }
            set.latestIndicatorDate?.let { latest = maxOf(latest ?: it, it) }
            byteCount += data.size
        }
        Limits.indicators(found, cancel)
        val digest = hashPayloads(ordered.map { it.second })
        ParseResult(IndicatorSet("Pegasus · Predator · Coruna · DarkSword · ${digest.take(12)}", found, skipped,
            feeds.map { it.url }, instant(recorded.downloadedAt), latest, byteCount, Origin.BUNDLED), null)
    } catch (e: EngineFailure) { ParseResult(null, e.message) }
      catch (_: IllegalArgumentException) { ParseResult(null, "Invalid feed metadata") }
}
