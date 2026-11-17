package com.attachdesign.kern.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.toTextFieldBuffer
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownDocumentInputTransformationTest {
    private fun type(source: String, inserted: String): Pair<String, TextRange> {
        val buffer = TextFieldState(source, TextRange(source.length)).toTextFieldBuffer()
        buffer.replace(buffer.length, buffer.length, inserted)
        buffer.selection = TextRange(buffer.length)
        with(MarkdownDocumentInputTransformation()) { buffer.transformInput() }
        return buffer.toString() to buffer.selection
    }

    @Test
    fun defaultPairingAppliesToTheDocumentField() {
        assertEquals("()" to TextRange(1), type("", "("))
    }

    @Test
    fun defaultHeadingSpacingAppliesAfterAnyDocumentLineBreak() {
        assertEquals("Earlier\r\n\r\n## t" to TextRange(15), type("Earlier\r\n\r\n##", "t"))
    }
}
