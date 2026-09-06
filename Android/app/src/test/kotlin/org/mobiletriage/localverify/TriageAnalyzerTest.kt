package org.mobiletriage.localverify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mobiletriage.localverify.core.Indicator
import org.mobiletriage.localverify.core.IndicatorSet
import org.mobiletriage.localverify.core.Report
import org.mobiletriage.localverify.core.TriageAnalyzer
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TriageAnalyzerTest {

    @Test
    fun analyzeAddsUnsupportedEntriesToSkipped() {
        val archive = File.createTempFile("android-analyzer-archive", ".zip").apply {
            deleteOnExit()
        }

        ZipOutputStream(BufferedOutputStream(archive.outputStream())).use { output ->
            output.putNextEntry(ZipEntry("logs/session.log"))
            output.write("nothing suspicious here".toByteArray())
            output.closeEntry()
            output.putNextEntry(ZipEntry("payload.bin"))
            output.write(byteArrayOf(0x00, 0x01, 0x02, 0x03))
            output.closeEntry()
        }

        val report = Report(
            caseID = "case-skipped",
            indicatorVersion = "test",
            findings = mutableListOf(),
            analyzed = mutableListOf(),
            skipped = mutableListOf(),
            errors = mutableListOf(),
        )
        val indicators = IndicatorSet(
            version = "test",
            indicators = listOf(
                Indicator(
                    id = "t1",
                    kind = "domain-name:value",
                    value = "triage-test.invalid",
                    campaigns = emptyList(),
                )
            ),
            unsupported = emptyList(),
        )

        val result = TriageAnalyzer.analyze(
            archive = archive,
            indicators = indicators,
            report = report,
            isCancelled = { false },
            onProgress = {},
            onCheckpoint = {},
        )

        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped.any { it.startsWith("payload.bin: unsupported format") })
        assertFalse(result.analyzed.contains("payload.bin"))
        assertEquals(listOf("logs/session.log"), result.analyzed)
        org.junit.Assert.assertNull(result.consentConfirmedAt)
    }

    @Test
    fun stoppedAnalysisRestartsEveryPathWithoutDuplicatingFindings() {
        val archive = File.createTempFile("synthetic-restart", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                for (name in listOf("first.txt", "second.txt")) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write("triage-test.invalid".toByteArray())
                    zip.closeEntry()
                }
            }
            val originalBytes = archive.readBytes()
            val indicators = IndicatorSet("test", listOf(Indicator("demo", "domain-name:value", "triage-test.invalid")), listOf("unsupported test indicator"))
            val report = Report(caseID = "synthetic-restart", indicatorVersion = "test", consentConfirmedAt = 123L)
            var stopped = false
            TriageAnalyzer.analyze(archive, indicators, report, { stopped }, {}, {
                if (it.analyzed.size == 1) stopped = true
            })
            assertFalse(report.completed)
            assertEquals(listOf("first.txt"), report.analyzed)
            assertTrue(report.errors.single().contains("from the beginning"))
            val oldFindingId = report.findings.single().id
            val restored = Report.fromJson(report.toJson())
            var firstCheckpoint = true
            TriageAnalyzer.analyze(archive, indicators, restored, { false }, {}, {
                if (firstCheckpoint) {
                    assertTrue(it.analyzed.isEmpty())
                    assertTrue(it.findings.isEmpty())
                    assertFalse(it.completed)
                    firstCheckpoint = false
                }
            })
            assertTrue(restored.completed)
            assertTrue(restored.errors.isEmpty())
            assertEquals(listOf("first.txt", "second.txt"), restored.analyzed)
            assertEquals(2, restored.findings.size)
            assertNotEquals(oldFindingId, restored.findings.first().id)
            assertEquals(listOf("unsupported test indicator"), restored.skipped)
            assertEquals(123L, restored.consentConfirmedAt)
            org.junit.Assert.assertArrayEquals(originalBytes, archive.readBytes())
        } finally { archive.delete() }
    }

    @Test
    fun immediateStopLeavesAnIncompleteReportAndRetainsArchive() {
        val archive = File.createTempFile("synthetic-stop", ".zip")
        try {
            val report = Report(caseID = "synthetic-stop", indicatorVersion = "test", completed = true)
            TriageAnalyzer.analyze(archive, IndicatorSet.demo, report, { true }, {}, {})
            assertFalse(report.completed)
            assertTrue(report.errors.single().contains("Analysis stopped"))
            assertTrue(archive.exists())
        } finally { archive.delete() }
    }
}
