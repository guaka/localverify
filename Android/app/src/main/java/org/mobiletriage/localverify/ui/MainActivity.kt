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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import org.mobiletriage.localverify.R
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
    private var displayedReport by mutableStateOf<Report?>(null)
    private var displayedIndicators by mutableStateOf<IndicatorSet?>(null)
    private var cases by mutableStateOf<List<Report>>(emptyList())

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
            LocalVerifyTheme {
                Surface(modifier = Modifier.padding(12.dp)) {
                    AppScreen(
                        report = displayedReport,
                        indicators = displayedIndicators,
                        cases = cases,
                        onSelectCase = { id ->
                            if (!ioBusy && analysisThread == null) {
                                caseId = id
                                resultsVisible = false
                                includeOriginal = false
                                refreshStateFromDisk()
                            }
                        },
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
                    .setMessage("This imports the archive as a new case on this device.")
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
        displayedReport = report
        displayedIndicators = store.readIndicators(caseId)
        cases = store.listCaseIds().mapNotNull { store.readReport(it) }
            .filter { store.caseArchivePath(it.caseID).exists() }.sortedByDescending { it.createdAt }
        hasArchive = store.caseArchivePath(caseId).exists()
        hasIndicators = store.readIndicators(caseId) != null
        findingCount = report.findings.size
        analyzedCount = report.analyzed.size
        archiveName = if (hasArchive) report.sysdiagnoseFilename ?: "Filename not recorded for this older case" else ""
        status = if (isRunning) "Running" else if (resultsVisible) report.status else if (report.completed && report.errors.isEmpty()) "Ready to review" else "Analysis incomplete"
        if (!isRunning) progress = ""
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
        val previousCaseId = caseId
        thread {
            var importedCaseId: String? = null
            try {
                val name = resolveDisplayName(uri)?.take(1024)
                val mimeType = (contentResolver.getType(uri) ?: "").lowercase(Locale.ROOT)
                require(ArchivePolicy.isSupportedArchive(name, mimeType)) { "Unsupported archive type" }
                val newCaseId = store.createCase()
                importedCaseId = newCaseId
                val set = store.readIndicators(previousCaseId) ?: IndicatorSet.demo
                store.writeIndicators(newCaseId, set)
                contentResolver.openInputStream(uri)?.use { input ->
                    val destination = store.caseArchivePath(newCaseId)
                    val partial = java.io.File(destination.path + ".partial")
                    try {
                        ArchiveUtil.copyToPrivate(input, partial)
                        // Invalidate old progress before replacing its input.
                        store.writeReport(newCaseId, Report(caseID = newCaseId, indicatorVersion = set.version,
                            sysdiagnoseFilename = name,
                            indicatorSources = set.sources, indicatorsCheckedAt = set.checkedAt,
                            indicatorSHA256 = store.indicatorDigest(newCaseId)))
                        java.nio.file.Files.move(partial.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } finally { partial.delete() }
                } ?: throw IllegalStateException("Cannot open archive")
                runOnUiThread {
                    caseId = newCaseId
                    ioBusy = false
                    hasArchive = true
                    archiveName = name ?: "bug-report.zip"
                    status = "Archive imported"
                    refreshStateFromDisk()
                }
            } catch (error: Exception) {
                importedCaseId?.let { store.deleteCase(it) }
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
    report: Report?,
    indicators: IndicatorSet?,
    cases: List<Report>,
    onSelectCase: (String) -> Unit,
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
    var pane by rememberSaveable { mutableStateOf("Scan") }
    var confirmDelete by remember { mutableStateOf(false) }
    var coverageExpanded by rememberSaveable(report?.caseID) { mutableStateOf(false) }
    val panes = listOf("Scan", "Cases", "Indicators", "About")
    val paneIcons = listOf(R.drawable.ic_scan, R.drawable.ic_cases, R.drawable.ic_indicators, R.drawable.ic_about)
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    }
    Scaffold(
        bottomBar = {
            Column {
                if (busy) {
                    Surface(tonalElevation = 3.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text(if (isRunning) "Analyzing diagnostics" else "Working on this device", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(progress.ifBlank { if (isRunning) "Preparing analysis" else status },
                                modifier = Modifier.fillMaxWidth().height(48.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (isRunning) TextButton(onClick = onStop) { Text("Stop analysis") }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (pane == "Scan") {
                            if (hasArchive && report?.completed != true) {
                                Button(onClick = onStart, enabled = hasIndicators, modifier = Modifier.fillMaxWidth()) { Text("Analyze bug report") }
                            } else if (hasArchive) {
                                Button(onClick = { pane = "Cases" }, modifier = Modifier.fillMaxWidth()) { Text("Review case") }
                            }
                            TextButton(onClick = onPickArchive, modifier = Modifier.fillMaxWidth()) { Text("Import bug report") }
                        }
                        if (pane == "Cases" && hasArchive) {
                            if (!resultsVisible) {
                                Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("Reveal results") }
                            } else if (report?.analysisStartedAt != null) {
                                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export report ZIP") }
                            }
                            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete case") }
                        }
                    }
                }
                NavigationBar {
                    panes.forEachIndexed { index, title ->
                        NavigationBarItem(selected = pane == title, onClick = { pane = title },
                            icon = { Icon(painterResource(paneIcons[index]), contentDescription = null) }, label = { Text(title) })
                    }
                }
            }
        }
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(pane, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 12.dp)) }
            when (pane) {
                "Scan" -> {
                    item { InfoCard("Private, on-device analysis", "Import a complete bug report ZIP. Each import creates a new case; your previous cases remain in Cases.") }
                    item { InfoCard("Collect a bug report", "1. Open Settings > System > Developer options.\n2. Choose Bug report and wait for collection to finish.\n3. Save the completed ZIP on this phone, then import it here.\n\nKeep the archive intact; you do not need to select the files inside it.") }
                    if (hasArchive) item { InfoCard("Selected bug report", archiveName) }
                    item { InfoCard("Analysis", if (busy) "Keep this app open while analyzing. Switching apps or locking the screen stops analysis." else status) }
                    item { Text("An interrupted analysis starts again from the beginning. Its partial results are replaced. Analysis stays on this device.", style = MaterialTheme.typography.bodySmall) }
                }
                "Cases" -> {
                    if (cases.isEmpty()) item { InfoCard("No cases yet", "Import a bug report in Scan to begin.") }
                    items(cases, key = { "case-" + it.caseID }) { saved ->
                        Card(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onSelectCase(saved.caseID) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(saved.sysdiagnoseFilename ?: "Older case: filename not recorded")
                                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(saved.createdAt)), style = MaterialTheme.typography.bodySmall)
                                    Text(if (saved.caseID == report?.caseID) "Selected case" else "Open case", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    if (hasArchive && report != null) {
                        item { InfoCard("Bug report", archiveName) }
                        if (!resultsVisible) item { InfoCard("Results hidden", "Reveal results when you are ready to review this case privately.") }
                        else {
                            item { InfoCard(if (report.analysisStartedAt == null) "Not analyzed yet" else report.status,
                                "${report.findings.size} matches for review in ${report.analyzed.size} checked files.\n\nFindings are leads, not proof of compromise. No matches does not establish that a device is uncompromised.") }
                            item { InfoCard("Analysis details", "Started: ${formatTime(report.analysisStartedAt)}\nFinished: ${formatTime(report.analysisFinishedAt)}\n\nSHA-256: ${report.archiveSHA256.ifBlank { "Not calculated yet" }}\n\nDefinitions: ${report.indicatorVersion}") }
                            items(report.findings) { finding ->
                                InfoCard(finding.value, "${finding.campaigns?.joinToString(", ") ?: "Uncategorized"}\n${finding.matchType} | ${finding.rule}\n${finding.source} | ${finding.record}\n\n${finding.explanation}\n\n${finding.excerpt}")
                            }
                            item { InfoCard("Coverage", "${report.analyzed.size} files checked\n${report.skipped.size} skipped items or limitations\n${report.errors.size} errors\n\nOnly supported evidence and indicator patterns are checked. Binary, unsupported, or oversized content may not be analyzed.") }
                            items(report.skipped) { InfoCard("Skipped or unsupported", it) }
                            items(report.errors) { InfoCard("Analysis error", it) }
                            item { TextButton(onClick = { coverageExpanded = !coverageExpanded }) { Text(if (coverageExpanded) "Hide checked files" else "Show checked files (${report.analyzed.size})") } }
                            if (coverageExpanded) items(report.analyzed) { Text(it, style = MaterialTheme.typography.bodySmall) }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = includeOriginal, onCheckedChange = onIncludeOriginalChanged, enabled = !busy)
                                    Text("Include original sensitive archive in export", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
                "Indicators" -> {
                    item { InfoCard("Threat definitions", "These are known indicators used to check the evidence, such as suspicious domains and file names. Definitions are loaded locally; no download is required.") }
                    item { InfoCard("Definitions for the selected case", "${indicators?.indicators?.size ?: 0} supported indicators\n${indicators?.unsupported?.size ?: 0} unsupported patterns\n\n${indicators?.version ?: "Not available"}") }
                    items(indicators?.sources.orEmpty()) { Text(it, style = MaterialTheme.typography.bodySmall) }
                    item { Text("Unsupported patterns", style = MaterialTheme.typography.titleMedium) }
                    if (indicators?.unsupported.isNullOrEmpty()) item { Text("No unsupported patterns recorded.") }
                    items(indicators?.unsupported.orEmpty()) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                "About" -> {
                    item { InfoCard("Local Verify", "Private, on-device diagnostic verification for investigators.\n\nVersion $appVersion") }
                    item {
                        TextButton(onClick = { uriHandler.openUri("https://github.com/guaka/LocalVerify") }) {
                            Text("View Local Verify on GitHub")
                        }
                    }
                    item { InfoCard("Privacy", "Analysis stays on this device.\n\nNo uploads or telemetry. Case files are protected and excluded from automatic backup. Data leaves Local Verify only when you choose to share an export.\n\nCopies you saved in Files or previously shared remain separate from a Local Verify case.") }
                    item { InfoCard("Experimental coverage", "Findings are leads for review, not proof of compromise. No matches does not establish that a device is uncompromised.\n\nOnly supported patterns in the installed definitions are checked; unsupported patterns are listed in Indicators.") }
                    item { InfoCard("Indicator sources", "Amnesty International - Pegasus and Predator/Cytrox. Unmodified source bundles, licensed CC BY 2.0. The app uses only supported patterns.\n\nSource: https://github.com/AmnestyTech/investigations\nLicense: https://creativecommons.org/licenses/by/2.0/\n\nMVT contributors - expanded Predator, Coruna and DarkSword collections, compiled from published research. MIT license; source references and license text accompany the bundled files.\n\nSource and license: https://github.com/mvt-project/mvt-indicators\n\nIndicator references are shown as text. The GitHub button opens the project in your browser.") }
                    item { InfoCard("Legal", "GNU AGPL v3 or later. Source and third-party notices accompany private builds.") }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this case permanently?") },
        text = { Text("The case and its local export will be removed. Copies saved or shared elsewhere remain.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

private fun formatTime(value: Long?): String = value?.let {
    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))
} ?: "Not recorded"

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(body, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
