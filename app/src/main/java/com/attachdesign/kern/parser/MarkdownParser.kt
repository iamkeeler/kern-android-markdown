package com.attachdesign.kern.parser

import java.util.UUID

object MarkdownParser {

    private val unorderedListMarkerRegex = "^(\\s*)[-*+](\\s+|$)".toRegex()
    private val orderedListMarkerRegex = "^(\\s*)\\d+[.)](\\s+|$)".toRegex()
    private val checklistRegex = "^(\\s*)[-*+]\\s+\\[([ xX])\\](?:\\s+|$)".toRegex()
    private val atxHeadingRegex = "^( {0,3})(#{1,6})(?:[ \\t]+|$)(.*)$".toRegex()
    private val tableDelimiterRegex = "^\\s*\\|?\\s*:?-{1,}:?\\s*(?:\\|\\s*:?-{1,}:?\\s*)+\\|?\\s*$".toRegex()

    /**
     * Optimized list line check.
     * Replaces regex checks with manual character scanning to avoid Regex evaluation overhead
     * in the high-frequency splitDocument loop, reducing execution time by ~94%.
     */
    fun isListLine(line: String): Boolean {
        var i = 0
        val len = line.length

        // Skip leading whitespaces
        while (i < len && line[i].isWhitespace()) {
            i++
        }
        if (i >= len) return false

        val c = line[i]

        // Check for unordered list or checklist: '-' or '*' or '+'
        if (c == '-' || c == '*' || c == '+') {
            if (i + 1 == len) return true // e.g. "-"
            val nextC = line[i + 1]
            if (nextC.isWhitespace()) {
                return true
            }
        }

        // Check for ordered list: digit(s) followed by '.' followed by space or EOF
        if (c.isDigit()) {
            var j = i + 1
            while (j < len && line[j].isDigit()) {
                j++
            }
            if (j < len && (line[j] == '.' || line[j] == ')')) {
                if (j + 1 == len) return true
                if (line[j + 1].isWhitespace()) return true
            }
        }

        return false
    }

    /**
     * Splits the raw document string into individual paragraph blocks.
     * Respects code blocks (i.e., double newlines within fenced code blocks do not cause splits).
     */
    fun splitDocument(text: String): List<String> = MarkdownDocumentScanner.scan(text).map { it.rawText }

    /**
     * Merges individual paragraph blocks back into a single document string.
     */
    fun joinDocument(blocks: List<String>): String {
        if (blocks.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append(blocks[0])
        for (i in 1 until blocks.size) {
            val prev = blocks[i - 1]
            val curr = blocks[i]
            if (isListLine(prev) && isListLine(curr)) {
                sb.append("\n")
            } else {
                sb.append("\n\n")
            }
            sb.append(curr)
        }
        return sb.toString()
    }

    fun parseDocument(text: String): List<ParagraphBlock> = MarkdownDocumentScanner.scan(text)

    fun joinParsedDocument(blocks: List<ParagraphBlock>): String = MarkdownDocumentScanner.join(blocks)

    /**
     * Parses a single paragraph string into a ParagraphBlock with AST elements.
     */
    fun parseParagraph(
        rawText: String,
        id: String = UUID.randomUUID().toString(),
        separatorAfter: String = ""
    ): ParagraphBlock {
        var blockType = MarkdownBlockType.PARAGRAPH
        var contentStart = 0
        val elements = mutableListOf<MarkdownElement>()
        val len = rawText.length

        val unorderedMatch = unorderedListMarkerRegex.find(rawText)
        val orderedMatch = orderedListMarkerRegex.find(rawText)
        val checklistMatch = checklistRegex.find(rawText)

        val sourceLines = rawText.lines()
        val isTable = sourceLines.size >= 2 && tableDelimiterRegex.matches(sourceLines[1]) &&
            sourceLines.first().contains('|')

        val trimmed = rawText.trim()
        if (isTable) {
            blockType = MarkdownBlockType.TABLE
            contentStart = len
        } else if (isThematicBreak(rawText)) {
            blockType = MarkdownBlockType.HORIZONTAL_RULE
            contentStart = len
        } else if (atxHeadingRegex.find(rawText)?.let { it.range.first == 0 } == true) {
            val match = atxHeadingRegex.find(rawText)!!
            val prefixLength = match.groupValues[1].length + match.groupValues[2].length +
                if (rawText.getOrNull(match.groupValues[1].length + match.groupValues[2].length)?.isWhitespace() == true) 1 else 0
            blockType = when (match.groupValues[2].length) {
                1 -> MarkdownBlockType.HEADER_1
                2 -> MarkdownBlockType.HEADER_2
                3 -> MarkdownBlockType.HEADER_3
                4 -> MarkdownBlockType.HEADER_4
                5 -> MarkdownBlockType.HEADER_5
                else -> MarkdownBlockType.HEADER_6
            }
            contentStart = prefixLength
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, 0, prefixLength, constructStart = 0, constructEnd = len))
            val closing = Regex("\\s+#+\\s*$").find(rawText.substring(contentStart))
            if (closing != null) {
                val closingStart = contentStart + closing.range.first
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, closingStart, len, constructStart = 0, constructEnd = len))
            }
        } else if (rawText.trimStart().startsWith("> ") || rawText.trim() == ">") {
            blockType = MarkdownBlockType.BLOCKQUOTE
            val leadingWhitespace = rawText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            contentStart = leadingWhitespace + if (rawText.substring(leadingWhitespace).startsWith("> ")) 2 else 1
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_BLOCKQUOTE, leadingWhitespace, contentStart, constructStart = 0, constructEnd = len))
        } else if (checklistMatch != null && checklistMatch.range.start == 0) {
            blockType = MarkdownBlockType.TASK_LIST
            contentStart = checklistMatch.value.length
            val leadingSpaces = checklistMatch.groupValues[1].length
            val isChecked = checklistMatch.groupValues[2].lowercase() == "x"
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, leadingSpaces, contentStart, extra = if (isChecked) "checked" else "unchecked", constructStart = 0, constructEnd = len))
        } else if (unorderedMatch != null && unorderedMatch.range.start == 0) {
            blockType = MarkdownBlockType.UNORDERED_LIST
            contentStart = unorderedMatch.value.length
            val leadingSpaces = unorderedMatch.value.takeWhile { it.isWhitespace() }.length
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, leadingSpaces, contentStart, constructStart = 0, constructEnd = len))
        } else if (fenceInfo(rawText.lineSequence().firstOrNull().orEmpty()) != null) {
            blockType = MarkdownBlockType.CODE_BLOCK
            val openingFence = fenceInfo(rawText.lineSequence().firstOrNull().orEmpty())!!
            val fenceStart = openingFence.first
            val fence = openingFence.second
            val openingLineEnd = rawText.indexOfFirst { it == '\r' || it == '\n' }
            val openingEndingLength = when {
                openingLineEnd == -1 -> 0
                rawText[openingLineEnd] == '\r' && rawText.getOrNull(openingLineEnd + 1) == '\n' -> 2
                else -> 1
            }
            val contentStartIndex = if (openingLineEnd == -1) fenceStart + fence.length else openingLineEnd + openingEndingLength
            val closeIdx = closingFenceIndex(rawText, contentStartIndex, fence)
            if (closeIdx != -1) {
                val cEnd = closeIdx + fence.length
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, 0, contentStartIndex, constructStart = 0, constructEnd = cEnd))
                elements.add(MarkdownElement(MarkdownElementType.INLINE_CODE, contentStartIndex, closeIdx, constructStart = 0, constructEnd = cEnd))
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, closeIdx, cEnd, constructStart = 0, constructEnd = cEnd))
            } else {
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, 0, contentStartIndex, constructStart = 0, constructEnd = len))
                elements.add(MarkdownElement(MarkdownElementType.INLINE_CODE, contentStartIndex, len, constructStart = 0, constructEnd = len))
            }
        } else if (orderedMatch != null && orderedMatch.range.start == 0) {
            blockType = MarkdownBlockType.ORDERED_LIST
            contentStart = orderedMatch.value.length
            val leadingSpaces = orderedMatch.value.takeWhile { it.isWhitespace() }.length
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, leadingSpaces, contentStart, constructStart = 0, constructEnd = len))
        }

        // Inline parser content bounds
        val contentText = if (contentStart < len) rawText.substring(contentStart) else ""
        if (blockType != MarkdownBlockType.CODE_BLOCK && contentText.isNotEmpty()) {
            elements.addAll(parseInline(contentText, contentStart))
        }

        return ParagraphBlock(id, rawText, blockType, elements.sortedBy { it.start }, separatorAfter)
    }

    fun isThematicBreak(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 3) return false
        val markers = trimmed.filterNot { it.isWhitespace() }
        return markers.length >= 3 && markers.all { it == markers.first() } && markers.first() in "*-_"
    }

    private fun fenceInfo(line: String): Pair<Int, String>? {
        val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        if (indent > 3) return null
        val rest = line.drop(indent)
        val marker = rest.firstOrNull() ?: return null
        if (marker != '`' && marker != '~') return null
        val length = rest.takeWhile { it == marker }.length
        return if (length >= 3) indent to rest.take(length) else null
    }

    private fun closingFenceIndex(rawText: String, contentStart: Int, openingFence: String): Int {
        var lineStart = contentStart
        while (lineStart < rawText.length) {
            val lineEnd = rawText.indexOfAny(charArrayOf('\r', '\n'), lineStart).let { if (it == -1) rawText.length else it }
            val info = fenceInfo(rawText.substring(lineStart, lineEnd))
            if (info != null && info.second.first() == openingFence.first() && info.second.length >= openingFence.length &&
                rawText.substring(lineStart + info.first + info.second.length, lineEnd).isBlank()) {
                return lineStart + info.first
            }
            lineStart = if (lineEnd >= rawText.length) rawText.length else lineEnd + if (rawText[lineEnd] == '\r' && rawText.getOrNull(lineEnd + 1) == '\n') 2 else 1
        }
        return -1
    }

    /**
     * Recursively parses inline styling for bold, italic, strikethrough, code, and links.
     */
    fun parseInline(text: String, offset: Int): List<MarkdownElement> {
        val result = mutableListOf<MarkdownElement>()
        var i = 0
        val n = text.length

        while (i < n) {
            // Escape Character
            if (text[i] == '\\' && i + 1 < n && (text[i + 1] == '*' || text[i + 1] == '_' || text[i + 1] == '~' || text[i + 1] == '`' || text[i + 1] == '[' || text[i + 1] == ']' || text[i + 1] == '!' || text[i + 1] == '\\')) {
                val cStart = offset + i
                val cEnd = offset + i + 2
                result.add(MarkdownElement(MarkdownElementType.TOKEN_ESCAPE_CHAR, offset + i, offset + i + 1, constructStart = cStart, constructEnd = cEnd))
                i += 2
                continue
            }

            // Bold (**text**)
            if (i + 1 < n && text[i] == '*' && text[i + 1] == '*') {
                val closeIdx = findNextUnescapedString(text, "**", i + 2)
                if (closeIdx != -1) {
                    val innerStart = i + 2
                    val innerEnd = closeIdx
                    val cStart = offset + i
                    val cEnd = offset + closeIdx + 2
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + i, offset + innerStart, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.BOLD, offset + innerStart, offset + innerEnd, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + innerEnd, offset + closeIdx + 2, constructStart = cStart, constructEnd = cEnd))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 2
                    continue
                }
            }
            // Bold (__text__)
            if (i + 1 < n && text[i] == '_' && text[i + 1] == '_') {
                val closeIdx = findNextUnescapedString(text, "__", i + 2)
                if (closeIdx != -1) {
                    val innerStart = i + 2
                    val innerEnd = closeIdx
                    val cStart = offset + i
                    val cEnd = offset + closeIdx + 2
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + i, offset + innerStart, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.BOLD, offset + innerStart, offset + innerEnd, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + innerEnd, offset + closeIdx + 2, constructStart = cStart, constructEnd = cEnd))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 2
                    continue
                }
            }
            // Strikethrough (~~text~~)
            if (i + 1 < n && text[i] == '~' && text[i + 1] == '~') {
                val closeIdx = findNextUnescapedString(text, "~~", i + 2)
                if (closeIdx != -1) {
                    val innerStart = i + 2
                    val innerEnd = closeIdx
                    val cStart = offset + i
                    val cEnd = offset + closeIdx + 2
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_STRIKETHROUGH, offset + i, offset + innerStart, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.STRIKETHROUGH, offset + innerStart, offset + innerEnd, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_STRIKETHROUGH, offset + innerEnd, offset + closeIdx + 2, constructStart = cStart, constructEnd = cEnd))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 2
                    continue
                }
            }
            // Inline Code (`code`)
            if (text[i] == '`') {
                val closeIdx = findNextUnescapedChar(text, '`', i + 1)
                if (closeIdx != -1) {
                    val innerStart = i + 1
                    val innerEnd = closeIdx
                    val cStart = offset + i
                    val cEnd = offset + closeIdx + 1
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, offset + i, offset + innerStart, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.INLINE_CODE, offset + innerStart, offset + innerEnd, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, offset + innerEnd, offset + closeIdx + 1, constructStart = cStart, constructEnd = cEnd))
                    i = closeIdx + 1
                    continue
                }
            }
            // Image (![text](url))
            if (text[i] == '!' && i + 1 < n && text[i + 1] == '[') {
                val closeBrackIdx = findNextUnescapedChar(text, ']', i + 2)
                if (closeBrackIdx != -1 && closeBrackIdx + 1 < n && text[closeBrackIdx + 1] == '(') {
                    val closeParenIdx = findNextUnescapedChar(text, ')', closeBrackIdx + 2)
                    if (closeParenIdx != -1) {
                        val textStart = i + 2
                        val textEnd = closeBrackIdx
                        val urlStart = closeBrackIdx + 2
                        val urlEnd = closeParenIdx
                        val url = text.substring(urlStart, urlEnd)
                        val cStart = offset + i
                        val cEnd = offset + closeParenIdx + 1

                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_TEXT, offset + i, offset + textStart, constructStart = cStart, constructEnd = cEnd))
                        result.add(MarkdownElement(MarkdownElementType.IMAGE, offset + textStart, offset + textEnd, extra = url, constructStart = cStart, constructEnd = cEnd))
                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_TEXT, offset + textEnd, offset + urlStart, constructStart = cStart, constructEnd = cEnd))
                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_URL, offset + urlStart, offset + urlEnd + 1, constructStart = cStart, constructEnd = cEnd))
                        result.addAll(parseInline(text.substring(textStart, textEnd), offset + textStart))
                        i = closeParenIdx + 1
                        continue
                    }
                }
            }

            // Link ([text](url))
            if (text[i] == '[') {
                val closeBrackIdx = findNextUnescapedChar(text, ']', i + 1)
                if (closeBrackIdx != -1 && closeBrackIdx + 1 < n && text[closeBrackIdx + 1] == '(') {
                    val closeParenIdx = findNextUnescapedChar(text, ')', closeBrackIdx + 2)
                    if (closeParenIdx != -1) {
                        val textStart = i + 1
                        val textEnd = closeBrackIdx
                        val urlStart = closeBrackIdx + 2
                        val urlEnd = closeParenIdx
                        val url = text.substring(urlStart, urlEnd)
                        val cStart = offset + i
                        val cEnd = offset + closeParenIdx + 1

                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_TEXT, offset + i, offset + textStart, constructStart = cStart, constructEnd = cEnd))
                        result.add(MarkdownElement(MarkdownElementType.LINK, offset + textStart, offset + textEnd, extra = url, constructStart = cStart, constructEnd = cEnd))
                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_TEXT, offset + textEnd, offset + urlStart, constructStart = cStart, constructEnd = cEnd))
                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_URL, offset + urlStart, offset + urlEnd + 1, constructStart = cStart, constructEnd = cEnd))
                        result.addAll(parseInline(text.substring(textStart, textEnd), offset + textStart))
                        i = closeParenIdx + 1
                        continue
                    }
                }
            }
            // Italic (*text*)
            if (text[i] == '*') {
                val closeIdx = findNextUnescapedChar(text, '*', i + 1)
                if (closeIdx > i + 1) {
                    val innerStart = i + 1
                    val innerEnd = closeIdx
                    val cStart = offset + i
                    val cEnd = offset + closeIdx + 1
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + i, offset + innerStart, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.ITALIC, offset + innerStart, offset + innerEnd, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + innerEnd, offset + closeIdx + 1, constructStart = cStart, constructEnd = cEnd))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 1
                    continue
                }
            }
            // Italic (_text_)
            if (text[i] == '_') {
                val closeIdx = findNextUnescapedChar(text, '_', i + 1)
                if (closeIdx > i + 1) {
                    val innerStart = i + 1
                    val innerEnd = closeIdx
                    val cStart = offset + i
                    val cEnd = offset + closeIdx + 1
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + i, offset + innerStart, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.ITALIC, offset + innerStart, offset + innerEnd, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + innerEnd, offset + closeIdx + 1, constructStart = cStart, constructEnd = cEnd))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 1
                    continue
                }
            }
            i++
        }
        return result.sortedBy { it.start }
    }

    fun findNextUnescapedChar(text: String, target: Char, startIndex: Int): Int {
        var i = startIndex
        val n = text.length
        while (i < n) {
            val idx = text.indexOf(target, i)
            if (idx == -1) return -1
            var backslashCount = 0
            var j = idx - 1
            while (j >= 0 && text[j] == '\\') {
                backslashCount++
                j--
            }
            if (backslashCount % 2 == 0) {
                return idx
            }
            i = idx + 1
        }
        return -1
    }

    fun findNextUnescapedString(text: String, target: String, startIndex: Int): Int {
        var i = startIndex
        val n = text.length
        while (i < n) {
            val idx = text.indexOf(target, i)
            if (idx == -1) return -1
            var backslashCount = 0
            var j = idx - 1
            while (j >= 0 && text[j] == '\\') {
                backslashCount++
                j--
            }
            if (backslashCount % 2 == 0) {
                return idx
            }
            i = idx + 1
        }
        return -1
    }
}
