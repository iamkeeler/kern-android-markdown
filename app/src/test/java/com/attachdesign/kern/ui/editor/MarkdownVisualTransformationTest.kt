package com.attachdesign.kern.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownVisualTransformationTest {

    private val tokenColor = Color.Gray
    private val codeBgColor = Color.LightGray

    @Test
    fun testSelectiveTokenRevealWhenCursorIsInsideBold() {
        val rawText = "**this is bold**, and *this is italic*"
        // **this is bold** is indices 0..16
        // , and  is indices 16..23
        // *this is italic* is indices 23..38
        val text = AnnotatedString(rawText)

        // Cursor at position 5 (inside "this is bold")
        val vt = MarkdownVisualTransformation(
            isFocused = true,
            selection = TextRange(5, 5),
            viewMode = ViewMode.RENDERED,
            tokenColor = tokenColor,
            codeBackgroundColor = codeBgColor
        )

        val transformed = vt.filter(text)
        // ** is revealed for bold, but * is stripped for italic
        // Expected text: "**this is bold**, and this is italic"
        assertEquals("**this is bold**, and this is italic", transformed.text.text)
    }

    @Test
    fun testSelectiveTokenRevealWhenCursorIsInsideItalic() {
        val rawText = "**this is bold**, and *this is italic*"
        val text = AnnotatedString(rawText)

        // Cursor at position 28 (inside "this is italic")
        val vt = MarkdownVisualTransformation(
            isFocused = true,
            selection = TextRange(28, 28),
            viewMode = ViewMode.RENDERED,
            tokenColor = tokenColor,
            codeBackgroundColor = codeBgColor
        )

        val transformed = vt.filter(text)
        // ** is stripped for bold, but * is revealed for italic
        // Expected text: "this is bold, and *this is italic*"
        assertEquals("this is bold, and *this is italic*", transformed.text.text)
    }

    @Test
    fun testSelectiveTokenRevealWhenCursorIsOutsideFormatting() {
        val rawText = "**this is bold**, and *this is italic*"
        val text = AnnotatedString(rawText)

        // Cursor at position 18 (inside ", and ")
        val vt = MarkdownVisualTransformation(
            isFocused = true,
            selection = TextRange(18, 18),
            viewMode = ViewMode.RENDERED,
            tokenColor = tokenColor,
            codeBackgroundColor = codeBgColor
        )

        val transformed = vt.filter(text)
        // Both ** and * are stripped
        // Expected text: "this is bold, and this is italic"
        assertEquals("this is bold, and this is italic", transformed.text.text)
    }

    @Test
    fun testSelectiveTokenRevealWhenSelectionSpansBothConstructs() {
        val rawText = "**this is bold**, and *this is italic*"
        val text = AnnotatedString(rawText)

        // Selection from 5 to 28
        val vt = MarkdownVisualTransformation(
            isFocused = true,
            selection = TextRange(5, 28),
            viewMode = ViewMode.RENDERED,
            tokenColor = tokenColor,
            codeBackgroundColor = codeBgColor
        )

        val transformed = vt.filter(text)
        // Both ** and * are revealed
        // Expected text: "**this is bold**, and *this is italic*"
        assertEquals("**this is bold**, and *this is italic*", transformed.text.text)
    }
}
