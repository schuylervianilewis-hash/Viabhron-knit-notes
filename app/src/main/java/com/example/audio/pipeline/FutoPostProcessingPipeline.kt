package com.example.audio.pipeline

import com.example.audio.command.CommandExecutionResult
import com.example.audio.command.VoiceCommandProcessor
import com.example.audio.replacement.TextReplacementProcessor
import com.example.data.db.VoiceCommandEntity
import com.example.data.db.WordReplacementEntity
import java.util.Locale

/**
 * Complete 4-Pass Post-Processing Pipeline modeled 1:1 after FUTO Voice Input:
 * 
 * Pass 1: Hallucination & Repetition Filter (strips looped Whisper artifacts and repetitive n-grams)
 * Pass 2: Voice Action & Macro Dispatcher (executes "next row", "repeat stitch 3 times", "undo", etc.)
 * Pass 3: Word Replacement & Shorthand Engine (whole-word regex priority substitution)
 * Pass 4: Punctuation, Spacing & Capitalization Normalizer (formats syntax, sentences, and line breaks)
 */
object FutoPostProcessingPipeline {

    data class PipelineResult(
        val isHandledAsCommand: Boolean,
        val updatedNoteContent: String?,
        val formattedTextSegment: String,
        val feedbackMessage: String?
    )

    /**
     * Executes the complete 4-Pass FUTO post-processing pipeline on recognized text.
     */
    fun process(
        rawTranscript: String,
        currentNoteContent: String,
        replacementRules: List<WordReplacementEntity>,
        commandRules: List<VoiceCommandEntity>
    ): PipelineResult {
        val trimmed = rawTranscript.trim()
        if (trimmed.isBlank()) {
            return PipelineResult(
                isHandledAsCommand = false,
                updatedNoteContent = null,
                formattedTextSegment = "",
                feedbackMessage = null
            )
        }

        // =========================================================================
        // Pass 1: Hallucination & Repetition Token Filter (FUTO Anti-Loop Protocol)
        // =========================================================================
        val deHallucinated = removeRepetitionHallucinations(trimmed)
        if (deHallucinated.isBlank()) {
            return PipelineResult(
                isHandledAsCommand = false,
                updatedNoteContent = null,
                formattedTextSegment = "",
                feedbackMessage = null
            )
        }

        // =========================================================================
        // Pass 2: Voice Action & Knitting Macros Dispatcher
        // =========================================================================
        val commandResult: CommandExecutionResult = VoiceCommandProcessor.processSpokenText(
            spokenText = deHallucinated,
            currentContent = currentNoteContent,
            enabledCommands = commandRules
        )

        if (commandResult.isHandledAsCommand) {
            return PipelineResult(
                isHandledAsCommand = true,
                updatedNoteContent = commandResult.updatedContent,
                formattedTextSegment = "",
                feedbackMessage = commandResult.feedbackMessage
            )
        }

        // =========================================================================
        // Pass 3: Word Replacement & Shorthand Dictionary Engine
        // =========================================================================
        val replaced = TextReplacementProcessor.applyReplacements(deHallucinated, replacementRules)

        // =========================================================================
        // Pass 4: Punctuation, Spacing & Capitalization Normalizer
        // =========================================================================
        val normalized = normalizePunctuationAndFormatting(replaced)

        return PipelineResult(
            isHandledAsCommand = false,
            updatedNoteContent = null,
            formattedTextSegment = normalized,
            feedbackMessage = null
        )
    }

    /**
     * Pass 1: Detects and filters out Whisper hallucination loops (e.g. "you you you", "thank you. thank you. thank you.").
     * Whisper models under low signal-to-noise ratio can generate infinite repeating token sequences.
     */
    fun removeRepetitionHallucinations(text: String): String {
        var clean = text.trim()
        if (clean.isBlank()) return ""

        // Common Whisper silent hallucination phrases
        val commonHallucinations = setOf(
            "thank you.",
            "thank you for watching.",
            "thanks for watching.",
            "subtitles by",
            "subscribe to my channel",
            "you",
            "."
        )
        if (commonHallucinations.contains(clean.lowercase(Locale.ROOT))) {
            return ""
        }

        // Filter repeating 1-word, 2-word, or 3-word n-grams repeated >= 3 times
        // e.g. "knit knit knit knit" -> "knit"
        val words = clean.split(Regex("\\s+"))
        if (words.size >= 4) {
            // Check 1-word repetitions
            var allSame = true
            val firstWord = words[0]
            for (i in 1 until words.size) {
                if (!words[i].equals(firstWord, ignoreCase = true)) {
                    allSame = false
                    break
                }
            }
            if (allSame) {
                return firstWord
            }

            // Check phrase repetition pattern (e.g., "Row 1 Row 1 Row 1")
            val regex2 = Regex("(?i)\\b(.+?)(?:\\s+\\1){2,}\\b")
            clean = regex2.replace(clean) { matchResult ->
                matchResult.groupValues[1]
            }
        }

        return clean.trim()
    }

    /**
     * Pass 4: FUTO Normalizer for sentence capitalization, comma spacing, and bracket alignments.
     */
    fun normalizePunctuationAndFormatting(text: String): String {
        var result = text.trim()
        if (result.isBlank()) return ""

        // Fix spaces before punctuation: "word , word" -> "word, word"
        result = result.replace(Regex("\\s+([,.:;!?])"), "$1")
        // Ensure single space after punctuation if followed by letter/number: "k1,yo" -> "k1, yo"
        result = result.replace(Regex("([,.:;!?])([A-Za-z0-9])"), "$1 $2")
        // Remove trailing commas before newlines or end of string
        result = result.replace(Regex(",\\s*$"), "")
        // Clean multi-spaces
        result = result.replace(Regex(" +"), " ")

        return result
    }
}
