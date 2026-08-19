package com.attachdesign.kern.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MarkdownRenderingInstrumentedTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderedEditorTextStripsMarkdownSyntaxButRetainsWriting() {
        val state = TextFieldState("# Title\n\n> Quote\n- Bullet\n1. Ordered\n- [x] Done\n\n~~~kotlin\nval code = true\n~~~\n\n**bold** and *italic* with `code`")
        // Selecting a formatted span reveals only that construct's Markdown source.
        val boldStart = state.text.indexOf("bold")
        state.edit { selection = TextRange(boldStart, boldStart + "bold".length) }

        composeTestRule.setContent {
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "Rendered markdown probe" },
                outputTransformation = MarkdownDocumentOutputTransformation(
                    viewMode = ViewMode.RENDERED,
                    bodySize = 16.sp,
                    tokenColor = Color.Gray,
                    codeBackgroundColor = Color.LightGray
                )
            )
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithContentDescription("Rendered markdown probe")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }

        assertEquals(
            "Title\n\n│ Quote\n• Bullet\n1. Ordered\n☑ Done\n\nval code = true\n\n**bold** and italic with code",
            layouts.single().layoutInput.text.text
        )
        assertEquals("# Title\n\n> Quote\n- Bullet\n1. Ordered\n- [x] Done\n\n~~~kotlin\nval code = true\n~~~\n\n**bold** and *italic* with `code`", state.text.toString())
    }
}
