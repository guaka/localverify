package org.mobiletriage.localverify.storage

import android.content.Context
import android.util.AtomicFile
import org.mobiletriage.localverify.core.ArchiveUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.mobiletriage.localverify.core.CoverageMatrix
import org.mobiletriage.localverify.core.IndicatorSet
import org.mobiletriage.localverify.core.Report
import java.io.File

class CaseStore(private val context: Context) {
    companion object { private val storageLock = Any() }
    private val gson = Gson()

    private val casesRoot = File(context.filesDir, "cases").apply { mkdirs() }
    private val exportsRoot = File(context.filesDir, "exports").apply { mkdirs() }

    fun listCaseIds(): List<String> = casesRoot
        .listFiles()
        ?.filter { it.isDirectory }
        ?.map { it.name }
        ?: emptyList()

    fun createCase(): String {
        val id = "case-" + System.currentTimeMillis().toString(16) + "-" + (0..9999).random().toString(16)
        File(casesRoot, id).mkdirs()
        return id
    }

    private fun caseDir(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,100}"))) { "Invalid case identifier" }
        return File(casesRoot, id)
    }

    private fun atomicWrite(file: File, text: String) = synchronized(storageLock) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 64 * 1024 * 1024) { "Stored record exceeds limit" }
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }

    private fun read(file: File): String = synchronized(storageLock) {
        AtomicFile(file).openRead().use { input ->
            val bytes = org.mobiletriage.localverify.core.InputLimits.read(input, 64 * 1024 * 1024)
            require(bytes.size <= 64 * 1024 * 1024) { "Stored record exceeds limit" }
            return bytes.toString(Charsets.UTF_8)
        }
    }

    fun indicatorDigest(caseId: String): String = ArchiveUtil.hashFile(File(caseDir(caseId), "indicators.json"))

    fun caseArchivePath(caseId: String): File = File(caseDir(caseId), "bug-report.zip")

    fun exportPath(caseId: String): File = File(exportsRoot, "${caseId}-local-verify.zip")

    fun writeReport(caseId: String, report: Report) {
        val file = File(caseDir(caseId), "report.json")
        file.parentFile?.mkdirs()
        require(report.caseID == caseId && report.schemaVersion == 1) { "Invalid checkpoint identity" }
        atomicWrite(file, report.toJson())
    }

    fun readReport(caseId: String): Report? {
        val file = File(caseDir(caseId), "report.json")
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return Report.fromJson(read(file)).also { require(it.caseID == caseId && it.schemaVersion == 1) { "Invalid checkpoint identity" } }
    }

    fun writeIndicators(caseId: String, indicators: IndicatorSet) {
        val target = File(caseDir(caseId), "indicators.json")
        atomicWrite(target, gson.toJson(indicators))
    }

    fun readIndicators(caseId: String): IndicatorSet? {
        val file = File(caseDir(caseId), "indicators.json")
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return gson.fromJson(read(file), object : TypeToken<IndicatorSet>() {}.type)
    }

    fun writeCoverageMatrix(caseId: String, matrix: CoverageMatrix) {
        val target = File(caseDir(caseId), "coverage-matrix.json")
        atomicWrite(target, gson.toJson(matrix))
    }

    fun readCoverageMatrix(caseId: String): CoverageMatrix? {
        val file = File(caseDir(caseId), "coverage-matrix.json")
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return gson.fromJson(read(file), object : TypeToken<CoverageMatrix>() {}.type)
    }

    fun deleteCase(caseId: String) {
        caseDir(caseId).deleteRecursively()
        exportPath(caseId).delete()
    }
}
