package com.example.audio.replacement

import com.example.data.db.WordReplacementEntity
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-performance text replacement processor.
 * Transforms recognized voice-to-text outputs using user-defined dictionary rules and sequence aggregators.
 * Handles sequence counting collapse (e.g. "knit 1 knit 2 knit 3 knit 4 knit 5" -> "k5", "make 1 make 2" -> "m2"),
 * whole-word boundaries, phrase priority ordering, and case sensitivity.
 */
object TextReplacementProcessor {

    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100
    )

    /**
     * Applies enabled word replacement rules and sequence aggregations to the input transcription text.
     * Sequence aggregation runs first, followed by descending target phrase length substitutions.
     */
    fun applyReplacements(rawText: String, rules: List<WordReplacementEntity>): String {
        if (rawText.isBlank() || rules.isEmpty()) return rawText

        val enabledRules = rules.filter { it.isEnabled && it.targetPhrase.isNotBlank() }
        if (enabledRules.isEmpty()) return rawText

        val sequenceRules = enabledRules.filter {
            it.category.equals("Sequence", ignoreCase = true) ||
            it.category.equals("Counting", ignoreCase = true)
        }
        val literalRules = enabledRules.filter {
            !it.category.equals("Sequence", ignoreCase = true) &&
            !it.category.equals("Counting", ignoreCase = true)
        }.sortedByDescending { it.targetPhrase.length }

        var processed = rawText

        // 1. Process Sequence Aggregations (e.g., "knit 1 knit 2 ... knit 5" -> "k5")
        for (seqRule in sequenceRules) {
            processed = collapseSequences(
                text = processed,
                prefix = seqRule.targetPhrase.trim(),
                shorthand = seqRule.replacementPhrase.trim(),
                isMatchCase = seqRule.isMatchCase
            )
        }

        // 2. Process Standard Word Replacements
        for (rule in literalRules) {
            val target = rule.targetPhrase.trim()
            val replacement = rule.replacementPhrase

            try {
                // Whole-word boundary regex with optional punctuation support
                val regexFlags = if (rule.isMatchCase) 0 else Pattern.CASE_INSENSITIVE
                val escapedTarget = Pattern.quote(target)
                val regexPattern = Pattern.compile("(?i)(?<=\\b|^|\\s)$escapedTarget(?=\\b|$|\\s|[.,!?;:])", regexFlags)
                
                processed = regexPattern.matcher(processed).replaceAll(replacement)
            } catch (e: Exception) {
                processed = if (rule.isMatchCase) {
                    processed.replace(target, replacement)
                } else {
                    processed.replace(target, replacement, ignoreCase = true)
                }
            }
        }

        // Clean up redundant whitespaces created during replacements
        return processed.replace(Regex(" +"), " ").trim()
    }

    /**
     * Collapses repetitive or ascending counting sequences for a given prefix.
     * E.g. with prefix="knit", shorthand="k":
     *   "knit 1 knit 2 knit 3 knit 4 knit 5" -> "k5"
     *   "knit one, knit two, knit three" -> "k3"
     *   "make 1 make 2" -> "m2"
     */
    fun collapseSequences(text: String, prefix: String, shorthand: String, isMatchCase: Boolean): String {
        if (text.isBlank() || prefix.isBlank()) return text

        val escapedPrefix = Pattern.quote(prefix)
        val numPatternStr = "(\\d+|zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty)"
        val flags = if (isMatchCase) 0 else Pattern.CASE_INSENSITIVE
        val pattern = Pattern.compile("\\b($escapedPrefix)\\s*$numPatternStr\\b", flags)

        val matcher = pattern.matcher(text)
        data class MatchToken(val start: Int, val end: Int, val number: Int, val rawText: String)
        val matches = mutableListOf<MatchToken>()

        while (matcher.find()) {
            val numStr = matcher.group(2)?.lowercase(Locale.ROOT) ?: ""
            val parsedNum = parseNumber(numStr)
            if (parsedNum != null) {
                matches.add(MatchToken(matcher.start(), matcher.end(), parsedNum, matcher.group(0) ?: ""))
            }
        }

        if (matches.size < 2) return text

        // Identify consecutive sequences of matches separated only by whitespace/punctuation/conjunctions
        val result = StringBuilder()
        var cursor = 0
        var i = 0

        while (i < matches.size) {
            var j = i
            var isSequential = true
            var maxVal = matches[i].number

            while (j + 1 < matches.size) {
                val current = matches[j]
                val next = matches[j + 1]

                // Check text between matches
                val between = text.substring(current.end, next.start).trim()
                val isLegitSeparator = between.isEmpty() ||
                        between.matches(Regex("^[.,!?;:\\s]*(?:and|then)?[.,!?;:\\s]*$", RegexOption.IGNORE_CASE))

                // Must be sequential (next == current + 1) or incremental
                val isAscending = next.number == current.number + 1

                if (isLegitSeparator && isAscending) {
                    j++
                    maxVal = next.number
                } else {
                    break
                }
            }

            if (j > i) {
                // We found a sequence of 2 or more items from matches[i] to matches[j]
                val seqStart = matches[i].start
                val seqEnd = matches[j].end

                result.append(text.substring(cursor, seqStart))
                result.append("$shorthand$maxVal")
                cursor = seqEnd
                i = j + 1
            } else {
                // Single token, do not collapse here
                i++
            }
        }

        if (cursor < text.length) {
            result.append(text.substring(cursor))
        }

        return result.toString()
    }

    private fun parseNumber(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        return NUMBER_WORDS[token.lowercase(Locale.ROOT)]
    }
}
