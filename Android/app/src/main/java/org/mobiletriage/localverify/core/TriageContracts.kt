package org.mobiletriage.localverify.core

import com.google.gson.Gson

const val MAX_FINDINGS = 10_000

data class Indicator(
    val id: String,
    val kind: String,
    val value: String,
    val campaigns: List<String>? = null,
)

data class IndicatorSet(
    val version: String,
    val indicators: List<Indicator>,
    val unsupported: List<String>,
    val sources: List<String>? = null,
    val checkedAt: Long? = null,
    val latestIndicatorDate: Long? = null,
    val byteCount: Int? = null,
) {
    companion object {
        val demo = IndicatorSet(
            version = "demo-1 — NOT threat intelligence",
            indicators = listOf(
                Indicator(
                    id = "demo-domain",
                    kind = "domain-name:value",
                    value = "triage-test.invalid",
                    campaigns = listOf("Demo")
                )
            ),
            unsupported = emptyList()
        )
    }
}

data class Finding(
    val id: String,
    val rule: String,
    val value: String,
    val source: String,
    val record: String,
    val timestamp: String?,
    val matchType: String,
    val explanation: String,
    val excerpt: String,
    val campaigns: List<String>? = null,
)

data class Report(
    val schemaVersion: Int = 1,
    val engineVersion: String = "0.3.0-android-native",
    val platform: String = "android",
    val caseID: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sysdiagnoseFilename: String? = null,
    var analysisStartedAt: Long? = null,
    var analysisFinishedAt: Long? = null,
    var archiveSHA256: String = "",
    var indicatorVersion: String,
    var indicatorSHA256: String = "",
    var indicatorSources: List<String>? = null,
    var indicatorsCheckedAt: Long? = null,
    var consentConfirmedAt: Long? = null,
    var completed: Boolean = false,
    var findings: MutableList<Finding> = mutableListOf(),
    var analyzed: MutableList<String> = mutableListOf(),
    var skipped: MutableList<String> = mutableListOf(),
    var errors: MutableList<String> = mutableListOf(),
) {
    val status: String
        get() = when {
            !completed || errors.isNotEmpty() -> "Analysis incomplete"
            findings.isEmpty() -> "No matches in analyzed evidence"
            findings.all { it.matchType == "raw-text" } -> "Unverified text matches"
            else -> "Leads requiring review"
        }

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): Report = Gson().fromJson(json, Report::class.java)
    }
}

data class CoverageMatrix(
    val caseId: String,
    val checks: List<String>,
    val unsupportedSections: List<String>,
)
