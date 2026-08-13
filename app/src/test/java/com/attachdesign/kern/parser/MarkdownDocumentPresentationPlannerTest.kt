package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDocumentPresentationPlannerTest {
    @Test
    fun `inactive inline tokens are hidden across the document`() {
        val source = "First **bold** paragraph\n\nSecond *italic* paragraph"

        val plan = MarkdownDocumentPresentationPlanner.build(source, 0, 0, true)

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

        val plan = MarkdownDocumentPresentationPlanner.build(source, 0, 0, true)

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
}
