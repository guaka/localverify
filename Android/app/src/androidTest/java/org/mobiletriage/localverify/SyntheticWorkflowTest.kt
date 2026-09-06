package org.mobiletriage.localverify

import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mobiletriage.localverify.core.*
import org.mobiletriage.localverify.storage.CaseStore
import org.mobiletriage.localverify.ui.MainActivity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** All fixtures and exported bytes remain inside a disposable emulator. */
class SyntheticWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var store: CaseStore
    private var scenario: ActivityScenario<MainActivity>? = null
    private var caseId: String? = null
    private val fixtures = mutableListOf<File>()
    private val downloads = mutableListOf<Uri>()

    @Before fun prepare() {
        check(Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic")) { "Disposable emulator required" }
        store = CaseStore(context)
        check(store.listCaseIds().isEmpty()) { "Use a fresh emulator without existing cases" }
        device.wakeUp(); device.pressMenu()
    }
    @After fun cleanup() {
        scenario?.close()
        // Only cases created after the empty-store precondition above.
        store.listCaseIds().forEach { store.deleteCase(it) }
        fixtures.forEach { it.delete() }
        downloads.forEach { context.contentResolver.delete(it, null, null) }
        device.wakeUp(); device.pressMenu()
    }
    private fun click(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
    private fun archive(name: String = "synthetic-workflow.zip", large: Boolean = false): File {
        val file = File(context.filesDir, "exports/$name")
        fixtures.add(file); file.parentFile!!.mkdirs()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("logs/synthetic.json"))
            zip.write("""{"hostname":"triage-test.invalid"}""".toByteArray()); zip.closeEntry()
            if (large) {
                zip.putNextEntry(ZipEntry("slow.log"))
                val block = "normal synthetic activity benign.invalid\n".repeat(2000).toByteArray()
                repeat(190) { zip.write(block) }; zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("synthetic.bin")); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry()
        }
        return file
    }
    private fun launchImport(file: File, action: String = Intent.ACTION_VIEW, mime: String = "application/zip") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val incoming = Intent(action).apply {
            setDataAndType(uri, mime)
            if (action == Intent.ACTION_SEND) putExtra(Intent.EXTRA_STREAM, uri)
            setPackage(context.packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Package-scoped implicit resolution still exercises manifest filters.
        if (mime == "text/plain") incoming.setClass(context, MainActivity::class.java)
        assertNotNull(context.packageManager.resolveActivity(incoming, 0))
        scenario = ActivityScenario.launch(incoming)
        val confirmation = device.wait(Until.findObject(By.res("android:id/button1")), 10_000)
        assertNotNull("Import must wait for the user's action", confirmation)
        confirmation.click()
        compose.waitUntil(10_000) { store.listCaseIds().singleOrNull()?.also { caseId = it } != null }
    }
    private fun waitImported(file: File) {
        compose.waitUntil(15_000) { store.caseArchivePath(checkNotNull(caseId)).length() == file.length() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Start analysis").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun report(): Report = checkNotNull(store.readReport(checkNotNull(caseId)))
    private fun startAndWait() {
        click("Start analysis")
        compose.waitUntil(30_000) { runCatching { report().completed }.getOrDefault(false) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Status: Ready to review").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun viewImportDirectAnalysisRecreateAndExportWithOriginal() { exercise(Intent.ACTION_VIEW, true) }
    @Test fun shareImportAnalysisAndReportOnlyExport() { exercise(Intent.ACTION_SEND, false) }
    private fun exercise(action: String, includeOriginal: Boolean) {
        val archive = archive()
        launchImport(archive, action); waitImported(archive); startAndWait()
        val result = report()
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertNull(result.consentConfirmedAt)
        assertEquals(1, result.findings.size)
        assertEquals("structured", result.findings.single().matchType)
        assertEquals(1, result.analyzed.size)
        assertTrue(result.skipped.any { it.startsWith("synthetic.bin:") })
        compose.onNodeWithText("Findings: 1").assertDoesNotExist()
        click("Reveal results"); compose.onNodeWithText("Findings: 1").assertExists()
        scenario!!.recreate(); compose.waitForIdle()
        assertFalse("Recreation must not replay the incoming import", device.hasObject(By.text("Import shared archive?")))
        assertEquals(result.findings, report().findings)
        compose.onNodeWithText("Findings: 1").assertDoesNotExist()
        if (includeOriginal) compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        click("Export report ZIP")
        val exported = store.exportPath(caseId!!)
        compose.waitUntil(15_000) { runCatching { ZipFile(exported).use { it.getEntry("report.html") != null } }.getOrDefault(false) }
        ZipFile(exported).use { zip ->
            assertNotNull(zip.getEntry("report.json")); assertNotNull(zip.getEntry("report.html"))
            val originals = zip.entries().asSequence().filter { it.name.startsWith("original.") }.toList()
            assertEquals(if (includeOriginal) 1 else 0, originals.size)
            if (includeOriginal) assertArrayEquals(archive.readBytes(), zip.getInputStream(originals.single()).use { it.readBytes() })
        }
        device.pressBack()
    }
    @Test fun stopThenFreshRestartRetainsOriginal() {
        val archive = archive(large = true)
        launchImport(archive); waitImported(archive)
        click("Start analysis"); click("Stop")
        compose.waitUntil(15_000) { runCatching { report().errors.any { it.contains("stopped") } }.getOrDefault(false) }
        assertFalse(report().completed)
        assertArrayEquals(archive.readBytes(), store.caseArchivePath(caseId!!).readBytes())
        startAndWait()
        assertEquals(1, report().findings.size); assertEquals(2, report().analyzed.size)
        assertNull(report().consentConfirmedAt)
    }
    @Test fun backgroundAndScreenLockStopAnalysisAndAllowRestart() {
        val archive = archive(large = true)
        launchImport(archive); waitImported(archive)
        click("Start analysis"); device.pressHome()
        compose.waitUntil(15_000) { runCatching { report().errors.any { it.contains("stopped") } }.getOrDefault(false) }
        device.executeShellCommand("wm dismiss-keyguard")
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
        click("Start analysis"); device.sleep()
        compose.waitUntil(15_000) { runCatching { report().errors.any { it.contains("stopped") } }.getOrDefault(false) }
        device.wakeUp(); device.pressMenu()
        device.executeShellCommand("wm dismiss-keyguard")
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
        startAndWait(); assertEquals(1, report().findings.size)
    }
    @Test fun rotationPreservesCompletedCaseAndScreenProtection() {
        val archive = archive()
        launchImport(archive); waitImported(archive); startAndWait()
        val before = report()
        scenario!!.onActivity { activity ->
            val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse(permissions.contains("android.permission.INTERNET"))
            assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        assertEquals(before.findings, report().findings)
        assertFalse(device.hasObject(By.text("Import shared archive?")))
        scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    @Test fun unsupportedImportAndMalformedArchiveRemainExplicitFailures() {
        val file = File(context.filesDir, "exports/synthetic.txt").apply { parentFile!!.mkdirs(); writeText("synthetic") }
        fixtures.add(file); launchImport(file, mime = "text/plain")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Import failed: Unsupported archive type", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(store.caseArchivePath(caseId!!).exists())
        scenario!!.close(); scenario = null
        store.listCaseIds().forEach { store.deleteCase(it) }
        val malformed = File(context.filesDir, "exports/malformed.zip").apply { writeText("not a ZIP") }
        fixtures.add(malformed); launchImport(malformed); waitImported(malformed)
        click("Start analysis")
        compose.waitUntil(15_000) { runCatching { report().errors.isNotEmpty() }.getOrDefault(false) }
        assertFalse(report().completed); assertTrue(report().analyzed.isEmpty())
    }
    @Test fun exportFailureLeavesCaseIntactAndDoesNotLaunchSharing() {
        val archive = archive()
        launchImport(archive); waitImported(archive); startAndWait()
        val before = report()
        val partial = File(store.exportPath(caseId!!).path + ".partial").apply { mkdirs() }
        try {
            click("Export report ZIP")
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Export failed:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            assertEquals(before.findings, report().findings)
            assertFalse(store.exportPath(caseId!!).exists())
            assertEquals(context.packageName, device.currentPackageName)
        } finally { partial.delete() }
    }
    @Test fun tarGzipIntakeMatchesSyntheticRecord() {
        val file = File(context.filesDir, "exports/synthetic.tar.gz")
        file.parentFile!!.mkdirs(); fixtures.add(file)
        val payload = "triage-test.invalid".toByteArray()
        val header = ByteArray(512)
        fun field(offset: Int, value: String) { value.toByteArray().copyInto(header, offset) }
        field(0, "synthetic.log")
        field(100, "0000644")
        field(124, payload.size.toString(8).padStart(11, '0'))
        for (i in 148..155) header[i] = 32
        header[156] = 48
        field(148, header.sumOf { it.toInt() and 255 }.toString(8).padStart(6, '0') + "\u0000 ")
        java.util.zip.GZIPOutputStream(file.outputStream()).use {
            it.write(header); it.write(payload); it.write(ByteArray(512 - payload.size)); it.write(ByteArray(1024))
        }
        launchImport(file, mime = "application/gzip"); waitImported(file); startAndWait()
        assertEquals(1, report().findings.size); assertEquals(1, report().analyzed.size)
    }
    @Test fun documentPickerImportsSyntheticDownload() {
        val file = archive("synthetic-picker.zip")
        val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, file.name); put(MediaStore.Downloads.MIME_TYPE, "application/zip") }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        downloads.add(uri)
        context.contentResolver.openOutputStream(uri)!!.use { it.write(file.readBytes()) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        caseId = store.listCaseIds().single()
        click("Import bug report")
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000) || device.hasObject(By.pkg("com.android.documentsui")))
        var item = device.wait(Until.findObject(By.text(file.name)), 5000)
        if (item == null) {
            device.findObject(By.desc("Show roots"))?.click()
            device.wait(Until.findObject(By.text("Downloads")), 5000)?.click()
            item = device.wait(Until.findObject(By.text(file.name)), 5000)
        }
        assertNotNull("Synthetic download must be selectable", item); item.click()
        waitImported(file); startAndWait(); assertEquals(1, report().findings.size)
        val definitions = """{"type":"bundle","id":"synthetic-manual","objects":[{"type":"indicator","id":"synthetic-rule","pattern":"[domain-name:value = 'different.invalid']"}]}"""
        val indicatorUri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, "synthetic-definitions.json"); put(MediaStore.Downloads.MIME_TYPE, "application/json") }))
        downloads.add(indicatorUri)
        context.contentResolver.openOutputStream(indicatorUri)!!.use { it.write(definitions.toByteArray()) }
        click("Replace indicators")
        val definitionItem = device.wait(Until.findObject(By.text("synthetic-definitions.json")), 10_000)
        assertNotNull("Indicator picker must display the synthetic JSON", definitionItem); definitionItem.click()
        compose.waitUntil(15_000) { runCatching { report().indicatorVersion == "synthetic-manual" }.getOrDefault(false) }
        assertFalse(report().completed); assertTrue(report().findings.isEmpty())
        startAndWait(); assertTrue(report().findings.isEmpty()); assertNull(report().consentConfirmedAt)
    }
}
