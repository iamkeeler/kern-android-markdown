package com.attachdesign.kern.parser

data class DocumentElementRange(
    val type: MarkdownElementType,
    val start: Int,
    val end: Int,
    val blockType: MarkdownBlockType
)

data class MarkdownDocumentPresentation(
    val hiddenRanges: List<IndexRange>,
    val elements: List<DocumentElementRange>,
    val blocks: List<DocumentElementRange>
)

/** Builds document-global source ranges for state-based text-field presentation. */
object MarkdownDocumentPresentationPlanner {
    fun build(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        hideInactiveTokens: Boolean
    ): MarkdownDocumentPresentation {
        val selectionMin = minOf(selectionStart, selectionEnd).coerceIn(0, source.length)
        val selectionMax = maxOf(selectionStart, selectionEnd).coerceIn(selectionMin, source.length)
        val hidden = mutableListOf<IndexRange>()
        val elements = mutableListOf<DocumentElementRange>()
        val blockRanges = mutableListOf<DocumentElementRange>()
        var blockStart = 0

        MarkdownDocumentScanner.scan(source).forEach { block ->
            val blockEnd = blockStart + block.rawText.length
            blockRanges += DocumentElementRange(block.blockType.asElementType(), blockStart, blockEnd, block.blockType)
            if (hideInactiveTokens && block.blockType == MarkdownBlockType.HORIZONTAL_RULE && blockStart < blockEnd) {
                hidden += IndexRange(blockStart, blockEnd)
            }
            val imageConstructs = block.elements
                .filter { it.type == MarkdownElementType.IMAGE }
                .map { it.constructStart to it.constructEnd }
                .toSet()

            block.elements.forEach { element ->
                val start = blockStart + element.start
                val end = blockStart + element.end
                if (start < end) {
                    elements += DocumentElementRange(element.type, start, end, block.blockType)
                }
                if (!hideInactiveTokens || !element.type.isSyntaxToken()) return@forEach
                if (element.type == MarkdownElementType.TOKEN_LIST_BULLET) return@forEach
                if ((element.constructStart to element.constructEnd) in imageConstructs) return@forEach

                val constructStart = blockStart + element.constructStart
                val constructEnd = blockStart + element.constructEnd
                // Block-level syntax remains hidden even while the caret is inside the block. This
                // keeps rendered blockquotes and fenced code looking rendered during editing;
                // inline syntax still follows the cursor-aware reveal behavior below.
                val alwaysHideBlockSyntax = element.type == MarkdownElementType.TOKEN_BLOCKQUOTE ||
                    (block.blockType == MarkdownBlockType.CODE_BLOCK && element.type == MarkdownElementType.TOKEN_INLINE_CODE)
                val selectionTouchesConstruct = !alwaysHideBlockSyntax &&
                    selectionMin <= constructEnd && selectionMax >= constructStart
                if (!selectionTouchesConstruct && start < end) hidden += IndexRange(start, end)
            }
            blockStart = blockEnd + block.separatorAfter.length
        }

        return MarkdownDocumentPresentation(
            hiddenRanges = hidden.distinct().sortedBy { it.start },
            elements = elements,
            blocks = blockRanges
        )
    }

    private fun MarkdownBlockType.asElementType(): MarkdownElementType = when (this) {
        MarkdownBlockType.HEADER_1 -> MarkdownElementType.HEADER_1
        MarkdownBlockType.HEADER_2 -> MarkdownElementType.HEADER_2
        MarkdownBlockType.HEADER_3 -> MarkdownElementType.HEADER_3
        MarkdownBlockType.HEADER_4 -> MarkdownElementType.HEADER_4
        MarkdownBlockType.HEADER_5 -> MarkdownElementType.HEADER_5
        MarkdownBlockType.HEADER_6 -> MarkdownElementType.HEADER_6
        MarkdownBlockType.BLOCKQUOTE -> MarkdownElementType.BLOCKQUOTE
        else -> MarkdownElementType.LIST_BULLET
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
