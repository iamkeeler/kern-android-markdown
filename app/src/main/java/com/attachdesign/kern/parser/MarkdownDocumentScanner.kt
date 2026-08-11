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
            val kind = classify(line.content)
            index = when (kind) {
                LineKind.FENCE -> consumeFence(lines, start)
                LineKind.TABLE -> consumeWhile(lines, start, LineKind.TABLE)
                LineKind.PARAGRAPH -> consumeWhile(lines, start, LineKind.PARAGRAPH)
                else -> start + 1
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
        var index = start + 1
        while (index < lines.size) {
            val isClosingFence = lines[index].content.trimStart().startsWith("```")
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

    private fun classify(line: String): LineKind {
        val trimmed = line.trim()
        val trimmedStart = line.trimStart()
        return when {
            trimmedStart.startsWith("```") -> LineKind.FENCE
            trimmedStart.matches(Regex("#{1,6}\\s+.*")) -> LineKind.STRUCTURAL
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> LineKind.STRUCTURAL
            trimmedStart.startsWith(">") -> LineKind.STRUCTURAL
            MarkdownParser.isListLine(line) -> LineKind.STRUCTURAL
            trimmed.startsWith("|") && trimmed.endsWith("|") -> LineKind.TABLE
            else -> LineKind.PARAGRAPH
        }
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
