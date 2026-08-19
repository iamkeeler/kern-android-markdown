package com.attachdesign.kern.parser

object MarkdownEditorEngine {

    private val blockquoteRegex = Regex("^(\\s*>\\s?)")

    // Empty checks (lines containing only the formatting prefix and trailing spaces)
    private val emptyBlockquoteRegex = Regex("^(\\s*>\\s*)$")

    data class ContinuationResult(
        val isContinuation: Boolean,
        val newCurrentText: String,
        val nextLinePrefix: String,
        val isExit: Boolean
    )

    data class DocumentContinuation(
        val markerStart: Int,
        val markerEnd: Int,
        val nextPrefix: String?
    )

    fun checkContinuation(line: String): ContinuationResult {
        val marker = MarkdownListSyntax.parse(line)
        if (marker != null) {
            val content = line.substring(marker.contentStart)
            if (content.isBlank()) {
                return ContinuationResult(isContinuation = true, newCurrentText = "", nextLinePrefix = "", isExit = true)
            }
            return ContinuationResult(
                isContinuation = true,
                newCurrentText = line,
                nextLinePrefix = marker.nextItemPrefix(),
                isExit = false
            )
        }

        // Blockquotes are intentionally not list syntax, but retain the existing continuation behavior.
        if (emptyBlockquoteRegex.matches(line)) {
            return ContinuationResult(isContinuation = true, newCurrentText = "", nextLinePrefix = "", isExit = true)
        }
        val blockquoteMatch = blockquoteRegex.find(line)
        if (blockquoteMatch != null) {
            val prefix = blockquoteMatch.value
            val nextPrefix = if (prefix.endsWith(" ")) prefix else "$prefix "
            return ContinuationResult(isContinuation = true, newCurrentText = line, nextLinePrefix = nextPrefix, isExit = false)
        }

        return ContinuationResult(isContinuation = false, newCurrentText = line, nextLinePrefix = "", isExit = false)
    }

    /**
     * Returns the local edit to apply after a single Enter press in the document editor.
     * Pasted or multi-line edits are deliberately left to the platform unchanged.
     */
    fun continueDocumentList(source: String, newlineOffset: Int, lineBreak: String): DocumentContinuation? {
        if (lineBreak != "\n" && lineBreak != "\r\n") return null
        val offset = newlineOffset.coerceIn(0, source.length)
        if (isInsideCodeBlock(source, offset)) return null

        val lineStart = source.lastIndexOf('\n', offset - 1).let { if (it == -1) 0 else it + 1 }
        val lineEnd = source.indexOf('\n', offset).let { if (it == -1) source.length else it }
        val line = source.substring(lineStart, lineEnd).removeSuffix("\r")
        if (MarkdownParser.isThematicBreak(line)) return null

        val marker = MarkdownListSyntax.parse(line) ?: return null
        val content = line.substring(marker.contentStart)
        return if (content.isBlank()) {
            DocumentContinuation(
                markerStart = lineStart,
                markerEnd = lineStart + marker.contentStart,
                nextPrefix = null
            )
        } else {
            DocumentContinuation(
                markerStart = lineStart,
                markerEnd = lineStart + marker.contentStart,
                nextPrefix = marker.nextItemPrefix()
            )
        }
    }

    private fun isInsideCodeBlock(source: String, offset: Int): Boolean {
        var blockStart = 0
        MarkdownDocumentScanner.scan(source).forEach { block ->
            val blockEnd = blockStart + block.rawText.length
            if (offset in blockStart..blockEnd) return block.blockType == MarkdownBlockType.CODE_BLOCK
            blockStart = blockEnd + block.separatorAfter.length
        }
        return false
    }

    data class TransformResult(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    fun handleTextChange(
        oldText: String,
        oldSelStart: Int,
        oldSelEnd: Int,
        newText: String,
        newSelStart: Int,
        newSelEnd: Int,
        autoHeaderSpacing: Boolean,
        autoCompleteEnabled: Boolean = true,
        autoCompleteQuotes: Boolean = true,
        autoCompleteSingleQuotes: Boolean = true,
        autoCompleteBraces: Boolean = true,
        autoCompleteParens: Boolean = true,
        autoCompleteBrackets: Boolean = true,
        sentenceCapitalization: Boolean = true
    ): TransformResult {
        // Selection wrapping: wraps active selection in matching delimiters
        val wasSelectionActive = oldSelStart != oldSelEnd
        if (wasSelectionActive && autoCompleteEnabled) {
            val replacedLength = oldSelEnd - oldSelStart
            if (newText.length == oldText.length - replacedLength + 1) {
                val typedChar = newText.getOrNull(oldSelStart)
                if (typedChar != null) {
                    val closeSym = when (typedChar) {
                        '(' -> if (autoCompleteParens) ")" else null
                        '[' -> if (autoCompleteBrackets) "]" else null
                        '{' -> if (autoCompleteBraces) "}" else null
                        '"' -> if (autoCompleteQuotes) "\"" else null
                        '\'' -> if (autoCompleteSingleQuotes) "'" else null
                        '*' -> "*"
                        '_' -> "_"
                        '`' -> "`"
                        '~' -> "~"
                        else -> null
                    }
                    if (closeSym != null) {
                        val openSym = typedChar.toString()
                        val originalSelectedText = oldText.substring(oldSelStart, oldSelEnd)
                        val wrappedText = oldText.substring(0, oldSelStart) + openSym + originalSelectedText + closeSym + oldText.substring(oldSelEnd)
                        val newSelStartOut = oldSelStart + openSym.length
                        val newSelEndOut = oldSelStart + openSym.length + originalSelectedText.length
                        return TransformResult(wrappedText, newSelStartOut, newSelEndOut)
                    }
                }
            }
        }

        // Normal typing insertion (only run auto-formatting on insertion of characters)
        val isInsertion = newText.length > oldText.length && newSelStart == newSelEnd
        if (!isInsertion) {
            return TransformResult(newText, newSelStart, newSelEnd)
        }

        var textState = newText
        var cursorState = newSelStart

        // 1. Auto Header Spacing: "#Text" -> "# Text"
        if (autoHeaderSpacing) {
            val headerMatch = Regex("^(#+)([^#\\s])").find(textState)
            if (headerMatch != null) {
                val hashes = headerMatch.groupValues[1]
                val headerLen = hashes.length
                textState = hashes + " " + textState.substring(headerLen)
                if (cursorState > headerLen) {
                    cursorState += 1
                }
            }
        }


        // 1.5 Auto Sentence Capitalization
        if (sentenceCapitalization && isInsertion && newText.length == oldText.length + 1) {
            val insertedChar = textState[cursorState - 1]
            if (insertedChar.isLowerCase()) {
                // Check if it's the start of the text block or start of a sentence
                var shouldCapitalize = false

                // If it's the very first character of the string
                if (cursorState == 1) {
                    shouldCapitalize = true
                } else {
                    // Check backwards to find if we're at the start of a sentence
                    // A sentence start is: [ . | ! | ? ] followed by whitespace(s)
                    var i = cursorState - 2

                    // Skip any leading whitespaces
                    while (i >= 0 && textState[i].isWhitespace()) {
                        i--
                    }

                    if (i < 0) {
                        // All whitespaces before this character, so it's start of paragraph
                        shouldCapitalize = true
                    } else {
                        val prevNonSpace = textState[i]
                        if (prevNonSpace == '.' || prevNonSpace == '!' || prevNonSpace == '?') {
                            shouldCapitalize = true
                        }
                    }
                }

                if (shouldCapitalize) {
                    textState = textState.substring(0, cursorState - 1) + insertedChar.uppercaseChar() + textState.substring(cursorState)
                }
            }
        }

        // 2. Smart Typography Heuristics
        if (cursorState > 0 && cursorState <= textState.length) {
            val char = textState[cursorState - 1]
            when (char) {
                '-' -> {
                    if (cursorState >= 2 && textState.substring(cursorState - 2, cursorState) == "–-") {
                        textState = textState.substring(0, cursorState - 2) + "—" + textState.substring(cursorState)
                        cursorState -= 1
                    } else if (cursorState >= 2 && textState.substring(cursorState - 2, cursorState) == "--") {
                        textState = textState.substring(0, cursorState - 2) + "–" + textState.substring(cursorState)
                        cursorState -= 1
                    }
                }
                '.' -> {
                    if (cursorState >= 3 && textState.substring(cursorState - 3, cursorState) == "...") {
                        textState = textState.substring(0, cursorState - 3) + "…" + textState.substring(cursorState)
                        cursorState -= 2
                    }
                }
                '"' -> {
                    val prevChar = if (cursorState >= 2) textState[cursorState - 2] else null
                    val isOpening = prevChar == null || prevChar.isWhitespace() || prevChar == '(' || prevChar == '[' || prevChar == '{' || prevChar == '<' || prevChar == '-' || prevChar == '—' || prevChar == '–'
                    val replacement = if (isOpening) "“" else "”"
                    textState = textState.substring(0, cursorState - 1) + replacement + textState.substring(cursorState)
                }
                '\'' -> {
                    val prevChar = if (cursorState >= 2) textState[cursorState - 2] else null
                    val isOpening = prevChar == null || prevChar.isWhitespace() || prevChar == '(' || prevChar == '[' || prevChar == '{' || prevChar == '<' || prevChar == '-' || prevChar == '—' || prevChar == '–'
                    val replacement = if (isOpening) "‘" else "’"
                    textState = textState.substring(0, cursorState - 1) + replacement + textState.substring(cursorState)
                }
            }
        }

        // 3. Overtype Skipping
        if (autoCompleteEnabled && cursorState > 0 && cursorState <= textState.length) {
            val typedChar = textState[cursorState - 1]
            val isClosingChar = when (typedChar) {
                ')' -> autoCompleteParens
                ']' -> autoCompleteBrackets
                '}' -> autoCompleteBraces
                '"', '”' -> autoCompleteQuotes
                '\'', '’' -> autoCompleteSingleQuotes
                else -> false
            }
            if (isClosingChar) {
                // If the user typed a closing character, and in the oldText at oldSelStart there is already that character, skip it.
                if (oldSelStart < oldText.length && oldText[oldSelStart] == typedChar) {
                    return TransformResult(oldText, oldSelStart + 1, oldSelStart + 1)
                }
            }
        }

        // 4. Auto-Pairing
        if (autoCompleteEnabled && cursorState > 0 && cursorState <= textState.length) {
            val typedChar = textState[cursorState - 1]
            val closePair = when (typedChar) {
                '(' -> if (autoCompleteParens) ')' else null
                '[' -> if (autoCompleteBrackets) ']' else null
                '{' -> if (autoCompleteBraces) '}' else null
                '"' -> if (autoCompleteQuotes) '"' else null
                '\'' -> if (autoCompleteSingleQuotes) '\'' else null
                '“' -> if (autoCompleteQuotes) '”' else null
                '‘' -> if (autoCompleteSingleQuotes) '’' else null
                else -> null
            }
            if (closePair != null) {
                textState = textState.substring(0, cursorState) + closePair + textState.substring(cursorState)
            }
        }

        return TransformResult(textState, cursorState, cursorState)
    }
}
