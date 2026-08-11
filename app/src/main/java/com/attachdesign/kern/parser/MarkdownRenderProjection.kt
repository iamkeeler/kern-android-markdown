package com.attachdesign.kern.parser

data class RenderSpan(
    val type: MarkdownElementType,
    val start: Int,
    val end: Int,
    val extra: String? = null
)

data class MarkdownRenderProjection(
    val text: String,
    val spans: List<RenderSpan>,
    val indexMatrix: IndexTransformationMatrix
)

/** Pure Kotlin rendered-text and clipboard projection shared by every UI surface. */
object MarkdownRenderer {
    fun render(block: ParagraphBlock): MarkdownRenderProjection {
        if (block.blockType == MarkdownBlockType.HORIZONTAL_RULE) {
            return MarkdownRenderProjection("", emptyList(), IndexTransformationMatrix(listOf(IndexRange(0, block.rawText.length))))
        }
        if (block.blockType == MarkdownBlockType.TABLE) {
            return MarkdownRenderProjection(renderTable(block.rawText), emptyList(), IndexTransformationMatrix(emptyList()))
        }

        val tokens = block.elements.filter { it.type.isSyntaxToken() }.sortedBy { it.start }
        val removedRanges = mutableListOf<IndexRange>()
        val rendered = StringBuilder()
        var sourceIndex = 0

        tokens.forEach { token ->
            if (token.start < sourceIndex) return@forEach
            rendered.append(block.rawText.substring(sourceIndex, token.start))
            when {
                token.type == MarkdownElementType.TOKEN_LIST_BULLET && block.blockType == MarkdownBlockType.TASK_LIST -> {
                    rendered.append(if (token.extra == "checked") "☑ " else "☐ ")
                    val retainedLength = minOf(2, token.end - token.start)
                    removedRanges += IndexRange(token.start + retainedLength, token.end)
                }
                token.type == MarkdownElementType.TOKEN_LIST_BULLET && block.blockType == MarkdownBlockType.UNORDERED_LIST -> {
                    rendered.append("•")
                    if (block.rawText.substring(token.start, token.end).lastOrNull()?.isWhitespace() == true) rendered.append(' ')
                }
                else -> removedRanges += IndexRange(token.start, token.end)
            }
            sourceIndex = token.end
        }
        if (sourceIndex < block.rawText.length) rendered.append(block.rawText.substring(sourceIndex))

        val matrix = IndexTransformationMatrix(removedRanges)
        val spans = block.elements.mapNotNull { element ->
            if (element.type.isSyntaxToken() || element.start >= element.end) return@mapNotNull null
            val start = matrix.originalToTransformed(element.start).coerceIn(0, rendered.length)
            val end = matrix.originalToTransformed(element.end).coerceIn(start, rendered.length)
            if (start == end) null else RenderSpan(element.type, start, end, element.extra)
        }
        return MarkdownRenderProjection(rendered.toString(), spans, matrix)
    }

    fun copyDocument(blocks: List<ParagraphBlock>): String = buildString {
        blocks.forEach { block ->
            append(render(block).text)
            append(block.separatorAfter)
        }
    }.trimEnd('\r', '\n')

    private fun renderTable(raw: String): String {
        val rows = raw.lines().mapNotNull { line ->
            val compact = line.replace(" ", "")
            if (compact.matches(Regex("^\\|[-:|]+\\|$"))) return@mapNotNull null
            if (!line.trim().startsWith('|') || !line.trim().endsWith('|')) return@mapNotNull null
            splitTableLine(line.trim()).joinToString("\t") { cell ->
                val parsed = MarkdownParser.parseParagraph(cell.trim())
                render(parsed).text
            }
        }
        return rows.joinToString("\n")
    }

    private fun splitTableLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var index = 1
        while (index < line.lastIndex) {
            if (line[index] == '\\' && index + 1 < line.lastIndex && line[index + 1] == '|') {
                current.append('|')
                index += 2
            } else if (line[index] == '|') {
                result += current.toString()
                current.clear()
                index++
            } else {
                current.append(line[index++])
            }
        }
        result += current.toString()
        return result
    }

    private fun MarkdownElementType.isSyntaxToken(): Boolean = when (this) {
        MarkdownElementType.TOKEN_HEADER,
        MarkdownElementType.TOKEN_BOLD,
        MarkdownElementType.TOKEN_ITALIC,
        MarkdownElementType.TOKEN_STRIKETHROUGH,
        MarkdownElementType.TOKEN_INLINE_CODE,
        MarkdownElementType.TOKEN_LINK_TEXT,
        MarkdownElementType.TOKEN_LINK_URL,
        MarkdownElementType.TOKEN_BLOCKQUOTE,
        MarkdownElementType.TOKEN_LIST_BULLET,
        MarkdownElementType.TOKEN_ESCAPE_CHAR -> true
        else -> false
    }
}
