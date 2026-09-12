package com.attachdesign.kern.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import io.mockk.coEvery
import io.mockk.mockk
import com.attachdesign.kern.test.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FileOperationsManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var fileOpsManager: FileOperationsManager
    private lateinit var sandboxDir: File
    private lateinit var project: ProjectEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        storageManager = StorageManager(context)
        fileOpsManager = FileOperationsManager(database, storageManager, context)
        project = ProjectEntity(id = 1, name = "TestProject", path = "TestProjectDir", isExternal = false, isSelected = true)
        // L7: getAbsoluteFile is suspend with internal IO dispatch.
        sandboxDir = runBlocking { storageManager.getAbsoluteFile(project, "").parentFile!! }
        
        database.projectDao().insertProject(project)
    }

    @After
    fun tearDown() {
        database.close()
        if (sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        }
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
    fun testDeleteNodeReportsFailureAndKeepsDbRowWhenDiskDeleteFails() = runTest {
        // M11: a failed disk delete must surface as Result.failure and must not prune the DB.
        val failingStorage = mockk<StorageManager>()
        coEvery { failingStorage.deleteFile(project, "note.md") } returns false
        val ops = FileOperationsManager(database, failingStorage, context)

        database.fileDao().insertFile(
            FileEntity(
                projectId = project.id,
                name = "note.md",
                relativePath = "note.md",
                isDirectory = false,
                lastModified = System.currentTimeMillis(),
                syncState = "SYNCED"
            )
        )

        val result = ops.deleteNode(project, VfsNode.File("note.md", "note.md", 8L, System.currentTimeMillis()))

        assertTrue(result.isFailure)
        assertNotNull(database.fileDao().getFileByPath(project.id, "note.md"))
    }

    @Test
    fun testMoveToTrashAndRestoreRoundTrip() = runTest {
        // H4: trash/restore must actually move the bytes on disk and keep the DB in sync.
        storageManager.writeFile(project, "note.md", "trash me")
        database.fileDao().insertFile(
            FileEntity(
                projectId = project.id,
                name = "note.md",
                relativePath = "note.md",
                isDirectory = false,
                lastModified = System.currentTimeMillis(),
                syncState = "SYNCED"
            )
        )

        val node = VfsNode.File("note.md", "note.md", 8L, System.currentTimeMillis())
        val trashResult = fileOpsManager.moveToTrash(project, node)

        assertTrue(trashResult.isSuccess)
        val trashNode = trashResult.getOrThrow()
        assertTrue(trashNode.relativePath.startsWith(".trash/"))
        assertFalse(File(sandboxDir, "TestProjectDir/note.md").exists())
        assertTrue(File(sandboxDir, "TestProjectDir/${trashNode.relativePath}").exists())
        assertEquals("trash me", storageManager.readFile(project, trashNode.relativePath))
        assertNotNull(database.fileDao().getFileByPath(project.id, trashNode.relativePath))
        assertNull(database.fileDao().getFileByPath(project.id, "note.md"))

        val restoreResult = fileOpsManager.restoreFromTrash(project, trashNode, "note.md")

        assertTrue(restoreResult.isSuccess)
        assertTrue(File(sandboxDir, "TestProjectDir/note.md").exists())
        assertFalse(File(sandboxDir, "TestProjectDir/${trashNode.relativePath}").exists())
        assertEquals("trash me", storageManager.readFile(project, "note.md"))
        assertNotNull(database.fileDao().getFileByPath(project.id, "note.md"))
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
