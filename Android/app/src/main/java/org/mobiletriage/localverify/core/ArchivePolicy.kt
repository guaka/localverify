package org.mobiletriage.localverify.core

object ArchivePolicy {
    val supportedArchiveExtensions: Set<String> = setOf(".zip", ".gz", ".tgz", ".tar.gz")

    val supportedArchiveMimeTypes: Set<String> = setOf(
        "application/zip",
        "application/gzip",
        "application/x-zip-compressed",
        "application/x-gzip",
        "application/x-gzip-compressed",
        "application/x-tar",
        "application/tar",
        "application/x-compressed",
        "application/octet-stream",
    )

    fun isSupportedArchive(name: String?, mimeType: String?): Boolean {
        val extension = (name ?: "").lowercase()
        val normalizedMimeType = (mimeType ?: "")
            .lowercase()
            .substringBefore(";")
            .trim()
        return supportedArchiveExtensions.any { extension.endsWith(it) } || supportedArchiveMimeTypes.contains(normalizedMimeType)
    }
}
