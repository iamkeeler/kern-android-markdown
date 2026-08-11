package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun `render strips syntax and retains formatting spans`() {
        val block = MarkdownParser.parseParagraph("## **Bold** and *italic* with [link](https://example.com)")

        val rendered = MarkdownRenderer.render(block)

        assertEquals("Bold and italic with link", rendered.text)
        assertEquals("Bold", rendered.textFor(MarkdownElementType.BOLD))
        assertEquals("italic", rendered.textFor(MarkdownElementType.ITALIC))
        assertEquals("link", rendered.textFor(MarkdownElementType.LINK))
    }

    @Test
    fun `render handles tasks bullets escapes and images`() {
        assertEquals("☑ Finished", MarkdownRenderer.render(MarkdownParser.parseParagraph("- [x] **Finished**")).text)
        assertEquals("• Item", MarkdownRenderer.render(MarkdownParser.parseParagraph("- Item")).text)
        assertEquals("*literal*", MarkdownRenderer.render(MarkdownParser.parseParagraph("\\*literal\\*")).text)
        assertEquals("Alt text", MarkdownRenderer.render(MarkdownParser.parseParagraph("![Alt text](image.png)")).text)
    }

    @Test
    fun `fenced code hides fence and language but leaves code literal`() {
        val block = MarkdownParser.parseParagraph("```kotlin\nval bold = \"**not bold**\"\n```")

        val rendered = MarkdownRenderer.render(block)

        assertEquals("val bold = \"**not bold**\"\n", rendered.text)
        assertTrue(rendered.spans.any { it.type == MarkdownElementType.INLINE_CODE })
        assertTrue(rendered.spans.none { it.type == MarkdownElementType.BOLD })
    }

    @Test
    fun `document copy uses visible text and preserves block spacing`() {
        val source = "# Heading\n\nText with **bold**.\n\n| Name | Value |\n| --- | --- |\n| One | Two |"

        val copied = MarkdownRenderer.copyDocument(MarkdownParser.parseDocument(source))

        assertEquals("Heading\n\nText with bold.\n\nName\tValue\nOne\tTwo", copied)
    }

    @Test
    fun `every projected span remains within rendered bounds`() {
        val source = "**bold and *italic*** plus ~~strike~~ and `code` and [link](url)"
        val rendered = MarkdownRenderer.render(MarkdownParser.parseParagraph(source))

        rendered.spans.forEach { span ->
            assertTrue(span.start >= 0)
            assertTrue(span.end >= span.start)
            assertTrue(span.end <= rendered.text.length)
        }
    }

    @Test
    fun `every rendered offset maps back to the same rendered position`() {
        val rendered = MarkdownRenderer.render(
            MarkdownParser.parseParagraph("## **bold** *italic* ~~strike~~ `code` [link](url)")
        )

        for (offset in 0..rendered.text.length) {
            val sourceOffset = rendered.indexMatrix.transformedToOriginal(offset)
            assertEquals(offset, rendered.indexMatrix.originalToTransformed(sourceOffset))
        }
    }

    @Test
    fun `incomplete markdown remains visible while typing`() {
        val raw = "**unfinished and [link]("

        assertEquals(raw, MarkdownRenderer.render(MarkdownParser.parseParagraph(raw)).text)
    }

    private fun MarkdownRenderProjection.textFor(type: MarkdownElementType): String {
        val span = spans.first { it.type == type }
        return text.substring(span.start, span.end)
    }
}
