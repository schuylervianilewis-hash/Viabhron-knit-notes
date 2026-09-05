package com.example.audio.command

import com.example.data.db.VoiceCommandEntity
import java.util.regex.Pattern

data class CommandExecutionResult(
    val isHandledAsCommand: Boolean,
    val updatedContent: String,
    val feedbackMessage: String? = null
)

object VoiceCommandProcessor {

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "once" to 1, "1" to 1,
        "two" to 2, "twice" to 2, "2" to 2,
        "three" to 3, "3" to 3, "thrice" to 3,
        "four" to 4, "4" to 4,
        "five" to 5, "5" to 5,
        "six" to 6, "6" to 6,
        "seven" to 7, "7" to 7,
        "eight" to 8, "8" to 8,
        "nine" to 9, "9" to 9,
        "ten" to 10, "10" to 10
    )

    /**
     * Tries to execute the spoken speech string as a voice command against the current editor content.
     */
    fun processSpokenText(
        spokenText: String,
        currentContent: String,
        enabledCommands: List<VoiceCommandEntity>
    ): CommandExecutionResult {
        val trimmed = spokenText.trim().lowercase()
        if (trimmed.isBlank() || enabledCommands.isEmpty()) {
            return CommandExecutionResult(false, currentContent)
        }

        // 1. Check for Next Row / Next Round command
        if (isTriggered(trimmed, listOf("next row", "new row", "next round", "new round"), enabledCommands, "NEXT_ROW")) {
            val isRound = trimmed.contains("round")
            val nextRowText = calculateNextRowPrefix(currentContent, isRound)
            val updated = if (currentContent.isBlank()) {
                nextRowText.trimStart('\n')
            } else {
                currentContent.trimEnd() + "\n" + nextRowText.trimStart('\n')
            }
            return CommandExecutionResult(true, updated, "Inserted $nextRowText")
        }

        // 2. Check for Next Line command
        if (isTriggered(trimmed, listOf("next line", "new line"), enabledCommands, "NEXT_LINE")) {
            val updated = currentContent + "\n"
            return CommandExecutionResult(true, updated, "Moved to next line")
        }

        // 3. Check for Undo Last command
        if (isTriggered(trimmed, listOf("undo", "undo last", "undo last stitch", "erase last"), enabledCommands, "UNDO_LAST")) {
            val updated = undoLastToken(currentContent)
            return CommandExecutionResult(true, updated, "Undid last entry")
        }

        // 4. Check for Repeat Last Group command (e.g. "repeat last group 3 times", "repeat group twice", "repeat last 2 stitches 3 times")
        if (matchesRepeatGroup(trimmed, enabledCommands)) {
            val groupSize = extractGroupSize(trimmed)
            val repeatCount = extractRepeatCount(trimmed)
            val updated = repeatLastGroup(currentContent, groupSize, repeatCount)
            return CommandExecutionResult(true, updated, "Repeated last group $repeatCount time(s)")
        }

        // 5. Check for Repeat Last Stitch command (e.g. "repeat last stitch 3 times", "repeat last twice", "repeat last")
        if (matchesRepeatStitch(trimmed, enabledCommands)) {
            val repeatCount = extractRepeatCount(trimmed)
            val updated = repeatLastStitch(currentContent, repeatCount)
            return CommandExecutionResult(true, updated, "Repeated last stitch $repeatCount time(s)")
        }

        // 6. Check for Asterisk / Star Repeat Marker
        if (isTriggered(trimmed, listOf("asterisk", "star", "repeat from star"), enabledCommands, "INSERT_STAR")) {
            val separator = if (currentContent.isNotEmpty() && !currentContent.endsWith(" ") && !currentContent.endsWith("\n")) " " else ""
            val updated = "$currentContent$separator* "
            return CommandExecutionResult(true, updated, "Inserted *")
        }

        // 7. Check for Punctuation: Comma
        if (isTriggered(trimmed, listOf("comma"), enabledCommands, "INSERT_COMMA")) {
            val updated = currentContent.trimEnd() + ", "
            return CommandExecutionResult(true, updated, "Inserted comma")
        }

        // 8. Check for Punctuation: Period
        if (isTriggered(trimmed, listOf("period", "full stop"), enabledCommands, "INSERT_PERIOD")) {
            val updated = currentContent.trimEnd() + ". "
            return CommandExecutionResult(true, updated, "Inserted period")
        }

        return CommandExecutionResult(false, currentContent)
    }

    private fun isTriggered(
        spoken: String,
        defaultTriggers: List<String>,
        enabledCommands: List<VoiceCommandEntity>,
        commandType: String
    ): Boolean {
        val rulesForType = enabledCommands.filter { it.commandType == commandType && it.isEnabled }
        if (rulesForType.isEmpty()) return false

        val triggers = rulesForType.map { it.triggerPhrase.trim().lowercase() } + defaultTriggers
        return triggers.any { spoken == it || spoken.startsWith("$it ") || spoken.endsWith(" $it") }
    }

    private fun matchesRepeatStitch(spoken: String, enabledCommands: List<VoiceCommandEntity>): Boolean {
        if (spoken.contains("group") || Regex("repeat\\s+last\\s+\\w+\\s+stitch").containsMatchIn(spoken)) return false
        val rulesForType = enabledCommands.filter { it.commandType == "REPEAT_LAST_STITCH" && it.isEnabled }
        if (rulesForType.isEmpty()) return false

        val pattern = Regex("^(repeat\\s+(last\\s+)?(stitch|one|that)|repeat\\s+last)(\\s+([a-z0-9]+)(\\s+times?)?)?\$")
        return pattern.containsMatchIn(spoken) || rulesForType.any {
            spoken == it.triggerPhrase.lowercase() || spoken.startsWith("${it.triggerPhrase.lowercase()} ")
        }
    }

    private fun matchesRepeatGroup(spoken: String, enabledCommands: List<VoiceCommandEntity>): Boolean {
        val rulesForType = enabledCommands.filter { it.commandType == "REPEAT_LAST_GROUP" && it.isEnabled }
        if (rulesForType.isEmpty()) return false

        val pattern = Regex("^(repeat\\s+(last\\s+)?group|repeat\\s+last\\s+\\w+\\s+stitches?)(\\s+([a-z0-9]+)(\\s+times?)?)?\$")
        return pattern.containsMatchIn(spoken) || rulesForType.any {
            spoken == it.triggerPhrase.lowercase() || spoken.startsWith("${it.triggerPhrase.lowercase()} ")
        }
    }

    private fun extractRepeatCount(spoken: String): Int {
        val timesMatch = Regex("(\\b\\w+\\b)\\s+times?").find(spoken)
        if (timesMatch != null) {
            val word = timesMatch.groupValues[1].lowercase()
            return NUMBER_WORDS[word] ?: word.toIntOrNull() ?: 1
        }

        for ((word, count) in NUMBER_WORDS) {
            if (spoken.endsWith(" $word")) {
                return count
            }
        }

        return 1
    }

    private fun extractGroupSize(spoken: String): Int {
        val match = Regex("repeat\\s+last\\s+(\\w+)\\s+stitches?").find(spoken)
        if (match != null) {
            val word = match.groupValues[1].lowercase()
            return NUMBER_WORDS[word] ?: word.toIntOrNull() ?: 2
        }
        return 2 // Default group size is last 2 stitch units
    }

    fun calculateNextRowPrefix(content: String, isRound: Boolean): String {
        val label = if (isRound) "Round" else "Row"
        val lines = content.lines()
        val regex = Regex("(?i)(Row|Round|R)\\s*(\\d+):?", RegexOption.IGNORE_CASE)

        var highestNumber = 0
        for (line in lines.reversed()) {
            val match = regex.find(line.trim())
            if (match != null) {
                val num = match.groupValues[2].toIntOrNull() ?: 0
                if (num > highestNumber) {
                    highestNumber = num
                }
            }
        }

        val nextNumber = if (highestNumber > 0) highestNumber + 1 else if (content.isBlank()) 1 else 2
        return "$label $nextNumber: "
    }

    fun repeatLastStitch(content: String, count: Int): String {
        if (content.isBlank() || count <= 0) return content

        val tokens = tokenizeStitches(content)
        if (tokens.isEmpty()) return content

        val lastStitch = tokens.last().trim().trimEnd(',', ';', '.')
        if (lastStitch.isBlank()) return content

        val isCommaDelimited = content.contains(",")
        val delimiter = if (isCommaDelimited) ", " else " "

        val repetitions = List(count) { lastStitch }.joinToString(delimiter)
        val trimmed = content.trimEnd()

        return if (trimmed.endsWith(",") || trimmed.endsWith(" ")) {
            "$trimmed$repetitions"
        } else {
            "$trimmed$delimiter$repetitions"
        }
    }

    fun repeatLastGroup(content: String, groupSize: Int, repeatCount: Int): String {
        if (content.isBlank() || repeatCount <= 0) return content

        // Check if there's a bracketed group like [k1, yo, ssk] or * k1, p1 *
        val bracketMatch = Regex("\\[([^\\]]+)\\]|\\*([^\\*]+)\\*").findAll(content).lastOrNull()
        if (bracketMatch != null) {
            val groupContent = (bracketMatch.groups[1]?.value ?: bracketMatch.groups[2]?.value ?: "").trim()
            if (groupContent.isNotBlank()) {
                val delimiter = if (content.contains(",")) ", " else " "
                val repetitions = List(repeatCount) { groupContent }.joinToString(delimiter)
                val trimmed = content.trimEnd()
                return if (trimmed.endsWith(",") || trimmed.endsWith(" ")) {
                    "$trimmed$repetitions"
                } else {
                    "$trimmed$delimiter$repetitions"
                }
            }
        }

        val tokens = tokenizeStitches(content)
        if (tokens.isEmpty()) return content

        val takeCount = groupSize.coerceAtMost(tokens.size)
        val groupTokens = tokens.takeLast(takeCount).map { it.trim().trimEnd(',', ';', '.') }
        val isCommaDelimited = content.contains(",")
        val delimiter = if (isCommaDelimited) ", " else " "

        val groupString = groupTokens.joinToString(delimiter)
        val repetitions = List(repeatCount) { groupString }.joinToString(delimiter)
        val trimmed = content.trimEnd()

        return if (trimmed.endsWith(",") || trimmed.endsWith(" ")) {
            "$trimmed$repetitions"
        } else {
            "$trimmed$delimiter$repetitions"
        }
    }

    fun undoLastToken(content: String): String {
        if (content.isBlank()) return ""

        val trimmed = content.trimEnd()
        // If it ends with comma or punctuation, strip it
        val tokens = tokenizeStitches(trimmed)
        if (tokens.size <= 1) {
            // Remove the whole content or line
            val lastNewline = trimmed.lastIndexOf('\n')
            return if (lastNewline >= 0) trimmed.substring(0, lastNewline + 1) else ""
        }

        val withoutLastToken = tokens.dropLast(1)
        val isCommaDelimited = trimmed.contains(",")
        val delimiter = if (isCommaDelimited) ", " else " "

        // Preserve previous newlines if any
        val lastNewline = trimmed.lastIndexOf('\n')
        if (lastNewline >= 0) {
            val prefix = trimmed.substring(0, lastNewline + 1)
            val currentLineContent = trimmed.substring(lastNewline + 1)
            val lineTokens = tokenizeStitches(currentLineContent)
            return if (lineTokens.size <= 1) {
                prefix.trimEnd('\n')
            } else {
                prefix + lineTokens.dropLast(1).joinToString(delimiter)
            }
        }

        return withoutLastToken.joinToString(delimiter)
    }

    private fun tokenizeStitches(text: String): List<String> {
        val currentLine = text.lines().lastOrNull { it.isNotBlank() } ?: text
        // Split by commas first if available, else whitespace
        return if (currentLine.contains(",")) {
            currentLine.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            currentLine.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        }
    }
}
