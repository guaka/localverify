@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.localverify.record
import kotlinx.cinterop.*
import platform.CoreCrypto.*
internal actual fun hashPayloads(data: List<ByteArray>): String = memScoped {
    val context = alloc<CC_SHA256_CTX>()
    CC_SHA256_Init(context.ptr)
    data.forEach { bytes ->
        if (bytes.isNotEmpty()) bytes.usePinned { CC_SHA256_Update(context.ptr, it.addressOf(0), bytes.size.toUInt()) }
    }
    val bytes = ByteArray(32)
    bytes.usePinned { CC_SHA256_Final(it.addressOf(0).reinterpret(), context.ptr) }
    bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
