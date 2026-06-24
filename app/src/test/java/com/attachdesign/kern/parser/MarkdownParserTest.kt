package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testParseHorizontalRules() {
        val resultDash = MarkdownParser.parseParagraph("---")
        assertEquals(MarkdownBlockType.HORIZONTAL_RULE, resultDash.blockType)

        val resultAsterisk = MarkdownParser.parseParagraph("***")
        assertEquals(MarkdownBlockType.HORIZONTAL_RULE, resultAsterisk.blockType)

        val resultUnderscore = MarkdownParser.parseParagraph("___")
        assertEquals(MarkdownBlockType.HORIZONTAL_RULE, resultUnderscore.blockType)

        val resultWithSpaces = MarkdownParser.parseParagraph("  ---  ")
        assertEquals(MarkdownBlockType.HORIZONTAL_RULE, resultWithSpaces.blockType)
    }

    @Test
    fun testNotHorizontalRules() {
        val resultNotHr1 = MarkdownParser.parseParagraph("--- text")
        assertEquals(MarkdownBlockType.PARAGRAPH, resultNotHr1.blockType)

        val resultNotHr2 = MarkdownParser.parseParagraph("--")
        assertEquals(MarkdownBlockType.PARAGRAPH, resultNotHr2.blockType)

        val resultNotHr3 = MarkdownParser.parseParagraph("=====")
        assertEquals(MarkdownBlockType.PARAGRAPH, resultNotHr3.blockType)
    }
}
