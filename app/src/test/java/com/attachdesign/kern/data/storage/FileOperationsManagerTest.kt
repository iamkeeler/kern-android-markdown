package com.attachdesign.kern.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FileOperationsManagerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var fileOpsManager: FileOperationsManager
    private lateinit var sandboxDir: File
    private lateinit var project: ProjectEntity

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        storageManager = StorageManager(context)
        fileOpsManager = FileOperationsManager(database, storageManager, context)
        sandboxDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Kern/sandbox")
        project = ProjectEntity(id = 1, name = "TestProject", path = "TestProjectDir", isExternal = false, isSelected = true)
        
        database.projectDao().insertProject(project)
    }

    @After
    fun tearDown() {
        database.close()
        if (sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        }
        Dispatchers.resetMain()
    }


    @Test
    fun testDuplicateFile() = runTest {
        val fileName = "note.md"
        val relativePath = "note.md"
        storageManager.writeFile(project, relativePath, "Original content")

        val fileEntity = FileEntity(
            projectId = project.id,
            name = fileName,
            relativePath = relativePath,
            isDirectory = false,
            lastModified = System.currentTimeMillis(),
            syncState = "SYNCED",
            wordCount = 2,
            characterCount = 16,
            readabilityGrade = "G1"
        )
        database.fileDao().insertFile(fileEntity)

        val node = VfsNode.File(fileName, relativePath, 16L, System.currentTimeMillis())
        val result = fileOpsManager.duplicateNode(project, node)

        assertTrue(result.isSuccess)
        val dupNode = result.getOrThrow() as VfsNode.File
        assertEquals("note_copy.md", dupNode.name)
        assertEquals("note_copy.md", dupNode.relativePath)

        val dupContent = storageManager.readFile(project, "note_copy.md")
        assertEquals("Original content", dupContent)

        val dbEntity = database.fileDao().getFileByPath(project.id, "note_copy.md")
        assertNotNull(dbEntity)
        assertEquals(2, dbEntity?.wordCount)
        assertEquals(16, dbEntity?.characterCount)
        assertEquals("G1", dbEntity?.readabilityGrade)
    }

    @Test
    fun testRenameFile() = runTest {
        val originalName = "note.md"
        val relativePath = "note.md"
        storageManager.writeFile(project, relativePath, "Original content")

        val fileEntity = FileEntity(
            projectId = project.id,
            name = originalName,
            relativePath = relativePath,
            isDirectory = false,
            lastModified = System.currentTimeMillis(),
            syncState = "SYNCED"
        )
        database.fileDao().insertFile(fileEntity)

        val node = VfsNode.File(originalName, relativePath, 16L, System.currentTimeMillis())
        val result = fileOpsManager.renameNode(project, node, "renamed.md")

        assertTrue(result.isSuccess)
        val renamedNode = result.getOrThrow() as VfsNode.File
        assertEquals("renamed.md", renamedNode.name)
        assertEquals("renamed.md", renamedNode.relativePath)

        // Check file on disk
        val content = storageManager.readFile(project, "renamed.md")
        assertEquals("Original content", content)

        // Check old file does not exist
        assertFalse(File(sandboxDir, "TestProjectDir/note.md").exists())

        // Check DB
        assertNull(database.fileDao().getFileByPath(project.id, "note.md"))
        assertNotNull(database.fileDao().getFileByPath(project.id, "renamed.md"))
    }

    @Test
    fun testRenameDirectoryAndPruneChildren() = runTest {
        // Create directory structure on disk
        storageManager.createDirectory(project, "docs")
        storageManager.writeFile(project, "docs/doc1.md", "Content 1")
        storageManager.writeFile(project, "docs/doc2.md", "Content 2")

        // Populate DB
        database.fileDao().insertFile(FileEntity(projectId = project.id, name = "docs", relativePath = "docs", isDirectory = true, lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
        database.fileDao().insertFile(FileEntity(projectId = project.id, name = "doc1.md", relativePath = "docs/doc1.md", isDirectory = false, lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
        database.fileDao().insertFile(FileEntity(projectId = project.id, name = "doc2.md", relativePath = "docs/doc2.md", isDirectory = false, lastModified = System.currentTimeMillis(), syncState = "SYNCED"))

        val node = VfsNode.Directory("docs", "docs")
        val result = fileOpsManager.renameNode(project, node, "documents")

        assertTrue(result.isSuccess)
        val renamedNode = result.getOrThrow() as VfsNode.Directory
        assertEquals("documents", renamedNode.name)
        assertEquals("documents", renamedNode.relativePath)

        // Verify disk contents
        assertTrue(File(sandboxDir, "TestProjectDir/documents").exists())
        assertTrue(File(sandboxDir, "TestProjectDir/documents/doc1.md").exists())
        assertTrue(File(sandboxDir, "TestProjectDir/documents/doc2.md").exists())
        assertFalse(File(sandboxDir, "TestProjectDir/docs").exists())

        // Verify DB updates
        assertNull(database.fileDao().getFileByPath(project.id, "docs"))
        assertNull(database.fileDao().getFileByPath(project.id, "docs/doc1.md"))
        
        assertNotNull(database.fileDao().getFileByPath(project.id, "documents"))
        assertNotNull(database.fileDao().getFileByPath(project.id, "documents/doc1.md"))
        assertNotNull(database.fileDao().getFileByPath(project.id, "documents/doc2.md"))
    }

    @Test
    fun testDeleteDirectoryRecursively() = runTest {
        storageManager.createDirectory(project, "docs")
        storageManager.writeFile(project, "docs/doc1.md", "Content 1")

        database.fileDao().insertFile(FileEntity(projectId = project.id, name = "docs", relativePath = "docs", isDirectory = true, lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
        database.fileDao().insertFile(FileEntity(projectId = project.id, name = "doc1.md", relativePath = "docs/doc1.md", isDirectory = false, lastModified = System.currentTimeMillis(), syncState = "SYNCED"))

        val node = VfsNode.Directory("docs", "docs")
        val result = fileOpsManager.deleteNode(project, node)

        assertTrue(result.isSuccess)

        // Verify disk
        assertFalse(File(sandboxDir, "TestProjectDir/docs").exists())

        // Verify DB
        assertNull(database.fileDao().getFileByPath(project.id, "docs"))
        assertNull(database.fileDao().getFileByPath(project.id, "docs/doc1.md"))
    }

    @Test
    fun testShareNodeFile() = runTest {
        val fileName = "share_test.md"
        val relativePath = "share_test.md"
        storageManager.writeFile(project, relativePath, "# Shared Content\nHello World")

        val node = VfsNode.File(fileName, relativePath, 28L, System.currentTimeMillis())
        val result = fileOpsManager.shareNode(project, node)

        assertTrue(result.isSuccess)
        val uri = result.getOrThrow()
        assertNotNull(uri)
        assertTrue(uri.toString().contains("com.attachdesign.kern.fileprovider"))

        // Check shared file in exports cache directory
        val exportFile = File(context.cacheDir, "shared_exports/$fileName")
        assertTrue(exportFile.exists())
        assertEquals("# Shared Content\nHello World", exportFile.readText())
    }
}
