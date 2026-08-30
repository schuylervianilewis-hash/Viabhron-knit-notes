package com.example.audio.replacement

import com.example.data.db.WordReplacementEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TextReplacementProcessorTest {

    @Test
    fun testKnittingWordReplacements() {
        val rules = listOf(
            WordReplacementEntity(id = 1, targetPhrase = "yarn over", replacementPhrase = "yo", isEnabled = true),
            WordReplacementEntity(id = 2, targetPhrase = "knit 1", replacementPhrase = "k1", isEnabled = true),
            WordReplacementEntity(id = 3, targetPhrase = "knit one", replacementPhrase = "k1", isEnabled = true),
            WordReplacementEntity(id = 4, targetPhrase = "purl 2", replacementPhrase = "p2", isEnabled = true),
            WordReplacementEntity(id = 5, targetPhrase = "make 1", replacementPhrase = "m1", isEnabled = true),
            WordReplacementEntity(id = 6, targetPhrase = "slip slip knit", replacementPhrase = "ssk", isEnabled = true),
            WordReplacementEntity(id = 7, targetPhrase = "knit two together", replacementPhrase = "k2tog", isEnabled = true)
        )

        val input1 = "yarn over and knit 1 then purl 2"
        val output1 = TextReplacementProcessor.applyReplacements(input1, rules)
        assertEquals("yo and k1 then p2", output1)

        val input2 = "make 1, slip slip knit, then knit two together"
        val output2 = TextReplacementProcessor.applyReplacements(input2, rules)
        assertEquals("m1, ssk, then k2tog", output2)

        val input3 = "knit one then yarn over."
        val output3 = TextReplacementProcessor.applyReplacements(input3, rules)
        assertEquals("k1 then yo.", output3)
    }

    @Test
    fun testSequenceCountingAggregation() {
        val rules = listOf(
            WordReplacementEntity(id = 1, targetPhrase = "knit", replacementPhrase = "k", category = "Sequence", isEnabled = true),
            WordReplacementEntity(id = 2, targetPhrase = "make", replacementPhrase = "m", category = "Sequence", isEnabled = true),
            WordReplacementEntity(id = 3, targetPhrase = "purl", replacementPhrase = "p", category = "Sequence", isEnabled = true),
            WordReplacementEntity(id = 4, targetPhrase = "knit 1", replacementPhrase = "k1", category = "Knitting", isEnabled = true),
            WordReplacementEntity(id = 5, targetPhrase = "purl 2", replacementPhrase = "p2", category = "Knitting", isEnabled = true)
        )

        // Test 1: Ascending digit sequence
        val input1 = "knit 1 knit 2 knit 3 knit 4 knit 5"
        val output1 = TextReplacementProcessor.applyReplacements(input1, rules)
        assertEquals("k5", output1)

        // Test 2: Spoken number words
        val input2 = "knit one knit two knit three knit four knit five"
        val output2 = TextReplacementProcessor.applyReplacements(input2, rules)
        assertEquals("k5", output2)

        // Test 3: Make sequence
        val input3 = "make 1 make 2"
        val output3 = TextReplacementProcessor.applyReplacements(input3, rules)
        assertEquals("m2", output3)

        // Test 4: Spoken words make
        val input4 = "make one make two"
        val output4 = TextReplacementProcessor.applyReplacements(input4, rules)
        assertEquals("m2", output4)

        // Test 5: Mixed sentence with sequences and punctuation
        val input5 = "Row 1: knit 1, knit 2, knit 3, knit 4, knit 5, purl 1, purl 2, knit 1"
        val output5 = TextReplacementProcessor.applyReplacements(input5, rules)
        assertEquals("Row 1: k5, p2, k1", output5)
    }

    @Test
    fun testSequenceCountingOffByDefault() {
        val rules = listOf(
            WordReplacementEntity(id = 1, targetPhrase = "knit", replacementPhrase = "k", category = "Sequence", isEnabled = false),
            WordReplacementEntity(id = 2, targetPhrase = "make", replacementPhrase = "m", category = "Sequence", isEnabled = false),
            WordReplacementEntity(id = 3, targetPhrase = "knit 1", replacementPhrase = "k1", category = "Knitting", isEnabled = false)
        )

        val input = "knit 1 knit 2 knit 3 knit 4 knit 5"
        val output = TextReplacementProcessor.applyReplacements(input, rules)
        assertEquals("knit 1 knit 2 knit 3 knit 4 knit 5", output)
    }

    @Test
    fun testDisabledRulesIgnored() {
        val rules = listOf(
            WordReplacementEntity(id = 1, targetPhrase = "yarn over", replacementPhrase = "yo", isEnabled = false),
            WordReplacementEntity(id = 2, targetPhrase = "knit 1", replacementPhrase = "k1", isEnabled = true)
        )

        val input = "yarn over and knit 1"
        val output = TextReplacementProcessor.applyReplacements(input, rules)
        assertEquals("yarn over and k1", output)
    }
}
