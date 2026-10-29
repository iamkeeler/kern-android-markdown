package com.attachdesign.kern.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NamedStorageOperationsTest {
    private lateinit var storage: StorageManager
    private lateinit var provider: NamingTestDocumentsProvider
    private val local = ProjectEntity(1, "Local", "NamedStorageOperationsTest", false, true)
    private val linked = ProjectEntity(2, "Linked", NamingTestDocumentsProvider.TREE_URI, true, false)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = StorageManager(context)
        provider = NamingTestDocumentsProvider.register(context)
    }

    @After
    fun cleanup() {
        storage.getAbsoluteFile(local, "").deleteRecursively()
    }

    @Test
    fun filesystemRenameRejectsCollisionAndPreservesBothExactContents() = runTest {
        storage.writeFile(local, "first.md", "First\n  exact text\n")
        storage.writeFile(local, "second.md", "Second\r\ntext\t")
        val outcome = storage.renameNamedNode(local, "first.md", "second.md")
        assertEquals(MutationFailure.Collision, (outcome as MutationOutcome.Failed).reason)
        assertEquals("First\n  exact text\n", storage.readFile(local, "first.md"))
        assertEquals("Second\r\ntext\t", storage.readFile(local, "second.md"))
    }

    @Test
    fun filesystemFolderRenameKeepsNestedBinaryBytesAndSameParent() = runTest {
        storage.createDirectory(local, "parent/assets/nested")
        val bytes = byteArrayOf(0, 0xff.toByte(), 0x80.toByte(), 10, 13, 42)
        storage.getAbsoluteFile(local, "parent/assets/nested/image.bin").writeBytes(bytes)
        val outcome = storage.renameNamedNode(local, "parent/assets", "資料  😀") as MutationOutcome.Success
        assertEquals("parent/資料  😀", outcome.actual.node.relativePath)
        assertArrayEquals(bytes, storage.getAbsoluteFile(local, "parent/資料  😀/nested/image.bin").readBytes())
        assertFalse(storage.fileExists(local, "parent/assets"))
    }

    @Test
    fun createDoesNotTreatAnExistingFolderAsNewSuccess() = runTest {
        storage.createDirectory(local, "existing")
        val result = storage.createNamedDirectory(local, "", "existing") as MutationOutcome.Failed
        assertEquals(MutationFailure.Collision, result.reason)
    }

    @Test
    fun missingParentDoesNotCreateIntermediateDirectories() = runTest {
        val result = storage.createNamedFile(local, "missing/child", "new.md") as MutationOutcome.Failed
        assertEquals(MutationFailure.Missing, result.reason)
        assertFalse(storage.fileExists(local, "missing"))
    }

    @Test
    fun providerCreateUsesNormalizedNameAndReturnedDocumentIdentity() = runTest {
        val folder = provider.add("docs", directory = true)
        provider.normalize = { it.replace(" ", "_") }
        val outcome = storage.createNamedFile(linked, "docs", "日本語 note.MD") as MutationOutcome.Success
        assertEquals("日本語_note.MD", outcome.actual.node.name)
        assertEquals("docs/日本語_note.MD", outcome.actual.node.relativePath)
        val id = DocumentsContract.getDocumentId(Uri.parse(outcome.actual.documentUri))
        assertEquals(folder.id, provider.entries.getValue(id).parent)
        assertEquals(1, provider.createCalls)
    }

    @Test
    fun providerRenameKeepsBytesAndUsesChangedUriRatherThanTheRequestedName() = runTest {
        val bytes = byteArrayOf(0, 0xff.toByte(), 0x80.toByte(), 13, 10, 32)
        val source = provider.add("old.markdown", bytes = bytes)
        provider.normalize = { "provider-$it" }
        val outcome = storage.renameNamedNode(linked, source.name, "chosen.txt") as MutationOutcome.Success
        assertEquals("provider-chosen.txt", outcome.actual.node.relativePath)
        val id = DocumentsContract.getDocumentId(Uri.parse(outcome.actual.documentUri))
        assertNotEquals(source.id, id)
        assertFalse(provider.entries.containsKey(source.id))
        assertArrayEquals(bytes, provider.entries.getValue(id).bytes)
        assertEquals(1, provider.renameCalls)
    }

    @Test
    fun providerSameNameIsNoOpAndCollisionNeverCallsRename() = runTest {
        provider.add("original.txt")
        provider.add("other.txt")
        val unchanged = storage.renameNamedNode(linked, "original.txt", "original.txt") as MutationOutcome.Success
        assertFalse(unchanged.changed)
        val collision = storage.renameNamedNode(linked, "original.txt", "other.txt") as MutationOutcome.Failed
        assertEquals(MutationFailure.Collision, collision.reason)
        assertEquals(0, provider.renameCalls)
    }

    @Test
    fun deniedProviderQueryIsRecoverableAndDoesNotAttemptMutation() = runTest {
        provider.queryFailure = SecurityException("Revoked grant")
        val result = storage.createNamedFile(linked, "", "new.md") as MutationOutcome.Failed
        assertEquals(MutationFailure.AccessDenied, result.reason)
        assertEquals(0, provider.createCalls)
        provider.queryFailure = null
        assertTrue(storage.createNamedFile(linked, "", "new.md") is MutationOutcome.Success)
    }

    @Test
    fun deniedProviderMutationDoesNotClaimSuccess() = runTest {
        provider.mutationFailure = SecurityException("Read-only grant")
        val result = storage.createNamedFile(linked, "", "new.md") as MutationOutcome.Failed
        assertEquals(MutationFailure.AccessDenied, result.reason)
        assertEquals(1, provider.entries.size)
    }

    @Test
    fun nullProviderMutationResultRequiresRefreshRatherThanBlindReplay() = runTest {
        provider.returnNullMutation = true
        val result = storage.createNamedFile(linked, "", "new.md") as MutationOutcome.ReconciliationRequired
        assertEquals("new.md", result.attemptedPath)
        assertNull(result.actual)
        assertEquals(1, provider.entries.size)
    }

    @Test
    fun readbackFailureAfterProviderCommitRetainsDiscoverableReturnedUri() = runTest {
        provider.normalize = { "actual-$it" }
        provider.failReadback = true
        val result = storage.createNamedFile(linked, "", "new.md") as MutationOutcome.ReconciliationRequired
        assertNull(result.actual)
        assertNotNull(result.returnedUri)
        val id = DocumentsContract.getDocumentId(Uri.parse(result.returnedUri))
        assertEquals("actual-new.md", provider.entries.getValue(id).name)
        assertEquals(1, provider.createCalls)
    }

    @Test
    fun providerCancellationRemainsCancellation() = runTest {
        provider.queryFailure = CancellationException("Canceled provider query")
        try {
            storage.createNamedFile(linked, "", "new.md")
            fail("Cancellation was converted to a mutation result")
        } catch (_: CancellationException) { }
        assertEquals(0, provider.createCalls)
    }

    @Test
    fun canceledRequestDoesNotMutateStorage() = runTest {
        val canceled = Job().apply { cancel() }
        try {
            withContext(canceled) { storage.createNamedFile(linked, "", "new.md") }
            fail("Canceled operation returned normally")
        } catch (_: CancellationException) { }
        assertEquals(0, provider.createCalls)
    }
}
