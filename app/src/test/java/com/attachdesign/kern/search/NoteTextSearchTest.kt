package com.attachdesign.kern.search

import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.DirectoryOutcome
import com.attachdesign.kern.data.storage.VfsNode
import java.io.IOException
import java.io.StringReader
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextSearchTest {
    @Test
    fun literalMatchesKeepSourceOffsetsAcrossCaseAndUnicode() {
        val source = "📝 **First** first FIRST"
        assertEquals(listOf(5..9, 13..17, 19..23), NoteTextSearch.matches(source, "first").toList())
        assertEquals(emptyList<IntRange>(), NoteTextSearch.matches(source, "").toList())
    }

    @Test
    fun excerptFindsPhrasesAcrossReadBoundaries() = runTest {
        val source = "x".repeat(8185) + " idea at dawn\nNext line."
        val result = NoteTextSearch.firstExcerpt(StringReader(source), "IDEA AT DAWN")
        assertTrue(result.orEmpty().startsWith("…"))
        assertTrue(result.orEmpty().contains("idea at dawn Next line."))
        assertNull(NoteTextSearch.firstExcerpt(StringReader(source), "missing"))
    }

    @Test
    fun workspaceSearchKeepsNestedMatchesWhenAnotherFolderOrNoteIsUnavailable() = runTest {
        val project = ProjectEntity(1, "Notes", "root", false, true)
        fun file(name: String, path: String = name) = VfsNode.File(name, path, 0, 0)
        val search = WorkspaceNoteSearch(
            list = { _, path ->
                when (path) {
                    "" -> DirectoryOutcome.Success(listOf(
                        VfsNode.Directory("Archive", "Archive"),
                        VfsNode.Directory("Locked", "Locked"),
                        VfsNode.Directory(".trash", ".trash"),
                        file("phrase.md"),
                        file("image.png")
                    ))
                    "Archive" -> DirectoryOutcome.Success(listOf(file("draft.MARKDOWN", "Archive/draft.MARKDOWN")))
                    ".trash" -> error("Trashed notes must not be searched")
                    else -> DirectoryOutcome.AccessDenied
                }
            },
            open = { _, path ->
                when (path) {
                    "Archive/draft.MARKDOWN" -> StringReader("A remembered PHRASE in a draft.")
                    "phrase.md" -> throw IOException("Unavailable")
                    else -> error("Unexpected read of $path")
                }
            }
        )
        val result = search.search(listOf(project), "phrase").last()
        assertTrue(result.incomplete)
        assertEquals(false, result.searching)
        assertEquals(listOf("phrase.md", "Archive/draft.MARKDOWN"), result.hits.map { it.node.relativePath })
        assertEquals(listOf(null, "A remembered PHRASE in a draft."), result.hits.map { it.excerpt })
    }
}
