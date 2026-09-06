package org.mobiletriage.localverify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mobiletriage.localverify.core.ArchivePolicy

class ArchivePolicyTest {

    @Test
    fun supportedByExtensionOrMimeType() {
        assertTrue(ArchivePolicy.isSupportedArchive("bugreport.zip", null))
        assertTrue(ArchivePolicy.isSupportedArchive("trace.tgz", null))
        assertTrue(ArchivePolicy.isSupportedArchive("capture.tar.gz", null))
        assertTrue(ArchivePolicy.isSupportedArchive(null, "application/zip; charset=UTF-8"))
        assertTrue(ArchivePolicy.isSupportedArchive("BugReport.TAR.GZ", "APPLICATION/X-GZIP; name=\"bugreport.tar.gz\""))

        assertTrue(ArchivePolicy.isSupportedArchive(null, "application/zip"))
        assertTrue(ArchivePolicy.isSupportedArchive(null, "application/x-gzip-compressed"))
        assertTrue(ArchivePolicy.isSupportedArchive(null, "application/tar"))
        assertTrue(ArchivePolicy.isSupportedArchive(null, "application/octet-stream"))
    }

    @Test
    fun rejectUnsupportedArchiveTypes() {
        assertFalse(ArchivePolicy.isSupportedArchive("readme.txt", null))
        assertFalse(ArchivePolicy.isSupportedArchive(null, "application/pdf"))
        assertFalse(ArchivePolicy.isSupportedArchive("readme", ""))
    }
}
