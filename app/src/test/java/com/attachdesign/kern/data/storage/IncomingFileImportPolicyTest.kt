package com.attachdesign.kern.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingFileImportPolicyTest {
    @Test
    fun acceptsSupportedSchemes() {
        assertTrue(IncomingFileImportPolicy.isSupportedScheme("content"))
        assertTrue(IncomingFileImportPolicy.isSupportedScheme("file"))
        assertTrue(IncomingFileImportPolicy.isSupportedScheme("CONTENT"))
    }

    @Test
    fun rejectsUnsupportedSchemes() {
        assertFalse(IncomingFileImportPolicy.isSupportedScheme(null))
        assertFalse(IncomingFileImportPolicy.isSupportedScheme("http"))
        assertFalse(IncomingFileImportPolicy.isSupportedScheme("javascript"))
    }

    @Test
    fun acceptsMarkdownAndTextExtensions() {
        assertTrue(IncomingFileImportPolicy.isSupportedType("README.md", null))
        assertTrue(IncomingFileImportPolicy.isSupportedType("README.markdown", null))
        assertTrue(IncomingFileImportPolicy.isSupportedType("README.mdown", null))
        assertTrue(IncomingFileImportPolicy.isSupportedType("notes.txt", null))
    }

    @Test
    fun acceptsSupportedMimeTypesEvenWithoutExtension() {
        assertTrue(IncomingFileImportPolicy.isSupportedType("download", "text/plain"))
        assertTrue(IncomingFileImportPolicy.isSupportedType("download", "text/markdown"))
        assertTrue(IncomingFileImportPolicy.isSupportedType("download", "text/x-markdown; charset=utf-8"))
    }

    @Test
    fun rejectsUnsupportedType() {
        assertFalse(IncomingFileImportPolicy.isSupportedType("image.png", "image/png"))
        assertFalse(IncomingFileImportPolicy.isSupportedType("archive.zip", "application/octet-stream"))
    }

    @Test
    fun sanitizesUnsafeFileNames() {
        assertEquals("secrets.md", IncomingFileImportPolicy.sanitizeFileName("../../secrets.md"))
        assertEquals("bad-name.md", IncomingFileImportPolicy.sanitizeFileName("bad:name.md"))
        assertEquals("spaced name.txt", IncomingFileImportPolicy.sanitizeFileName("  spaced   name.txt  "))
        assertEquals("Opened File.md", IncomingFileImportPolicy.sanitizeFileName(" ../.. "))
    }

    @Test
    fun createsPredictableDuplicateNames() {
        assertEquals("README (2).md", IncomingFileImportPolicy.duplicateFileName("README.md", 2))
        assertEquals("notes (3)", IncomingFileImportPolicy.duplicateFileName("notes", 3))
        assertEquals(".env (2)", IncomingFileImportPolicy.duplicateFileName(".env", 2))
    }
}
