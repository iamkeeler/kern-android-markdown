package com.attachdesign.kern.parser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A document-level guard against source syntax leaking into the live-rendered projection.
 * Keep this fixture small and add the minimal source that exposed every editor regression.
 */
class MarkdownRenderingRegressionTest {
    @Test
    fun `supported markdown fixture has a rendered projection for every text style`() {
        val fixture = listOf(
            File("app/src/test/resources/markdown-rendering-regression.md"),
            File("src/test/resources/markdown-rendering-regression.md")
        ).first { it.exists() }.readText()

        val blocks = MarkdownParser.parseDocument(fixture)
        val rendered = blocks.associateBy({ it.rawText }, MarkdownRenderer::render)

        assertTrue(blocks.any { it.blockType == MarkdownBlockType.HEADER_1 })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.HEADER_2 })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.BLOCKQUOTE })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.UNORDERED_LIST })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.ORDERED_LIST })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.TASK_LIST })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.HORIZONTAL_RULE })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.CODE_BLOCK })
        assertTrue(blocks.any { it.blockType == MarkdownBlockType.TABLE })

        assertEquals("Heading two", rendered["## Heading two ##"]!!.text)
        assertEquals("bold, italic, deleted, and code.", rendered["**bold**, *italic*, ~~deleted~~, and `code`."]!!.text)
        assertEquals("☑ A completed task", rendered["- [x] A completed task"]!!.text)
        assertEquals("", rendered["---"]!!.text)
        assertEquals("val literal = \"**not bold**\"\n", rendered["~~~kotlin\nval literal = \"**not bold**\"\n~~~"]!!.text)
        assertEquals("Left\tCenter\tRight\nOne\tTwo\tThree", rendered["| Left | Center | Right |\n| :--- | :----: | ----: |\n| **One** | `Two` | Three |"]!!.text)
        assertEquals("Kern and Diagram", rendered["[Kern](https://kern.attach.design \"Kern home\") and ![Diagram](images/diagram.png)"]!!.text)
        assertEquals("*escaped literal*", rendered["\\*escaped literal\\*"]!!.text)

        val emphasis = blocks.first { it.rawText.startsWith("**bold**") }.elements
        assertTrue(emphasis.any { it.type == MarkdownElementType.BOLD })
        assertTrue(emphasis.any { it.type == MarkdownElementType.ITALIC })
        assertTrue(emphasis.any { it.type == MarkdownElementType.STRIKETHROUGH })
        assertTrue(emphasis.any { it.type == MarkdownElementType.INLINE_CODE })
        assertFalse(rendered.values.any { projection -> projection.text.startsWith("# ") })
    }

    @Test
    fun `fence delimiter must match and closing fence must be long enough`() {
        val source = "````\nbody\n```\n````"

        val rendered = MarkdownRenderer.render(MarkdownParser.parseParagraph(source))

        assertEquals("body\n```\n", rendered.text)
    }
}
