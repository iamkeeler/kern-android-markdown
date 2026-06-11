package com.attachdesign.kern.parser

object MarkdownEditorEngine {

    // Regex patterns for continuation detection
    private val checklistRegex = Regex("^(\\s*[-*+]\\s+\\[[ xX]?\\]\\s+)")
    private val orderedListRegex = Regex("^(\\s*)(\\d+)\\.(\\s+)")
    private val bulletListRegex = Regex("^(\\s*[-*+]\\s+)")
    private val blockquoteRegex = Regex("^(\\s*>\\s?)")

    // Empty checks (lines containing only the formatting prefix and trailing spaces)
    private val emptyChecklistRegex = Regex("^(\\s*[-*+]\\s+\\[[ xX]?\\]\\s*)$")
    private val emptyOrderedListRegex = Regex("^(\\s*\\d+\\.\\s*)$")
    private val emptyBulletListRegex = Regex("^(\\s*[-*+]\\s*)$")
    private val emptyBlockquoteRegex = Regex("^(\\s*>\\s*)$")

    data class ContinuationResult(
        val isContinuation: Boolean,
        val newCurrentText: String,
        val nextLinePrefix: String,
        val isExit: Boolean
    )

    fun checkContinuation(line: String): ContinuationResult {
        // 1. Checklist
        if (emptyChecklistRegex.matches(line)) {
            return ContinuationResult(isContinuation = true, newCurrentText = "", nextLinePrefix = "", isExit = true)
        }
        val checklistMatch = checklistRegex.find(line)
        if (checklistMatch != null) {
            val prefix = checklistMatch.value
            val indentAndBulletRegex = Regex("^(\\s*[-*+])")
            val match = indentAndBulletRegex.find(prefix)
            val nextPrefix = if (match != null) {
                match.value + " [ ] "
            } else {
                "- [ ] "
            }
            return ContinuationResult(isContinuation = true, newCurrentText = line, nextLinePrefix = nextPrefix, isExit = false)
        }

        // 2. Ordered List
        if (emptyOrderedListRegex.matches(line)) {
            return ContinuationResult(isContinuation = true, newCurrentText = "", nextLinePrefix = "", isExit = true)
        }
        val orderedMatch = orderedListRegex.find(line)
        if (orderedMatch != null) {
            val indent = orderedMatch.groupValues[1]
            val numStr = orderedMatch.groupValues[2]
            val spacing = orderedMatch.groupValues[3]
            val num = numStr.toIntOrNull() ?: 1
            val nextNum = num + 1
            val nextPrefix = "$indent$nextNum.$spacing"
            return ContinuationResult(isContinuation = true, newCurrentText = line, nextLinePrefix = nextPrefix, isExit = false)
        }

        // 3. Bullet List
        if (emptyBulletListRegex.matches(line)) {
            return ContinuationResult(isContinuation = true, newCurrentText = "", nextLinePrefix = "", isExit = true)
        }
        val bulletMatch = bulletListRegex.find(line)
        if (bulletMatch != null) {
            return ContinuationResult(isContinuation = true, newCurrentText = line, nextLinePrefix = bulletMatch.value, isExit = false)
        }

        // 4. Blockquote
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
        autoCompleteBrackets: Boolean = true
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
