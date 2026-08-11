package com.attachdesign.kern.parser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDocumentScannerTest {
    @Test
    fun `formatting examples fixture renders without visible syntax tokens`() {
        val fixture = listOf(
            File("src/main/assets/Formatting Examples.md"),
            File("app/src/main/assets/Formatting Examples.md")
        ).first { it.exists() }.readText()

        val blocks = MarkdownParser.parseDocument(fixture)
        val headings = blocks.filter { it.blockType.name.startsWith("HEADER_") }

        assertTrue(headings.size >= 6)
        headings.forEach { heading ->
            assertFalse(MarkdownRenderer.render(heading).text.startsWith("#"))
        }
        assertEquals(fixture, MarkdownParser.joinParsedDocument(blocks))
    }

    @Test
    fun `consecutive headings become independent blocks`() {
        val source = (1..6).joinToString("\n") { level -> "${"#".repeat(level)} Heading $level" }

        val blocks = MarkdownParser.parseDocument(source)

        assertEquals(6, blocks.size)
        assertEquals(
            listOf(
                MarkdownBlockType.HEADER_1,
                MarkdownBlockType.HEADER_2,
                MarkdownBlockType.HEADER_3,
                MarkdownBlockType.HEADER_4,
                MarkdownBlockType.HEADER_5,
                MarkdownBlockType.HEADER_6
            ),
            blocks.map { it.blockType }
        )
        assertEquals(source, MarkdownParser.joinParsedDocument(blocks))
    }

    @Test
    fun `scan and join preserve exact whitespace and line endings`() {
        val source = "\r\n# Heading\r\n\r\n\r\nParagraph line one\r\nline two\r\n- item\r\n"

        val blocks = MarkdownParser.parseDocument(source)

        assertEquals(source, MarkdownParser.joinParsedDocument(blocks))
    }

    @Test
    fun `fenced code remains one block while adjacent structures split`() {
        val source = "Before\n```kotlin\n# literal\n\n**literal**\n```\n## After"

        val blocks = MarkdownParser.parseDocument(source)

        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlockType.CODE_BLOCK, blocks[1].blockType)
        assertEquals(source, MarkdownParser.joinParsedDocument(blocks))
    }

    @Test
    fun `tables remain one block`() {
        val source = "| A | B |\n| - | - |\n| 1 | 2 |\nParagraph"

        val blocks = MarkdownParser.parseDocument(source)

        assertEquals(2, blocks.size)
        assertEquals(MarkdownBlockType.TABLE, blocks.first().blockType)
        assertEquals(source, MarkdownParser.joinParsedDocument(blocks))
    }

    @Test
    fun `list continuation lines remain attached to their item`() {
        val source = "- First line\n  continued text"

        val blocks = MarkdownParser.parseDocument(source)

        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlockType.UNORDERED_LIST, blocks.single().blockType)
        assertEquals("• First line\n  continued text", MarkdownRenderer.render(blocks.single()).text)
        assertEquals(source, MarkdownParser.joinParsedDocument(blocks))
    }
}
