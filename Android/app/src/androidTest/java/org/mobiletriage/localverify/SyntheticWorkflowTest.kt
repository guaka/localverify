package org.mobiletriage.localverify

import android.content.Intent
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mobiletriage.localverify.core.Report
import org.mobiletriage.localverify.storage.CaseStore
import org.mobiletriage.localverify.ui.MainActivity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Run only on a disposable emulator: all inputs are generated synthetic data. */
class SyntheticWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: CaseStore
    private var scenario: ActivityScenario<MainActivity>? = null
    private var caseId: String? = null
    private var fixture: File? = null

    @Before fun prepare() {
        store = CaseStore(context)
        check(store.listCaseIds().isEmpty()) { "Use a fresh emulator without existing cases" }
    }

    @After fun cleanup() {
        scenario?.close()
        caseId?.let {
            store.exportPath(it).delete()
            store.deleteCase(it)
        }
        fixture?.delete()
    }

    @Test fun viewImportConsentAnalysisRecreateAndExportWithOriginal() {
        exercise(Intent.ACTION_VIEW, includeOriginal = true)
    }

    @Test fun shareImportAnalysisAndReportOnlyExport() {
        exercise(Intent.ACTION_SEND, includeOriginal = false)
    }

    private fun exercise(action: String, includeOriginal: Boolean) {
        val archive = File(context.filesDir, "exports/synthetic-workflow.zip")
        fixture = archive
        archive.parentFile!!.mkdirs()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("logs/synthetic.json"))
            zip.write("""{"hostname":"triage-test.invalid"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("synthetic.bin"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archive)
        val incoming = Intent(context, MainActivity::class.java).apply {
            this.action = action
            setDataAndType(uri, "application/zip")
            if (action == Intent.ACTION_SEND) putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        scenario = ActivityScenario.launch(incoming)
        compose.waitUntil(15_000) {
            val id = store.listCaseIds().singleOrNull()
            caseId = id
            id != null && store.caseArchivePath(id).length() == archive.length()
        }
        compose.onNodeWithText("Start / Resume").performClick()
        compose.waitForIdle()
        assertNull("Analysis must require consent", report().analysisStartedAt)
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText("Start / Resume").performClick()
        compose.waitUntil(15_000) { runCatching { report().completed }.getOrDefault(false) }
        val result = report()
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertNotNull("Consent timestamp must survive checkpoints", result.consentConfirmedAt)
        assertEquals(1, result.findings.size)
        assertEquals("structured", result.findings.single().matchType)
        assertEquals(2, result.analyzed.size)
        assertTrue(result.skipped.any { it.startsWith("synthetic.bin:") })

        scenario!!.recreate()
        compose.waitForIdle()
        assertEquals(result.findings, report().findings)
        assertEquals(result.consentConfirmedAt, report().consentConfirmedAt)
        if (!includeOriginal) compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("Export report ZIP").performClick()
        val exported = store.exportPath(caseId!!)
        compose.waitUntil(15_000) {
            runCatching { ZipFile(exported).use { it.getEntry("report.html") != null } }.getOrDefault(false)
        }
        ZipFile(exported).use { zip ->
            assertNotNull(zip.getEntry("report.json"))
            assertNotNull(zip.getEntry("report.html"))
            val originals = zip.entries().asSequence().filter { it.name.startsWith("original.") }.toList()
            assertEquals(if (includeOriginal) 1 else 0, originals.size)
            if (includeOriginal) {
                assertArrayEquals(archive.readBytes(), zip.getInputStream(originals.single()).use { it.readBytes() })
            }
        }
    }

    private fun report(): Report = checkNotNull(store.readReport(checkNotNull(caseId)))
}
