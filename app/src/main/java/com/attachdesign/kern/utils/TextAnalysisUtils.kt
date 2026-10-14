package com.attachdesign.kern.utils

object TextAnalysisUtils {
    fun countWords(s: CharSequence): Int {
        var count = 0
        var inWord = false
        for (i in 0 until s.length) {
            val c = s[i]
            if (c.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    fun countSentences(text: CharSequence): Int =
        com.attachdesign.kern.analysis.HemingwayAnalyzer.countSentences(text)

    fun calculateAriGrade(wordCount: Int, charCount: Int, sentenceCount: Int): String {
        if (wordCount <= 0 || sentenceCount <= 0) return "N/A"
        val ari = 4.71 * (charCount.toDouble() / wordCount) + 0.5 * (wordCount.toDouble() / sentenceCount) - 21.43
        return when {
            ari <= 0 -> "N/A"
            ari >= 14 -> "Post-Graduate"
            else -> "Grade ${Math.round(ari)}"
        }
    }
}
