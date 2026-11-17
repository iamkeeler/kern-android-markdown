package com.attachdesign.kern.parser

data class DocumentTypingPreferences(
    val autoHeaderSpacing: Boolean = true,
    val autoCompleteEnabled: Boolean = true,
    val quotes: Boolean = true,
    val singleQuotes: Boolean = true,
    val braces: Boolean = true,
    val parens: Boolean = true,
    val brackets: Boolean = true
) {
    fun closingFor(opening: Char): Char? = if (!autoCompleteEnabled) null else when (opening) {
        '(' -> ')'.takeIf { parens }
        '[' -> ']'.takeIf { brackets }
        '{' -> '}'.takeIf { braces }
        '"' -> '"'.takeIf { quotes }
        '\'' -> '\''.takeIf { singleQuotes }
        '“' -> '”'.takeIf { quotes }
        '‘' -> '’'.takeIf { singleQuotes }
        else -> null
    }

    fun openingFor(closing: Char): Char? {
        val opening = when (closing) {
            ')' -> '('; ']' -> '['; '}' -> '{'; '"' -> '"'; '\'' -> '\''; '”' -> '“'; '’' -> '‘'
            else -> return null
        }
        return opening.takeIf { closingFor(it) == closing }
    }
}

data class DocumentTypingEdit(
    val start: Int,
    val end: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart
)

/** Returns a local edit in the buffer after the user's single-character change. */
object DocumentTypingEngine {
    fun transform(
        source: CharSequence,
        start: Int,
        end: Int,
        inserted: String,
        selectionStart: Int,
        selectionEnd: Int,
        preferences: DocumentTypingPreferences,
        committedComposition: Boolean = false,
        selectionAfterStart: Int = start + inserted.length,
        selectionAfterEnd: Int = selectionAfterStart
    ): DocumentTypingEdit? {
        if (start !in 0..source.length || end !in start..source.length) return null
        if (inserted.length > 1 && !committedComposition) return null
        val lineStart = source.lastIndexOf('\n', start - 1) + 1
        val edit = when {
            selectionStart != selectionEnd -> {
                if (start != minOf(selectionStart, selectionEnd) || end != maxOf(selectionStart, selectionEnd)) return null
                val opening = inserted.singleOrNull() ?: return null
                val closing = preferences.closingFor(opening) ?: return null
                val selected = source.subSequence(start, end)
                DocumentTypingEdit(start, start + 1, "$opening$selected$closing",
                    selectionStart + 1, selectionEnd + 1)
            }
            end == start + 1 && inserted.isEmpty() && selectionStart == end -> {
                val closing = preferences.closingFor(source[start]) ?: return null
                if (source.getOrNull(end) != closing) return null
                DocumentTypingEdit(start, start + 1, "", start)
            }
            start == end && inserted.length == 1 && selectionStart == start -> {
                val typed = inserted.single()
                val closing = preferences.closingFor(typed)
                when {
                    source.getOrNull(start) == typed && hasUnclosedOpening(source, lineStart, start, typed, preferences) ->
                        DocumentTypingEdit(start, start + 1, "", start + 1)
                    closing != null && canOpenPair(source, start, typed) ->
                        DocumentTypingEdit(start + 1, start + 1, closing.toString(), start + 1)
                    else -> null
                }
            }
            else -> null
        } ?: if (preferences.autoHeaderSpacing && selectionStart == selectionEnd && inserted.isNotEmpty()) {
            headingSpacing(source, lineStart, start, end, inserted, selectionAfterStart, selectionAfterEnd)
        } else null
        if (edit == null) return null
        if (isEscaped(source, start) || MarkdownEditorEngine.isOffsetInCodeBlock(source, start) ||
            isInlineCode(source, lineStart, start)) return null
        return edit
    }

    private fun headingSpacing(source: CharSequence, lineStart: Int, start: Int, end: Int,
        inserted: String, selectionStart: Int, selectionEnd: Int): DocumentTypingEdit? {
        var markerStart = lineStart
        while (markerStart < source.length && markerStart - lineStart < 4 && source[markerStart] == ' ') markerStart++
        if (markerStart - lineStart > 3) return null
        var markerEnd = markerStart
        while (markerEnd < source.length && markerEnd - markerStart < 7 && source[markerEnd] == '#') markerEnd++
        if (markerEnd - markerStart !in 1..6 || markerEnd > start) return null
        val next = if (markerEnd == start) inserted.firstOrNull() ?: source.getOrNull(end) else source.getOrNull(markerEnd)
        if (next == null || next == '#' || next.isWhitespace()) return null
        return DocumentTypingEdit(markerEnd, markerEnd, " ",
            selectionStart + if (selectionStart >= markerEnd) 1 else 0,
            selectionEnd + if (selectionEnd >= markerEnd) 1 else 0)
    }

    private fun canOpenPair(source: CharSequence, offset: Int, typed: Char): Boolean {
        if (source.getOrNull(offset)?.isLetterOrDigit() == true) return false
        if (typed in "\"'“‘" && source.getOrNull(offset - 1)?.isLetterOrDigit() == true) return false
        return true
    }

    private fun hasUnclosedOpening(
        source: CharSequence, lineStart: Int, offset: Int, closing: Char, preferences: DocumentTypingPreferences
    ): Boolean {
        val opening = preferences.openingFor(closing) ?: return false
        var depth = 0
        var escaped = false
        for (index in lineStart until offset) {
            if (escaped) { escaped = false; continue }
            if (source[index] == '\\') { escaped = true; continue }
            when (source[index]) {
                opening -> depth++
                closing -> depth--
            }
        }
        return if (opening == closing) depth % 2 == 1 else depth > 0
    }

    private fun isInlineCode(source: CharSequence, start: Int, end: Int): Boolean {
        var delimiter = 0
        var index = start
        while (index < end) {
            if (source[index] != '`' || isEscaped(source, index)) { index++; continue }
            val runStart = index
            while (index < end && source[index] == '`') index++
            val run = index - runStart
            if (delimiter == 0) delimiter = run else if (delimiter == run) delimiter = 0
        }
        return delimiter != 0
    }

    private fun isEscaped(source: CharSequence, offset: Int): Boolean {
        var index = offset - 1
        while (index >= 0 && source[index] == '\\') index--
        return (offset - index - 1) % 2 == 1
    }
}
