package org.mobiletriage.localverify.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.mobiletriage.localverify.core.CoverageMatrix
import org.mobiletriage.localverify.core.IndicatorSet
import org.mobiletriage.localverify.core.Report
import java.io.File

class CaseStore(private val context: Context) {
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

    private fun caseDir(id: String): File = File(casesRoot, id)

    fun caseArchivePath(caseId: String): File = File(caseDir(caseId), "bug-report.zip")

    fun exportPath(caseId: String): File = File(exportsRoot, "${caseId}-local-verify.zip")

    fun writeReport(caseId: String, report: Report) {
        val file = File(caseDir(caseId), "report.json")
        file.parentFile?.mkdirs()
        file.writeText(report.toJson())
    }

    fun readReport(caseId: String): Report? {
        val file = File(caseDir(caseId), "report.json")
        if (!file.exists()) return null
        return Report.fromJson(file.readText())
    }

    fun writeIndicators(caseId: String, indicators: IndicatorSet) {
        val target = File(caseDir(caseId), "indicators.json")
        target.writeText(gson.toJson(indicators))
    }

    fun readIndicators(caseId: String): IndicatorSet? {
        val file = File(caseDir(caseId), "indicators.json")
        if (!file.exists()) return null
        return gson.fromJson(file.readText(), object : TypeToken<IndicatorSet>() {}.type)
    }

    fun writeCoverageMatrix(caseId: String, matrix: CoverageMatrix) {
        val target = File(caseDir(caseId), "coverage-matrix.json")
        target.writeText(gson.toJson(matrix))
    }

    fun readCoverageMatrix(caseId: String): CoverageMatrix? {
        val file = File(caseDir(caseId), "coverage-matrix.json")
        if (!file.exists()) return null
        return gson.fromJson(file.readText(), object : TypeToken<CoverageMatrix>() {}.type)
    }

    fun deleteCase(caseId: String) {
        caseDir(caseId).deleteRecursively()
    }
}
