package com.attachdesign.kern.ui.editor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownElementType
import com.attachdesign.kern.parser.MarkdownRenderer
import com.attachdesign.kern.parser.MarkdownDocumentScanner
import com.attachdesign.kern.parser.MarkdownElement
import com.attachdesign.kern.parser.ParagraphBlock

class MarkdownDocumentOutputTransformation(
    private val viewMode: ViewMode,
    private val bodySize: TextUnit,
    private val tokenColor: Color,
    private val codeBackgroundColor: Color
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val source = asCharSequence().toString()
        if (source.isEmpty()) return
        if (viewMode == ViewMode.RAW_PLAIN_TEXT) {
            addStyle(SpanStyle(fontFamily = FontFamily.Monospace), 0, length)
            return
        }

        var blockStart = 0
        val indexedBlocks = MarkdownDocumentScanner.scan(source).map { block ->
            val start = blockStart
            blockStart += block.rawText.length + block.separatorAfter.length
            start to block
        }

        // Do not replace the entire document in one edit. Compose maps a whole-document
        // replacement as one transformed range, which collapses word-level selection into
        // the document boundaries. Apply the Markdown changes as local edits instead so
        // the platform can retain an accurate offset mapping for selection handles.
        val offsetMap = applyMarkdownProjection(source, indexedBlocks)

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
    }

    private fun TextFieldBuffer.applyMarkdownProjection(
        source: String,
        indexedBlocks: List<Pair<Int, ParagraphBlock>>
    ): SourceOffsetMap {
        val selectionStart = selection.min
        val selectionEnd = selection.max
        val documentEdits = mutableListOf<LocalEdit>()

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
                val selectionTouchesConstruct = selectionStart <= constructEnd &&
                    selectionEnd >= constructStart
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
                    LocalEdit(element.start, element.end, replacement)
                }
            }.sortedByDescending { it.start }

            val locallyProjected = edits.fold(block.rawText) { text, edit ->
                text.replaceRange(edit.start, edit.end, edit.replacement)
            }
            if (locallyProjected == rendered || revealsSelectedSyntax) {
                edits.forEach { edit ->
                    val documentEdit = LocalEdit(start + edit.start, start + edit.end, edit.replacement)
                    documentEdits += documentEdit
                    replace(documentEdit.start, documentEdit.end, documentEdit.replacement)
                }
            } else if (block.rawText != rendered) {
                // Tables and other compound blocks need a richer layout projection. Keep
                // their replacement block-local so ordinary text retains granular mapping.
                val documentEdit = LocalEdit(start, start + block.rawText.length, rendered)
                documentEdits += documentEdit
                replace(documentEdit.start, documentEdit.end, documentEdit.replacement)
            }
        }
        return SourceOffsetMap(documentEdits)
    }

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
        val style = when (blockType) {
            MarkdownBlockType.HEADER_1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = bodySize * 1.75f)
            MarkdownBlockType.HEADER_2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = bodySize * 1.55f)
            MarkdownBlockType.HEADER_3 -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = bodySize * 1.35f)
            MarkdownBlockType.HEADER_4,
            MarkdownBlockType.HEADER_5,
            MarkdownBlockType.HEADER_6 -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = bodySize * 1.15f)
            MarkdownBlockType.CODE_BLOCK -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = bodySize * 0.90f,
                background = codeBackgroundColor
            )
            MarkdownBlockType.BLOCKQUOTE -> SpanStyle(
                color = tokenColor,
                fontStyle = FontStyle.Italic
            )
            else -> return
        }
        addStyle(style, start, end)
    }

    private fun TextFieldBuffer.applyElementStyle(type: MarkdownElementType, start: Int, end: Int) {
        if (start >= end) return
        val style = when (type) {
            MarkdownElementType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            MarkdownElementType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            MarkdownElementType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            MarkdownElementType.INLINE_CODE -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = bodySize * 0.90f,
                background = codeBackgroundColor
            )
            MarkdownElementType.LINK -> SpanStyle(
                color = tokenColor,
                textDecoration = TextDecoration.Underline
            )
            MarkdownElementType.IMAGE -> SpanStyle(color = tokenColor, fontStyle = FontStyle.Italic)
            MarkdownElementType.TOKEN_HEADER,
            MarkdownElementType.TOKEN_BOLD,
            MarkdownElementType.TOKEN_ITALIC,
            MarkdownElementType.TOKEN_STRIKETHROUGH,
            MarkdownElementType.TOKEN_INLINE_CODE,
            MarkdownElementType.TOKEN_LINK_TEXT,
            MarkdownElementType.TOKEN_LINK_URL,
            MarkdownElementType.TOKEN_BLOCKQUOTE,
            MarkdownElementType.TOKEN_LIST_BULLET,
            MarkdownElementType.TOKEN_ESCAPE_CHAR -> SpanStyle(color = tokenColor)
            else -> return
        }
        addStyle(style, start, end)
    }
}
