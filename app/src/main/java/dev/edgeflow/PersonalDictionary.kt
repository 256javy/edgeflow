package dev.edgeflow

import java.util.Locale

/** User-owned terms and exact corrections, without acoustic training or guessed aliases. */
object PersonalDictionary {
    data class Lexicon(val terms: List<String>, val corrections: Map<String, String>)
    fun parse(text: String): Lexicon {
        val words = mutableListOf<String>()
        val rules = mutableListOf<Pair<String, String>>()
        text.lineSequence().take(80).forEach { line ->
            val parts = line.trim().split("=>", limit = 2).map { it.trim() }
            if (parts.any { it.isBlank() || it.length > 60 || it.any(Char::isISOControl) }) return@forEach
            words += parts.last()
            if (parts.size == 2 && parts[0] != parts[1]) rules += parts[0].lowercase(Locale.ROOT) to parts[1]
        }
        val corrections = rules.groupBy({ it.first }, { it.second }).mapNotNull { (alias, values) ->
            values.distinct().singleOrNull()?.let { alias to it }
        }.toMap()
        return Lexicon(words.distinct(), corrections)
    }
    fun correct(original: String, dictionary: String): String {
        val rules = parse(dictionary).corrections
        if (rules.isEmpty()) return original
        val alternatives = rules.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val pattern = Regex("(?<![\\p{L}\\p{N}_])(?:$alternatives)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        // Single pass: a correction never triggers another rule in this operation.
        return pattern.replace(original) { rules[it.value.lowercase(Locale.ROOT)] ?: it.value }
    }
}
