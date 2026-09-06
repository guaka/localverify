@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package org.localverify.record

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlinx.serialization.Serializable

@Serializable
data class Indicator(val id: String, val kind: String, val value: String, val campaigns: List<String> = emptyList())
@Serializable
enum class Origin { IMPORTED, BUNDLED, UNKNOWN }
@Serializable
data class IndicatorSet(
    val version: String,
    val indicators: List<Indicator>,
    val unsupported: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val checkedAt: Long? = null,
    val latestIndicatorDate: Long? = null,
    val byteCount: Long? = null,
    val origin: Origin = Origin.UNKNOWN,
)
data class ParseResult(val set: IndicatorSet?, val error: String?)
data class Finding(
    val rule: String, val value: String, val source: String, val record: String,
    val matchType: String, val timestamp: String?, val excerpt: String,
    val campaigns: List<String>, val explanation: String,
)
data class ScanResult(
    val findings: List<Finding>, val coverageGaps: List<String>, val cancelled: Boolean,
    val definitionsChecked: Int, val textWorkBytes: Long,
) { val complete: Boolean get() = !cancelled && coverageGaps.isEmpty() }
enum class Phase { PREFLIGHT, INDEX_TEXT, INDEX_RECORDS, MATCH, FINISHED }
data class Progress(val phase: Phase, val bytesVisited: Long, val definitionsChecked: Int)

/** One token per run. Safe to poll/cancel from another thread; contains no evidence content. */
class Cancellation {
    private val stopped = AtomicInt(0)
    private val phase = AtomicInt(Phase.PREFLIGHT.ordinal)
    private val bytes = AtomicLong(0)
    private val definitions = AtomicInt(0)
    fun cancel() { stopped.store(1) }
    fun isCancelled(): Boolean = stopped.load() != 0
    fun progress(): Progress = Progress(Phase.entries[phase.load()], bytes.load(), definitions.load())
    internal fun check() { if (isCancelled()) throw EngineFailure("cancelled") }
    internal fun stage(value: Phase) { phase.store(value.ordinal); bytes.store(0) }
    internal fun visited(value: Long) { bytes.store(value); check() }
    internal fun checked(value: Int) { definitions.store(value); check() }
}
internal class EngineFailure(message: String) : Exception(message)
internal fun fail(message: String): Nothing = throw EngineFailure(message)
