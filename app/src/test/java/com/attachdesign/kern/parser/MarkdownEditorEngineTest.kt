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
