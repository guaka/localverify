package org.localverify.record
internal expect fun hashPayloads(data: List<ByteArray>): String
internal fun sha256(data: ByteArray): String = hashPayloads(listOf(data))
