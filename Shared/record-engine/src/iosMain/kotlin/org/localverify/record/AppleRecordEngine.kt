@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.localverify.record
import kotlinx.cinterop.*
import platform.Foundation.NSData

/** Validate length before allocating or crossing into common parsing. Swift Data bridges to NSData. */
class AppleRecordEngine {
    private fun bytes(data: NSData, max: Int, cancel: Cancellation): ByteArray {
        cancel.check()
        if (data.length > max.toULong()) fail("Input byte limit reached")
        return ByteArray(data.length.toInt()).also { buffer ->
            if (buffer.isNotEmpty()) buffer.usePinned { platform.posix.memcpy(it.addressOf(0), data.bytes, data.length) }
        }
    }
    fun parseBundle(data: NSData, cancel: Cancellation): ParseResult = try {
        IndicatorParser().parseBundle(bytes(data, Limits.INDICATOR_BYTES, cancel), cancel)
    } catch (e: EngineFailure) { ParseResult(null, e.message) }
    fun scanRecord(data: NSData, source: String, indicators: List<Indicator>, cancel: Cancellation, findingLimit: Int): ScanResult = try {
        RecordEngine().scanRecord(bytes(data, Limits.TEXT_BYTES, cancel), source, indicators, cancel, findingLimit)
    } catch (e: EngineFailure) {
        ScanResult(emptyList(), if (cancel.isCancelled()) emptyList() else listOf(e.message ?: "Invalid input"), cancel.isCancelled(), 0, 0)
    }
    fun decodeCache(data: NSData, platform: LegacyPlatform, cancel: Cancellation): ParseResult = try {
        IndicatorCache().decode(bytes(data, 8 * 1024 * 1024, cancel), platform, cancel)
    } catch (e: EngineFailure) { ParseResult(null, e.message) }
    fun payload(name: String, data: NSData): FeedPayload? =
        if (data.length > Limits.INDICATOR_BYTES.toULong()) null else FeedPayload(name, bytes(data, Limits.INDICATOR_BYTES, Cancellation()))
    fun combine(manifest: NSData, catalog: NSData, payloads: List<FeedPayload>, cancel: Cancellation): ParseResult = try {
        ThreatData().combine(bytes(manifest, Limits.INDICATOR_BYTES, cancel), bytes(catalog, Limits.INDICATOR_BYTES, cancel), payloads, cancel)
    } catch (e: EngineFailure) { ParseResult(null, e.message) }
}
