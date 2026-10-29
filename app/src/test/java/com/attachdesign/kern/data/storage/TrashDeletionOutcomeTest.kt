package com.attachdesign.kern.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashDeletionOutcomeTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var storage: StorageManager
    private lateinit var provider: NamingTestDocumentsProvider
    private val local = ProjectEntity(1, "Local", "TrashDeletionOutcomeTest", false, true)
    private val linked = ProjectEntity(2, "Linked", NamingTestDocumentsProvider.TREE_URI, true, false)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.projectDao().insertProject(local)
        db.projectDao().insertProject(linked)
        storage = StorageManager(context)
        provider = NamingTestDocumentsProvider.register(context)
    }

    @After
    fun cleanup() {
        db.close()
        storage.getAbsoluteFile(local, "").deleteRecursively()
    }

    private fun index(project: ProjectEntity, path: String, directory: Boolean = false): VfsNode {
        db.fileDao().insertFile(FileEntity(
            projectId = project.id, name = path.substringAfterLast('/'), relativePath = path,
            isDirectory = directory, lastModified = 123, syncState = "SYNCED", wordCount = 12, characterCount = 27
        ))
        return if (directory) VfsNode.Directory(path.substringAfterLast('/'), path)
        else VfsNode.File(path.substringAfterLast('/'), path, 0, 123)
    }

    private fun manager(
        ids: (() -> String)? = null,
        failInsert: () -> Boolean = { false },
        failDelete: () -> Boolean = { false }
    ): FileOperationsManager {
        val delegate = db.fileDao()
        val dao = mockk<FileDao>()
        every { dao.getFilesForProject(any()) } answers { delegate.getFilesForProject(firstArg()) }
        every { dao.getFileByPath(any(), any()) } answers { delegate.getFileByPath(firstArg(), secondArg()) }
        every { dao.deleteFile(any(), any()) } answers {
            if (failDelete()) throw IllegalStateException("Injected metadata delete failure")
            delegate.deleteFile(firstArg(), secondArg())
        }
        every { dao.insertFile(any()) } answers {
            if (failInsert()) throw IllegalStateException("Injected metadata insert failure")
            delegate.insertFile(firstArg())
        }
        val wrapped = mockk<AppDatabase>()
        every { wrapped.projectDao() } returns db.projectDao()
        every { wrapped.fileDao() } returns dao
        every { wrapped.runInTransaction(any<Runnable>()) } answers { db.runInTransaction(firstArg<Runnable>()) }
        return if (ids == null) FileOperationsManager(wrapped, storage, context)
        else FileOperationsManager(wrapped, storage, context, ids)
    }

    @Test
    fun generatedTrashCollisionChoosesAnotherIdAndNeverOverwritesExistingData() = runTest {
        storage.writeFile(local, "note.md", "source\r\n  ")
        storage.createDirectory(local, ".trash")
        storage.writeFile(local, ".trash/collision_note.md", "retained trash\t")
        val source = index(local, "note.md")
        val ids = ArrayDeque(listOf("collision", "available"))

        val result = manager(ids = { ids.removeFirst() }).moveToTrashOutcome(local, source) as MutationOutcome.Success

        assertEquals(".trash/available_note.md", result.actual.node.relativePath)
        assertEquals("source\r\n  ", storage.readFile(local, result.actual.node.relativePath))
        assertEquals("retained trash\t", storage.readFile(local, ".trash/collision_note.md"))
        assertEquals("SYNCED", db.fileDao().getFileByPath(local.id, result.actual.node.relativePath)?.syncState)
        assertNull(db.fileDao().getFileByPath(local.id, "note.md"))
    }

    @Test
    fun sameTimestampSourcesReceiveDistinctUuidTrashNames() = runTest {
        provider.add("first", directory = true).also { provider.add("note.md", parent = it.id) }
        provider.add("second", directory = true).also { provider.add("note.md", parent = it.id) }
        val first = index(linked, "first/note.md")
        val second = index(linked, "second/note.md")
        val manager = FileOperationsManager(db, storage, context)
        val a = manager.moveToTrashOutcome(linked, first) as MutationOutcome.Success
        val b = manager.moveToTrashOutcome(linked, second) as MutationOutcome.Success

        assertNotEquals(a.actual.node.relativePath, b.actual.node.relativePath)
        assertTrue(a.actual.node.name.matches(Regex("[0-9a-f-]{36}_note\\.md")))
        assertTrue(b.actual.node.name.matches(Regex("[0-9a-f-]{36}_note\\.md")))
        assertNotNull(db.fileDao().getFileByPath(linked.id, a.actual.node.relativePath))
        assertNotNull(db.fileDao().getFileByPath(linked.id, b.actual.node.relativePath))
    }

    @Test
    fun nestedFolderTrashAndRestorePreserveBinaryBytesDescendantsAndMetadataIds() = runTest {
        storage.createDirectory(local, "assets/nested")
        val bytes = byteArrayOf(0, 0xff.toByte(), 0x80.toByte(), 13, 10)
        storage.getAbsoluteFile(local, "assets/nested/image.bin").writeBytes(bytes)
        val folder = index(local, "assets", directory = true)
        index(local, "assets/nested", directory = true)
        index(local, "assets/nested/image.bin")
        val before = db.fileDao().getFilesForProject(local.id).associateBy { it.relativePath }
        val manager = FileOperationsManager(db, storage, context)

        val trashed = manager.moveToTrashOutcome(local, folder) as MutationOutcome.Success
        assertArrayEquals(bytes, storage.getAbsoluteFile(local, "${trashed.actual.node.relativePath}/nested/image.bin").readBytes())
        val restored = manager.restoreFromTrashOutcome(local, trashed.actual.node, "assets") as MutationOutcome.Success

        assertEquals("assets", restored.actual.node.relativePath)
        assertArrayEquals(bytes, storage.getAbsoluteFile(local, "assets/nested/image.bin").readBytes())
        val after = db.fileDao().getFilesForProject(local.id).associateBy { it.relativePath }
        assertEquals(before.keys, after.keys)
        before.forEach { (path, row) ->
            assertEquals(row.id, after[path]?.id)
            assertEquals(row.wordCount, after[path]?.wordCount)
            assertEquals(row.syncState, after[path]?.syncState)
        }
    }

    @Test
    fun restoreCollisionKeepsTrashRecoverableAndExistingDestinationExact() = runTest {
        storage.writeFile(local, "note.md", "deleted original")
        val source = index(local, "note.md")
        val manager = FileOperationsManager(db, storage, context)
        val trash = (manager.moveToTrashOutcome(local, source) as MutationOutcome.Success).actual.node
        storage.writeFile(local, "note.md", "new destination\r\n  ")
        index(local, "note.md")
        val before = db.fileDao().getFilesForProject(local.id)

        val result = manager.restoreFromTrash(linked.copy(id = local.id, path = local.path, isExternal = false), trash, "note.md")

        assertTrue(result.isFailure)
        assertEquals(MutationFailure.Collision, ((result.exceptionOrNull() as MutationOutcomeException).outcome as MutationOutcome.Failed).reason)
        assertEquals("new destination\r\n  ", storage.readFile(local, "note.md"))
        assertEquals("deleted original", storage.readFile(local, trash.relativePath))
        assertEquals(before, db.fileDao().getFilesForProject(local.id))
    }

    @Test
    fun providerNormalizedTrashAndRestoreUseActualParentUriAndNames() = runTest {
        val docs = provider.add("docs", directory = true)
        val bytes = byteArrayOf(0, 0xff.toByte(), 0x80.toByte())
        provider.add("original.md", parent = docs.id, bytes = bytes)
        val source = index(linked, "docs/original.md")
        provider.normalize = { if (it == ".trash") it else "stored-$it" }
        val manager = manager(ids = { "receipt" })

        val trash = manager.moveToTrashOutcome(linked, source) as MutationOutcome.Success

        assertEquals(".trash/stored-receipt_original.md", trash.actual.node.relativePath)
        val trashId = DocumentsContract.getDocumentId(Uri.parse(trash.actual.documentUri))
        assertEquals(".trash", provider.entries.getValue(provider.entries.getValue(trashId).parent!!).name)
        assertEquals("stored-receipt_original.md", db.fileDao().getFileByPath(linked.id, trash.actual.node.relativePath)?.name)
        val restored = manager.restoreFromTrashOutcome(linked, trash.actual.node, "docs/original.md") as MutationOutcome.Success
        assertEquals("docs/stored-original.md", restored.actual.node.relativePath)
        val restoredId = DocumentsContract.getDocumentId(Uri.parse(restored.actual.documentUri))
        assertEquals(docs.id, provider.entries.getValue(restoredId).parent)
        assertArrayEquals(bytes, provider.entries.getValue(restoredId).bytes)
        assertNotNull(db.fileDao().getFileByPath(linked.id, "docs/stored-original.md"))
        assertNull(db.fileDao().getFileByPath(linked.id, trash.actual.node.relativePath))
    }

    @Test
    fun partialTrashMoveReconcilesActualLocationWithoutRepeatingNativeMoveOrRename() = runTest {
        val bytes = byteArrayOf(0, 0xff.toByte(), 13, 10)
        provider.add("original.md", bytes = bytes)
        provider.add(".trash", directory = true)
        val source = index(linked, "original.md")
        val before = db.fileDao().getFilesForProject(linked.id)
        provider.mutationFailure = IOException("Rename failed after native move")
        val manager = manager(ids = { "receipt" })

        val partial = manager.moveToTrashOutcome(linked, source) as MutationOutcome.ReconciliationRequired

        assertEquals(".trash/original.md", partial.actual?.node?.relativePath)
        assertEquals(before, db.fileDao().getFilesForProject(linked.id))
        assertNotNull(partial.metadata)
        assertTrue(manager.reconcileMetadata(linked, partial) is MutationOutcome.Success)
        assertEquals(1, provider.moveCalls)
        assertEquals(1, provider.renameCalls)
        assertEquals("SYNCED", db.fileDao().getFileByPath(linked.id, ".trash/original.md")?.syncState)
        assertArrayEquals(bytes, provider.entries.values.single { !it.directory }.bytes)
    }

    @Test
    fun diskTrashSuccessAndMetadataFailureRetainExactDataAndRetryOnlyRoom() = runTest {
        provider.add("original.md", bytes = byteArrayOf(0, 0xff.toByte()))
        val source = index(linked, "original.md")
        val before = db.fileDao().getFilesForProject(linked.id)
        var fail = true
        val manager = manager(ids = { "receipt" }, failInsert = { fail })

        val partial = manager.moveToTrashOutcome(linked, source) as MutationOutcome.ReconciliationRequired

        assertEquals(before, db.fileDao().getFilesForProject(linked.id))
        assertNotNull(partial.actual)
        fail = false
        assertTrue(manager.reconcileMetadata(linked, partial) is MutationOutcome.Success)
        assertEquals(1, provider.moveCalls)
        assertEquals(1, provider.renameCalls)
        assertEquals("SYNCED", db.fileDao().getFileByPath(linked.id, partial.actual!!.node.relativePath)?.syncState)
    }

    @Test
    fun unsupportedTrashMoveKeepsSourceAndMetadataAndOffersNoSuccessResult() = runTest {
        val sourceEntry = provider.add("original.md", bytes = byteArrayOf(42))
        val source = index(linked, "original.md")
        val before = db.fileDao().getFilesForProject(linked.id)
        provider.supportsNativeMove = false

        val result = FileOperationsManager(db, storage, context).moveToTrash(linked, source)

        assertTrue(result.isFailure)
        assertEquals(MutationFailure.Unsupported, ((result.exceptionOrNull() as MutationOutcomeException).outcome as MutationOutcome.Failed).reason)
        assertEquals(NamingTestDocumentsProvider.ROOT, provider.entries.getValue(sourceEntry.id).parent)
        assertEquals(before, db.fileDao().getFilesForProject(linked.id))
        assertEquals(0, provider.moveCalls)
    }

    @Test
    fun falseNativeDeleteKeepsMetadataAndReturnsTypedFailure() = runTest {
        provider.add("original.md", bytes = byteArrayOf(42))
        val source = index(linked, "original.md")
        val before = db.fileDao().getFilesForProject(linked.id)
        provider.deleteFailure = IOException("Native provider could not delete")

        val result = FileOperationsManager(db, storage, context).deleteNode(linked, source)

        assertTrue(result.isFailure)
        val partial = (result.exceptionOrNull() as DeleteOutcomeException).outcome as DeleteOutcome.ReconciliationRequired
        assertTrue(partial.cause.message!!.contains("returned false"))
        assertNotNull(partial.remaining)
        assertFalse(partial.absenceConfirmed)
        assertEquals(before, db.fileDao().getFilesForProject(linked.id))
        assertEquals(1, provider.deleteCalls)
    }

    @Test
    fun deleteThatClaimsSuccessButLeavesSourceDoesNotPruneMetadata() = runTest {
        provider.add("original.md")
        val source = index(linked, "original.md")
        provider.deleteNoOp = true
        val result = FileOperationsManager(db, storage, context).deleteNodeOutcome(linked, source)
        assertTrue(result is DeleteOutcome.ReconciliationRequired)
        assertNotNull(db.fileDao().getFileByPath(linked.id, "original.md"))
    }

    @Test
    fun alreadyAbsentDeletionIsExplicitIdempotencyAndDoesNotCallProviderDelete() = runTest {
        val source = index(linked, "absent.md")
        val outcome = FileOperationsManager(db, storage, context).deleteNodeOutcome(linked, source) as DeleteOutcome.Success
        assertTrue(outcome.alreadyAbsent)
        assertNull(outcome.deletedIdentity)
        assertNull(db.fileDao().getFileByPath(linked.id, "absent.md"))
        assertEquals(0, provider.deleteCalls)
    }

    @Test
    fun deniedInspectionIsNotAlreadyAbsentAndKeepsMetadata() = runTest {
        provider.add("original.md")
        val source = index(linked, "original.md")
        provider.queryFailure = SecurityException("Revoked grant")
        val outcome = FileOperationsManager(db, storage, context).deleteNodeOutcome(linked, source) as DeleteOutcome.Failed
        assertEquals(MutationFailure.AccessDenied, outcome.reason)
        assertNotNull(db.fileDao().getFileByPath(linked.id, "original.md"))
        assertEquals(0, provider.deleteCalls)
    }

    @Test
    fun diskDeletionAndMetadataFailureReconcileAbsenceWithoutDeletingAgain() = runTest {
        provider.add("original.md")
        val source = index(linked, "original.md")
        var fail = true
        val manager = manager(failDelete = { fail })

        val partial = manager.deleteNodeOutcome(linked, source) as DeleteOutcome.ReconciliationRequired

        assertTrue(partial.absenceConfirmed)
        assertNotNull(partial.metadata)
        assertNull(partial.remaining)
        assertNotNull(db.fileDao().getFileByPath(linked.id, "original.md"))
        fail = false
        assertTrue(manager.reconcileDeleteMetadata(linked, partial) is DeleteOutcome.Success)
        assertNull(db.fileDao().getFileByPath(linked.id, "original.md"))
        assertEquals(1, provider.deleteCalls)
    }

    @Test
    fun reappearingFilePreventsDeletionMetadataRetryFromPruningIt() = runTest {
        provider.add("original.md")
        val source = index(linked, "original.md")
        var fail = true
        val manager = manager(failDelete = { fail })
        val partial = manager.deleteNodeOutcome(linked, source) as DeleteOutcome.ReconciliationRequired
        provider.add("original.md", bytes = byteArrayOf(7))
        fail = false

        val retried = manager.reconcileDeleteMetadata(linked, partial) as DeleteOutcome.ReconciliationRequired

        assertFalse(retried.absenceConfirmed)
        assertNotNull(retried.remaining)
        assertNotNull(db.fileDao().getFileByPath(linked.id, "original.md"))
        assertEquals(1, provider.deleteCalls)
        assertArrayEquals(byteArrayOf(7), provider.entries.values.single { !it.directory }.bytes)
    }

    @Test
    fun nativeDeleteCancellationPropagatesAndPreservesMetadata() = runTest {
        provider.add("original.md")
        val source = index(linked, "original.md")
        provider.deleteFailure = CancellationException("Native deletion canceled")
        try {
            FileOperationsManager(db, storage, context).deleteNodeOutcome(linked, source)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertNotNull(db.fileDao().getFileByPath(linked.id, "original.md"))
        assertEquals(1, provider.deleteCalls)
    }
}
