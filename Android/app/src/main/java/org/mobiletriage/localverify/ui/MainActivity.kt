package org.mobiletriage.localverify.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import org.mobiletriage.localverify.core.ArchiveUtil
import org.mobiletriage.localverify.core.ArchivePolicy
import org.mobiletriage.localverify.core.CoverageMatrix
import org.mobiletriage.localverify.core.Exporter
import org.mobiletriage.localverify.core.IndicatorParser
import org.mobiletriage.localverify.core.IndicatorSet
import org.mobiletriage.localverify.core.Report
import org.mobiletriage.localverify.core.TriageAnalyzer
import org.mobiletriage.localverify.storage.CaseStore
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var store: CaseStore
    private var caseId: String = ""
    private var analysisThread: Thread? = null
    private var importDialog: android.app.AlertDialog? = null

    private var resultsVisible by mutableStateOf(false)
    private var ioBusy by mutableStateOf(false)
    private var status by mutableStateOf("Initializing")
    private var progress by mutableStateOf("")
    private var findingCount by mutableStateOf(0)
    private var analyzedCount by mutableStateOf(0)
    private var hasArchive by mutableStateOf(false)
    private var hasIndicators by mutableStateOf(false)
    private var archiveName by mutableStateOf("")
    private var isRunning by mutableStateOf(false)
    private var includeOriginal by mutableStateOf(false)

    private val archivePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        importArchive(uri)
    }

    private val indicatorsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        importIndicators(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 31) window.setHideOverlayWindows(true)
        store = CaseStore(this)
        caseId = if (store.listCaseIds().isEmpty()) {
            store.createCase().also { initializeCase(it) }
        } else {
            store.listCaseIds().firstOrNull() ?: store.createCase().also { initializeCase(it) }
        }
        ensureBundledIndicators()
        refreshStateFromDisk()
        if (savedInstanceState == null) handleIncomingIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.padding(12.dp)) {
                    AppScreen(
                        status = status,
                        progress = progress,
                        findingCount = findingCount,
                        resultsVisible = resultsVisible,
                        onReveal = { resultsVisible = true; refreshStateFromDisk() },
                        busy = ioBusy || analysisThread != null,
                        analyzedCount = analyzedCount,
                        hasArchive = hasArchive,
                        hasIndicators = hasIndicators,
                        archiveName = archiveName,
                        isRunning = isRunning,
                        includeOriginal = includeOriginal,
                        manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault()),
                        onIncludeOriginalChanged = { includeOriginal = it },
                        onPickArchive = { archivePicker.launch((ArchivePolicy.supportedArchiveMimeTypes + "*/*").toTypedArray()) },
                        onPickIndicators = { indicatorsPicker.launch(arrayOf("application/json", "*/*")) },
                        onStart = { startAnalysis() },
                        onStop = { stopAnalysis() },
                        onExport = { exportReport() },
                        onDelete = { deleteCase() },
                        onCoverage = { showCoverage() },
                        onRefresh = { refreshStateFromDisk() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStop() {
        resultsVisible = false
        status = "Results hidden"
        super.onStop()
        stopAnalysis(persisted = false)
    }

    override fun onDestroy() {
        importDialog?.dismiss()
        super.onDestroy()
        stopAnalysis(persisted = false)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.data
                ?: intent.clipData?.getItemAt(0)?.uri
            if (uri != null && uri.scheme == "content" && !ioBusy && analysisThread == null) {
                importDialog?.dismiss()
                importDialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Import shared archive?")
                    .setMessage("This replaces the current case input and resets its analysis.")
                    .setPositiveButton("Import") { _, _ -> importArchive(uri) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun initializeCase(id: String) {
        store.writeReport(id, Report(caseID = id, indicatorVersion = IndicatorSet.demo.version))
    }

    private fun refreshStateFromDisk() {
        val report = store.readReport(caseId) ?: return
        hasArchive = store.caseArchivePath(caseId).exists()
        hasIndicators = store.readIndicators(caseId) != null
        findingCount = report.findings.size
        analyzedCount = report.analyzed.size
        archiveName = if (hasArchive) store.caseArchivePath(caseId).name else ""
        status = if (isRunning) "Running" else if (resultsVisible) report.status else if (report.completed && report.errors.isEmpty()) "Ready to review" else "Analysis incomplete"
        progress = ""
    }

    private fun ensureBundledIndicators() {
        if (store.readIndicators(caseId) == null) {
            val data = assets.open("bundled-indicators.stix2").readBytes()
            val parsed = IndicatorParser.parse(data)
            store.writeIndicators(caseId, parsed)
            writeReportSnapshot { report ->
                report.indicatorVersion = parsed.version
                report.indicatorSources = parsed.sources
                report.indicatorSHA256 = store.indicatorDigest(caseId)
                report.indicatorsCheckedAt = parsed.checkedAt
            }
        }
        hasIndicators = true
    }

    private fun currentReport(): Report = store.readReport(caseId) ?: Report(caseID = caseId, indicatorVersion = IndicatorSet.demo.version)

    private fun writeReportSnapshot(update: (Report) -> Unit) {
        val report = currentReport()
        update(report)
        store.writeReport(caseId, report)
    }

    private fun importArchive(uri: Uri) {
        if (ioBusy || analysisThread != null || uri.scheme != "content") return
        ioBusy = true; resultsVisible = false
        status = "Importing archive"
        thread {
            try {
                val name = resolveDisplayName(uri)?.take(1024)
                val mimeType = (contentResolver.getType(uri) ?: "").lowercase(Locale.ROOT)
                require(ArchivePolicy.isSupportedArchive(name, mimeType)) { "Unsupported archive type" }
                contentResolver.openInputStream(uri)?.use { input ->
                    val destination = store.caseArchivePath(caseId)
                    val partial = java.io.File(destination.path + ".partial")
                    try {
                        ArchiveUtil.copyToPrivate(input, partial)
                        // Invalidate old progress before replacing its input.
                        val set = store.readIndicators(caseId) ?: IndicatorSet.demo
                        store.writeReport(caseId, Report(caseID = caseId, indicatorVersion = set.version,
                            indicatorSHA256 = store.indicatorDigest(caseId)))
                        java.nio.file.Files.move(partial.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        store.exportPath(caseId).delete()
                    } finally { partial.delete() }
                } ?: throw IllegalStateException("Cannot open archive")
                runOnUiThread {
                    ioBusy = false
                    hasArchive = true
                    archiveName = name ?: "bug-report.zip"
                    status = "Archive imported"
                    refreshStateFromDisk()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    ioBusy = false
                    status = "Import failed: ${error.message}"
                    showToast(status)
                }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val documentName = DocumentFile.fromSingleUri(this, uri)?.name
        if (!documentName.isNullOrBlank()) return documentName

        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                val fromCursor = cursor.getString(nameIndex)
                if (!fromCursor.isNullOrBlank()) return fromCursor
            }
        }

        return uri.lastPathSegment
    }

    private fun importIndicators(uri: Uri) {
        if (ioBusy || analysisThread != null || uri.scheme != "content") return
        ioBusy = true; resultsVisible = false
        thread {
            try {
                val name = DocumentFile.fromSingleUri(this, uri)?.name ?: "indicators.json"
                require(name.lowercase(Locale.ROOT).endsWith(".json")) { "Indicators import requires a .json STIX bundle" }
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = org.mobiletriage.localverify.core.InputLimits.read(input, 5 * 1024 * 1024)
                    val parsed = IndicatorParser.parse(bytes)
                    store.writeReport(caseId, Report(caseID = caseId, indicatorVersion = parsed.version))
                    store.writeIndicators(caseId, parsed)
                    store.exportPath(caseId).delete()
                    writeReportSnapshot { report ->
                        report.indicatorVersion = parsed.version
                        report.indicatorSources = parsed.sources
                        report.indicatorSHA256 = store.indicatorDigest(caseId)
                        report.indicatorsCheckedAt = parsed.checkedAt
                        report.skipped = parsed.unsupported.toMutableList()
                        report.errors.clear()
                    }
                    val matrix = CoverageMatrix(
                        caseId = caseId,
                        checks = listOf(
                            "tar.gz stream checks",
                            "structured field recognition",
                            "tokenized raw-text matching",
                            "incomplete-result persistence; restart from beginning",
                        ),
                        unsupportedSections = listOf("binary payloads", "unsupported archive extensions", "oversized entries")
                    )
                    store.writeCoverageMatrix(caseId, matrix)
                    runOnUiThread {
                        ioBusy = false
                        hasIndicators = true
                        status = "Indicators imported"
                        refreshStateFromDisk()
                    }
                } ?: throw IllegalStateException("Cannot open indicators")
            } catch (error: Exception) {
                runOnUiThread {
                    ioBusy = false
                    status = "Indicator import failed: ${error.message}"
                    showToast(status)
                }
            }
        }
    }

    private fun startAnalysis() {
        if (isRunning || ioBusy || analysisThread != null) return
        val archive = store.caseArchivePath(caseId)
        val indicators = store.readIndicators(caseId)
        val report = currentReport()

        if (!archive.exists() || indicators == null) {
            showToast("Import archive and indicators first")
            return
        }

        if (report.completed && report.errors.isEmpty()) {
            showToast("Case already complete")
            return
        }

        resultsVisible = false
        isRunning = true
        analysisThread = thread(start = true) {
            try {
                runOnUiThread { status = "Running" }
                check(store.indicatorDigest(caseId) == report.indicatorSHA256) { "Indicator snapshot changed or legacy snapshot is unverified; re-import indicators" }
                val updated = TriageAnalyzer.analyze(
                    archive = archive,
                    indicators = indicators,
                    report = report,
                    isCancelled = { !isRunning },
                    onProgress = { detail -> runOnUiThread { progress = detail } },
                    onCheckpoint = { checkpoint ->
                        store.writeReport(caseId, checkpoint)
                        runOnUiThread { refreshStateFromDisk() }
                    }
                )
                store.writeReport(caseId, updated)
                runOnUiThread {
                    isRunning = false
                    analysisThread = null
                    status = updated.status
                    progress = ""
                    refreshStateFromDisk()
                }
            } catch (error: Exception) {
                report.completed = false
                report.analysisFinishedAt = null
                report.errors.add(error.message ?: "Analysis failed")
                runCatching { store.writeReport(caseId, report) }
                runOnUiThread {
                    isRunning = false
                    analysisThread = null
                    refreshStateFromDisk()
                    status = "Analysis incomplete: ${error.message ?: "Analysis failed"}"
                    progress = ""
                }
            }
        }
    }

    private fun stopAnalysis(persisted: Boolean = true) {
        if (!isRunning) return
        isRunning = false
        analysisThread?.interrupt()
        if (persisted) {
            showToast("Stopping analysis. Start again to scan from the beginning.")
        }
    }

    private fun exportReport() {
        if (ioBusy || analysisThread != null) return
        if (!isRunning && findingCount == 0 && analyzedCount == 0) {
            showToast("Run analysis before exporting")
            return
        }
        ioBusy = true
        thread {
            try {
                val report = currentReport()
                val destination = store.exportPath(caseId)
                val original = if (includeOriginal) store.caseArchivePath(caseId) else null
                if (includeOriginal && original != null && !original.exists()) {
                    runOnUiThread { showToast("Original archive unavailable; exporting report only") }
                }
                Exporter.writeExportZip(report, original?.takeIf { it.exists() }, destination)
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", destination)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { ioBusy = false; startActivity(Intent.createChooser(share, "Share report")) }
            } catch (error: Exception) {
                runOnUiThread { ioBusy = false; status = "Export failed: ${error.message ?: "Could not write export"}" }
            }
        }
    }

    private fun deleteCase() {
        if (ioBusy || analysisThread != null) return
        resultsVisible = false
        store.deleteCase(caseId)
        caseId = store.createCase().also { initializeCase(it) }
        ensureBundledIndicators()
        refreshStateFromDisk()
        status = "Case deleted"
    }

    private fun showCoverage() {
        val matrix = store.readCoverageMatrix(caseId)
        val message = matrix?.let {
            "Checks: ${it.checks.joinToString(", ")}\nUnsupported: ${it.unsupportedSections.joinToString(", ")}"
        } ?: "No coverage matrix"
        showToast(message)
    }

    private fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
private fun AppScreen(
    status: String,
    progress: String,
    findingCount: Int,
    resultsVisible: Boolean,
    onReveal: () -> Unit,
    busy: Boolean,
    analyzedCount: Int,
    hasArchive: Boolean,
    hasIndicators: Boolean,
    archiveName: String,
    isRunning: Boolean,
    includeOriginal: Boolean,
    manufacturer: String,
    onIncludeOriginalChanged: (Boolean) -> Unit,
    onPickArchive: () -> Unit,
    onPickIndicators: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCoverage: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Local Verify (Android)", style = MaterialTheme.typography.headlineMedium)
        Text("Status: $status")
        if (progress.isNotBlank()) Text("$progress")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Collection guide (bug report)")
                Text("1) Open Developer options.")
                Text("2) Create a bug report ZIP and save it.")
                Text("3) Share/import ZIP into this app with a private storage destination.")
                if (manufacturer.contains("samsung")) {
                    Text("Manufacturer note: Samsung may label this as \"Full bug report\".")
                } else {
                    Text("Vendor naming and menu location differ by manufacturer.")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickArchive, enabled = !busy) { Text(if (hasArchive) "Replace bug report" else "Import bug report") }
            Button(onClick = onPickIndicators, enabled = !busy) { Text(if (hasIndicators) "Replace indicators" else "Import indicators") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Archive: ${if (archiveName.isBlank()) "not imported" else archiveName}")
        }

        Text("Analysis runs locally on this device. Use Export report ZIP to share a report when you choose.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = includeOriginal, onCheckedChange = onIncludeOriginalChanged)
            Text("Include original archive in ZIP export")
        }

        if (resultsVisible) Text("Findings: $findingCount")
        else Button(onClick = onReveal, enabled = !busy) { Text("Reveal results") }
        Text("Analyzed paths: $analyzedCount")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !busy) { Text("Start analysis") }
            Button(onClick = onStop, enabled = isRunning) { Text("Stop") }
        }

        Text("Keep this app open while analyzing. Switching apps or locking the screen stops analysis. Start again to scan from the beginning; the imported archive stays on this device.")
        Text("Stopped analyses are incomplete. Starting again replaces their partial results.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, enabled = !busy) { Text("Export report ZIP") }
            Button(onClick = onCoverage) { Text("Coverage") }
            Button(onClick = onDelete, enabled = !busy) { Text("Delete case") }
            Button(onClick = onRefresh, enabled = !busy) { Text("Reload") }
        }

        if (isRunning) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("Analysis running")
            }
        }
    }
}
