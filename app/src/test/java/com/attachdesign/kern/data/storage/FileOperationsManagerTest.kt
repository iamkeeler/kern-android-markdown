package com.attachdesign.kern.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.test.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import io.mockk.every
import io.mockk.mockk

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
        sandboxDir = storageManager.getAbsoluteFile(project, "").parentFile!!
        
        database.projectDao().insertProject(project)
    }

    @After
    fun tearDown() {
        database.close()
        if (sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        }
        try {
            val sCacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            sCacheField.isAccessible = true
            (sCacheField.get(null) as? MutableMap<*, *>)?.clear()
        } catch (_: Exception) {
            // Ignored if field is unavailable
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
    fun renameNodeRejectsPathEntryWithoutChangingSourceOrMetadata() = runTest {
        storageManager.writeFile(project, "original.md", "Exact\r\nsource  \n")
        database.fileDao().insertFile(FileEntity(
            projectId = project.id, name = "original.md", relativePath = "original.md",
            isDirectory = false, lastModified = 123, syncState = "SYNCED"
        ))
        val result = fileOpsManager.renameNode(project, VfsNode.File("original.md", "original.md", 0, 123), "folder/moved.md")

        assertTrue(result.isFailure)
        assertEquals(MutationFailure.InvalidName, ((result.exceptionOrNull() as MutationOutcomeException).outcome as MutationOutcome.Failed).reason)
        assertEquals("Exact\r\nsource  \n", storageManager.readFile(project, "original.md"))
        assertNotNull(database.fileDao().getFileByPath(project.id, "original.md"))
        assertFalse(storageManager.fileExists(project, "folder/moved.md"))
    }

    @Test
    fun renameNodeSameNameIsNoOpPreservingMetadataAndContents() = runTest {
        storageManager.writeFile(project, "notes.markdown", "\n# source  \n")
        val id = database.fileDao().insertFile(FileEntity(
            projectId = project.id, name = "notes.markdown", relativePath = "notes.markdown",
            isDirectory = false, lastModified = 123, syncState = "SYNCED", wordCount = 22
        ))
        val before = database.fileDao().getFileByPath(project.id, "notes.markdown")
        val result = fileOpsManager.renameNode(project, VfsNode.File("notes.markdown", "notes.markdown", 0, 123), "notes.markdown")

        assertTrue(result.isSuccess)
        assertEquals(id, database.fileDao().getFileByPath(project.id, "notes.markdown")?.id)
        assertEquals(before, database.fileDao().getFileByPath(project.id, "notes.markdown"))
        assertEquals("\n# source  \n", storageManager.readFile(project, "notes.markdown"))
    }

    @Test
    fun renameNodeCollisionDoesNotOverwriteEitherFile() = runTest {
        storageManager.writeFile(project, "old.txt", "Original\n")
        storageManager.writeFile(project, "taken.txt", "Destination\t")
        val result = fileOpsManager.renameNode(project, VfsNode.File("old.txt", "old.txt", 0, 123), "taken.txt")

        assertTrue(result.isFailure)
        assertEquals("Original\n", storageManager.readFile(project, "old.txt"))
        assertEquals("Destination\t", storageManager.readFile(project, "taken.txt"))
    }

    @Test
    fun creationUsesCapturedParentAndCaseInsensitiveMarkdownDefault() = runTest {
        storageManager.createDirectory(project, "existing/subfolder")
        val outcome = fileOpsManager.createFile(project, "existing/subfolder", " 日本語  Notes.MD ") as MutationOutcome.Success

        assertEquals("existing/subfolder/日本語  Notes.MD", outcome.actual.node.relativePath)
        assertNotNull(database.fileDao().getFileByPath(project.id, outcome.actual.node.relativePath))
        assertFalse(storageManager.fileExists(project, "日本語  Notes.MD"))
        assertFalse(storageManager.fileExists(project, "existing/subfolder/日本語  Notes.MD.md"))
    }

    @Test
    fun createFolderCollisionKeepsExistingMetadata() = runTest {
        storageManager.createDirectory(project, "existing")
        database.fileDao().insertFile(FileEntity(
            projectId = project.id, name = "existing", relativePath = "existing",
            isDirectory = true, lastModified = 123, syncState = "SYNCED"
        ))
        val before = database.fileDao().getFileByPath(project.id, "existing")

        val result = fileOpsManager.createFolder(project, "", "existing") as MutationOutcome.Failed

        assertEquals(MutationFailure.Collision, result.reason)
        assertEquals(before, database.fileDao().getFileByPath(project.id, "existing"))
    }

    @Test
    fun providerNormalizedCreationIndexesActualIdentity() = runTest {
        val provider = NamingTestDocumentsProvider.register(context)
        val linked = project.copy(id = 2, name = "Linked", path = NamingTestDocumentsProvider.TREE_URI, isExternal = true)
        database.projectDao().insertProject(linked)
        provider.normalize = { it.replace(" ", "_") }

        val result = fileOpsManager.createFile(linked, "", "new note.MD") as MutationOutcome.Success

        assertEquals("new_note.MD", result.actual.node.relativePath)
        assertNotNull(result.actual.documentUri)
        assertNull(database.fileDao().getFileByPath(linked.id, "new note.MD"))
        assertEquals("SYNCED", database.fileDao().getFileByPath(linked.id, "new_note.MD")?.syncState)
    }

    @Test
    fun falseProviderCreationDoesNotInsertPhantomFileOrFolderMetadata() = runTest {
        val provider = NamingTestDocumentsProvider.register(context)
        val linked = project.copy(id = 2, path = NamingTestDocumentsProvider.TREE_URI, isExternal = true)
        database.projectDao().insertProject(linked)
        provider.returnNullMutation = true

        assertTrue(fileOpsManager.createFile(linked, "", "new") is MutationOutcome.ReconciliationRequired)
        assertTrue(fileOpsManager.createFolder(linked, "", "folder") is MutationOutcome.ReconciliationRequired)
        assertTrue(database.fileDao().getFilesForProject(linked.id).isEmpty())
        assertEquals(1, provider.entries.size)
    }

    @Test
    fun metadataFailureAfterRenameRollsBackRowsAndReconcilesWithoutRepeatingDiskMutation() = runTest {
        val provider = NamingTestDocumentsProvider.register(context)
        val linked = project.copy(id = 2, path = NamingTestDocumentsProvider.TREE_URI, isExternal = true)
        database.projectDao().insertProject(linked)
        val bytes = "Exact\r\nsource  \n".toByteArray()
        provider.add("original.markdown", bytes = bytes)
        database.fileDao().insertFile(FileEntity(
            projectId = linked.id, name = "original.markdown", relativePath = "original.markdown",
            isDirectory = false, lastModified = 123, syncState = "SYNCED", wordCount = 12, characterCount = 27
        ))
        val before = database.fileDao().getFileByPath(linked.id, "original.markdown")
        var failInsert = true
        val manager = managerWithFailingInsert { failInsert }
        provider.normalize = { "actual-$it" }

        val result = manager.renameNodeOutcome(linked,
            VfsNode.File("original.markdown", "original.markdown", bytes.size.toLong(), 123), "chosen.txt"
        ) as MutationOutcome.ReconciliationRequired

        assertEquals("actual-chosen.txt", result.actual?.node?.relativePath)
        assertNotNull(result.returnedUri)
        assertNotNull(result.metadata)
        assertEquals(before, database.fileDao().getFileByPath(linked.id, "original.markdown"))
        assertNull(database.fileDao().getFileByPath(linked.id, "actual-chosen.txt"))
        assertEquals(1, provider.renameCalls)
        assertArrayEquals(bytes, provider.entries.values.single { !it.directory }.bytes)

        failInsert = false
        val reconciled = manager.reconcileMetadata(linked, result) as MutationOutcome.Success

        assertEquals("actual-chosen.txt", reconciled.actual.node.name)
        assertEquals(1, provider.renameCalls)
        assertNull(database.fileDao().getFileByPath(linked.id, "original.markdown"))
        val after = database.fileDao().getFileByPath(linked.id, "actual-chosen.txt")
        assertEquals(before?.id, after?.id)
        assertEquals(12, after?.wordCount)
        assertEquals(27, after?.characterCount)
    }

    @Test
    fun metadataFailureAfterCreateKeepsActualFileAndRetryDoesNotCreateAgain() = runTest {
        val provider = NamingTestDocumentsProvider.register(context)
        val linked = project.copy(id = 2, path = NamingTestDocumentsProvider.TREE_URI, isExternal = true)
        database.projectDao().insertProject(linked)
        var failInsert = true
        val manager = managerWithFailingInsert { failInsert }

        val result = manager.createFile(linked, "", "new.MD") as MutationOutcome.ReconciliationRequired

        assertTrue(database.fileDao().getFilesForProject(linked.id).isEmpty())
        assertEquals("new.MD", result.actual?.node?.name)
        assertEquals(1, provider.createCalls)
        failInsert = false
        assertTrue(manager.reconcileMetadata(linked, result) is MutationOutcome.Success)
        assertEquals(1, provider.createCalls)
        assertNotNull(database.fileDao().getFileByPath(linked.id, "new.MD"))
    }

    @Test
    fun providerCreateReadbackFailureRetainsReceiptAndRecoversActualNameWithoutCreatingAgain() = runTest {
        val provider = NamingTestDocumentsProvider.register(context)
        val linked = project.copy(id = 2, path = NamingTestDocumentsProvider.TREE_URI, isExternal = true)
        database.projectDao().insertProject(linked)
        val folder = provider.add("docs", directory = true)
        provider.normalize = { it.replace(" ", "_") }
        provider.failReadback = true

        val partial = fileOpsManager.createFile(linked, "docs", "new note.MD") as MutationOutcome.ReconciliationRequired

        assertNull(partial.actual)
        assertNotNull(partial.returnedUri)
        assertNotNull(partial.metadata)
        assertEquals("docs/new note.MD", partial.attemptedPath)
        assertTrue(database.fileDao().getFilesForProject(linked.id).isEmpty())
        assertTrue(fileOpsManager.reconcileMetadata(linked, partial) is MutationOutcome.ReconciliationRequired)
        assertTrue(database.fileDao().getFilesForProject(linked.id).isEmpty())
        assertEquals(1, provider.createCalls)

        provider.failReadback = false
        val recovered = fileOpsManager.reconcileMetadata(linked, partial) as MutationOutcome.Success

        assertEquals("docs/new_note.MD", recovered.actual.node.relativePath)
        assertEquals(partial.returnedUri, recovered.actual.documentUri)
        val created = provider.entries.values.single { !it.directory }
        assertEquals(folder.id, created.parent)
        assertEquals("new_note.MD", database.fileDao().getFileByPath(linked.id, "docs/new_note.MD")?.name)
        assertNull(database.fileDao().getFileByPath(linked.id, "docs/new note.MD"))
        assertEquals(1, provider.createCalls)
        assertEquals(0, provider.renameCalls)
    }

    @Test
    fun providerRenameReadbackFailureRetainsDescendantMetadataAndRecoversWithoutRenamingAgain() = runTest {
        val provider = NamingTestDocumentsProvider.register(context)
        val linked = project.copy(id = 2, path = NamingTestDocumentsProvider.TREE_URI, isExternal = true)
        database.projectDao().insertProject(linked)
        val folder = provider.add("notes", directory = true)
        val bytes = byteArrayOf(0, 0xff.toByte(), 0x80.toByte(), 13, 10, 32)
        val child = provider.add("draft.txt", parent = folder.id, bytes = bytes)
        database.fileDao().insertFile(FileEntity(
            projectId = linked.id, name = "notes", relativePath = "notes",
            isDirectory = true, lastModified = 123, syncState = "SYNCED"
        ))
        database.fileDao().insertFile(FileEntity(
            projectId = linked.id, name = "draft.txt", relativePath = "notes/draft.txt",
            isDirectory = false, lastModified = 123, syncState = "SYNCED", wordCount = 12, characterCount = 27
        ))
        val before = database.fileDao().getFilesForProject(linked.id).sortedBy { it.id }
        provider.normalize = { "actual-$it" }
        provider.failReadback = true

        val partial = fileOpsManager.renameNodeOutcome(linked, VfsNode.Directory("notes", "notes"), "chosen")
            as MutationOutcome.ReconciliationRequired

        assertNull(partial.actual)
        assertNotNull(partial.returnedUri)
        assertNotNull(partial.metadata)
        assertEquals(before, database.fileDao().getFilesForProject(linked.id).sortedBy { it.id })
        assertEquals(1, provider.renameCalls)

        provider.failReadback = false
        val recovered = fileOpsManager.reconcileMetadata(linked, partial) as MutationOutcome.Success

        assertEquals("actual-chosen", recovered.actual.node.relativePath)
        assertEquals(partial.returnedUri, recovered.actual.documentUri)
        assertNull(database.fileDao().getFileByPath(linked.id, "notes"))
        assertNull(database.fileDao().getFileByPath(linked.id, "notes/draft.txt"))
        val after = database.fileDao().getFileByPath(linked.id, "actual-chosen/draft.txt")
        assertEquals(before.single { !it.isDirectory }.id, after?.id)
        assertEquals(12, after?.wordCount)
        assertEquals(27, after?.characterCount)
        assertEquals("draft.txt", after?.name)
        assertArrayEquals(bytes, provider.entries.getValue(child.id).bytes)
        assertEquals(1, provider.renameCalls)
        assertEquals(0, provider.createCalls)
    }

    @Test
    fun unavailableActualIdentityKeepsReconciliationReceiptAndOriginalMetadata() = runTest {
        storageManager.writeFile(project, "original.txt", "source")
        database.fileDao().insertFile(FileEntity(
            projectId = project.id, name = "original.txt", relativePath = "original.txt",
            isDirectory = false, lastModified = 123, syncState = "SYNCED"
        ))
        var failInsert = true
        val manager = managerWithFailingInsert { failInsert }
        val partial = manager.renameNodeOutcome(project, VfsNode.File("original.txt", "original.txt", 6, 123), "new.txt")
            as MutationOutcome.ReconciliationRequired
        storageManager.deleteFile(project, "new.txt")
        failInsert = false

        assertTrue(manager.reconcileMetadata(project, partial) is MutationOutcome.ReconciliationRequired)
        assertNotNull(database.fileDao().getFileByPath(project.id, "original.txt"))
        assertNull(database.fileDao().getFileByPath(project.id, "new.txt"))
    }

    private fun managerWithFailingInsert(shouldFail: () -> Boolean): FileOperationsManager {
        val delegate = database.fileDao()
        val failingDao = mockk<FileDao>()
        every { failingDao.getFilesForProject(any()) } answers { delegate.getFilesForProject(firstArg()) }
        every { failingDao.deleteFile(any(), any()) } answers { delegate.deleteFile(firstArg(), secondArg()) }
        every { failingDao.insertFile(any()) } answers {
            if (shouldFail()) throw IllegalStateException("Injected metadata failure")
            delegate.insertFile(firstArg())
        }
        val failingDb = mockk<AppDatabase>()
        every { failingDb.projectDao() } returns database.projectDao()
        every { failingDb.fileDao() } returns failingDao
        every { failingDb.runInTransaction(any<Runnable>()) } answers { database.runInTransaction(firstArg<Runnable>()) }
        return FileOperationsManager(failingDb, storageManager, context)
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
        val payload = result.getOrThrow()
        assertNotNull(payload)
        assertEquals("text/markdown", payload.mimeType)
        assertEquals("# Shared Content\nHello World", payload.textContent)
        assertTrue(payload.uri.toString().contains("com.attachdesign.kern.fileprovider"))

        // Check shared file in exports cache directory
        val exportFile = File(context.cacheDir, "shared_exports/$fileName")
        assertTrue(exportFile.exists())
        assertEquals("# Shared Content\nHello World", exportFile.readText())
    }

    @Test
    fun testShareNodeDirectory_preservesBinaryAssets() = runTest {
        storageManager.createDirectory(project, "assets")
        val binaryBytes = byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x80.toByte(), 0x12, 0x34)
        val imageFile = storageManager.getAbsoluteFile(project, "assets/test.png")
        imageFile.writeBytes(binaryBytes)

        val dirNode = VfsNode.Directory("assets", "assets")
        val result = fileOpsManager.shareNode(project, dirNode)

        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals("application/zip", payload.mimeType)

        // Read back the zip entry and verify exact byte equality
        val exportsDir = File(context.cacheDir, "shared_exports")
        val zipFiles = exportsDir.listFiles { _, name -> name.startsWith("assets_") && name.endsWith(".zip") }
        assertNotNull(zipFiles)
        assertTrue(zipFiles!!.isNotEmpty())
        val zipFile = zipFiles.first()

        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            var found = false
            while (entry != null) {
                if (entry.name == "test.png") {
                    val readBytes = zis.readBytes()
                    assertArrayEquals(binaryBytes, readBytes)
                    found = true
                    break
                }
                entry = zis.nextEntry
            }
            assertTrue("Expected to find test.png in zip", found)
        }
    }
}
