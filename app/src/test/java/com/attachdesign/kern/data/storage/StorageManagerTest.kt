package com.attachdesign.kern.data.storage

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.attachdesign.kern.data.local.ProjectEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageManagerTest {

    private lateinit var context: Context
    private lateinit var storageManager: StorageManager
    private lateinit var sandboxDir: File
    private lateinit var project: ProjectEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageManager = StorageManager(context)
        sandboxDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Kern/sandbox")
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
    fun testReadAssetFile() {
        val content = storageManager.readAssetFile("Welcome.md")
        assertTrue(content.contains("Welcome to Kern"))

        val examplesContent = storageManager.readAssetFile("Formatting Examples.md")
        assertTrue(examplesContent.contains("Formatting Examples"))
    }
}
