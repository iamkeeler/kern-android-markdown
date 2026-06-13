package com.attachdesign.kern.data.storage

import android.content.Context
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StorageManagerTest {

    private lateinit var context: Context
    private lateinit var storageManager: StorageManager
    private lateinit var sandboxDir: File
    private lateinit var project: ProjectEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageManager = StorageManager(context)
        sandboxDir = File(context.filesDir, "sandbox")
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
}
