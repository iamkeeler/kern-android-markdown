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

        val projection = MarkdownRenderer.renderDocument(source)
        replace(0, length, projection.text)
        projection.blocks.forEach { block -> applyBlockStyle(block.type, block.start, block.end) }
        projection.spans.forEach { span -> applyElementStyle(span.type, span.start, span.end) }
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
                fontSize = bodySize * 0.94f,
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
                fontSize = bodySize * 0.94f,
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
