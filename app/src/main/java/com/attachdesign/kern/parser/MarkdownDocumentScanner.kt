package com.attachdesign.kern.parser

/**
 * Lossless block scanner for the editor's supported Markdown dialect.
 *
 * Block contents and their following separators are kept separately so opening and saving a
 * document never normalizes line endings or rewrites intentional blank-line spacing.
 */
object MarkdownDocumentScanner {
    private data class SourceLine(val content: String, val ending: String)

    fun scan(source: String): List<ParagraphBlock> {
        if (source.isEmpty()) return listOf(MarkdownParser.parseParagraph(""))

        val lines = tokenize(source)
        val blocks = mutableListOf<ParagraphBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.content.isBlank()) {
                val separator = StringBuilder()
                while (index < lines.size && lines[index].content.isBlank()) {
                    separator.append(lines[index].content).append(lines[index].ending)
                    index++
                }
                if (blocks.isEmpty()) {
                    blocks += MarkdownParser.parseParagraph("", separatorAfter = separator.toString())
                } else {
                    val previous = blocks.last()
                    blocks[blocks.lastIndex] = previous.copy(
                        separatorAfter = previous.separatorAfter + separator
                    )
                }
                continue
            }

            val start = index
            val kind = if (isTableStart(lines, index)) LineKind.TABLE else classify(line.content)
            index = when (kind) {
                LineKind.FENCE -> consumeFence(lines, start)
                LineKind.TABLE -> consumeTable(lines, start)
                LineKind.PARAGRAPH -> consumeWhile(lines, start, LineKind.PARAGRAPH)
                LineKind.STRUCTURAL -> if (MarkdownParser.isListLine(line.content)) {
                    consumeListItem(lines, start)
                } else {
                    start + 1
                }
            }

            val raw = buildString {
                for (lineIndex in start until index) {
                    append(lines[lineIndex].content)
                    if (lineIndex < index - 1) append(lines[lineIndex].ending)
                }
            }
            val separator = lines[index - 1].ending
            blocks += MarkdownParser.parseParagraph(raw, separatorAfter = separator)
        }

        return if (blocks.isEmpty()) listOf(MarkdownParser.parseParagraph("")) else blocks
    }

    fun join(blocks: List<ParagraphBlock>): String = buildString {
        blocks.forEach { block ->
            append(block.rawText)
            append(block.separatorAfter)
        }
    }

    private fun consumeFence(lines: List<SourceLine>, start: Int): Int {
        val opening = fenceMarker(lines[start].content) ?: return start + 1
        var index = start + 1
        while (index < lines.size) {
            val closing = fenceMarker(lines[index].content)
            val isClosingFence = closing != null && closing.first == opening.first && closing.second >= opening.second &&
                lines[index].content.dropWhile { it.isWhitespace() }.drop(closing.second).isBlank()
            index++
            if (isClosingFence) break
        }
        return index
    }

    private fun consumeWhile(lines: List<SourceLine>, start: Int, kind: LineKind): Int {
        var index = start + 1
        while (index < lines.size && lines[index].content.isNotBlank() && classify(lines[index].content) == kind) {
            index++
        }
        return index
    }

    private fun consumeTable(lines: List<SourceLine>, start: Int): Int {
        var index = start + 2 // header and required delimiter row
        while (index < lines.size && lines[index].content.isNotBlank() && lines[index].content.contains('|')) index++
        return index
    }

    private fun consumeListItem(lines: List<SourceLine>, start: Int): Int {
        val markerIndent = lines[start].content.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        var index = start + 1
        while (index < lines.size && lines[index].content.isNotBlank()) {
            val line = lines[index].content
            val continuationIndent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            if (classify(line) != LineKind.PARAGRAPH || continuationIndent <= markerIndent) break
            index++
        }
        return index
    }

    private fun classify(line: String): LineKind {
        val trimmed = line.trim()
        val trimmedStart = line.trimStart()
        return when {
            fenceMarker(line) != null -> LineKind.FENCE
            trimmedStart.matches(Regex("#{1,6}\\s+.*")) -> LineKind.STRUCTURAL
            MarkdownParser.isThematicBreak(line) -> LineKind.STRUCTURAL
            trimmedStart.startsWith(">") -> LineKind.STRUCTURAL
            MarkdownParser.isListLine(line) -> LineKind.STRUCTURAL
            else -> LineKind.PARAGRAPH
        }
    }

    private fun isTableStart(lines: List<SourceLine>, index: Int): Boolean =
        index + 1 < lines.size && lines[index].content.contains('|') &&
            lines[index + 1].content.matches(Regex("^\\s*\\|?\\s*:?-{1,}:?\\s*(?:\\|\\s*:?-{1,}:?\\s*)+\\|?\\s*$"))

    private fun fenceMarker(line: String): Pair<Char, Int>? {
        val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        if (indent > 3) return null
        val marker = line.getOrNull(indent) ?: return null
        if (marker != '`' && marker != '~') return null
        val length = line.drop(indent).takeWhile { it == marker }.length
        return if (length >= 3) marker to length else null
    }

    private fun tokenize(source: String): List<SourceLine> {
        val result = mutableListOf<SourceLine>()
        var start = 0
        var index = 0
        while (index < source.length) {
            if (source[index] == '\r' || source[index] == '\n') {
                val endingLength = if (source[index] == '\r' && index + 1 < source.length && source[index + 1] == '\n') 2 else 1
                result += SourceLine(source.substring(start, index), source.substring(index, index + endingLength))
                index += endingLength
                start = index
            } else {
                index++
            }
        }
        if (start < source.length) result += SourceLine(source.substring(start), "")
        return result
    }

    private enum class LineKind { PARAGRAPH, STRUCTURAL, FENCE, TABLE }
}
