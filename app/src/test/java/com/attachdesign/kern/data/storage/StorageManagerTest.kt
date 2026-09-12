package com.attachdesign.kern.data.storage

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.attachdesign.kern.data.local.ProjectEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageManagerTest {

    companion object {
        /**
         * M23: per-class temp directory instead of a fixed shared real path, so
         * parallel workers or residue from an interrupted run cannot collide.
         * Per-test isolation is still provided by tearDown deleting [sandboxDir].
         */
        @ClassRule
        @JvmField
        val classTempDir = TemporaryFolder()
    }

    private lateinit var context: Context
    private lateinit var storageManager: StorageManager
    private lateinit var sandboxDir: File
    private lateinit var project: ProjectEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val kernRoot = File(classTempDir.root, "Kern")
        storageManager = StorageManager(context, kernRoot)
        sandboxDir = File(kernRoot, "sandbox")
        project = ProjectEntity(name = "TestProject", path = "TestProjectDir", isExternal = false, isSelected = false)
    }

    @After
    fun tearDown() {
        if (sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        }
    }

    @Test
    fun testCreateFile_internal() = runTest {
        val result = storageManager.createFile(project, "file1.md")
        assertTrue(result)

        val file = File(sandboxDir, "TestProjectDir/file1.md")
        assertTrue(file.exists())
        assertTrue(file.isFile)
    }

    @Test
    fun testCreateDirectory_internal() = runTest {
        val result = storageManager.createDirectory(project, "folder1")
        assertTrue(result)

        val dir = File(sandboxDir, "TestProjectDir/folder1")
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun testListDirectory_internal() = runTest {
        storageManager.createFile(project, "file1.md")
        storageManager.createDirectory(project, "folder1")
        storageManager.createFile(project, "folder1/file2.md")

        val rootNodes = storageManager.listDirectory(project, "")
        assertEquals(2, rootNodes.size)
        assertTrue(rootNodes.any { it.name == "folder1" && it.isDirectory })
        assertTrue(rootNodes.any { it.name == "file1.md" && !it.isDirectory })

        val folderNodes = storageManager.listDirectory(project, "folder1")
        assertEquals(1, folderNodes.size)
        assertTrue(folderNodes.any { it.name == "file2.md" && !it.isDirectory })
    }

    @Test
    fun listDirectory_returnsEmptyWhenLinkedFolderPermissionIsRevoked() = runTest {
        val deniedResolver = mockk<ContentResolver>()
        val deniedContext = mockk<Context>(relaxed = true)
        every { deniedContext.contentResolver } returns deniedResolver
        every {
            deniedResolver.query(any<Uri>(), any(), any(), any(), any())
        } throws SecurityException("Permission denied")

        val linkedProject = ProjectEntity(
            name = "Revoked folder",
            path = "content://com.example.documents/tree/revoked",
            isExternal = true,
            isSelected = true
        )

        val files = StorageManager(deniedContext).listDirectory(linkedProject, "")

        assertTrue(files.isEmpty())
    }

    @Test
    fun documentToVfsNodeOrNull_skipsDocumentsWithoutNames() {
        val document = mockk<DocumentFile>()
        every { document.name } returns null

        assertNull(document.toVfsNodeOrNull(""))
    }

    @Test
    fun testWriteAndReadFile_internal() = runTest {
        val content = "Hello, world!\nMarkdown content."
        val filename = "hello.md"

        storageManager.writeFile(project, filename, content)

        val readContent = storageManager.readFile(project, filename)
        assertEquals(content, readContent)
    }

    @Test
    fun testDeleteFile_internal() = runTest {
        storageManager.createFile(project, "delete_me.md")
        var file = File(sandboxDir, "TestProjectDir/delete_me.md")
        assertTrue(file.exists())

        val result = storageManager.deleteFile(project, "delete_me.md")
        assertTrue(result)

        file = File(sandboxDir, "TestProjectDir/delete_me.md")
        assertFalse(file.exists())
    }

    @Test
    fun testRenameFile_internal() = runTest {
        storageManager.createFile(project, "old_name.md")
        var oldFile = File(sandboxDir, "TestProjectDir/old_name.md")
        assertTrue(oldFile.exists())

        val result = storageManager.renameFile(project, "old_name.md", "new_name.md")
        assertTrue(result)

        oldFile = File(sandboxDir, "TestProjectDir/old_name.md")
        assertFalse(oldFile.exists())

        val newFile = File(sandboxDir, "TestProjectDir/new_name.md")
        assertTrue(newFile.exists())
    }

    @Test
    fun testMoveNode_internal() = runTest {
        val project2 = ProjectEntity(name = "Project2", path = "Project2Dir", isExternal = false, isSelected = false)

        storageManager.createFile(project, "move_me.md")
        storageManager.writeFile(project, "move_me.md", "content to move")

        val result = storageManager.moveNode(project, "move_me.md", project2, "moved.md")
        assertTrue(result)

        val oldFile = File(sandboxDir, "TestProjectDir/move_me.md")
        assertFalse(oldFile.exists())

        val newFile = File(sandboxDir, "Project2Dir/moved.md")
        assertTrue(newFile.exists())
        assertEquals("content to move", newFile.readText())
    }

    @Test(expected = SecurityException::class)
    fun testPathTraversalVulnerability_readFile() = runTest {
        storageManager.readFile(project, "../../../etc/passwd")
    }

    @Test(expected = SecurityException::class)
    fun testPathTraversalVulnerability_createFile() = runTest {
        storageManager.createFile(project, "../outside.md")
    }

    @Test
    fun testReadAssetFile() = runTest {
        val content = storageManager.readAssetFile("Welcome.md")
        assertTrue(content.contains("Welcome to Kern"))

        val examplesContent = storageManager.readAssetFile("Formatting Examples.md")
        assertTrue(examplesContent.contains("Formatting Examples"))
    }

    @Test
    fun moveNode_saf_doesNotDeleteSourceWhenWriteFails() = runTest {
        // C1: the SAF move must not delete the source when the destination write fails.
        mockkStatic(DocumentFile::class)
        try {
            val resolver = mockk<ContentResolver>()
            val safContext = mockk<Context>(relaxed = true)
            every { safContext.contentResolver } returns resolver

            val rootDoc = mockk<DocumentFile>()
            val srcDoc = mockk<DocumentFile>()
            val srcUri = Uri.parse("content://com.example.documents/tree/saf/document/1")
            every { DocumentFile.fromTreeUri(any(), any()) } returns rootDoc
            every { rootDoc.findFile("note.md") } returns srcDoc
            every { srcDoc.uri } returns srcUri
            every { srcDoc.isDirectory } returns false
            every { resolver.openInputStream(srcUri) } returns ByteArrayInputStream("hello".toByteArray())
            // Destination write fails: temp document creation returns null.
            every { rootDoc.createFile(any(), any()) } returns null

            val safManager = StorageManager(safContext)
            val safProject = ProjectEntity(
                name = "SafProject",
                path = "content://com.example.documents/tree/saf",
                isExternal = true,
                isSelected = false
            )

            val moved = safManager.moveNode(safProject, "note.md", safProject, "moved.md")

            assertFalse(moved)
            verify(exactly = 0) { srcDoc.delete() }
        } finally {
            unmockkStatic(DocumentFile::class)
        }
    }

    @Test
    fun moveNode_saf_doesNotDeleteSourceWhenReadFails() = runTest {
        // C1: the SAF move must report failure (not success) when the source cannot be read.
        mockkStatic(DocumentFile::class)
        try {
            every { DocumentFile.fromTreeUri(any(), any()) } returns null

            val safManager = StorageManager(context)
            val safProject = ProjectEntity(
                name = "SafProject",
                path = "content://com.example.documents/tree/saf",
                isExternal = true,
                isSelected = false
            )

            val moved = safManager.moveNode(safProject, "missing.md", safProject, "moved.md")

            assertFalse(moved)
        } finally {
            unmockkStatic(DocumentFile::class)
        }
    }
}
