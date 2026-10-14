package com.attachdesign.kern.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownElementType

class MarkdownSpanStyleProvider(
    val tokenColor: Color,
    val codeBackgroundColor: Color,
    val bodySize: TextUnit? = null
) {
    fun styleForBlock(blockType: MarkdownBlockType): SpanStyle? = when (blockType) {
        MarkdownBlockType.HEADER_1 -> SpanStyle(
            fontWeight = FontWeight.Bold,
            fontSize = if (bodySize != null && bodySize.isSpecified) bodySize * 1.75f else TextUnit.Unspecified
        )
        MarkdownBlockType.HEADER_2 -> SpanStyle(
            fontWeight = FontWeight.Bold,
            fontSize = if (bodySize != null && bodySize.isSpecified) bodySize * 1.55f else TextUnit.Unspecified
        )
        MarkdownBlockType.HEADER_3 -> SpanStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = if (bodySize != null && bodySize.isSpecified) bodySize * 1.35f else TextUnit.Unspecified
        )
        MarkdownBlockType.HEADER_4,
        MarkdownBlockType.HEADER_5,
        MarkdownBlockType.HEADER_6 -> SpanStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = if (bodySize != null && bodySize.isSpecified) bodySize * 1.15f else TextUnit.Unspecified
        )
        MarkdownBlockType.CODE_BLOCK -> SpanStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = if (bodySize != null && bodySize.isSpecified) bodySize * 0.90f else TextUnit.Unspecified,
            background = codeBackgroundColor
        )
        MarkdownBlockType.BLOCKQUOTE -> SpanStyle(
            color = tokenColor,
            fontStyle = FontStyle.Italic
        )
        else -> null
    }

    fun styleForElement(type: MarkdownElementType): SpanStyle? = when (type) {
        MarkdownElementType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        MarkdownElementType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        MarkdownElementType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        MarkdownElementType.INLINE_CODE -> SpanStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = if (bodySize != null && bodySize.isSpecified) bodySize * 0.90f else TextUnit.Unspecified,
            background = codeBackgroundColor
        )
        MarkdownElementType.LINK -> SpanStyle(
            color = tokenColor,
            textDecoration = TextDecoration.Underline
        )
        MarkdownElementType.IMAGE -> SpanStyle(
            color = tokenColor,
            fontStyle = FontStyle.Italic,
            textDecoration = TextDecoration.Underline
        )
        MarkdownElementType.TOKEN_HEADER,
        MarkdownElementType.TOKEN_BOLD,
        MarkdownElementType.TOKEN_ITALIC,
        MarkdownElementType.TOKEN_STRIKETHROUGH,
        MarkdownElementType.TOKEN_INLINE_CODE,
        MarkdownElementType.TOKEN_LINK_TEXT,
        MarkdownElementType.TOKEN_LINK_URL,
        MarkdownElementType.TOKEN_BLOCKQUOTE,
        MarkdownElementType.TOKEN_LIST_BULLET,
        MarkdownElementType.TOKEN_ESCAPE_CHAR -> SpanStyle(
            color = tokenColor,
            fontWeight = FontWeight.Normal
        )
        else -> null
    }

    fun styleForTokenBulletBold(): SpanStyle = SpanStyle(
        color = tokenColor,
        fontWeight = FontWeight.Bold
    )

    fun styleForCheckedTaskItem(): SpanStyle = SpanStyle(
        textDecoration = TextDecoration.LineThrough,
        color = tokenColor.copy(alpha = 0.6f)
    )

    fun styleForRawCodeBlock(): SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = codeBackgroundColor
    )

    fun styleForMonospaceRaw(): SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace
    )
}
