package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDocumentPresentationPlannerTest {
    @Test
    fun `inactive inline tokens are hidden across the document`() {
        val source = "First **bold** paragraph\n\nSecond *italic* paragraph"

        val plan = MarkdownDocumentPresentationPlanner.build(source, source.length, source.length, true)

        assertEquals(listOf("**", "**", "*", "*"), plan.hiddenRanges.map { source.substring(it.start, it.end) })
    }

    @Test
    fun `selection reveals only the intersecting construct`() {
        val source = "First **bold** paragraph\n\nSecond *italic* paragraph"
        val boldCursor = source.indexOf("bold") + 2

        val plan = MarkdownDocumentPresentationPlanner.build(source, boldCursor, boldCursor, true)

        assertEquals(listOf("*", "*"), plan.hiddenRanges.map { source.substring(it.start, it.end) })
    }

    @Test
    fun `image source remains visible while editing`() {
        val source = "Before ![Diagram](images/diagram.png) after"

        val plan = MarkdownDocumentPresentationPlanner.build(source, source.length, source.length, true)

        assertTrue(plan.hiddenRanges.isEmpty())
    }

    @Test
    fun `element ranges use document offsets`() {
        val source = "Plain\n\nSecond **bold**"

        val plan = MarkdownDocumentPresentationPlanner.build(source, 0, 0, false)
        val bold = plan.elements.single { it.type == MarkdownElementType.BOLD }

        assertEquals(source.indexOf("bold"), bold.start)
        assertEquals(source.indexOf("bold") + 4, bold.end)
    }

    @Test
    fun `rendered document plan hides blockquote and fenced code syntax only`() {
        val source = "> A quote\n\n```kotlin\nval answer = 42\n```"

        val plan = MarkdownDocumentPresentationPlanner.build(source, 0, 0, true)

        assertEquals(
            listOf(MarkdownBlockType.BLOCKQUOTE, MarkdownBlockType.CODE_BLOCK),
            plan.blocks.map { it.blockType }
        )
        assertEquals("> ", plan.hiddenRanges.first().let { source.substring(it.start, it.end) })
        assertEquals(
            listOf("```kotlin\n", "```"),
            plan.hiddenRanges.drop(1).map { source.substring(it.start, it.end) }
        )
        assertTrue(plan.elements.any { it.type == MarkdownElementType.INLINE_CODE && source.substring(it.start, it.end) == "val answer = 42\n" })
    }

    @Test
    fun `blockquote stays rendered when caret is on a different line`() {
        val source = "Editing here\n\n> Quote remains rendered"
        val caret = source.indexOf("Editing") + 3

        val plan = MarkdownDocumentPresentationPlanner.build(source, caret, caret, true)

        assertEquals("> ", plan.hiddenRanges.map { source.substring(it.start, it.end) }.single())
        assertEquals(MarkdownBlockType.BLOCKQUOTE, plan.blocks.last().blockType)
    }

    @Test
    fun `indented blockquote retains its content while hiding the marker`() {
        val source = "  > Indented quote"

        val plan = MarkdownDocumentPresentationPlanner.build(source, 0, 0, true)

        assertEquals(listOf("> "), plan.hiddenRanges.map { source.substring(it.start, it.end) })
        assertEquals(MarkdownBlockType.BLOCKQUOTE, plan.blocks.single().blockType)
    }

    @Test
    fun `rendered document plan hides horizontal rule source`() {
        val source = "Before\n\n---\n\nAfter"

        val plan = MarkdownDocumentPresentationPlanner.build(source, source.length, source.length, true)

        assertEquals(listOf("---"), plan.hiddenRanges.map { source.substring(it.start, it.end) })
        assertTrue(plan.blocks.any { it.blockType == MarkdownBlockType.HORIZONTAL_RULE })
    }
}
