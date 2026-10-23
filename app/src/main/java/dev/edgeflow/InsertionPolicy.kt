package dev.edgeflow

/** Pure policy: refuse ambiguous selection; never replace selected content in v0. */
object InsertionPolicy {
    data class Edit(val text: String, val cursor: Int)
    fun insert(existing: String, start: Int, end: Int, dictation: String): Edit? {
        if (start != end || start !in 0..existing.length || dictation.isBlank()) return null
        // Avoid splitting a UTF-16 surrogate pair.
        if (start > 0 && start < existing.length && existing[start].isLowSurrogate() && existing[start - 1].isHighSurrogate()) return null
        val left = existing.substring(0, start)
        val right = existing.substring(start)
        val prefix = if (left.isNotEmpty() && !left.last().isWhitespace()) " " else ""
        val suffix = if (right.isNotEmpty() && !right.first().isWhitespace()) " " else ""
        val addition = prefix + dictation.trim() + suffix
        return Edit(left + addition + right, start + addition.length)
    }
    fun sensitive(inputType: Int, password: Boolean): Boolean {
        val variation = inputType and 0xff0
        val clazz = inputType and 0xf
        return password || (clazz == 1 && variation in setOf(0x80, 0x90, 0xe0)) ||
            (clazz == 2 && variation == 0x10)
    }
}
