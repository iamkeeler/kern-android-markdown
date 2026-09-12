package com.attachdesign.kern.parser

import java.util.UUID

object MarkdownParser {

    private val atxHeadingRegex = "^( {0,3})(#{1,6})(?:[ \\t]+|$)(.*)$".toRegex()
    private val tableDelimiterRegex = "^\\s*\\|?\\s*:?-{1,}:?\\s*(?:\\|\\s*:?-{1,}:?\\s*)+\\|?\\s*$".toRegex()
    // M6: hoisted off the per-heading path (was compiled on every heading block).
    private val closingHashesRegex = Regex("\\s+#+\\s*$")

    /**
     * Optimized list line check.
     * Replaces regex checks with manual character scanning to avoid Regex evaluation overhead
     * in the high-frequency splitDocument loop, reducing execution time by ~94%.
     */
    fun isListLine(line: String): Boolean {
        return MarkdownListSyntax.parse(line) != null
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

        val listMarker = MarkdownListSyntax.parse(rawText)

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
            // L15: group 3 is the heading content; its start is the exact end of the
            // "#"-plus-whitespace prefix, however many spaces follow the hashes.
            val prefixLength = match.groups[3]?.range?.first
                ?: (match.groupValues[1].length + match.groupValues[2].length)
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
            val closing = closingHashesRegex.find(rawText.substring(contentStart))
            if (closing != null) {
                val closingStart = contentStart + closing.range.first
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, closingStart, len, constructStart = 0, constructEnd = len))
            }
        } else if (rawText.trimStart().startsWith(">")) {
            // L14: accept `>foo` (no mandatory space), matching checkContinuation's `^(\s*>\s?)`.
            blockType = MarkdownBlockType.BLOCKQUOTE
            val leadingWhitespace = rawText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            contentStart = leadingWhitespace + if (rawText.substring(leadingWhitespace).startsWith("> ")) 2 else 1
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_BLOCKQUOTE, leadingWhitespace, contentStart, constructStart = 0, constructEnd = len))
        } else if (listMarker?.kind == MarkdownListSyntax.Kind.TASK) {
            blockType = MarkdownBlockType.TASK_LIST
            contentStart = listMarker.contentStart
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, listMarker.indent.length, contentStart, extra = if (listMarker.checked == true) "checked" else "unchecked", constructStart = 0, constructEnd = len))
        } else if (listMarker?.kind == MarkdownListSyntax.Kind.UNORDERED) {
            blockType = MarkdownBlockType.UNORDERED_LIST
            contentStart = listMarker.contentStart
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, listMarker.indent.length, contentStart, constructStart = 0, constructEnd = len))
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
        } else if (listMarker?.kind == MarkdownListSyntax.Kind.ORDERED) {
            blockType = MarkdownBlockType.ORDERED_LIST
            contentStart = listMarker.contentStart
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, listMarker.indent.length, contentStart, constructStart = 0, constructEnd = len))
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
        // M5: "no closer after this point" watermarks, one per delimiter type. Each guarded scan
        // below is monotone — once it fails from `from`, it fails from any later start too, because
        // a later opener scans a subset of the same text. (The M2 balanced `[`/`]`/`(`/`)` scans
        // are intentionally NOT watermarked: their depth counter restarts per scan, so a later
        // opener can succeed where an earlier one failed.)
        val noCloserFrom = HashMap<String, Int>()

        fun findCloser(key: String, from: Int, find: (Int) -> Int): Int {
            val marked = noCloserFrom[key]
            if (marked != null && from >= marked) return -1
            val idx = find(from)
            if (idx == -1) noCloserFrom[key] = from
            return idx
        }

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
                val closeIdx = findCloser("**", i + 2) { from -> findNextUnescapedString(text, "**", from) }
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
                val closeIdx = findCloser("__", i + 2) { from -> findNextUnescapedString(text, "__", from) }
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
                val closeIdx = findCloser("~~", i + 2) { from -> findNextUnescapedString(text, "~~", from) }
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
            // Inline Code (`code` or ``code with ` backtick``)
            if (text[i] == '`') {
                // M3: the closing run must be exactly as long as the opening run.
                var runEnd = i + 1
                while (runEnd < n && text[runEnd] == '`') runEnd++
                val runLength = runEnd - i
                val closeIdx = findCloser("`$runLength", runEnd) { from -> findBacktickRun(text, from, runLength) }
                if (closeIdx != -1) {
                    val innerStart = runEnd
                    val innerEnd = closeIdx
                    val cStart = offset + i
                    val cEnd = offset + closeIdx + runLength
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, offset + i, offset + innerStart, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.INLINE_CODE, offset + innerStart, offset + innerEnd, constructStart = cStart, constructEnd = cEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, offset + innerEnd, offset + closeIdx + runLength, constructStart = cStart, constructEnd = cEnd))
                    i = closeIdx + runLength
                    continue
                }
                // M3: no equal-length closer — the whole run is literal text.
                // Skip past it instead of retrying at i + 1, so `` ``a` ``
                // can't form a code span out of the opening run's tail.
                i = runEnd
                continue
            }
            // Image (![text](url))
            if (text[i] == '!' && i + 1 < n && text[i + 1] == '[') {
                val closeBrackIdx = findBalancedBracketClose(text, i + 1)
                if (closeBrackIdx != -1 && closeBrackIdx + 1 < n && text[closeBrackIdx + 1] == '(') {
                    val closeParenIdx = findBalancedParenClose(text, closeBrackIdx + 2)
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
                val closeBrackIdx = findBalancedBracketClose(text, i)
                if (closeBrackIdx != -1 && closeBrackIdx + 1 < n && text[closeBrackIdx + 1] == '(') {
                    val closeParenIdx = findBalancedParenClose(text, closeBrackIdx + 2)
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
                // M1: skip a closer candidate that is part of a longer `*` run (e.g. the
                // inner `**` of `*a **b** c*); the run belongs to the nested construct.
                val closeIdx = findCloser("*", i + 1) { from -> findEmphasisCloser(text, '*', from) }
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
                // M1: same run-skipping as `*` above, for `_a __b__ c_`.
                val closeIdx = findCloser("_", i + 1) { from -> findEmphasisCloser(text, '_', from) }
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

    /**
     * M1: finds the closing delimiter for single-`*`/`_` emphasis at or after [startIndex],
     * skipping any delimiter that is part of a longer run (e.g. the inner `**` of `*a **b** c*).
     */
    private fun findEmphasisCloser(text: String, marker: Char, startIndex: Int): Int {
        var searchFrom = startIndex
        while (true) {
            val idx = findNextUnescapedChar(text, marker, searchFrom)
            if (idx == -1) return -1
            val inRun = (idx > 0 && text[idx - 1] == marker) ||
                (idx + 1 < text.length && text[idx + 1] == marker)
            if (!inRun) return idx
            searchFrom = idx + 1
        }
    }

    /**
     * M3: finds a closing backtick run of exactly [length] backticks at or after [startIndex].
     * Runs of any other length are skipped, so `` ``a`b`` `` keeps its inner backtick.
     */
    private fun findBacktickRun(text: String, startIndex: Int, length: Int): Int {
        var i = startIndex
        while (i < text.length) {
            val idx = text.indexOf('`', i)
            if (idx == -1) return -1
            var backslashCount = 0
            var j = idx - 1
            while (j >= 0 && text[j] == '\\') {
                backslashCount++
                j--
            }
            var runEnd = idx + 1
            while (runEnd < text.length && text[runEnd] == '`') runEnd++
            if (backslashCount % 2 == 0 && runEnd - idx == length) return idx
            i = runEnd
        }
        return -1
    }

    /**
     * M2: returns the index of the `]` balancing the `[` at [openIndex], or -1.
     * Nested `[`…`]` pairs add depth, so `[a [b](c) d](e)` closes at the outer bracket.
     */
    private fun findBalancedBracketClose(text: String, openIndex: Int): Int {
        var depth = 0
        var i = openIndex
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                i += 2
                continue
            }
            when (c) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /**
     * M2: returns the index of the `)` balancing a link destination whose `(` sits just before
     * [fromIndex], or -1. Nested parens add depth, so `[a](b(c))` keeps the destination `b(c)`.
     */
    private fun findBalancedParenClose(text: String, fromIndex: Int): Int {
        var depth = 1
        var i = fromIndex
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                i += 2
                continue
            }
            when (c) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
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
