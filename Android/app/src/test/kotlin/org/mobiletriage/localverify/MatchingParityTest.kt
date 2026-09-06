package org.mobiletriage.localverify

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mobiletriage.localverify.core.Indicator
import org.mobiletriage.localverify.core.TriageAnalyzer
import java.io.InputStreamReader

private data class MatchingFixture(
    val text: String,
    val indicatorKind: String = "domain-name:value",
    val indicatorValue: String = "triage-test.invalid",
    val expected: Int,
    val matchType: String?,
)

class MatchingParityTest {

    @Test
    fun matchingFixtureParity() {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/matching.json")
            ?: throw IllegalStateException("Missing fixtures/matching.json")
        val fixtures = InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            Gson().fromJson<List<MatchingFixture>>(
                reader,
                object : TypeToken<List<MatchingFixture>>() {}.type,
            )
        }

        fixtures.forEach { fixture ->
            val indicator = Indicator(
                id = "test-indicator",
                kind = fixture.indicatorKind,
                value = fixture.indicatorValue,
            )
            val findings = TriageAnalyzer.scanText(
                text = fixture.text,
                source = "fixture.txt",
                indicators = listOf(indicator),
                progressHint = {}
            )

            assertEquals(fixture.expected, findings.size)
            if (fixture.expected > 0) {
                if (fixture.matchType != null) {
                    assertTrue(findings.any { it.matchType == fixture.matchType })
                }
            } else {
                assertTrue(findings.isEmpty())
            }
        }
    }
}
