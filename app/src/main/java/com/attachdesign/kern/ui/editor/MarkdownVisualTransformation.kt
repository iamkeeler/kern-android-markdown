package com.attachdesign.kern.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownElementType
import com.attachdesign.kern.parser.MarkdownParser
import com.attachdesign.kern.parser.IndexTransformationMatrix
import com.attachdesign.kern.parser.IndexRange

enum class ViewMode {
    RENDERED,
    SYNTAX_HIGHLIGHTED,
    RAW_PLAIN_TEXT
}

class MarkdownVisualTransformation(
    val isFocused: Boolean,
    val viewMode: ViewMode,
    val tokenColor: Color,
    val codeBackgroundColor: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val paragraph = MarkdownParser.parseParagraph(rawText)

        return when (viewMode) {
            ViewMode.RAW_PLAIN_TEXT -> {
                val styledText = AnnotatedString(
                    rawText,
                    spanStyles = listOf(AnnotatedString.Range(SpanStyle(fontFamily = FontFamily.Monospace), 0, rawText.length))
                )
                TransformedText(styledText, OffsetMapping.Identity)
            }
            ViewMode.SYNTAX_HIGHLIGHTED -> {
                val builder = AnnotatedString.Builder(rawText)
                applySyntaxHighlightingStyles(paragraph, builder)
                TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
            }
            ViewMode.RENDERED -> {
                if (isFocused) {
                    val builder = AnnotatedString.Builder(rawText)
                    applySyntaxHighlightingStyles(paragraph, builder)
                    TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
                } else {
                    val (strippedText, matrix) = stripTokens(paragraph)
                    val builder = AnnotatedString.Builder(strippedText)
                    applyRenderedStyles(paragraph, builder, matrix)

                    val offsetMapping = object : OffsetMapping {
                        override fun originalToTransformed(offset: Int): Int {
                            return matrix.originalToTransformed(offset)
                        }
                        override fun transformedToOriginal(offset: Int): Int {
                            return matrix.transformedToOriginal(offset)
                        }
                    }
                    TransformedText(builder.toAnnotatedString(), offsetMapping)
                }
            }
        }
    }

    private fun applySyntaxHighlightingStyles(
        paragraph: com.attachdesign.kern.parser.ParagraphBlock,
        builder: AnnotatedString.Builder
    ) {
        if (paragraph.blockType == MarkdownBlockType.CODE_BLOCK) {
            builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor), 0, paragraph.rawText.length)
        }

        for (element in paragraph.elements) {
            val start = element.start
            val end = element.end
            if (start >= end) continue

            when (element.type) {
                MarkdownElementType.BOLD -> {
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
                MarkdownElementType.ITALIC -> {
                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }
                MarkdownElementType.STRIKETHROUGH -> {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                }
                MarkdownElementType.INLINE_CODE -> {
                    builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor), start, end)
                }
                MarkdownElementType.LINK -> {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = tokenColor), start, end)
                }
                MarkdownElementType.TOKEN_HEADER,
                MarkdownElementType.TOKEN_BOLD,
                MarkdownElementType.TOKEN_ITALIC,
                MarkdownElementType.TOKEN_STRIKETHROUGH,
                MarkdownElementType.TOKEN_INLINE_CODE,
                MarkdownElementType.TOKEN_LINK_TEXT,
                MarkdownElementType.TOKEN_LINK_URL,
                MarkdownElementType.TOKEN_BLOCKQUOTE,
                MarkdownElementType.TOKEN_LIST_BULLET -> {
                    builder.addStyle(SpanStyle(color = tokenColor, fontWeight = FontWeight.Normal), start, end)
                }
                else -> {}
            }
        }
    }

    private fun stripTokens(
        paragraph: com.attachdesign.kern.parser.ParagraphBlock
    ): Pair<String, IndexTransformationMatrix> {
        val raw = paragraph.rawText
        val tokensToProcess = paragraph.elements.filter {
            when (it.type) {
                MarkdownElementType.TOKEN_HEADER,
                MarkdownElementType.TOKEN_BOLD,
                MarkdownElementType.TOKEN_ITALIC,
                MarkdownElementType.TOKEN_STRIKETHROUGH,
                MarkdownElementType.TOKEN_INLINE_CODE,
                MarkdownElementType.TOKEN_LINK_TEXT,
                MarkdownElementType.TOKEN_LINK_URL,
                MarkdownElementType.TOKEN_BLOCKQUOTE,
                MarkdownElementType.TOKEN_LIST_BULLET -> true
                else -> false
            }
        }.sortedBy { it.start }

        val strippedRanges = mutableListOf<IndexRange>()
        val sb = StringBuilder()
        var lastIdx = 0
        for (token in tokensToProcess) {
            if (token.start >= lastIdx) {
                sb.append(raw.substring(lastIdx, token.start))
                if (token.type == MarkdownElementType.TOKEN_LIST_BULLET) {
                    if (paragraph.blockType == MarkdownBlockType.UNORDERED_LIST) {
                        val originalMarker = raw.substring(token.start, token.end)
                        val bulletChar = "•"
                        val replaced = if (originalMarker.endsWith(" ")) "$bulletChar " else bulletChar
                        sb.append(replaced)
                    } else {
                        sb.append(raw.substring(token.start, token.end))
                    }
                } else {
                    strippedRanges.add(IndexRange(token.start, token.end))
                }
                lastIdx = token.end
            }
        }
        if (lastIdx < raw.length) {
            sb.append(raw.substring(lastIdx))
        }
        return Pair(sb.toString(), IndexTransformationMatrix(strippedRanges))
    }

    private fun applyRenderedStyles(
        paragraph: com.attachdesign.kern.parser.ParagraphBlock,
        builder: AnnotatedString.Builder,
        matrix: IndexTransformationMatrix
    ) {
        if (paragraph.blockType == MarkdownBlockType.CODE_BLOCK) {
            builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor), 0, builder.length)
            return
        }

        for (element in paragraph.elements) {
            val start = element.start
            val end = element.end
            if (start >= end) continue

            when (element.type) {
                MarkdownElementType.BOLD -> {
                    val tStart = matrix.originalToTransformed(start)
                    val tEnd = matrix.originalToTransformed(end)
                    if (tStart < tEnd) {
                        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), tStart, tEnd)
                    }
                }
                MarkdownElementType.ITALIC -> {
                    val tStart = matrix.originalToTransformed(start)
                    val tEnd = matrix.originalToTransformed(end)
                    if (tStart < tEnd) {
                        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), tStart, tEnd)
                    }
                }
                MarkdownElementType.STRIKETHROUGH -> {
                    val tStart = matrix.originalToTransformed(start)
                    val tEnd = matrix.originalToTransformed(end)
                    if (tStart < tEnd) {
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), tStart, tEnd)
                    }
                }
                MarkdownElementType.INLINE_CODE -> {
                    val tStart = matrix.originalToTransformed(start)
                    val tEnd = matrix.originalToTransformed(end)
                    if (tStart < tEnd) {
                        builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor), tStart, tEnd)
                    }
                }
                MarkdownElementType.LINK -> {
                    val tStart = matrix.originalToTransformed(start)
                    val tEnd = matrix.originalToTransformed(end)
                    if (tStart < tEnd) {
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = tokenColor), tStart, tEnd)
                    }
                }
                MarkdownElementType.TOKEN_LIST_BULLET -> {
                    val tStart = matrix.originalToTransformed(start)
                    val tEnd = matrix.originalToTransformed(end)
                    if (tStart < tEnd) {
                        builder.addStyle(SpanStyle(color = tokenColor, fontWeight = FontWeight.Bold), tStart, tEnd)
                    }
                }
                else -> {}
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MarkdownVisualTransformation) return false

        if (isFocused != other.isFocused) return false
        if (viewMode != other.viewMode) return false
        if (tokenColor != other.tokenColor) return false
        if (codeBackgroundColor != other.codeBackgroundColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isFocused.hashCode()
        result = 31 * result + viewMode.hashCode()
        result = 31 * result + tokenColor.hashCode()
        result = 31 * result + codeBackgroundColor.hashCode()
        return result
    }
}