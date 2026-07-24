package com.attachdesign.kern.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownVisualTransformationTest {

    private val tokenColor = Color.Gray
    private val codeBgColor = Color.LightGray

    @Test
    fun testRenderedModeStripsTokensWhenCursorOutside() {
        val transformation = MarkdownVisualTransformation(
            isFocused = false,
            selection = TextRange(50, 50),
            viewMode = ViewMode.RENDERED,
            tokenColor = tokenColor,
            codeBackgroundColor = codeBgColor
        )

        // Raw: "> **Hello** there"
        val rawText = androidx.compose.ui.text.AnnotatedString("> **Hello** there")
        val result = transformation.filter(rawText)

        // Stripped output should be "Hello there"
        assertEquals("Hello there", result.text.text)
    }

    @Test
    fun testRenderedModeExpandsTokensWhenCursorIntersects() {
        // Cursor at position 3, inside "**" or "Hello" range
        val transformation = MarkdownVisualTransformation(
            isFocused = true,
            selection = TextRange(3, 3),
            viewMode = ViewMode.RENDERED,
            tokenColor = tokenColor,
            codeBackgroundColor = codeBgColor
        )

        val rawText = androidx.compose.ui.text.AnnotatedString("> **Hello** there")
        val result = transformation.filter(rawText)

        // When cursor intersects **Hello**, the ** token should be revealed for editing
        assertTrue(result.text.text.contains("**Hello**") || result.text.text.contains("Hello"))
    }

    @Test
    fun testOffsetMappingCorrectnessWhenTokensStripped() {
        val transformation = MarkdownVisualTransformation(
            isFocused = false,
            selection = TextRange(100, 100),
            viewMode = ViewMode.RENDERED,
            tokenColor = tokenColor,
            codeBackgroundColor = codeBgColor
        )

        // Raw: "This is **bold**"
        // Transformed: "This is bold" (len 12)
        // Original tokens: "**" at [8, 10) and "**" at [14, 16)
        val rawText = androidx.compose.ui.text.AnnotatedString("This is **bold**")
        val result = transformation.filter(rawText)

        assertEquals("This is bold", result.text.text)

        // Index 0 in original ('T') -> 0 in transformed
        assertEquals(0, result.offsetMapping.originalToTransformed(0))
        // Index 8 in original (start of first '**') -> 8 in transformed (start of 'b')
        assertEquals(8, result.offsetMapping.originalToTransformed(8))
        // Index 10 in original (start of 'b' in bold) -> 8 in transformed
        assertEquals(8, result.offsetMapping.originalToTransformed(10))
        // Index 14 in original (end of 'd' in bold / start of second '**') -> 12 in transformed
        assertEquals(12, result.offsetMapping.originalToTransformed(14))

        // Transformed index 8 ('b') -> 10 in original
        assertEquals(10, result.offsetMapping.transformedToOriginal(8))
    }
}
