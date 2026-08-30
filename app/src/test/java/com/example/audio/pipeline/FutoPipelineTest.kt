package com.example.audio.pipeline

import com.example.audio.vad.SileroVadDetector
import com.example.audio.vad.VadTransition
import com.example.data.db.VoiceCommandEntity
import com.example.data.db.WordReplacementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FutoPipelineTest {

    @Test
    fun testHallucinationRemoval() {
        val repetitive = "knit knit knit knit"
        val cleaned = FutoPostProcessingPipeline.removeRepetitionHallucinations(repetitive)
        assertEquals("knit", cleaned)

        val phantomYoutube = "Thank you for watching."
        val cleanedPhantom = FutoPostProcessingPipeline.removeRepetitionHallucinations(phantomYoutube)
        assertEquals("", cleanedPhantom)
    }

    @Test
    fun testPunctuationAndSpacingNormalization() {
        val unformatted = "k1 , yo , ssk , "
        val normalized = FutoPostProcessingPipeline.normalizePunctuationAndFormatting(unformatted)
        assertEquals("k1, yo, ssk", normalized)

        val unspacedComma = "Row 1: k1,yo,k2tog"
        val formatted = FutoPostProcessingPipeline.normalizePunctuationAndFormatting(unspacedComma)
        assertEquals("Row 1: k1, yo, k2tog", formatted)
    }

    @Test
    fun testCompletePipelineWithCommand() {
        val rules = listOf(
            WordReplacementEntity(1, "knit one", "k1", true, false, "Stitches", System.currentTimeMillis()),
            WordReplacementEntity(2, "yarn over", "yo", true, false, "Stitches", System.currentTimeMillis())
        )
        val commands = listOf(
            VoiceCommandEntity(
                id = 1,
                commandType = "NEXT_ROW",
                triggerPhrase = "next row",
                displayName = "Next Row",
                description = "Start next row with auto-incremented row count",
                category = "Navigation",
                isEnabled = true
            )
        )

        val result = FutoPostProcessingPipeline.process(
            rawTranscript = "next row",
            currentNoteContent = "Row 1: k1, yo, k1",
            replacementRules = rules,
            commandRules = commands
        )

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: k1, yo, k1\nRow 2: ", result.updatedNoteContent)
    }

    @Test
    fun testSileroVadHysteresis() {
        val vad = SileroVadDetector(speechStartThreshold = 0.5f, speechEndThreshold = 0.35f, minSilenceDurationMs = 100L)
        val frame = FloatArray(SileroVadDetector.FRAME_SIZE_SAMPLES) { 0.0f }

        val result = vad.processFrame(frame)
        assertFalse(result.isSpeech)
        assertEquals(VadTransition.NONE, result.transition)
    }
}
