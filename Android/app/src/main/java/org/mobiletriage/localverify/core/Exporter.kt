package org.mobiletriage.localverify.core

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val MAX_ZIP_ENTRY_BYTES: Long = 4L * 1024 * 1024 * 1024

object Exporter {
    fun toJson(report: Report): String = report.toJson()

    fun payloadText(finding: Finding): String {
        return """
Rule: ${finding.rule}
Match: ${finding.matchType}
Campaign: ${(finding.campaigns ?: listOf("Uncategorized")).joinToString(", ")}
Value: ${finding.value}
Source: ${finding.source}
Record: ${finding.record}
Timestamp: ${finding.timestamp ?: "Not available"}
Review: ${finding.explanation}

Evidence excerpt (not the complete source file):
${finding.excerpt}

---
""".trimIndent()
    }

    fun payloadsText(report: Report): String {
        val header = "Case: ${report.caseID}\nStatus: ${report.status}\nOriginal SHA-256: ${report.archiveSHA256}\n\nExperimental triage. These are leads requiring review, not proof of compromise.\n\n"
        if (report.findings.isEmpty()) {
            return header + "No finding payloads recorded."
        }
        return header + report.findings.joinToString("\n") { payloadText(it) }
    }

    fun html(report: Report): String {
        fun esc(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        val findings = report.findings.joinToString("\n") { finding ->
            """
<article>
  <h2>${esc(finding.value)}</h2>
  <p>${esc(finding.matchType)} · ${esc(finding.rule)}</p>
  <p>${esc(finding.source)} — ${esc(finding.record)}</p>
  <p>${esc(finding.explanation)}</p>
  <pre>${esc(finding.excerpt)}</pre>
</article>
"""
        }

        return """
<!doctype html>
<meta charset='utf-8'>
<meta http-equiv='Content-Security-Policy' content="default-src 'none'; style-src 'unsafe-inline'">
<meta name='viewport' content='width=device-width'>
<title>Local Verify report</title>
<style>body{font:16px system-ui;max-width:900px;margin:40px auto;padding:20px}pre{white-space:pre-wrap;overflow-wrap:anywhere}article{border-top:1px solid #aaa}</style>
<h1>${esc(report.status)}</h1>
<p>Experimental triage. Absence of matches does not establish that a device is uncompromised.</p>
<p>Case: ${esc(report.caseID)}</p>
<p>SHA-256: ${esc(report.archiveSHA256)}</p>
<p>Indicators: ${esc(report.indicatorVersion)}</p>
$findings
<h2>Coverage</h2>
<pre>${esc(report.analyzed.joinToString("\n"))}</pre>
<h2>Skipped / unsupported</h2>
<pre>${esc(report.skipped.joinToString("\n"))}</pre>
<h2>Errors</h2>
<pre>${esc(report.errors.joinToString("\n"))}</pre>
""".trimIndent()
    }

    fun writeExportZip(report: Report, original: File?, destination: File) {
        if (original != null) {
            val originalHash = ArchiveUtil.hashFile(original)
            require(originalHash == report.archiveSHA256) { "Original evidence hash changed; export stopped" }
        }

        val partial = File(destination.path + ".partial")
        try {
            ZipOutputStream(FileOutputStream(partial)).use { out ->
                addStoredEntry(out, "report.json", toJson(report).toByteArray())
                addStoredEntry(out, "report.html", html(report).toByteArray())
                if (original != null) {
                    require(original.length() < MAX_ZIP_ENTRY_BYTES) { "Evidence exceeds ZIP limit" }
                    val gzip = original.inputStream().use { it.read() == 0x1f && it.read() == 0x8b }
                    out.putNextEntry(ZipEntry(if (gzip) "original.tar.gz" else "original.zip"))
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    original.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                            total += count
                            require(total < MAX_ZIP_ENTRY_BYTES) { "Evidence exceeds ZIP limit" }
                            digest.update(buffer, 0, count)
                            out.write(buffer, 0, count)
                        }
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == report.archiveSHA256) { "Evidence changed during export" }
                    out.closeEntry()
                }
            }
            require(partial.length() < MAX_ZIP_ENTRY_BYTES) { "Export exceeds ZIP limit" }
            java.nio.file.Files.move(partial.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { partial.delete() }
    }

    private fun addStoredEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = CRC32()
        crc.update(bytes)
        require(bytes.size.toLong() < MAX_ZIP_ENTRY_BYTES) { "Evidence exceeds 4 GiB ZIP entry limit" }

        val entry = ZipEntry(name)
        entry.method = ZipEntry.STORED
        entry.size = bytes.size.toLong()
        entry.compressedSize = bytes.size.toLong()
        entry.crc = crc.value

        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }
}
