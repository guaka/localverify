package org.mobiletriage.localverify.core

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val MAX_EXPANDED_BYTES: Long = 8L * 1024 * 1024 * 1024
private const val MAX_ENTRY_COUNT = 100_000
private const val ENTRY_SIZE_LIMIT_BYTES = 16L * 1024 * 1024
private val SUPPORTED_EXTENSIONS = setOf("ips", "json", "txt", "log", "crash")

private fun safePath(path: String): Boolean {
    if (path.isEmpty()) return false
    if (path.startsWith('/')) return false
    if (path.contains('\\')) return false
    return !path.split('/').contains("..")
}

private fun isRegularFile(type: Byte): Boolean {
    return type == 0.toByte() || type == 48.toByte() || type == 0x30.toByte()
}

private fun parseOctal(value: ByteArray, start: Int, end: Int): Long {
    val raw = String(value, start, end - start)
        .trim()
        .trim('\u0000')
    if (raw.isEmpty()) return 0
    return raw.toLong(8)
}

private class TarReader(input: GZIPInputStream) {
    private val stream = input
    private val expandedBytes = LongArray(1)

    private fun readExact(bytes: Int): ByteArray {
        val result = ByteArray(bytes)
        var total = 0
        while (total < bytes) {
            val read = stream.read(result, total, bytes - total)
            if (read < 0) throw IllegalStateException("Truncated or corrupt gzip/tar archive")
            total += read
            expandedBytes[0] += read.toLong()
            if (expandedBytes[0] > MAX_EXPANDED_BYTES) throw IllegalStateException("Archive exceeds 8 GiB expanded limit")
        }
        return result
    }

    private fun checksumIsValid(header: ByteArray): Boolean {
        val headerChecksum = String(header, 148, 8).trim().trim('\u0000')
        val expected = if (headerChecksum.isEmpty()) -1L else headerChecksum.toLong(8)
        var actual = 0
        for (i in header.indices) {
            actual += if (i in 148..155) 32 else (header[i].toInt() and 0xff)
        }
        return expected == actual.toLong()
    }

    fun walk(
        onProgress: (Long, String?) -> Unit,
        onVisit: (String, ByteArray?, String?) -> Unit,
    ) {
        val regularPaths = HashSet<String>()
        var entries = 0

        while (true) {
            val header = readExact(512)
            onProgress(expandedBytes[0], null)
            if (header.all { it == 0.toByte() }) {
                val trailer = readExact(512)
                if (!trailer.all { it == 0.toByte() }) throw IllegalStateException("Invalid tar terminator")
                return
            }

            if (!checksumIsValid(header)) throw IllegalStateException("Invalid tar header checksum")

            val name = String(header, 0, 100).trimEnd { it == '\u0000' }
            val prefix = String(header, 345, 155).trimEnd { it == '\u0000' }
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            if (!safePath(path)) throw IllegalStateException("Unsafe archive path")

            val entrySize = parseOctal(header, 124, 136)
            if (entrySize < 0 || entrySize > MAX_EXPANDED_BYTES) throw IllegalStateException("Invalid tar entry size")
            val type = header[156]
            val regular = isRegularFile(type)
            if (regular && !regularPaths.add(path)) throw IllegalStateException("Duplicate archive path")
            entries += 1
            if (entries > MAX_ENTRY_COUNT) throw IllegalStateException("Too many archive entries")

            val extension = path.substringAfterLast('.', "").lowercase()
            val keepPayload = regular && SUPPORTED_EXTENSIONS.contains(extension) && entrySize <= ENTRY_SIZE_LIMIT_BYTES
            val payload = if (keepPayload) ByteArray(entrySize.toInt()) else null
            var remaining = entrySize
            var copied = 0

            while (remaining > 0) {
                val chunk = readExact(minOf(remaining, 65_536).toInt())
                if (keepPayload) {
                    System.arraycopy(chunk, 0, payload!!, copied, chunk.size)
                    copied += chunk.size
                }
                remaining -= chunk.size.toLong()
                onProgress(expandedBytes[0], path)
            }

            val padding = (512 - (entrySize % 512)).let { if (it == 512L) 0L else it }
            if (padding > 0L) readExact(padding.toInt())

            val reason = when {
                !regular -> "unsupported tar entry type"
                !SUPPORTED_EXTENSIONS.contains(extension) -> "unsupported format"
                !keepPayload -> "exceeds 16 MiB parser limit"
                else -> null
            }
            onVisit(path, payload, reason)
            onProgress(expandedBytes[0], path)
        }
    }
}

object ArchiveUtil {
    fun hashFile(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun walkArchive(file: File, onProgress: (Long, String?) -> Unit, onVisit: (String, ByteArray?, String?) -> Unit) {
        val raw = FileInputStream(file)
        val magic = ByteArray(2)
        if (raw.read(magic) != 2 || magic[0] != 0x1F.toByte() || magic[1] != 0x8B.toByte()) {
            raw.close()
            if (magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()) {
                walkZipArchive(file, onProgress, onVisit)
                return
            }
            throw IllegalStateException("Expected gzip-compressed tar or a supported zip archive")
        }
        raw.close()

        val stream = GZIPInputStream(BufferedInputStream(FileInputStream(file)))
        TarReader(stream).walk(onProgress, onVisit)
        stream.close()
    }

    private fun walkZipArchive(
        file: File,
        onProgress: (Long, String?) -> Unit,
        onVisit: (String, ByteArray?, String?) -> Unit,
    ) {
        val regularPaths = HashSet<String>()
        var entries = 0
        var expandedBytes = 0L
        val zip = ZipInputStream(BufferedInputStream(FileInputStream(file)))
        try {
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                entries += 1
                if (entries > MAX_ENTRY_COUNT) throw IllegalStateException("Too many archive entries")
                val path = entry.name
                if (!safePath(path)) throw IllegalStateException("Unsafe archive path")
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (!regularPaths.add(path)) throw IllegalStateException("Duplicate archive path")

                val extension = path.substringAfterLast('.', "").lowercase()
                var reason: String? = null
                val payload = ByteArrayOutputStream()
                var totalRead = 0L
                val chunk = ByteArray(65_536)
                while (true) {
                    val read = zip.read(chunk)
                    if (read < 0) break
                    totalRead += read.toLong()
                    expandedBytes += read.toLong()
                    if (expandedBytes > MAX_EXPANDED_BYTES) throw IllegalStateException("Archive exceeds 8 GiB expanded limit")
                if (reason == null && SUPPORTED_EXTENSIONS.contains(extension) && totalRead <= ENTRY_SIZE_LIMIT_BYTES) {
                        payload.write(chunk, 0, read)
                    } else {
                        reason = reason ?: if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                            "unsupported format"
                        } else {
                            "exceeds 16 MiB parser limit"
                        }
                    }
                    onProgress(expandedBytes, path)
                }

                val finalReason = reason ?: supportedEntryReason(path, totalRead)
                val finalPayload = if (finalReason == null) payload.toByteArray() else null
                onVisit(path, finalPayload, finalReason)
                onProgress(expandedBytes, path)
                zip.closeEntry()
            }
        } finally {
            zip.close()
        }
    }

    private fun supportedEntryReason(path: String, size: Long): String? {
        val extension = path.substringAfterLast('.', "").lowercase()
        if (!SUPPORTED_EXTENSIONS.contains(extension)) return "unsupported format"
        if (size > ENTRY_SIZE_LIMIT_BYTES) return "exceeds 16 MiB parser limit"
        return null
    }

    fun copyToPrivate(source: java.io.InputStream, destination: File, onBytes: (Int) -> Unit = {}) {
        destination.parentFile?.mkdirs()
        val output = destination.outputStream()
        val buffer = ByteArray(1024 * 1024)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_EXPANDED_BYTES) throw IllegalStateException("Compressed archive exceeds 8 GiB import limit")
            output.write(buffer, 0, read)
            onBytes(read)
        }
        output.close()
    }
}
