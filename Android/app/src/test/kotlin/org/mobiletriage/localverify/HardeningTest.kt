package org.mobiletriage.localverify

import org.junit.Assert.*
import org.junit.Test
import org.mobiletriage.localverify.core.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HardeningTest {
    @Test fun deepJSONFailsBeforeRecursiveParsing() {
        val deep = "[".repeat(65) + "0" + "]".repeat(65)
        assertThrows(IllegalArgumentException::class.java) { TriageAnalyzer.scanText(deep, "deep.json", IndicatorSet.demo.indicators, {}) }
        assertThrows(IllegalArgumentException::class.java) { TriageAnalyzer.scanText("not json\n$deep", "deep.ips", IndicatorSet.demo.indicators, {}) }
    }
    @Test fun indicatorReadsAreBounded() {
        assertThrows(IllegalArgumentException::class.java) { InputLimits.read(byteArrayOf(1, 2, 3).inputStream(), 2) }
        assertArrayEquals(byteArrayOf(1, 2), InputLimits.read(byteArrayOf(1, 2).inputStream(), 2))
        assertThrows(IllegalArgumentException::class.java) { IndicatorParser.parse(ByteArray(InputLimits.INDICATOR_BYTES + 1)) }
    }
    @Test fun skippedAndInvalidUTF8AreNeverCountedAsAnalyzed() {
        val archive = File.createTempFile("synthetic", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use {
                it.putNextEntry(ZipEntry("unsupported.bin")); it.write(byteArrayOf(0)); it.closeEntry()
                it.putNextEntry(ZipEntry("invalid.log")); it.write(byteArrayOf(0xc3.toByte(), 0x28)); it.closeEntry()
            }
            val report = TriageAnalyzer.analyze(archive, IndicatorSet.demo, Report(caseID = "synthetic", indicatorVersion = "demo"), { false }, {}, {})
            assertTrue(report.analyzed.isEmpty()); assertEquals(2, report.skipped.size)
            assertEquals("Analysis incomplete", report.status)
        } finally { archive.delete() }
    }
    @Test fun denseMatchesSurviveLimitAndResumeWithoutDuplicates() {
        val archive = File.createTempFile("synthetic", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use {
                it.putNextEntry(ZipEntry("dense.log")); it.write("triage-test.invalid\n".repeat(10001).toByteArray()); it.closeEntry()
            }
            val report = TriageAnalyzer.analyze(archive, IndicatorSet.demo, Report(caseID = "synthetic", indicatorVersion = "demo"), { false }, {}, {})
            assertFalse(report.completed); assertEquals(10000, report.findings.size); assertTrue(report.analyzed.isEmpty())
            TriageAnalyzer.analyze(archive, IndicatorSet.demo, report, { false }, {}, {})
            assertEquals(10000, report.findings.size); assertEquals(10000, report.findings.map { it.record }.toSet().size)
        } finally { archive.delete() }
    }
    @Test fun truncatedCentralDirectoryCannotProduceCompleteAnalysis() {
        val archive = File.createTempFile("synthetic", ".zip")
        try {
            ZipOutputStream(archive.outputStream()).use {
                it.putNextEntry(ZipEntry("ok.log")); it.write("triage-test.invalid".toByteArray()); it.closeEntry()
            }
            java.io.RandomAccessFile(archive, "rw").use { it.setLength(it.length() - 22) }
            val report = TriageAnalyzer.analyze(archive, IndicatorSet.demo, Report(caseID = "synthetic", indicatorVersion = "demo"), { false }, {}, {})
            assertFalse(report.completed); assertTrue(report.errors.isNotEmpty())
        } finally { archive.delete() }
    }
    @Test fun streamedExportPreservesOriginalAndCleansPartialFile() {
        val archive = File.createTempFile("synthetic", ".zip")
        val export = File.createTempFile("synthetic-export", ".zip")
        try {
            archive.writeBytes(ByteArray(2 * 1024 * 1024) { (it % 251).toByte() })
            val report = Report(caseID = "synthetic", indicatorVersion = "demo", archiveSHA256 = ArchiveUtil.hashFile(archive))
            Exporter.writeExportZip(report, archive, export)
            java.util.zip.ZipFile(export).use {
                assertArrayEquals(archive.readBytes(), it.getInputStream(it.getEntry("original.zip")).readBytes())
            }
            assertFalse(File(export.path + ".partial").exists())
            archive.appendText("changed")
            assertThrows(IllegalArgumentException::class.java) { Exporter.writeExportZip(report, archive, export) }
        } finally { archive.delete(); export.delete() }
    }
    @Test fun cancellationAndArrayStructuredMatching() {
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            TriageAnalyzer.scanText("benign", "x.log", IndicatorSet.demo.indicators, {}, isCancelled = { true })
        }
        val found = TriageAnalyzer.scanText("[{\"domain\":\"triage-test.invalid\"}]", "x.json", IndicatorSet.demo.indicators, {})
        assertEquals("structured", found.single().matchType)
    }
}
