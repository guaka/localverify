package org.localverify.record

/** Unicode 16.0 default lowercase, including Final_Sigma; no locale tailoring or normalization. */
internal fun unicodeLowercase(text: String): String {
    if (text.all { it.code < 128 }) return asciiLower(text)
    val points = mutableListOf<Int>()
    var offset = 0
    while (offset < text.length) {
        val first = text[offset++].code
        if (first in 0xd800..0xdbff && offset < text.length) {
            points.add(0x10000 + ((first - 0xd800) shl 10) + (text[offset++].code - 0xdc00))
        } else points.add(first)
    }
    val after = BooleanArray(points.size)
    var nextCased = false
    for (i in points.indices.reversed()) {
        after[i] = nextCased
        if (!UnicodeLowercaseTables.ignorable.containsPoint(points[i])) nextCased = UnicodeLowercaseTables.cased.containsPoint(points[i])
    }
    var previousCased = false
    return buildString(text.length) {
        points.forEachIndexed { i, point ->
            if (point == 0x3a3 && previousCased && !after[i]) append('\u03c2')
            else {
                val mapped = UnicodeLowercaseTables.mapping[point]
                if (mapped == null) appendPoint(point) else mapped.forEach { appendPoint(it) }
            }
            if (!UnicodeLowercaseTables.ignorable.containsPoint(point)) previousCased = UnicodeLowercaseTables.cased.containsPoint(point)
        }
    }
}
private fun StringBuilder.appendPoint(point: Int) {
    if (point <= 0xffff) append(point.toChar()) else {
        append((0xd800 + ((point - 0x10000) shr 10)).toChar())
        append((0xdc00 + ((point - 0x10000) and 1023)).toChar())
    }
}
private class CodeRanges(encoded: String) {
    private val values = encoded.split(';').map { row -> row.split(',').map { it.toInt(16) } }
    fun containsPoint(point: Int): Boolean {
        var low = 0; var high = values.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1; val range = values[middle]
            if (point < range[0]) high = middle-1 else if (point > range[1]) low = middle+1 else return true
        }
        return false
    }
}
private object UnicodeLowercaseTables {
    val mapping = UnicodeCaseData.mappings.split(';').associate { row ->
        val fields = row.split('='); fields[0].toInt(16) to fields[1].split(',').map { it.toInt(16) }
    }
    val cased = CodeRanges(UnicodeCaseData.Cased)
    val ignorable = CodeRanges(UnicodeCaseData.Case_Ignorable)
}
