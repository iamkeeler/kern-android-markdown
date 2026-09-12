package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the parser findings M1–M4:
 * nested emphasis, link/image destinations, multi-backtick code spans, pipe-less tables.
 */
class MarkdownParserFixesTest {

    private fun textOf(block: ParagraphBlock, type: MarkdownElementType): String {
        val element = block.elements.first { it.type == type }
        return block.rawText.substring(element.start, element.end)
    }

    // M1: nested emphasis with the same marker must not lose the inner construct.

    @Test
    fun `nested bold inside italic keeps both spans`() {
        val block = MarkdownParser.parseParagraph("*a **b** c*")

        val italic = block.elements.first { it.type == MarkdownElementType.ITALIC }
        val bold = block.elements.first { it.type == MarkdownElementType.BOLD }
        assertEquals("a **b** c", block.rawText.substring(italic.start, italic.end))
        assertEquals("b", block.rawText.substring(bold.start, bold.end))
        assertTrue(italic.start < bold.start && bold.end < italic.end)
    }

    @Test
    fun `nested bold inside underscore italic keeps both spans`() {
        val block = MarkdownParser.parseParagraph("_a __b__ c_")

        val italic = block.elements.first { it.type == MarkdownElementType.ITALIC }
        val bold = block.elements.first { it.type == MarkdownElementType.BOLD }
        assertEquals("a __b__ c", block.rawText.substring(italic.start, italic.end))
        assertEquals("b", block.rawText.substring(bold.start, bold.end))
    }

    @Test
    fun `plain italic and bold still parse`() {
        val block = MarkdownParser.parseParagraph("*italic* and **bold**")

        assertEquals("italic", textOf(block, MarkdownElementType.ITALIC))
        assertEquals("bold", textOf(block, MarkdownElementType.BOLD))
    }

    // M2: link/image destinations tolerate balanced parens; link text tolerates nested brackets.

    @Test
    fun `link destination may contain balanced parentheses`() {
        val block = MarkdownParser.parseParagraph("[a](b(c))")

        val link = block.elements.first { it.type == MarkdownElementType.LINK }
        assertEquals("b(c)", link.extra)
        assertEquals("a", block.rawText.substring(link.start, link.end))
    }

    @Test
    fun `link text may contain balanced brackets`() {
        val block = MarkdownParser.parseParagraph("[a [b](c) d](e)")

        val link = block.elements.first { it.type == MarkdownElementType.LINK }
        assertEquals("e", link.extra)
        assertEquals("a [b](c) d", block.rawText.substring(link.start, link.end))
    }

    @Test
    fun `image destination may contain balanced parentheses`() {
        val block = MarkdownParser.parseParagraph("![a](b(c))")

        val image = block.elements.first { it.type == MarkdownElementType.IMAGE }
        assertEquals("b(c)", image.extra)
        assertEquals("a", block.rawText.substring(image.start, image.end))
    }

    // M3: a code span closes on a backtick run exactly as long as the opening run.

    @Test
    fun `double backtick code span keeps inner backtick`() {
        val block = MarkdownParser.parseParagraph("``a`b``")

        assertEquals("a`b", textOf(block, MarkdownElementType.INLINE_CODE))
    }

    @Test
    fun `single backtick code span still parses`() {
        val block = MarkdownParser.parseParagraph("`code`")

        assertEquals("code", textOf(block, MarkdownElementType.INLINE_CODE))
    }

    @Test
    fun `mismatched backtick runs do not form a code span`() {
        val block = MarkdownParser.parseParagraph("``a`")

        assertTrue(block.elements.none { it.type == MarkdownElementType.INLINE_CODE })
    }

    // M4: tables without outer pipes render their rows instead of projecting to "".

    @Test
    fun `table without outer pipes renders its rows`() {
        val source = "a | b\n---|---\nc | d"

        val document = MarkdownRenderer.renderDocument(source)

        assertTrue(document.blocks.any { it.type == MarkdownBlockType.TABLE })
        assertEquals("a\tb\nc\td", document.text)
    }

    @Test
    fun `table with outer pipes still renders`() {
        val source = "| a | b |\n| --- | --- |\n| c | d |"

        assertEquals("a\tb\nc\td", MarkdownRenderer.renderDocument(source).text)
    }

    @Test
    fun `pipe-less table copies as visible tab-separated text`() {
        val blocks = MarkdownParser.parseDocument("a | b\n---|---\nc | d")

        assertEquals("a\tb\nc\td", MarkdownRenderer.copyDocument(blocks))
    }
}
