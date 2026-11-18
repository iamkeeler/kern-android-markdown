package com.attachdesign.kern.ui.editor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.TextUnit
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownElementType
import com.attachdesign.kern.parser.MarkdownRenderer
import com.attachdesign.kern.parser.MarkdownDocumentScanner
import com.attachdesign.kern.parser.MarkdownElement
import com.attachdesign.kern.parser.ParagraphBlock

internal class MarkdownDocumentOutputTransformation(
    private val viewMode: ViewMode,
    private val bodySize: TextUnit,
    private val tokenColor: Color,
    codeBackgroundColor: Color,
    private val find: DocumentFindHighlight? = null
) : OutputTransformation {
    private val styleProvider = MarkdownSpanStyleProvider(tokenColor, codeBackgroundColor, bodySize)

    override fun TextFieldBuffer.transformOutput() {
        val source = asCharSequence().toString()
        if (source.isEmpty()) return
        val match = find?.takeIf { it.source == source }
        if (viewMode == ViewMode.RAW_PLAIN_TEXT) {
            addStyle(styleProvider.styleForMonospaceRaw(), 0, length)
            match?.let { addStyle(it.style, it.range.first, it.range.last + 1) }
            return
        }

        val indexedBlocks = indexedBlocks(source)

        // Do not replace the entire document in one edit. Compose maps a whole-document
        // replacement as one transformed range, which collapses word-level selection into
        // the document boundaries. Apply the Markdown changes as local edits instead so
        // the platform can retain an accurate offset mapping for selection handles.
        val edits = projectionEdits(indexedBlocks, selection, match?.range)
        edits.forEach { replace(it.start, it.end, it.replacement) }
        val offsetMap = SourceOffsetMap(edits)

        indexedBlocks.forEach { (start, block) ->
            val blockTransformedStart = offsetMap.map(start)
            val blockTransformedEnd = offsetMap.map(start + block.rawText.length)
            applyBlockStyle(block.blockType, blockTransformedStart, blockTransformedEnd)

            block.elements.forEach { element ->
                val elementTransformedStart = offsetMap.map(start + element.start)
                val elementTransformedEnd = offsetMap.map(start + element.end)
                applyElementStyle(element.type, elementTransformedStart, elementTransformedEnd)
            }
        }
        match?.let {
            val range = offsetMap.mapRange(it.range)
            if (!range.isEmpty()) addStyle(it.style, range.first, range.last + 1)
        }
    }

    internal fun displayedFindRange(selection: TextRange): IntRange? = find?.let {
        if (viewMode == ViewMode.RAW_PLAIN_TEXT) it.range
        else SourceOffsetMap(projectionEdits(indexedBlocks(it.source), selection, it.range)).mapRange(it.range)
    }

    private fun indexedBlocks(source: String): List<Pair<Int, ParagraphBlock>> {
        var blockStart = 0
        return MarkdownDocumentScanner.scan(source).map { block ->
            val start = blockStart
            blockStart += block.rawText.length + block.separatorAfter.length
            start to block
        }
    }

    private fun projectionEdits(
        indexedBlocks: List<Pair<Int, ParagraphBlock>>,
        selection: TextRange,
        match: IntRange?
    ): List<LocalEdit> = buildList {
        indexedBlocks.asReversed().forEach { (start, block) ->
            val blockProjection = MarkdownRenderer.render(block).text
            val rendered = when (block.blockType) {
                MarkdownBlockType.BLOCKQUOTE -> if (blockProjection.isNotBlank()) "│ $blockProjection" else blockProjection
                else -> blockProjection
            }
            var revealsSelectedSyntax = false
            val edits = block.elements.mapNotNull { element ->
                val constructStart = start + element.constructStart
                val constructEnd = start + element.constructEnd
                val selectionTouchesConstruct = selection.min <= constructEnd &&
                    selection.max >= constructStart
                // Inline Markdown is a live preview until the user works within that
                // construct. At that point preserve its source syntax so selection,
                // editing, and formatting are all legible. Blockquote and fenced-code
                // presentation intentionally remain rendered while editing.
                val alwaysHideBlockSyntax = element.type == MarkdownElementType.TOKEN_BLOCKQUOTE ||
                    (block.blockType == MarkdownBlockType.CODE_BLOCK &&
                        element.type == MarkdownElementType.TOKEN_INLINE_CODE)
                if (selectionTouchesConstruct && !alwaysHideBlockSyntax) {
                    revealsSelectedSyntax = true
                    return@mapNotNull null
                }
                element.replacementFor(block.blockType)?.let { replacement ->
                    if (match.intersects(start + element.start, start + element.end)) {
                        return@mapNotNull null
                    }
                    LocalEdit(element.start, element.end, replacement)
                }
            }.sortedByDescending { it.start }

            val locallyProjected = edits.fold(block.rawText) { text, edit ->
                text.replaceRange(edit.start, edit.end, edit.replacement)
            }
            if (locallyProjected == rendered || revealsSelectedSyntax ||
                match.intersects(start, start + block.rawText.length)) {
                edits.forEach { edit ->
                    add(LocalEdit(start + edit.start, start + edit.end, edit.replacement))
                }
            } else if (block.rawText != rendered) {
                // Tables and other compound blocks need a richer layout projection. Keep
                // their replacement block-local so ordinary text retains granular mapping.
                add(LocalEdit(start, start + block.rawText.length, rendered))
            }
        }
    }

    private fun IntRange?.intersects(start: Int, end: Int): Boolean =
        this != null && first < end && last >= start

    private fun MarkdownElement.replacementFor(blockType: MarkdownBlockType): String? = when (type) {
        MarkdownElementType.TOKEN_LIST_BULLET -> when (blockType) {
            MarkdownBlockType.TASK_LIST -> if (extra == "checked") "☑ " else "☐ "
            MarkdownBlockType.UNORDERED_LIST -> "• "
            else -> null
        }
        MarkdownElementType.TOKEN_BLOCKQUOTE -> "│ "
        MarkdownElementType.TOKEN_HEADER,
        MarkdownElementType.TOKEN_BOLD,
        MarkdownElementType.TOKEN_ITALIC,
        MarkdownElementType.TOKEN_STRIKETHROUGH,
        MarkdownElementType.TOKEN_INLINE_CODE,
        MarkdownElementType.TOKEN_LINK_TEXT,
        MarkdownElementType.TOKEN_LINK_URL,
        MarkdownElementType.TOKEN_ESCAPE_CHAR -> ""
        else -> null
    }

    private data class LocalEdit(val start: Int, val end: Int, val replacement: String)

    /** Maps source offsets into the local output edits without replacing the whole document. */
    private class SourceOffsetMap(edits: List<LocalEdit>) {
        private val edits = edits.sortedBy { it.start }

        fun mapRange(range: IntRange): IntRange = map(range.first) until map(range.last + 1)

        fun map(offset: Int): Int {
            var delta = 0
            edits.forEach { edit ->
                when {
                    offset >= edit.end -> delta += edit.replacement.length - (edit.end - edit.start)
                    offset > edit.start -> return edit.start + delta
                    else -> return offset + delta
                }
            }
            return offset + delta
        }
    }

    private fun TextFieldBuffer.applyBlockStyle(blockType: MarkdownBlockType, start: Int, end: Int) {
        if (start >= end) return
        styleProvider.styleForBlock(blockType)?.let { style ->
            addStyle(style, start, end)
            val headingLineRatio = when (blockType) {
                MarkdownBlockType.HEADER_1 -> 1.25f
                MarkdownBlockType.HEADER_2, MarkdownBlockType.HEADER_3 -> 1.3f
                MarkdownBlockType.HEADER_4, MarkdownBlockType.HEADER_5, MarkdownBlockType.HEADER_6 -> 1.3f
                else -> null
            }
            if (headingLineRatio != null && style.fontSize.isSpecified) {
                val headingHeight = style.fontSize * headingLineRatio
                val bodyHeight = bodySize * 1.6f
                addStyle(ParagraphStyle(
                    lineHeight = if (headingHeight.value > bodyHeight.value) headingHeight else bodyHeight,
                    lineHeightStyle = LineHeightStyle(
                        LineHeightStyle.Alignment.Proportional,
                        LineHeightStyle.Trim.None,
                        LineHeightStyle.Mode.Minimum
                    )
                ), start, end)
            }
        }
    }

    private fun TextFieldBuffer.applyElementStyle(type: MarkdownElementType, start: Int, end: Int) {
        if (start >= end) return
        styleProvider.styleForElement(type)?.let { addStyle(it, start, end) }
    }
}
