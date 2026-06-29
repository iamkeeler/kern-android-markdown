package com.attachdesign.kern.analysis

import java.util.regex.Pattern
import com.attachdesign.kern.utils.TextAnalysisUtils.countWords

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
    private val excludedAdverbs = setOf("only", "family", "early", "holy", "reply")
    
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

    private val complexWordsPattern = Pattern.compile(
        "\\b(${complexWords.keys.joinToString("|")})\\b",
        Pattern.CASE_INSENSITIVE
    )

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
        // Character count excluding whitespaces for ARI calculation without allocations
        val charCount = text.count { !it.isWhitespace() }

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
            if (lower.isNotEmpty() && !excludedAdverbs.contains(lower)) {
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

        // 5. Complex words using single-pass regex matcher
        val complexMatcher = complexWordsPattern.matcher(text)
        while (complexMatcher.find()) {
            val matchedWord = complexMatcher.group(1)?.lowercase() ?: ""
            val simple = complexWords[matchedWord]
            highlights.add(HemingwayHighlight(complexMatcher.start(), complexMatcher.end(), HighlightType.COMPLEX_WORD, "Simplify to: '$simple'"))
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
        if (text.isBlank()) return emptyList()

        val abbreviations = setOf(
            "Mr.", "Mrs.", "Ms.", "Dr.", "Prof.", "Sr.", "Jr.",
            "e.g.", "i.e.", "etc.", "vs.", "St.", "Ltd.", "Co."
        )

        val spans = mutableListOf<SentenceSpan>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                // 1) Don't split decimal numbers: digit . digit
                val isDecimal = c == '.' &&
                    i > 0 && i < text.lastIndex &&
                    text[i - 1].isDigit() && text[i + 1].isDigit()
                if (isDecimal) {
                    i++
                    continue
                }

                // 2) Don't split for common abbreviations: check the token ending at i
                val tokenStart = (text.lastIndexOf(' ', i - 1) + 1).coerceAtLeast(0)
                val token = text.substring(tokenStart, i + 1) // includes the dot
                if (abbreviations.any { token.endsWith(it, ignoreCase = true) }) {
                    i++
                    continue
                }

                // 3) Determine if punctuation is followed by whitespace + uppercase letter (likely sentence boundary)
                val nextIndex = (i + 1).let { if (it < text.length) it else -1 }
                var followedBySentenceStart = false
                if (nextIndex == -1) {
                    followedBySentenceStart = true // punctuation at end of text
                } else {
                    // skip whitespace
                    var j = nextIndex
                    while (j < text.length && text[j].isWhitespace()) j++
                    if (j < text.length) {
                        followedBySentenceStart = text[j].isUpperCase() || text[j].isDigit()
                    } else {
                        followedBySentenceStart = true // trailing spaces after punctuation at end
                    }
                }

                if (followedBySentenceStart) {
                    val end = i + 1
                    val sentenceText = text.substring(start, end)
                    spans.add(SentenceSpan(sentenceText, start, end))
                    // advance start to first non-space after i
                    var k = end
                    while (k < text.length && text[k].isWhitespace()) k++
                    start = k
                    i = start
                    continue
                }
            }
            i++
        }

        if (start < text.length) {
            val sentenceText = text.substring(start)
            if (sentenceText.isNotBlank()) {
                spans.add(SentenceSpan(sentenceText, start, text.length))
            }
        }

        return spans
    }
}
