package com.attachdesign.kern.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.attachdesign.kern.data.local.ProjectEntity
import java.io.IOException
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
class DirectoryOutcomeTest {
    private lateinit var storage: StorageManager
    private lateinit var provider: NamingTestDocumentsProvider
    private val local = ProjectEntity(1, "Local", "DirectoryOutcomeTest", false, true)
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
    fun filesystemDistinguishesEmptyAndNonemptySuccessFromMissingOrNonDirectory() = runTest {
        storage.createDirectory(local, "empty")
        assertEquals(emptyList<VfsNode>(), (storage.listDirectoryOutcome(local, "empty") as DirectoryOutcome.Success).nodes)
        storage.writeFile(local, "empty/notes.md", "exact\nsource  \n")
        val listed = storage.listDirectoryOutcome(local, "empty") as DirectoryOutcome.Success
        assertEquals(listOf("empty/notes.md"), listed.nodes.map { it.relativePath })
        assertEquals(DirectoryOutcome.Missing, storage.listDirectoryOutcome(local, "missing"))
        assertEquals(DirectoryOutcome.Missing, storage.listDirectoryOutcome(local, "empty/notes.md"))
        assertEquals("exact\nsource  \n", storage.readFile(local, "empty/notes.md"))
    }

    @Test
    fun providerGenuineEmptyAndNonemptyListingsAreCompleteSuccessAndCloseCursors() = runTest {
        assertTrue((storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Success).nodes.isEmpty())
        val folder = provider.add("資料", directory = true)
        provider.add("zeta.txt", bytes = byteArrayOf(1, 2, 3))
        provider.add("alpha.MD")
        provider.add("nested.markdown", parent = folder.id)

        val root = storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Success
        assertEquals(listOf("資料", "alpha.MD", "zeta.txt"), root.nodes.map { it.name })
        assertEquals(3L, (root.nodes.last() as VfsNode.File).size)
        val nested = storage.listDirectoryOutcome(linked, "資料") as DirectoryOutcome.Success
        assertEquals(listOf("資料/nested.markdown"), nested.nodes.map { it.relativePath })
        assertEquals(provider.openedCursors, provider.closedCursors)
    }

    @Test
    fun revokedGrantIsAccessDeniedAndSuccessfulRetryRetainsActualFiles() = runTest {
        provider.add("existing.md")
        provider.queryFailure = SecurityException("Revoked grant")
        assertEquals(DirectoryOutcome.AccessDenied, storage.listDirectoryOutcome(linked, ""))

        provider.queryFailure = null
        val retry = storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Success
        assertEquals(listOf("existing.md"), retry.nodes.map { it.name })
        assertEquals(provider.openedCursors, provider.closedCursors)
    }

    @Test
    fun providerMissingFolderAndExistingFileAreNotEmptyDirectorySuccess() = runTest {
        provider.add("notes.txt")
        assertEquals(DirectoryOutcome.Missing, storage.listDirectoryOutcome(linked, "absent"))
        assertEquals(DirectoryOutcome.Missing, storage.listDirectoryOutcome(linked, "notes.txt"))
        assertEquals(provider.openedCursors, provider.closedCursors)
    }

    @Test
    fun nullProviderCursorIsFailureAndSuccessfulRetryIsPossible() = runTest {
        provider.add("existing.md")
        provider.nullChildCursor = true
        assertTrue(storage.listDirectoryOutcome(linked, "") is DirectoryOutcome.Failure)
        assertEquals(provider.openedCursors, provider.closedCursors)

        provider.nullChildCursor = false
        assertEquals(listOf("existing.md"), (storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Success).nodes.map { it.name })
    }

    @Test
    fun partialCursorFailureDiscardsCollectedRowsClosesCursorAndAllowsRetry() = runTest {
        provider.add("first.md")
        provider.add("second.md")
        val failure = IOException("Disconnected after one row")
        provider.childCursorFailure = failure

        val result = storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Failure

        assertSame(failure, result.cause)
        assertEquals(provider.openedCursors, provider.closedCursors)
        provider.childCursorFailure = null
        assertEquals(2, (storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Success).nodes.size)
    }

    @Test
    fun loadingProviderCursorIsIncompleteEvenWhenItHasRows() = runTest {
        provider.add("already-loaded.md")
        provider.childCursorLoading = true
        assertTrue(storage.listDirectoryOutcome(linked, "") is DirectoryOutcome.Failure)
        assertEquals(provider.openedCursors, provider.closedCursors)

        provider.childCursorLoading = false
        assertEquals(1, (storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Success).nodes.size)
    }

    @Test
    fun unrepresentableProviderChildFailsWholeListingInsteadOfSilentlyDroppingIt() = runTest {
        provider.add("valid.md")
        provider.add("")
        assertTrue(storage.listDirectoryOutcome(linked, "") is DirectoryOutcome.Failure)
        assertEquals(provider.openedCursors, provider.closedCursors)
    }

    @Test
    fun cancellationDuringEnumerationClosesCursorsAndIsNeverAnOrdinaryOutcome() = runTest {
        provider.add("first.md")
        provider.add("second.md")
        val request = Job()
        provider.onChildRow = { request.cancel() }
        try {
            withContext(request) { storage.listDirectoryOutcome(linked, "") }
            fail("Canceled enumeration must not return a successful or failed listing")
        } catch (_: CancellationException) { }
        assertEquals(provider.openedCursors, provider.closedCursors)

        provider.onChildRow = null
        assertEquals(2, (storage.listDirectoryOutcome(linked, "") as DirectoryOutcome.Success).nodes.size)
    }

    @Test
    fun providerCancellationAfterOneRowRemainsCancellationAndClosesCursor() = runTest {
        provider.add("first.md")
        provider.add("second.md")
        provider.childCursorFailure = CancellationException("Canceled provider enumeration")
        try {
            storage.listDirectoryOutcome(linked, "")
            fail("Provider cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(provider.openedCursors, provider.closedCursors)
    }
}
