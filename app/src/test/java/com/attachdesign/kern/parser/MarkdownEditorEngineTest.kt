package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorEngineTest {

    @Test
    fun testBulletListContinuation() {
        val result = MarkdownEditorEngine.checkContinuation("* Item 1")
        assertTrue(result.isContinuation)
        assertFalse(result.isExit)
        assertEquals("* Item 1", result.newCurrentText)
        assertEquals("* ", result.nextLinePrefix)
    }

    @Test
    fun `continuation preserves every supported list marker`() {
        assertEquals("- ", MarkdownEditorEngine.checkContinuation("- One").nextLinePrefix)
        assertEquals("* ", MarkdownEditorEngine.checkContinuation("* One").nextLinePrefix)
        assertEquals("+ ", MarkdownEditorEngine.checkContinuation("+ One").nextLinePrefix)
        assertEquals("2. ", MarkdownEditorEngine.checkContinuation("1. One").nextLinePrefix)
        assertEquals("2) ", MarkdownEditorEngine.checkContinuation("1) One").nextLinePrefix)
        assertEquals("  * [ ] ", MarkdownEditorEngine.checkContinuation("  * [x] Done").nextLinePrefix)
    }

    @Test
    fun `document continuation preserves formatted list content and cursor prefix`() {
        val result = MarkdownEditorEngine.continueDocumentList("- **Bold**", 10, "\n")

        assertEquals(0, result?.markerStart)
        assertEquals(2, result?.markerEnd)
        assertEquals("- ", result?.nextPrefix)
    }

    @Test
    fun `document continuation skips code blocks and thematic breaks`() {
        val code = "```\n- literal"

        assertEquals(null, MarkdownEditorEngine.continueDocumentList(code, code.length, "\n"))
        assertEquals(null, MarkdownEditorEngine.continueDocumentList("* * *", 5, "\n"))
    }

    @Test
    fun `isOffsetInCodeBlock accurately tracks fence open and close boundaries`() {
        val text = buildString {
            append("Before code\n")        // 0..11
            append("```kotlin\n")          // 12..21
            append("val x = 1\n")          // 22..31
            append("- not a list item\n")  // 32..50
            append("```\n")                // 51..54
            append("- real list item\n")   // 55..72
        }

        // Before code block
        assertFalse(MarkdownEditorEngine.isOffsetInCodeBlock(text, 5))
        assertFalse(MarkdownEditorEngine.isOffsetInCodeBlock(text, 11))

        // Inside code block
        assertTrue(MarkdownEditorEngine.isOffsetInCodeBlock(text, 25))
        assertTrue(MarkdownEditorEngine.isOffsetInCodeBlock(text, 40))

        // After closing fence
        val listOffset = text.indexOf("- real list item") + "- real list item".length
        assertFalse(MarkdownEditorEngine.isOffsetInCodeBlock(text, listOffset))

        // Continuation after closed code block works normally
        val continuation = MarkdownEditorEngine.continueDocumentList(text, listOffset, "\n")
        assertEquals("- ", continuation?.nextPrefix)
    }

    @Test
    fun `isOffsetInCodeBlock respects fence marker character and minimum length`() {
        val text = buildString {
            append("````\n")
            append("```\n") // 3 backticks cannot close a 4-backtick fence
            append("still in code\n")
            append("~~~~\n") // tildes cannot close a backtick fence
            append("````\n") // closes here
            append("outside\n")
        }

        val insideOffset = text.indexOf("still in code")
        assertTrue(MarkdownEditorEngine.isOffsetInCodeBlock(text, insideOffset))

        val outsideOffset = text.indexOf("outside")
        assertFalse(MarkdownEditorEngine.isOffsetInCodeBlock(text, outsideOffset))
    }

    @Test
    fun `empty list item exits by removing its source marker`() {
        val result = MarkdownEditorEngine.continueDocumentList("  - ", 4, "\n")

        assertEquals(0, result?.markerStart)
        assertEquals(4, result?.markerEnd)
        assertEquals(null, result?.nextPrefix)
    }

    @Test
    fun testBulletListExit() {
        val result = MarkdownEditorEngine.checkContinuation("* ")
        assertTrue(result.isContinuation)
        assertTrue(result.isExit)
        assertEquals("", result.newCurrentText)
        assertEquals("", result.nextLinePrefix)
    }

    @Test
    fun testBulletListExitAlternatePrefixes() {
        val resultDash = MarkdownEditorEngine.checkContinuation("- ")
        assertTrue(resultDash.isContinuation)
        assertTrue(resultDash.isExit)
        assertEquals("", resultDash.newCurrentText)

        val resultPlus = MarkdownEditorEngine.checkContinuation("+ ")
        assertTrue(resultPlus.isContinuation)
        assertTrue(resultPlus.isExit)
        assertEquals("", resultPlus.newCurrentText)
    }

    @Test
    fun testNoListContinuation() {
        val result = MarkdownEditorEngine.checkContinuation("Just regular text")
        assertFalse(result.isContinuation)
    }
}
