package com.attachdesign.kern.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HemingwayAnalyzerTest {

    @Test
    fun testBasicMetrics() {
        val text = "This is a simple sentence. It has some words."
        val metrics = HemingwayAnalyzer.analyze(text)
        assertEquals(9, metrics.wordCount)
        assertEquals(2, metrics.sentenceCount)
        assertEquals(0, metrics.adverbCount)
        assertEquals(0, metrics.passiveVoiceCount)
        assertEquals(0, metrics.hardSentenceCount)
        assertEquals(0, metrics.veryHardSentenceCount)
    }

    @Test
    fun testAdverbs() {
        val text = "He quickly ran to the store. It was only a test, reply immediately."
        val metrics = HemingwayAnalyzer.analyze(text)
        // "quickly" and "immediately" are adverbs.
        // "only" and "reply" are in the excluded set.
        assertEquals(2, metrics.adverbCount)
        
        val adverbs = metrics.highlights.filter { it.type == HighlightType.ADVERB }
        assertEquals(2, adverbs.size)
        assertEquals("quickly", text.substring(adverbs[0].start, adverbs[0].end))
        assertEquals("immediately", text.substring(adverbs[1].start, adverbs[1].end))
    }

    @Test
    fun testPassiveVoice() {
        val text = "The work was done by the team. He was seen at the park."
        val metrics = HemingwayAnalyzer.analyze(text)
        assertEquals(2, metrics.passiveVoiceCount)
        
        val passive = metrics.highlights.filter { it.type == HighlightType.PASSIVE_VOICE }
        assertEquals(2, passive.size)
        assertEquals("was done", text.substring(passive[0].start, passive[0].end))
        assertEquals("was seen", text.substring(passive[1].start, passive[1].end))
    }

    @Test
    fun testComplexWords() {
        val text = "Please utilize the simple tools to implement the feature."
        val metrics = HemingwayAnalyzer.analyze(text)
        
        val complex = metrics.highlights.filter { it.type == HighlightType.COMPLEX_WORD }
        assertEquals(2, complex.size)
        assertEquals("utilize", text.substring(complex[0].start, complex[0].end))
        assertEquals("implement", text.substring(complex[1].start, complex[1].end))
    }

    @Test
    fun testSentenceHardness() {
        // Very hard sentence: > 25 words
        val veryHardText = "This is a very long sentence that has more than twenty five words in order to trigger the very hard sentence highlight rules of the Hemingway analyzer."
        // Hard sentence: >= 18 words and <= 25 words
        val hardText = "This is a moderately long sentence that has at least eighteen words to trigger the hard sentence highlight."
        
        val metrics = HemingwayAnalyzer.analyze("$veryHardText $hardText")
        assertEquals(1, metrics.veryHardSentenceCount)
        assertEquals(1, metrics.hardSentenceCount)
    }

    @Test
    fun testDecimalAndAbbreviations() {
        // Decimal numbers like 3.14 and abbreviations like Mr. and Dr. should not split sentences.
        val text = "Mr. Smith bought 3.14 lbs of apples from Dr. Jones. It was delicious!"
        val metrics = HemingwayAnalyzer.analyze(text)
        
        // Should be 2 sentences:
        // 1. "Mr. Smith bought 3.14 lbs of apples from Dr. Jones."
        // 2. "It was delicious!"
        assertEquals(2, metrics.sentenceCount)
    }
}
