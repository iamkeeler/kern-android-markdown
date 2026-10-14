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
import com.attachdesign.kern.parser.MarkdownRenderer

import androidx.compose.ui.text.TextRange

enum class ViewMode {
    RENDERED,
    SYNTAX_HIGHLIGHTED,
    RAW_PLAIN_TEXT
}

class MarkdownVisualTransformation(
    val isFocused: Boolean,
    val selection: TextRange = TextRange.Zero,
    val viewMode: ViewMode,
    val tokenColor: Color,
    val codeBackgroundColor: Color
) : VisualTransformation {

    private val styleProvider = MarkdownSpanStyleProvider(tokenColor, codeBackgroundColor)

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val paragraph = MarkdownParser.parseParagraph(rawText)

        return when (viewMode) {
            ViewMode.RAW_PLAIN_TEXT -> {
                val styledText = AnnotatedString(
                    rawText,
                    spanStyles = listOf(AnnotatedString.Range(styleProvider.styleForMonospaceRaw(), 0, rawText.length))
                )
                TransformedText(styledText, OffsetMapping.Identity)
            }
            ViewMode.SYNTAX_HIGHLIGHTED -> {
                val builder = AnnotatedString.Builder(rawText)
                applySyntaxHighlightingStyles(paragraph, builder)
                TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
            }
            ViewMode.RENDERED -> {
                val (strippedText, matrix) = if (isFocused) {
                    stripTokens(paragraph)
                } else {
                    val projection = MarkdownRenderer.render(paragraph)
                    projection.text to projection.indexMatrix
                }
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

    private fun applySyntaxHighlightingStyles(
        paragraph: com.attachdesign.kern.parser.ParagraphBlock,
        builder: AnnotatedString.Builder
    ) {
        if (paragraph.blockType == MarkdownBlockType.CODE_BLOCK) {
            builder.addStyle(styleProvider.styleForRawCodeBlock(), 0, paragraph.rawText.length)
        }

        for (element in paragraph.elements) {
            val start = element.start
            val end = element.end
            if (start >= end) continue

            styleProvider.styleForElement(element.type)?.let { style ->
                builder.addStyle(style, start, end)
            }
        }
    }

    private fun stripTokens(
        paragraph: com.attachdesign.kern.parser.ParagraphBlock
    ): Pair<String, IndexTransformationMatrix> {
        val raw = paragraph.rawText
        val tokensToProcess = paragraph.elements.filter { element ->
            if (!element.type.isSyntaxToken) return@filter false

            // If focused, reveal token if active selection intersects or is inside the construct range
            if (isFocused) {
                val cursorStart = selection.min
                val cursorEnd = selection.max
                val constructStart = element.constructStart
                val constructEnd = element.constructEnd
                // Reveal token if cursor/selection intersects or touches construct bounds
                val intersects = (cursorStart <= constructEnd && cursorEnd >= constructStart)
                !intersects
            } else {
                true
            }
        }.sortedBy { it.start }

        val strippedRanges = mutableListOf<IndexRange>()
        val sb = StringBuilder()
        var lastIdx = 0
        for (token in tokensToProcess) {
            if (token.start >= lastIdx) {
                sb.append(raw.substring(lastIdx, token.start))
                if (token.type == MarkdownElementType.TOKEN_LIST_BULLET) {
                    if (paragraph.blockType == MarkdownBlockType.TASK_LIST) {
                        val isChecked = token.extra == "checked"
                        val checkboxChar = if (isChecked) "☑" else "☐"
                        sb.append("$checkboxChar ")
                        strippedRanges.add(IndexRange(token.start + 2, token.end))
                    } else if (paragraph.blockType == MarkdownBlockType.UNORDERED_LIST) {
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
            builder.addStyle(styleProvider.styleForRawCodeBlock(), 0, builder.length)
            return
        }

        if (paragraph.blockType == MarkdownBlockType.TASK_LIST) {
            val isChecked = paragraph.elements.any { it.type == MarkdownElementType.TOKEN_LIST_BULLET && it.extra == "checked" }
            if (isChecked && builder.length > 2) {
                builder.addStyle(styleProvider.styleForCheckedTaskItem(), 2, builder.length)
            }
        }

        for (element in paragraph.elements) {
            val start = element.start
            val end = element.end
            if (start >= end) continue

            val style = if (element.type == MarkdownElementType.TOKEN_LIST_BULLET) {
                styleProvider.styleForTokenBulletBold()
            } else {
                styleProvider.styleForElement(element.type)
            }

            if (style != null) {
                val tStart = matrix.originalToTransformed(start)
                val tEnd = matrix.originalToTransformed(end)
                if (tStart < tEnd) {
                    builder.addStyle(style, tStart, tEnd)
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MarkdownVisualTransformation) return false

        if (isFocused != other.isFocused) return false
        if (selection != other.selection) return false
        if (viewMode != other.viewMode) return false
        if (tokenColor != other.tokenColor) return false
        if (codeBackgroundColor != other.codeBackgroundColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isFocused.hashCode()
        result = 31 * result + selection.hashCode()
        result = 31 * result + viewMode.hashCode()
        result = 31 * result + tokenColor.hashCode()
        result = 31 * result + codeBackgroundColor.hashCode()
        return result
    }
}
