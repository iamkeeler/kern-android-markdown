package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentEditEngineTest {
    @Test
    fun `wrap formats a selection and keeps it selected`() {
        val result = apply("Hello world", 6, 11, DocumentEditEngine.Command.Wrap("**"))

        assertEquals("Hello **world**", result.text)
        assertEquals(8, result.selectionStart)
        assertEquals(13, result.selectionEnd)
    }

    @Test
    fun `wrap inserts a pair at a collapsed cursor`() {
        val result = apply("Hello ", 6, 6, DocumentEditEngine.Command.Wrap("*"))

        assertEquals("Hello **", result.text)
        assertEquals(7, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    @Test
    fun `heading applies to every selected paragraph`() {
        val source = "First paragraph\n\nSecond paragraph"

        val result = apply(source, 0, source.length, DocumentEditEngine.Command.SetHeading(2))

        assertEquals("## First paragraph\n\n## Second paragraph", result.text)
    }

    @Test
    fun `bullet list applies across paragraphs and preserves CRLF`() {
        val source = "First\r\nSecond\r\n"

        val result = apply(source, 0, source.length, DocumentEditEngine.Command.ToggleBulletList)

        assertEquals("- First\r\n- Second\r\n", result.text)
    }

    @Test
    fun `selection ending at next line start does not edit that line`() {
        val source = "First\nSecond"

        val result = apply(source, 0, 6, DocumentEditEngine.Command.Indent)

        assertEquals("    First\nSecond", result.text)
    }

    @Test
    fun `outdent changes every selected line`() {
        val source = "    First\n\tSecond"

        val result = apply(source, 0, source.length, DocumentEditEngine.Command.Outdent)

        assertEquals("First\nSecond", result.text)
    }

    @Test
    fun `checklist toggles each selected task without changing source boundaries`() {
        val source = "- [ ] First\n\n- [x] Second"

        val result = apply(source, 0, source.length, DocumentEditEngine.Command.ToggleChecklist)

        assertEquals("- [x] First\n\n- [ ] Second", result.text)
    }

    @Test
    fun `checklist conversion replaces existing bullet and ordered markers`() {
        val bullets = apply("  * Item", 0, 8, DocumentEditEngine.Command.ToggleChecklist)
        val ordered = apply("3) Item", 0, 7, DocumentEditEngine.Command.ToggleChecklist)

        assertEquals("  * [ ] Item", bullets.text)
        assertEquals("- [ ] Item", ordered.text)
    }

    @Test
    fun `bullet conversion replaces task and ordered markers`() {
        val task = apply("  + [x] Done", 0, 12, DocumentEditEngine.Command.ToggleBulletList)
        val ordered = apply("3) Item", 0, 7, DocumentEditEngine.Command.ToggleBulletList)

        assertEquals("  + Done", task.text)
        assertEquals("- Item", ordered.text)
    }

    @Test
    fun `toggle bullet on empty line creates bullet prefix and positions cursor`() {
        val result = apply("", 0, 0, DocumentEditEngine.Command.ToggleBulletList)

        assertEquals("- ", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(2, result.selectionEnd)
    }

    @Test
    fun `toggle bullet on blank line preserves indentation and positions cursor`() {
        val result = apply("  ", 2, 2, DocumentEditEngine.Command.ToggleBulletList)

        assertEquals("  - ", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(4, result.selectionEnd)
    }

    @Test
    fun `toggle checklist on empty line creates task prefix and positions cursor`() {
        val result = apply("", 0, 0, DocumentEditEngine.Command.ToggleChecklist)

        assertEquals("- [ ] ", result.text)
        assertEquals(6, result.selectionStart)
        assertEquals(6, result.selectionEnd)
    }

    @Test
    fun `set heading on empty line creates heading prefix and positions cursor`() {
        val result = apply("", 0, 0, DocumentEditEngine.Command.SetHeading(1))

        assertEquals("# ", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(2, result.selectionEnd)
    }

    private fun apply(
        text: String,
        start: Int,
        end: Int,
        command: DocumentEditEngine.Command
    ) = DocumentEditEngine.apply(text, start, end, command)
}
