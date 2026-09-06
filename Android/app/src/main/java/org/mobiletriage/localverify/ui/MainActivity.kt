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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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

    private var status by mutableStateOf("Initializing")
    private var progress by mutableStateOf("")
    private var findingCount by mutableStateOf(0)
    private var analyzedCount by mutableStateOf(0)
    private var hasArchive by mutableStateOf(false)
    private var hasIndicators by mutableStateOf(false)
    private var archiveName by mutableStateOf("")
    private var isRunning by mutableStateOf(false)
    private var consent by mutableStateOf(false)
    private var includeOriginal by mutableStateOf(true)

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
        store = CaseStore(this)
        caseId = if (store.listCaseIds().isEmpty()) {
            store.createCase().also { initializeCase(it) }
        } else {
            store.listCaseIds().firstOrNull() ?: store.createCase().also { initializeCase(it) }
        }
        ensureBundledIndicators()
        refreshStateFromDisk()
        handleIncomingIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.padding(12.dp)) {
                    AppScreen(
                        status = status,
                        progress = progress,
                        findingCount = findingCount,
                        analyzedCount = analyzedCount,
                        hasArchive = hasArchive,
                        hasIndicators = hasIndicators,
                        archiveName = archiveName,
                        isRunning = isRunning,
                        consent = consent,
                        includeOriginal = includeOriginal,
                        manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault()),
                        onConsentChanged = { consent = it },
                        onIncludeOriginalChanged = { includeOriginal = it },
                        onPickArchive = { archivePicker.launch((ArchivePolicy.supportedArchiveMimeTypes + "*/*").toTypedArray()) },
                        onPickIndicators = { indicatorsPicker.launch(arrayOf("application/json", "*/*")) },
                        onStart = { if (consent) startAnalysis() else showToast("Consent is required") },
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
        super.onStop()
        stopAnalysis(persisted = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAnalysis(persisted = false)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.data
                ?: intent.clipData?.getItemAt(0)?.uri
            if (uri != null) importArchive(uri)
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
        status = if (isRunning) "Running" else report.status
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
                report.indicatorSHA256 = "bundled"
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
        val name = resolveDisplayName(uri)
        val mimeType = (contentResolver.getType(uri) ?: "").lowercase(Locale.getDefault())
        val looksArchive = ArchivePolicy.isSupportedArchive(name, mimeType)
        if (!looksArchive) {
            runOnUiThread {
                status = "Unsupported archive type; import .zip/.gz/.tgz/.tar.gz"
                val details = if (name != null) "filename=$name" else "uri type=$mimeType"
                showToast("Unsupported archive type ($details)")
            }
            return
        }
        runOnUiThread { status = "Importing archive" }
        thread {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    ArchiveUtil.copyToPrivate(input, store.caseArchivePath(caseId))
                } ?: throw IllegalStateException("Cannot open archive")
                runOnUiThread {
                    hasArchive = true
                    archiveName = name ?: "bug-report.zip"
                    status = "Archive imported"
                    refreshStateFromDisk()
                }
            } catch (error: Exception) {
                runOnUiThread {
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
        val name = DocumentFile.fromSingleUri(this, uri)?.name ?: "indicators.json"
        if (!name.lowercase(Locale.getDefault()).endsWith(".json")) {
            runOnUiThread {
                status = "Indicators import requires a .json STIX bundle"
                showToast("Indicators import requires a .json STIX bundle")
            }
            return
        }

        thread {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    val parsed = IndicatorParser.parse(bytes)
                    store.writeIndicators(caseId, parsed)
                    writeReportSnapshot { report ->
                        report.indicatorVersion = parsed.version
                        report.indicatorSources = parsed.sources
                        report.indicatorSHA256 = "imported-${UUID.randomUUID()}"
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
                            "interrupted-job checkpointing",
                        ),
                        unsupportedSections = listOf("binary payloads", "unsupported archive extensions", "oversized entries")
                    )
                    store.writeCoverageMatrix(caseId, matrix)
                    runOnUiThread {
                        hasIndicators = true
                        status = "Indicators imported"
                        refreshStateFromDisk()
                    }
                } ?: throw IllegalStateException("Cannot open indicators")
            } catch (error: Exception) {
                runOnUiThread {
                    status = "Indicator import failed: ${error.message}"
                    showToast(status)
                }
            }
        }
    }

    private fun startAnalysis() {
        if (isRunning) return
        val archive = store.caseArchivePath(caseId)
        val indicators = store.readIndicators(caseId)
        val report = currentReport()

        if (!archive.exists() || indicators == null) {
            showToast("Import archive and indicators first")
            return
        }

        if (report.completed && report.errors.isEmpty() && report.findings.isNotEmpty()) {
            showToast("Case already complete")
            return
        }

        if (report.consentConfirmedAt == null) {
            writeReportSnapshot { checkpoint ->
                checkpoint.consentConfirmedAt = System.currentTimeMillis()
            }
        }

        isRunning = true
        analysisThread = thread(start = true) {
            try {
                runOnUiThread { status = "Running" }
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
                runOnUiThread {
                    isRunning = false
                    analysisThread = null
                    status = error.message ?: "Analysis failed"
                    progress = ""
                    refreshStateFromDisk()
                }
            }
        }
    }

    private fun stopAnalysis(persisted: Boolean = true) {
        if (!isRunning) return
        isRunning = false
        analysisThread?.interrupt()
        if (persisted) {
            showToast("Stopping analysis")
        }
    }

    private fun exportReport() {
        if (!isRunning && findingCount == 0 && analyzedCount == 0) {
            showToast("Run analysis before exporting")
            return
        }
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
                runOnUiThread { startActivity(Intent.createChooser(share, "Share report")) }
            } catch (error: Exception) {
                runOnUiThread { showToast(error.message ?: "Export failed") }
            }
        }
    }

    private fun deleteCase() {
        stopAnalysis()
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
    analyzedCount: Int,
    hasArchive: Boolean,
    hasIndicators: Boolean,
    archiveName: String,
    isRunning: Boolean,
    consent: Boolean,
    includeOriginal: Boolean,
    manufacturer: String,
    onConsentChanged: (Boolean) -> Unit,
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Button(onClick = onPickArchive) { Text(if (hasArchive) "Replace bug report" else "Import bug report") }
            Button(onClick = onPickIndicators) { Text(if (hasIndicators) "Replace indicators" else "Import indicators") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Archive: ${if (archiveName.isBlank()) "not imported" else archiveName}")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = onConsentChanged)
            Text("I consent to local processing")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = includeOriginal, onCheckedChange = onIncludeOriginalChanged)
            Text("Include original archive in ZIP export")
        }

        Text("Findings: $findingCount")
        Text("Analyzed paths: $analyzedCount")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !isRunning) { Text("Start / Resume") }
            Button(onClick = onStop, enabled = isRunning) { Text("Stop") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport) { Text("Export report ZIP") }
            Button(onClick = onCoverage) { Text("Coverage") }
            Button(onClick = onDelete) { Text("Delete case") }
            Button(onClick = onRefresh) { Text("Reload") }
        }

        if (isRunning) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("Analysis running")
            }
        }
    }
}
