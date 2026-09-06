package org.mobiletriage.localverify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertTrue(result.analyzed.contains("payload.bin"))
    }
}
