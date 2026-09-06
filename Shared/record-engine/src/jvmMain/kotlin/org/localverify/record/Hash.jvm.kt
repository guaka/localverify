package org.localverify.record
import java.security.MessageDigest
internal actual fun hashPayloads(data: List<ByteArray>): String {
    val hash = MessageDigest.getInstance("SHA-256")
    data.forEach { hash.update(it) }
    return hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
