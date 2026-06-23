package com.attachdesign.kern.parser

import java.util.UUID

object MarkdownParser {

    /**
     * Determines if a line starts a list block.
     * Performance optimization: A manual character-by-character scan is used here instead of
     * regular expressions to avoid Regex object creation overhead and match evaluation latency.
     * This method is called frequently during document parsing.
     */
    fun isListLine(line: String): Boolean {
        var i = 0
        val len = line.length
        while (i < len && line[i].isWhitespace()) {
            i++
        }
        if (i >= len) return false

        val c = line[i]
        if (c == '-' || c == '*' || c == '+') {
            i++
            if (i == len) return true
            return line[i].isWhitespace()
        } else if (c in '0'..'9') {
            i++
            while (i < len && line[i] in '0'..'9') {
                i++
            }
            if (i < len && line[i] == '.') {
                i++
                if (i == len) return true
                return line[i].isWhitespace()
            }
        }
        return false
    }

    /**
     * Splits the raw document string into individual paragraph blocks.
     * Respects code blocks (i.e., double newlines within fenced code blocks do not cause splits).
     */
    fun splitDocument(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        
        val blocks = mutableListOf<String>()
        val currentBlock = StringBuilder()
        var inCodeBlock = false
        
        // Normalize line endings
        val lines = text.replace("\r\n", "\n").split("\n")
        var consecutiveEmptyLines = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
            }
            
            if (!inCodeBlock && line.isEmpty()) {
                consecutiveEmptyLines++
                if (consecutiveEmptyLines == 1) {
                    if (currentBlock.isNotEmpty()) {
                        blocks.add(currentBlock.toString())
                        currentBlock.clear()
                    } else {
                        // Empty block (e.g. at the beginning or empty paragraph)
                        blocks.add("")
                    }
                }
            } else {
                consecutiveEmptyLines = 0
                if (!inCodeBlock && isListLine(line)) {
                    if (currentBlock.isNotEmpty()) {
                        blocks.add(currentBlock.toString())
                        currentBlock.clear()
                    }
                    currentBlock.append(line)
                } else {
                    if (currentBlock.isNotEmpty()) {
                        currentBlock.append("\n")
                    }
                    currentBlock.append(line)
                }
            }
        }
        
        if (currentBlock.isNotEmpty() || consecutiveEmptyLines > 0) {
            blocks.add(currentBlock.toString())
        }
        
        return if (blocks.isEmpty()) listOf("") else blocks
    }

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

    /**
     * Parses a single paragraph string into a ParagraphBlock with AST elements.
     */
    fun parseParagraph(rawText: String, id: String = UUID.randomUUID().toString()): ParagraphBlock {
        var blockType = MarkdownBlockType.PARAGRAPH
        var contentStart = 0
        val elements = mutableListOf<MarkdownElement>()
        val len = rawText.length

        val unorderedMatch = unorderedListMarkerRegex.find(rawText)
        val orderedMatch = orderedListMarkerRegex.find(rawText)

        if (rawText.startsWith("###### ")) {
            blockType = MarkdownBlockType.HEADER_6
            contentStart = 7
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, 0, 7))
        } else if (rawText.startsWith("##### ")) {
            blockType = MarkdownBlockType.HEADER_5
            contentStart = 6
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, 0, 6))
        } else if (rawText.startsWith("#### ")) {
            blockType = MarkdownBlockType.HEADER_4
            contentStart = 5
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, 0, 5))
        } else if (rawText.startsWith("### ")) {
            blockType = MarkdownBlockType.HEADER_3
            contentStart = 4
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, 0, 4))
        } else if (rawText.startsWith("## ")) {
            blockType = MarkdownBlockType.HEADER_2
            contentStart = 3
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, 0, 3))
        } else if (rawText.startsWith("# ")) {
            blockType = MarkdownBlockType.HEADER_1
            contentStart = 2
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_HEADER, 0, 2))
        } else if (rawText.startsWith("> ") || rawText == ">") {
            blockType = MarkdownBlockType.BLOCKQUOTE
            contentStart = if (rawText.startsWith("> ")) 2 else 1
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_BLOCKQUOTE, 0, contentStart))
        } else if (unorderedMatch != null && unorderedMatch.range.start == 0) {
            blockType = MarkdownBlockType.UNORDERED_LIST
            contentStart = unorderedMatch.value.length
            val leadingSpaces = unorderedMatch.value.takeWhile { it.isWhitespace() }.length
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, leadingSpaces, contentStart))
        } else if (rawText.startsWith("```")) {
            blockType = MarkdownBlockType.CODE_BLOCK
            val closeIdx = rawText.indexOf("```", 3)
            if (closeIdx != -1) {
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, 0, 3))
                elements.add(MarkdownElement(MarkdownElementType.INLINE_CODE, 3, closeIdx))
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, closeIdx, closeIdx + 3))
            } else {
                elements.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, 0, 3))
                elements.add(MarkdownElement(MarkdownElementType.INLINE_CODE, 3, len))
            }
        } else if (orderedMatch != null && orderedMatch.range.start == 0) {
            blockType = MarkdownBlockType.ORDERED_LIST
            contentStart = orderedMatch.value.length
            val leadingSpaces = orderedMatch.value.takeWhile { it.isWhitespace() }.length
            elements.add(MarkdownElement(MarkdownElementType.TOKEN_LIST_BULLET, leadingSpaces, contentStart))
        }

        // Inline parser content bounds
        val contentText = if (contentStart < len) rawText.substring(contentStart) else ""
        if (blockType != MarkdownBlockType.CODE_BLOCK && contentText.isNotEmpty()) {
            elements.addAll(parseInline(contentText, contentStart))
        }

        return ParagraphBlock(id, rawText, blockType, elements.sortedBy { it.start })
    }

    /**
     * Recursively parses inline styling for bold, italic, strikethrough, code, and links.
     */
    fun parseInline(text: String, offset: Int): List<MarkdownElement> {
        val result = mutableListOf<MarkdownElement>()
        var i = 0
        val n = text.length

        while (i < n) {
            // Bold (**text**)
            if (i + 1 < n && text[i] == '*' && text[i + 1] == '*') {
                val closeIdx = text.indexOf("**", i + 2)
                if (closeIdx != -1) {
                    val innerStart = i + 2
                    val innerEnd = closeIdx
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + i, offset + innerStart))
                    result.add(MarkdownElement(MarkdownElementType.BOLD, offset + innerStart, offset + innerEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + innerEnd, offset + closeIdx + 2))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 2
                    continue
                }
            }
            // Bold (__text__)
            if (i + 1 < n && text[i] == '_' && text[i + 1] == '_') {
                val closeIdx = text.indexOf("__", i + 2)
                if (closeIdx != -1) {
                    val innerStart = i + 2
                    val innerEnd = closeIdx
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + i, offset + innerStart))
                    result.add(MarkdownElement(MarkdownElementType.BOLD, offset + innerStart, offset + innerEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_BOLD, offset + innerEnd, offset + closeIdx + 2))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 2
                    continue
                }
            }
            // Strikethrough (~~text~~)
            if (i + 1 < n && text[i] == '~' && text[i + 1] == '~') {
                val closeIdx = text.indexOf("~~", i + 2)
                if (closeIdx != -1) {
                    val innerStart = i + 2
                    val innerEnd = closeIdx
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_STRIKETHROUGH, offset + i, offset + innerStart))
                    result.add(MarkdownElement(MarkdownElementType.STRIKETHROUGH, offset + innerStart, offset + innerEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_STRIKETHROUGH, offset + innerEnd, offset + closeIdx + 2))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 2
                    continue
                }
            }
            // Inline Code (`code`)
            if (text[i] == '`') {
                val closeIdx = text.indexOf('`', i + 1)
                if (closeIdx != -1) {
                    val innerStart = i + 1
                    val innerEnd = closeIdx
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, offset + i, offset + innerStart))
                    result.add(MarkdownElement(MarkdownElementType.INLINE_CODE, offset + innerStart, offset + innerEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_INLINE_CODE, offset + innerEnd, offset + closeIdx + 1))
                    i = closeIdx + 1
                    continue
                }
            }
            // Link ([text](url))
            if (text[i] == '[') {
                val closeBrackIdx = text.indexOf(']', i + 1)
                if (closeBrackIdx != -1 && closeBrackIdx + 1 < n && text[closeBrackIdx + 1] == '(') {
                    val closeParenIdx = text.indexOf(')', closeBrackIdx + 2)
                    if (closeParenIdx != -1) {
                        val textStart = i + 1
                        val textEnd = closeBrackIdx
                        val urlStart = closeBrackIdx + 2
                        val urlEnd = closeParenIdx
                        val url = text.substring(urlStart, urlEnd)

                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_TEXT, offset + i, offset + textStart))
                        result.add(MarkdownElement(MarkdownElementType.LINK, offset + textStart, offset + textEnd, url))
                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_TEXT, offset + textEnd, offset + urlStart))
                        result.add(MarkdownElement(MarkdownElementType.TOKEN_LINK_URL, offset + urlStart, offset + urlEnd + 1))
                        result.addAll(parseInline(text.substring(textStart, textEnd), offset + textStart))
                        i = closeParenIdx + 1
                        continue
                    }
                }
            }
            // Italic (*text*)
            if (text[i] == '*') {
                val closeIdx = text.indexOf('*', i + 1)
                if (closeIdx != -1) {
                    val innerStart = i + 1
                    val innerEnd = closeIdx
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + i, offset + innerStart))
                    result.add(MarkdownElement(MarkdownElementType.ITALIC, offset + innerStart, offset + innerEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + innerEnd, offset + closeIdx + 1))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 1
                    continue
                }
            }
            // Italic (_text_)
            if (text[i] == '_') {
                val closeIdx = text.indexOf('_', i + 1)
                if (closeIdx != -1) {
                    val innerStart = i + 1
                    val innerEnd = closeIdx
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + i, offset + innerStart))
                    result.add(MarkdownElement(MarkdownElementType.ITALIC, offset + innerStart, offset + innerEnd))
                    result.add(MarkdownElement(MarkdownElementType.TOKEN_ITALIC, offset + innerEnd, offset + closeIdx + 1))
                    result.addAll(parseInline(text.substring(innerStart, innerEnd), offset + innerStart))
                    i = closeIdx + 1
                    continue
                }
            }
            i++
        }
        return result.sortedBy { it.start }
    }
}
