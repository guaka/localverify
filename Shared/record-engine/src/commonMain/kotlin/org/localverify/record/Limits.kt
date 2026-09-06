package org.localverify.record

object Limits {
    const val INDICATOR_BYTES = 5 * 1024 * 1024
    const val TEXT_BYTES = 16 * 1024 * 1024
    const val TEXT_WORK_BYTES = 128L * 1024 * 1024
    const val FINDINGS = 10_000
    internal val kinds = setOf("domain-name:value", "url:value", "process:name", "file:path", "file:name")

    internal fun decode(data: ByteArray, maximum: Int, cancel: Cancellation): String {
        cancel.check()
        if (data.size > maximum) fail("Input byte limit reached")
        return try { data.decodeToString(throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { fail("Invalid UTF-8") }
    }
    internal fun text(data: ByteArray, cancel: Cancellation) {
        var lines = 1
        var length = 0
        for (i in data.indices) {
            if (i % 4096 == 0) cancel.visited(i.toLong())
            if (data[i] == 10.toByte() || data[i] == 13.toByte()) { lines++; length = 0 } else length++
            if (lines > 500_000 || length > 1024 * 1024) fail("Text line limit reached")
        }
    }
    /** Lexical complexity preflight, never a substitute for strict JSON parsing. */
    internal fun json(text: String, cancel: Cancellation) {
        var depth = 0; var tokens = 0; var quoted = false; var escaped = false
        text.forEachIndexed { i, c ->
            if (i % 4096 == 0) cancel.check()
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else {
                when (c) {
                    '"' -> { quoted = true; tokens++ }
                    '[', '{' -> { depth++; tokens++ }
                    ']', '}' -> depth = maxOf(0, depth - 1)
                    ',' -> tokens++
                }
                if (depth > 64 || tokens > 200_000) fail("JSON complexity limit reached")
            }
        }
    }
    internal fun indicators(values: List<Indicator>, cancel: Cancellation) {
        if (values.size > 10_000) fail("Indicator count limit reached")
        values.forEach {
            cancel.check()
            if (it.kind !in kinds || it.value.isEmpty() || it.value.length > 8192 || it.id.length > 1024 || utf8Size(it.value) > 8192 || utf8Size(it.id) > 1024 ||
                it.campaigns.size > 16 || it.campaigns.any { label -> label.length > 128 || utf8Size(label) > 128 }) fail("Indicator metadata limit reached")
        }
    }
}

/** Count bytes without allocating a second encoded copy. Reject unpaired surrogate input. */
internal fun utf8Size(s: String): Int {
    var count = 0; var i = 0
    while (i < s.length) {
        val c = s[i++]
        count += when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            c.isHighSurrogate() -> {
                if (i >= s.length || !s[i].isLowSurrogate()) fail("Unpaired surrogate")
                i++; 4
            }
            c.isLowSurrogate() -> fail("Unpaired surrogate")
            else -> 3
        }
    }
    return count
}
internal fun String.codePointCount(): Int {
    utf8Size(this)
    return indices.count { !this[it].isLowSurrogate() }
}
internal fun String.takeCodePoints(limit: Int): String {
    var i = 0; var count = 0
    while (i < length && count < limit) {
        if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) i++
        i++; count++
    }
    return substring(0, i)
}
internal fun asciiLower(s: String): String = buildString(s.length) {
    s.forEach { append(if (it in 'A'..'Z') it + 32 else it) }
}
internal fun tokenChar(c: Char, domain: Boolean): Boolean =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in (if (domain) "_.-" else "_./:%?=&-")
internal fun normalized(value: String, kind: String): String =
    if (kind == "domain-name:value") unicodeLowercase(value.trim('.')) else value

/** Stable Unicode White_Space property; independent of the host JDK/ICU version. */
internal fun String.unicodeBlank(): Boolean = all {
    it in '\u0009'..'\u000d' || it in '\u2000'..'\u200a' || when (it.code) { 0x20, 0x85, 0xa0, 0x1680, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000 -> true; else -> false }
}
