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
