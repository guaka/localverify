package org.mobiletriage.localverify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mobiletriage.localverify.core.ArchiveUtil
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveWalkerTest {

    @Test
    fun walkArchiveParsesSupportedZipEntries() {
        val zipFile = File.createTempFile("android-archive-test", ".zip")
        zipFile.deleteOnExit()

        ZipOutputStream(BufferedOutputStream(zipFile.outputStream())).use { output ->
            output.putNextEntry(ZipEntry("logs/session.txt"))
            output.write("safe-content".toByteArray())
            output.closeEntry()
            output.putNextEntry(ZipEntry("payload.bin"))
            output.write(ByteArray(4) { 0x01 })
            output.closeEntry()
        }

        val reasons = HashMap<String, String?>()
        val payloads = HashMap<String, ByteArray?>()
        ArchiveUtil.walkArchive(
            file = zipFile,
            onProgress = { _, _ -> },
            onVisit = { path, payload, reason ->
                reasons[path] = reason
                payloads[path] = payload
            },
        )

        assertEquals("safe-content", String(payloads["logs/session.txt"] ?: ByteArray(0)))
        assertNull(reasons["logs/session.txt"])
        assertEquals("unsupported format", reasons["payload.bin"])
        assertNull(payloads["payload.bin"])
    }

    @Test
    fun walkArchiveRejectsUnsupportedMagic() {
        val plainFile = File.createTempFile("android-archive-test", ".txt")
        plainFile.deleteOnExit()
        plainFile.writeBytes("not an archive".toByteArray())

        try {
            ArchiveUtil.walkArchive(
                file = plainFile,
                onProgress = { _, _ -> },
                onVisit = { _, _, _ -> },
            )
            throw AssertionError("Expected unsupported archive exception")
        } catch (error: IllegalStateException) {
            assertEquals("Expected gzip-compressed tar or a supported zip archive", error.message)
        }
    }

    @Test
    fun walkArchiveRejectsUnsafePathEntries() {
        val zipFile = File.createTempFile("android-archive-test", ".zip").apply { deleteOnExit() }
        ZipOutputStream(BufferedOutputStream(zipFile.outputStream())).use { output ->
            output.putNextEntry(ZipEntry("../outside/session.txt"))
            output.write("should be rejected".toByteArray())
            output.closeEntry()
        }

        try {
            ArchiveUtil.walkArchive(
                file = zipFile,
                onProgress = { _, _ -> },
                onVisit = { _, _, _ -> },
            )
            throw AssertionError("Expected unsafe path exception")
        } catch (error: IllegalStateException) {
            assertEquals("Unsafe archive path", error.message)
        }
    }
}
