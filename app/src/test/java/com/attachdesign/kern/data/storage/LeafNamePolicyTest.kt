package com.attachdesign.kern.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LeafNamePolicyTest {
    @Test
    fun creationKeepsMarkdownDefaultAndRecognizesUppercaseExtension() {
        assertEquals("Notes.MD", LeafNamePolicy.markdownFileName(" Notes.MD "))
        assertEquals("Notes.md", LeafNamePolicy.markdownFileName("Notes"))
        assertEquals("Notes.txt.md", LeafNamePolicy.markdownFileName("Notes.txt"))
        assertEquals("Notes.markdown.md", LeafNamePolicy.markdownFileName("Notes.markdown"))
    }

    @Test
    fun preservesUnicodeAndInternalSpacesWithoutImposingLengthLimit() {
        assertEquals("日本語  café 😀", LeafNamePolicy.validate("  日本語  café 😀  "))
        assertEquals("a".repeat(500), LeafNamePolicy.validate("a".repeat(500)))
        assertEquals("notes.markdown", LeafNamePolicy.validate("notes.markdown"))
        assertEquals("notes.txt", LeafNamePolicy.validate("notes.txt"))
    }

    @Test
    fun rejectsPathsDotNamesAndRawControlCharacters() {
        listOf("", "   ", ".", " .. ", "a/b", "a\\b", "a\u0000b", "\nname", "name\t", "a\u007fb").forEach { name ->
            try {
                LeafNamePolicy.validate(name)
                fail("Invalid leaf name was accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
