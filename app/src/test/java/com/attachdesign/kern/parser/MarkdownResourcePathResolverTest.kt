package com.attachdesign.kern.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownResourcePathResolverTest {
    @Test
    fun `relative image paths resolve from the open document directory`() {
        assertEquals(
            "notes/images/diagram.png",
            MarkdownResourcePathResolver.resolve("notes/chapter.md", "images/diagram.png")
        )
        assertEquals(
            "shared/diagram.png",
            MarkdownResourcePathResolver.resolve("notes/chapter.md", "../shared/diagram.png")
        )
    }

    @Test
    fun `root paths and remote urls remain stable`() {
        assertEquals("images/diagram.png", MarkdownResourcePathResolver.resolve("notes/chapter.md", "/images/diagram.png"))
        assertEquals(
            "https://example.com/diagram.png",
            MarkdownResourcePathResolver.resolve("notes/chapter.md", "https://example.com/diagram.png")
        )
    }

    @Test
    fun `paths cannot escape the project root`() {
        assertNull(MarkdownResourcePathResolver.resolve("chapter.md", "../diagram.png"))
    }
}
