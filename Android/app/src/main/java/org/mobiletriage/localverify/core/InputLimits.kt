package org.mobiletriage.localverify.core

object InputLimits {
    fun read(input: java.io.InputStream, maximum: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maximum - out.size() + 1))
            if (count < 0) break
            require(count <= maximum - out.size()) { "Input exceeds byte limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    const val INDICATOR_BYTES = 5 * 1024 * 1024
    fun text(text: String) {
        require(text.length <= 16 * 1024 * 1024) { "Text exceeds size limit" }
        var lines = 1
        var length = 0
        for (char in text) {
            if (char == '\n' || char == '\r') { lines++; length = 0 } else length++
            require(lines <= 500_000 && length <= 1024 * 1024) { "Text line limit reached" }
        }
    }
    // Conservative preflight before recursive platform JSON parsing, not validation.
    fun json(text: String) {
        var depth = 0
        var tokens = 0
        var quote: Char? = null
        var escaped = false
        for ((index, char) in text.withIndex()) {
            if (index % 65536 == 0 && Thread.currentThread().isInterrupted) {
                throw java.util.concurrent.CancellationException("Analysis interrupted")
            }
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == quote) quote = null
            } else {
                if (char == '"' || char == '\'') { quote = char; tokens++ }
                if (char == '{' || char == '[') { depth++; tokens++ }
                if (char == '}' || char == ']') depth = maxOf(0, depth - 1)
                if (char == ',') tokens++
                require(depth <= 64 && tokens <= 200_000) { "JSON complexity limit reached" }
            }
        }
    }
    fun indicators(values: List<Indicator>) {
        require(values.size <= 10_000 && values.all {
            it.value.isNotEmpty() && it.value.length <= 2048 && it.id.length <= 1024 &&
                (it.campaigns?.size ?: 0) <= 16 && it.campaigns.orEmpty().all { label -> label.length <= 128 }
        }) { "Indicator size/count limit reached" }
    }
}
