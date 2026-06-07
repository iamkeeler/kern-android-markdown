package com.example.modernandroidmarkdowneditor.analysis

import java.util.regex.Pattern
import com.example.modernandroidmarkdowneditor.utils.TextAnalysisUtils.countWords

enum class HighlightType {
    ADVERB,
    PASSIVE_VOICE,
    HARD_SENTENCE,
    VERY_HARD_SENTENCE,
    COMPLEX_WORD
}

data class HemingwayHighlight(
    val start: Int,
    val end: Int,
    val type: HighlightType,
    val suggestion: String? = null
)

data class HemingwayMetrics(
    val wordCount: Int,
    val charCount: Int,
    val sentenceCount: Int,
    val readabilityGrade: String,
    val adverbCount: Int,
    val passiveVoiceCount: Int,
    val hardSentenceCount: Int,
    val veryHardSentenceCount: Int,
    val highlights: List<HemingwayHighlight>
)

object HemingwayAnalyzer {

    private val adverbPattern = Pattern.compile("\\b(\\w+ly)\\b", Pattern.CASE_INSENSITIVE)
    
    // Simple helper regex for passive voice: helper verbs + verb ending in ed, or common irregular past participles
    private val passiveVoicePattern = Pattern.compile(
        "\\b(am|is|are|was|were|be|been|being)\\b\\s+(\\w+ed|done|taken|written|seen|known|given|made|shown|chosen|broken|eaten|drunk|gone|run)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val complexWords = mapOf(
        "utilize" to "use",
        "initialize" to "start",
        "terminate" to "end",
        "subsequent" to "next",
        "attempt" to "try",
        "require" to "need",
        "implement" to "build"
    )

    private val complexWordPatterns = complexWords.map { (complex, _) ->
        complex to Pattern.compile("\\b$complex\\b", Pattern.CASE_INSENSITIVE)
    }

    private val sentencePattern = Pattern.compile("([^.!?]+[.!?]*)")

    fun analyze(text: String): HemingwayMetrics {
        if (text.isBlank()) {
            return HemingwayMetrics(
                wordCount = 0,
                charCount = 0,
                sentenceCount = 0,
                readabilityGrade = "N/A",
                adverbCount = 0,
                passiveVoiceCount = 0,
                hardSentenceCount = 0,
                veryHardSentenceCount = 0,
                highlights = emptyList()
            )
        }

        val highlights = mutableListOf<HemingwayHighlight>()
        
        // 1. Basic counts
        val wordCount = countWords(text)
        // Character count excluding whitespaces for ARI calculation
        val charCount = text.filter { !it.isWhitespace() }.length

        // 2. Sentence Splitting & Sentence metrics
        // We'll split the text into sentences while tracking their original indices.
        val sentences = splitSentencesWithIndices(text)
        val sentenceCount = maxOf(1, sentences.size)

        var hardSentenceCount = 0
        var veryHardSentenceCount = 0

        for (sent in sentences) {
            val wCount = countWords(sent.text)
            if (wCount > 25) {
                veryHardSentenceCount++
                highlights.add(HemingwayHighlight(sent.start, sent.end, HighlightType.VERY_HARD_SENTENCE, "One of your sentences is very hard to read."))
            } else if (wCount >= 18) {
                hardSentenceCount++
                highlights.add(HemingwayHighlight(sent.start, sent.end, HighlightType.HARD_SENTENCE, "One of your sentences is hard to read."))
            }
        }

        // 3. Adverbs
        val adverbMatcher = adverbPattern.matcher(text)
        var adverbCount = 0
        while (adverbMatcher.find()) {
            val adverb = adverbMatcher.group(1)
            val lower = adverb?.lowercase() ?: ""
            if (lower.isNotEmpty() && lower != "only" && lower != "family" && lower != "early" && lower != "holy" && lower != "reply") {
                adverbCount++
                highlights.add(HemingwayHighlight(adverbMatcher.start(), adverbMatcher.end(), HighlightType.ADVERB, "Use a stronger verb instead of an adverb."))
            }
        }

        // 4. Passive Voice
        val passiveMatcher = passiveVoicePattern.matcher(text)
        var passiveVoiceCount = 0
        while (passiveMatcher.find()) {
            passiveVoiceCount++
            highlights.add(HemingwayHighlight(passiveMatcher.start(), passiveMatcher.end(), HighlightType.PASSIVE_VOICE, "Rewrite in active voice if possible."))
        }

        // 5. Complex words
        for ((complex, pattern) in complexWordPatterns) {
            val simple = complexWords[complex]
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                highlights.add(HemingwayHighlight(matcher.start(), matcher.end(), HighlightType.COMPLEX_WORD, "Simplify to: '$simple'"))
            }
        }

        // 6. Readability Grade Level (ARI)
        // Formula: 4.71 * (chars / words) + 0.5 * (words / sentences) - 21.43
        val ari = if (wordCount > 0 && sentenceCount > 0) {
            4.71 * (charCount.toDouble() / wordCount) + 0.5 * (wordCount.toDouble() / sentenceCount) - 21.43
        } else {
            0.0
        }
        val gradeLevel = when {
            ari <= 0 -> "N/A"
            ari >= 14 -> "Post-Graduate"
            else -> "Grade ${Math.round(ari)}"
        }

        return HemingwayMetrics(
            wordCount = wordCount,
            charCount = text.length,
            sentenceCount = sentences.size,
            readabilityGrade = gradeLevel,
            adverbCount = adverbCount,
            passiveVoiceCount = passiveVoiceCount,
            hardSentenceCount = hardSentenceCount,
            veryHardSentenceCount = veryHardSentenceCount,
            highlights = highlights.sortedBy { it.start }
        )
    }

    private data class SentenceSpan(val text: String, val start: Int, val end: Int)

    private fun splitSentencesWithIndices(text: String): List<SentenceSpan> {
        val spans = mutableListOf<SentenceSpan>()
        // Simple sentence boundary splitter: period, question mark, or exclamation followed by space or newline
        val matcher = sentencePattern.matcher(text)
        
        while (matcher.find()) {
            val sentenceText = matcher.group()
            val start = matcher.start()
            val end = matcher.end()
            if (sentenceText.trim().isNotEmpty()) {
                spans.add(SentenceSpan(sentenceText, start, end))
            }
        }
        
        if (spans.isEmpty() && text.isNotEmpty()) {
            spans.add(SentenceSpan(text, 0, text.length))
        }
        
        return spans
    }
}
