package com.example.audio.command

import com.example.data.db.VoiceCommandEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceCommandProcessorTest {

    private lateinit var enabledCommands: List<VoiceCommandEntity>

    @Before
    fun setUp() {
        enabledCommands = listOf(
            VoiceCommandEntity(commandType = "NEXT_ROW", triggerPhrase = "next row", displayName = "Next Row", description = "Next row", isEnabled = true),
            VoiceCommandEntity(commandType = "NEXT_ROW", triggerPhrase = "next round", displayName = "Next Round", description = "Next round", isEnabled = true),
            VoiceCommandEntity(commandType = "NEXT_LINE", triggerPhrase = "next line", displayName = "Next Line", description = "Next line", isEnabled = true),
            VoiceCommandEntity(commandType = "REPEAT_LAST_STITCH", triggerPhrase = "repeat last stitch", displayName = "Repeat Stitch", description = "Repeat stitch", isEnabled = true),
            VoiceCommandEntity(commandType = "REPEAT_LAST_STITCH", triggerPhrase = "repeat last", displayName = "Repeat Last", description = "Repeat last", isEnabled = true),
            VoiceCommandEntity(commandType = "REPEAT_LAST_GROUP", triggerPhrase = "repeat last group", displayName = "Repeat Group", description = "Repeat group", isEnabled = true),
            VoiceCommandEntity(commandType = "REPEAT_LAST_GROUP", triggerPhrase = "repeat group", displayName = "Repeat Group", description = "Repeat group", isEnabled = true),
            VoiceCommandEntity(commandType = "UNDO_LAST", triggerPhrase = "undo last", displayName = "Undo", description = "Undo", isEnabled = true),
            VoiceCommandEntity(commandType = "UNDO_LAST", triggerPhrase = "undo", displayName = "Undo", description = "Undo", isEnabled = true),
            VoiceCommandEntity(commandType = "INSERT_STAR", triggerPhrase = "asterisk", displayName = "Star", description = "Star", isEnabled = true),
            VoiceCommandEntity(commandType = "INSERT_COMMA", triggerPhrase = "comma", displayName = "Comma", description = "Comma", isEnabled = true),
            VoiceCommandEntity(commandType = "INSERT_PERIOD", triggerPhrase = "period", displayName = "Period", description = "Period", isEnabled = true)
        )
    }

    @Test
    fun testNextRowCalculatesCorrectIncrement() {
        val initialText = "Row 1: k2, p2, yo"
        val result = VoiceCommandProcessor.processSpokenText("next row", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: k2, p2, yo\nRow 2: ", result.updatedContent)
    }

    @Test
    fun testNextRoundCalculatesIncrement() {
        val initialText = "Round 3: k1, yo, k2tog"
        val result = VoiceCommandProcessor.processSpokenText("next round", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Round 3: k1, yo, k2tog\nRound 4: ", result.updatedContent)
    }

    @Test
    fun testNextLineAppendsNewline() {
        val initialText = "k2, p2"
        val result = VoiceCommandProcessor.processSpokenText("next line", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("k2, p2\n", result.updatedContent)
    }

    @Test
    fun testRepeatLastStitchSingleTime() {
        val initialText = "Row 1: k2, p2, yo"
        val result = VoiceCommandProcessor.processSpokenText("repeat last stitch", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: k2, p2, yo, yo", result.updatedContent)
    }

    @Test
    fun testRepeatLastStitchMultipleTimesWithWord() {
        val initialText = "Row 1: k2, p2, k1"
        val result = VoiceCommandProcessor.processSpokenText("repeat last stitch 3 times", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: k2, p2, k1, k1, k1, k1", result.updatedContent)
    }

    @Test
    fun testRepeatLastGroupBracketed() {
        val initialText = "Row 1: [k1, yo, ssk]"
        val result = VoiceCommandProcessor.processSpokenText("repeat last group 2 times", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: [k1, yo, ssk], k1, yo, ssk, k1, yo, ssk", result.updatedContent)
    }

    @Test
    fun testRepeatLastTwoStitches() {
        val initialText = "Row 1: yo, ssk, p2"
        val result = VoiceCommandProcessor.processSpokenText("repeat last 2 stitches 2 times", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: yo, ssk, p2, ssk, p2, ssk, p2", result.updatedContent)
    }

    @Test
    fun testUndoLastToken() {
        val initialText = "Row 1: k2, p2, yo, ssk"
        val result = VoiceCommandProcessor.processSpokenText("undo last", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: k2, p2, yo", result.updatedContent)
    }

    @Test
    fun testInsertAsterisk() {
        val initialText = "Row 1: k2,"
        val result = VoiceCommandProcessor.processSpokenText("asterisk", initialText, enabledCommands)

        assertTrue(result.isHandledAsCommand)
        assertEquals("Row 1: k2, * ", result.updatedContent)
    }

    @Test
    fun testNonCommandFallsThrough() {
        val initialText = "Row 1: k2"
        val result = VoiceCommandProcessor.processSpokenText("cable four front", initialText, enabledCommands)

        assertFalse(result.isHandledAsCommand)
        assertEquals("Row 1: k2", result.updatedContent)
    }
}
