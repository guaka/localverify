package org.localverify.record
import kotlin.test.*
class BudgetTest {
    private val engine = RecordEngine()
    @Test fun invalidDirectUnicodeIsTypedFailure() {
        val result = engine.scanRecord("benign".encodeToByteArray(), "x", listOf(Indicator("id", "file:name", "\uD800")), Cancellation())
        assertFalse(result.complete); assertEquals(listOf("Unpaired surrogate"), result.coverageGaps)
    }
    @Test fun directMetadataHasByteBudgets() {
        val result = engine.scanRecord("benign".encodeToByteArray(), "x", listOf(Indicator("😀".repeat(257), "file:name", "name")), Cancellation())
        assertEquals(listOf("Indicator metadata limit reached"), result.coverageGaps)
    }
    @Test fun noFindingCapacityIsIncomplete() {
        val result = engine.scanRecord("name".encodeToByteArray(), "x", listOf(Indicator("id", "file:name", "name")), Cancellation(), 0)
        assertFalse(result.complete); assertTrue(result.findings.isEmpty()); assertEquals(listOf("Finding limit reached"), result.coverageGaps)
    }
    @Test fun literalWorkIsBoundedEvenWithoutAsciiPrefilter() {
        val data = "benign evidence\n".repeat(100_000).encodeToByteArray()
        val indicators = (0..100).map { Indicator("$it", "file:name", "missing ☃ $it") }
        val result = engine.scanRecord(data, "x", indicators, Cancellation())
        assertFalse(result.complete); assertEquals(listOf("Text matching work limit reached"), result.coverageGaps)
        assertTrue(result.definitionsChecked < indicators.size)
    }
}
