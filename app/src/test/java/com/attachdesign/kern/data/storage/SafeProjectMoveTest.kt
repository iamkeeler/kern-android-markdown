package com.attachdesign.kern.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.attachdesign.kern.data.local.ProjectEntity
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
class SafeProjectMoveTest {
    private lateinit var storage: StorageManager
    private lateinit var provider: NamingTestDocumentsProvider
    private val local = ProjectEntity(1, "Local", "SafeProjectMoveTest", false, true)
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
    fun filesystemNativeMovePreservesNestedFolderAndExactBinaryContents() = runTest {
        storage.createDirectory(local, "source/assets/nested")
        storage.createDirectory(local, "destination")
        val bytes = byteArrayOf(0, 0xff.toByte(), 0x80.toByte(), 13, 10, 32)
        storage.getAbsoluteFile(local, "source/assets/nested/image.bin").writeBytes(bytes)

        val result = storage.moveWithinProject(local, "source/assets", "destination", "資料") as MutationOutcome.Success

        assertEquals("destination/資料", result.actual.node.relativePath)
        assertEquals(storage.getAbsoluteFile(local, "destination/資料").absolutePath, result.actual.absolutePath)
        assertArrayEquals(bytes, storage.getAbsoluteFile(local, "destination/資料/nested/image.bin").readBytes())
        assertFalse(storage.fileExists(local, "source/assets"))
    }

    @Test
    fun filesystemCollisionAndDescendantMoveLeaveSourceAndDestinationIntact() = runTest {
        storage.createDirectory(local, "source/nested")
        storage.createDirectory(local, "destination")
        storage.writeFile(local, "source/notes.md", "source\r\n  ")
        storage.writeFile(local, "destination/taken.md", "destination\t")
        val collision = storage.moveWithinProject(local, "source/notes.md", "destination", "taken.md") as MutationOutcome.Failed
        assertEquals(MutationFailure.Collision, collision.reason)
        val descendant = storage.moveWithinProject(local, "source", "source/nested") as MutationOutcome.Failed
        assertEquals(MutationFailure.InvalidName, descendant.reason)
        assertEquals("source\r\n  ", storage.readFile(local, "source/notes.md"))
        assertEquals("destination\t", storage.readFile(local, "destination/taken.md"))
    }

    @Test
    fun sameParentSameNameIsNoOpWithActualIdentityAndMissingSourceStillFails() = runTest {
        storage.writeFile(local, "notes.txt", "exact  \n")
        val same = storage.moveWithinProject(local, "notes.txt", "") as MutationOutcome.Success
        assertFalse(same.changed)
        assertEquals("notes.txt", same.actual.node.relativePath)
        assertEquals(storage.getAbsoluteFile(local, "notes.txt").absolutePath, same.actual.absolutePath)
        assertEquals(MutationFailure.Missing, (storage.moveWithinProject(local, "absent.md", "") as MutationOutcome.Failed).reason)

        val source = provider.add("notes.txt")
        val providerSame = storage.moveWithinProject(linked, "notes.txt", "") as MutationOutcome.Success
        assertFalse(providerSame.changed)
        assertEquals(source.id, DocumentsContract.getDocumentId(Uri.parse(providerSame.actual.documentUri)))
        assertEquals(0, provider.moveCalls)
        assertEquals(0, provider.renameCalls)
    }

    @Test
    fun providerNativeMoveReceivesExactParentsAndReturnsActualNormalizedUri() = runTest {
        val sourceParent = provider.add("source", directory = true)
        val destination = provider.add("destination", directory = true)
        val source = provider.add("notes.MD", parent = sourceParent.id, bytes = byteArrayOf(0, 0xff.toByte()))
        provider.moveNormalize = { "stored-$it" }

        val result = storage.moveWithinProject(linked, "source/notes.MD", "destination") as MutationOutcome.Success

        assertEquals(Triple(source.id, sourceParent.id, destination.id), provider.lastMoveArguments)
        val returnedId = DocumentsContract.getDocumentId(Uri.parse(result.actual.documentUri))
        assertNotEquals(source.id, returnedId)
        assertEquals(destination.id, provider.entries.getValue(returnedId).parent)
        assertEquals("destination/stored-notes.MD", result.actual.node.relativePath)
        assertFalse(provider.entries.containsKey(source.id))
        assertEquals(1, provider.moveCalls)
        assertEquals(0, provider.renameCalls)
        assertEquals(provider.openedCursors, provider.closedCursors)
    }

    @Test
    fun providerFolderMovePreservesNestedChildrenAndBinaryBytes() = runTest {
        val source = provider.add("assets", directory = true)
        val nested = provider.add("nested", directory = true, parent = source.id)
        val bytes = byteArrayOf(0, 0xff.toByte(), 0x80.toByte(), 10, 13)
        val image = provider.add("image.bin", parent = nested.id, bytes = bytes)
        val destination = provider.add("destination", directory = true)

        val result = storage.moveWithinProject(linked, "assets", "destination") as MutationOutcome.Success

        val movedId = DocumentsContract.getDocumentId(Uri.parse(result.actual.documentUri))
        assertEquals(destination.id, provider.entries.getValue(movedId).parent)
        assertEquals(movedId, provider.entries.getValue(nested.id).parent)
        assertEquals(nested.id, provider.entries.getValue(image.id).parent)
        assertArrayEquals(bytes, provider.entries.getValue(image.id).bytes)
        assertEquals("destination/assets", result.actual.node.relativePath)
        assertEquals(0, provider.renameCalls)
    }

    @Test
    fun providerMoveThenExplicitRenameReturnsFinalNormalizedNameAndUri() = runTest {
        val source = provider.add("original.md", bytes = byteArrayOf(0, 0xff.toByte(), 13, 10))
        val destination = provider.add("destination", directory = true)
        provider.normalize = { "stored-$it" }

        val result = storage.moveWithinProject(linked, "original.md", "destination", "chosen.txt") as MutationOutcome.Success

        assertEquals("destination/stored-chosen.txt", result.actual.node.relativePath)
        val actualId = DocumentsContract.getDocumentId(Uri.parse(result.actual.documentUri))
        assertEquals(destination.id, provider.entries.getValue(actualId).parent)
        assertArrayEquals(source.bytes, provider.entries.getValue(actualId).bytes)
        assertEquals(1, provider.moveCalls)
        assertEquals(1, provider.renameCalls)
    }

    @Test
    fun missingOrFileDestinationParentIsRejectedBeforeNativeMove() = runTest {
        val source = provider.add("original.md")
        provider.add("file.txt")
        assertEquals(MutationFailure.Missing, (storage.moveWithinProject(linked, "original.md", "missing") as MutationOutcome.Failed).reason)
        assertEquals(MutationFailure.Missing, (storage.moveWithinProject(linked, "original.md", "file.txt") as MutationOutcome.Failed).reason)
        assertEquals(NamingTestDocumentsProvider.ROOT, provider.entries.getValue(source.id).parent)
        assertEquals(0, provider.moveCalls)
    }

    @Test
    fun unsupportedNativeMoveOrRequiredRenameFailsBeforeMutation() = runTest {
        val source = provider.add("notes.md")
        provider.add("destination", directory = true)
        provider.supportsNativeMove = false
        val unsupportedMove = storage.moveWithinProject(linked, "notes.md", "destination") as MutationOutcome.Failed
        assertEquals(MutationFailure.Unsupported, unsupportedMove.reason)
        provider.supportsNativeMove = true
        provider.supportsRename = false
        val unsupportedRename = storage.moveWithinProject(linked, "notes.md", "destination", "new.md") as MutationOutcome.Failed
        assertEquals(MutationFailure.Unsupported, unsupportedRename.reason)
        assertEquals(NamingTestDocumentsProvider.ROOT, provider.entries.getValue(source.id).parent)
        assertEquals(0, provider.moveCalls)
        assertEquals(0, provider.renameCalls)
    }

    @Test
    fun providerRejectsFinalAndIntermediateNativeNameCollisionsBeforeMutation() = runTest {
        val source = provider.add("original.md")
        val destination = provider.add("destination", directory = true)
        val occupied = provider.add("chosen.md", parent = destination.id, bytes = byteArrayOf(42))
        val finalCollision = storage.moveWithinProject(linked, "original.md", "destination", "chosen.md") as MutationOutcome.Failed
        assertEquals(MutationFailure.Collision, finalCollision.reason)
        provider.entries.remove(occupied.id)
        provider.add("original.md", parent = destination.id, bytes = byteArrayOf(7))
        val intermediate = storage.moveWithinProject(linked, "original.md", "destination", "available.md") as MutationOutcome.Failed
        assertEquals(MutationFailure.Collision, intermediate.reason)
        assertEquals(NamingTestDocumentsProvider.ROOT, provider.entries.getValue(source.id).parent)
        assertEquals(0, provider.moveCalls)
    }

    @Test
    fun providerRejectsSelfAndDescendantDestinationsBeforeMutation() = runTest {
        val source = provider.add("assets", directory = true)
        provider.add("nested", parent = source.id, directory = true)
        assertEquals(MutationFailure.InvalidName, (storage.moveWithinProject(linked, "assets", "assets") as MutationOutcome.Failed).reason)
        assertEquals(MutationFailure.InvalidName, (storage.moveWithinProject(linked, "assets", "assets/nested") as MutationOutcome.Failed).reason)
        assertEquals(0, provider.moveCalls)
    }

    @Test
    fun revokedGrantFailsBeforeMutationAndRetainsSource() = runTest {
        val source = provider.add("notes.md")
        provider.add("destination", directory = true)
        provider.queryFailure = SecurityException("Revoked grant")
        val result = storage.moveWithinProject(linked, "notes.md", "destination") as MutationOutcome.Failed
        assertEquals(MutationFailure.AccessDenied, result.reason)
        assertEquals(NamingTestDocumentsProvider.ROOT, provider.entries.getValue(source.id).parent)
        assertEquals(0, provider.moveCalls)
    }

    @Test
    fun moveThenRenameFailureReportsActualDestinationWithOriginalNameAndUri() = runTest {
        val source = provider.add("original.md", bytes = byteArrayOf(0, 0xff.toByte()))
        val destination = provider.add("destination", directory = true)
        provider.mutationFailure = IOException("Rename rejected after move")

        val result = storage.moveWithinProject(linked, "original.md", "destination", "requested.md") as MutationOutcome.ReconciliationRequired

        assertEquals("destination/original.md", result.actual?.node?.relativePath)
        assertEquals("destination/requested.md", result.attemptedPath)
        assertEquals(result.returnedUri, result.actual?.documentUri)
        val actualId = DocumentsContract.getDocumentId(Uri.parse(result.actual?.documentUri))
        assertEquals(destination.id, provider.entries.getValue(actualId).parent)
        assertFalse(provider.entries.containsKey(source.id))
        assertArrayEquals(source.bytes, provider.entries.getValue(actualId).bytes)
        assertEquals(1, provider.moveCalls)
        assertEquals(1, provider.renameCalls)
    }

    @Test
    fun nullOrThrownNativeMoveResultRemainsIndeterminateRatherThanClaimingNoMutation() = runTest {
        provider.add("original.md")
        val destination = provider.add("destination", directory = true)
        provider.returnNullMove = true
        assertTrue(storage.moveWithinProject(linked, "original.md", "destination") is MutationOutcome.ReconciliationRequired)

        provider.returnNullMove = false
        provider.moveFailureAfterCommit = IOException("Provider committed but did not return identity")
        val partial = storage.moveWithinProject(linked, "original.md", "destination") as MutationOutcome.ReconciliationRequired
        assertNull(partial.actual)
        assertNotNull(partial.originalUri)
        assertEquals(destination.id, provider.entries.values.single { !it.directory }.parent)
        assertEquals(2, provider.moveCalls)
    }

    @Test
    fun cancellationIsPropagatedWithoutCopyDeleteFallback() = runTest {
        val source = provider.add("original.md")
        provider.add("destination", directory = true)
        provider.moveFailure = CancellationException("Native move canceled")
        try {
            storage.moveWithinProject(linked, "original.md", "destination")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(NamingTestDocumentsProvider.ROOT, provider.entries.getValue(source.id).parent)
        assertEquals(1, provider.moveCalls)
        assertEquals(0, provider.createCalls)
        assertEquals(0, provider.renameCalls)
    }
}
