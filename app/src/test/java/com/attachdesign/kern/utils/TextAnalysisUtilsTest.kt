package com.attachdesign.kern.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TextAnalysisUtilsTest {

    @Test
    fun testCountWords() {
        assertEquals(0, TextAnalysisUtils.countWords(""))
        assertEquals(0, TextAnalysisUtils.countWords("   \n\t  "))
        assertEquals(1, TextAnalysisUtils.countWords("Hello"))
        assertEquals(5, TextAnalysisUtils.countWords("The quick brown fox jumps"))
        assertEquals(4, TextAnalysisUtils.countWords("Line one\nLine two"))
    }

    @Test
    fun testCountSentences() {
        assertEquals(0, TextAnalysisUtils.countSentences(""))
        assertEquals(0, TextAnalysisUtils.countSentences("   "))
        assertEquals(1, TextAnalysisUtils.countSentences("This is a simple sentence."))
        assertEquals(2, TextAnalysisUtils.countSentences("First sentence. Second sentence!"))
        // Abbreviations and decimals should not split sentences
        assertEquals(2, TextAnalysisUtils.countSentences("Dr. Jones paid \$3.14 for apples. It was a good deal."))
    }

    @Test
    fun testCalculateAriGrade() {
        assertEquals("N/A", TextAnalysisUtils.calculateAriGrade(wordCount = 0, charCount = 0, sentenceCount = 0))
        assertEquals("N/A", TextAnalysisUtils.calculateAriGrade(wordCount = 10, charCount = 10, sentenceCount = 0))
        assertEquals("N/A", TextAnalysisUtils.calculateAriGrade(wordCount = 0, charCount = 10, sentenceCount = 1))

        // Negative ARI -> N/A
        // 4.71 * (1 / 100) + 0.5 * (100 / 100) - 21.43 < 0
        assertEquals("N/A", TextAnalysisUtils.calculateAriGrade(wordCount = 100, charCount = 1, sentenceCount = 100))

        // Post-Graduate -> >= 14
        // ARI formula: 4.71 * (charCount / wordCount) + 0.5 * (wordCount / sentenceCount) - 21.43
        // If wordCount = 100, charCount = 800, sentenceCount = 1:
        // 4.71 * 8.0 + 0.5 * 100 - 21.43 = 37.68 + 50 - 21.43 = 66.25 >= 14
        assertEquals("Post-Graduate", TextAnalysisUtils.calculateAriGrade(wordCount = 100, charCount = 800, sentenceCount = 1))

        // Standard grade level:
        // E.g., wordCount = 20, charCount = 100, sentenceCount = 2:
        // 4.71 * (100 / 20) + 0.5 * (20 / 2) - 21.43 = 4.71 * 5 + 5 - 21.43 = 23.55 + 5 - 21.43 = 7.12 -> Math.round is 7
        assertEquals("Grade 7", TextAnalysisUtils.calculateAriGrade(wordCount = 20, charCount = 100, sentenceCount = 2))
    }
}
