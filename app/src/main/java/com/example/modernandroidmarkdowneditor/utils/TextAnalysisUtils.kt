package com.example.modernandroidmarkdowneditor.utils

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
}
