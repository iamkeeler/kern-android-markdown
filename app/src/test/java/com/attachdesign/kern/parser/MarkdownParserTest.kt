package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun testParseTaskLists() {
        val resultUnchecked = MarkdownParser.parseParagraph("- [ ] Buy milk")
        assertEquals(MarkdownBlockType.TASK_LIST, resultUnchecked.blockType)
        val uncheckedBulletToken = resultUnchecked.elements.find { it.type == MarkdownElementType.TOKEN_LIST_BULLET }
        assertEquals("unchecked", uncheckedBulletToken?.extra)
        assertEquals(0, uncheckedBulletToken?.start)
        assertEquals(6, uncheckedBulletToken?.end) // "- [ ] " is 6 chars

        val resultChecked = MarkdownParser.parseParagraph("  * [x] Task complete")
        assertEquals(MarkdownBlockType.TASK_LIST, resultChecked.blockType)
        val checkedBulletToken = resultChecked.elements.find { it.type == MarkdownElementType.TOKEN_LIST_BULLET }
        assertEquals("checked", checkedBulletToken?.extra)
        assertEquals(2, checkedBulletToken?.start) // 2 leading spaces
        assertEquals(8, checkedBulletToken?.end) // "  * [x] "
    }

    @Test
    fun testParseTables() {
        val tableText = """
            | Header 1 | Header 2 |
            | -------- | -------- |
            | Cell 1   | Cell 2   |
        """.trimIndent()
        val result = MarkdownParser.parseParagraph(tableText)
        assertEquals(MarkdownBlockType.TABLE, result.blockType)
    }

    @Test
    fun testParseEscapeCharacters() {
        val result = MarkdownParser.parseParagraph("\\*literal\\*")
        val escapeTokens = result.elements.filter { it.type == MarkdownElementType.TOKEN_ESCAPE_CHAR }
        assertEquals(2, escapeTokens.size)
        // First backslash at 0
        assertEquals(0, escapeTokens[0].start)
        assertEquals(1, escapeTokens[0].end)
        // Second backslash at 9
        assertEquals(9, escapeTokens[1].start)
        assertEquals(10, escapeTokens[1].end)

        // Make sure no bold/italic elements were parsed
        val formattingElements = result.elements.filter {
            it.type == MarkdownElementType.BOLD || it.type == MarkdownElementType.ITALIC
        }
        assertTrue(formattingElements.isEmpty())
    }

    @Test
    fun testParseImages() {
        val result = MarkdownParser.parseParagraph("Here is an image: ![Alt Text](image.png)")
        val imageElement = result.elements.find { it.type == MarkdownElementType.IMAGE }
        assertEquals("image.png", imageElement?.extra)
        assertEquals(20, imageElement?.start) // starts after "Here is an image: !["
        assertEquals(28, imageElement?.end)

        val linkTextTokens = result.elements.filter { it.type == MarkdownElementType.TOKEN_LINK_TEXT }
        assertEquals(2, linkTextTokens.size)
    }
}
