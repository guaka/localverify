package org.mobiletriage.localverify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mobiletriage.localverify.core.IndicatorParser

class IndicatorParserParityTest {

    @Test
    fun parseKnownAndSkippedStixIndicators() {
        val stixJson = """
            {
              "type": "bundle",
              "id": "bundle--00000000-0000-0000-0000-000000000001",
              "objects": [
                {
                  "type": "indicator",
                  "id": "indicator--triage-1",
                  "pattern": "[domain-name:value = 'triage-test.invalid']",
                  "pattern_type": "stix",
                  "modified": "2026-01-01T00:00:00Z",
                  "valid_from": "2026-01-01T00:00:00Z",
                  "x_mvt_campaigns": ["Parity"]
                },
                {
                  "type": "indicator",
                  "id": "indicator--triage-2",
                  "pattern": "[windows-registry-key:value = 'HKCU\\\\Software\\\\Bad']",
                  "pattern_type": "stix"
                },
                {
                  "type": "indicator",
                  "id": "indicator--triage-3",
                  "pattern": "not-a-valid-pattern",
                  "pattern_type": "stix"
                },
                {
                  "type": "indicator",
                  "id": "indicator--triage-4",
                  "pattern": "[domain-name:value = 'revoked.invalid']",
                  "pattern_type": "stix",
                  "revoked": true
                },
                {
                  "type": "indicator",
                  "id": "indicator--triage-5",
                  "pattern": "[file:name = 'payload.bin']",
                  "pattern_type": "stix2"
                },
                {
                  "type": "indicator",
                  "id": "indicator--triage-6",
                  "pattern": "[software:name = 'synthetic-app']",
                  "pattern_type": "stix"
                }
              ]
            }
        """.trimIndent()

        val indicators = IndicatorParser.parse(stixJson.toByteArray())

        assertEquals(5, indicators.unsupported.size)
        assertEquals(1, indicators.indicators.size)
        assertEquals("indicator--triage-1", indicators.indicators[0].id)
        assertEquals("domain-name:value", indicators.indicators[0].kind)
        assertEquals("triage-test.invalid", indicators.indicators[0].value)
        assertEquals(listOf("Parity"), indicators.indicators[0].campaigns)
        assertEquals(listOf("bundle--00000000-0000-0000-0000-000000000001"), indicators.sources)
        assertTrue(indicators.unsupported.any { it.contains("revoked") })
        assertTrue(indicators.unsupported.any { it.contains("unsupported pattern") })
        assertTrue(indicators.unsupported.any { it.contains("unsupported pattern_type") })
        assertTrue(indicators.unsupported.any { it.contains("unsupported kind") })
    }
}
