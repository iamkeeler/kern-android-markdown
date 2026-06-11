package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.FileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VfsNodeMapperTest {

    @Test
    fun testEnrichFiles() {
        val diskFiles = listOf(
            VfsNode.File("Welcome.md", "Welcome.md", 1024L, 123456789L),
            VfsNode.File("LocalOnly.md", "LocalOnly.md", 512L, 123456789L),
            VfsNode.Directory("Work", "Work")
        )

        val dbFiles = listOf(
            FileEntity(
                id = 1L,
                projectId = 1L,
                name = "Welcome.md",
                relativePath = "Welcome.md",
                isDirectory = false,
                lastModified = 123456789L,
                syncState = "SYNCED"
            )
        )

        val enriched = VfsNodeMapper.enrichFiles(diskFiles, dbFiles)

        assertEquals(3, enriched.size)

        val welcome = enriched[0] as VfsNode.File
        assertEquals("Welcome.md", welcome.name)
        assertEquals("SYNCED", welcome.syncState)

        val localOnly = enriched[1] as VfsNode.File
        assertEquals("LocalOnly.md", localOnly.name)
        assertNull(localOnly.syncState)

        val dir = enriched[2] as VfsNode.Directory
        assertEquals("Work", dir.name)
    }
}
