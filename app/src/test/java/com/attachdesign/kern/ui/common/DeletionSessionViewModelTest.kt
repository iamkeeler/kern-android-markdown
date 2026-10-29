package com.attachdesign.kern.ui.common

import androidx.lifecycle.viewModelScope
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeletionSessionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val project = ProjectEntity(1, "Files", "root", false, true)
    private val file = VfsNode.File("Draft.md", "Draft.md", 1, 1, "PENDING")
    private val trash = file.copy(name = "unique.md", relativePath = ".trash/unique.md")
    private val backend = FakeBackend()
    private val model = DeletionSessionViewModel(backend)

    @After fun cleanup() { model.viewModelScope.cancel() }

    @Test fun delayedPresentationNeverPurgesAndUndoRunsExactlyOnce() = runTest {
        model.requestDelete(project, file)
        advanceUntilIdle()
        advanceTimeBy(600_000)
        assertEquals(0, backend.finalizations)
        val offer = model.claimNextOffer()!!
        model.finishOffer(offer.receipt.id, offer.lease, true)
        model.finishOffer(offer.receipt.id, offer.lease, false)
        advanceUntilIdle()
        assertEquals(1, backend.restores)
        assertEquals(0, backend.finalizations)
        assertTrue(model.receipts.value.isEmpty())
    }

    @Test fun canceledLeaseRequeuesAndRejectsStaleCallbacks() = runTest {
        model.requestDelete(project, file)
        advanceUntilIdle()
        val old = model.claimNextOffer()!!
        model.releaseOffer(old.receipt.id, old.lease)
        val current = model.claimNextOffer()!!
        assertNotEquals(old.lease, current.lease)
        model.finishOffer(old.receipt.id, old.lease, false)
        model.releaseOffer(old.receipt.id, old.lease)
        assertEquals(DeletionPhase.Offered, model.receipts.value.single().phase)
        model.finishOffer(current.receipt.id, current.lease, true)
        advanceUntilIdle()
        assertEquals(1, backend.restores)
        assertEquals(0, backend.finalizations)
    }

    @Test fun queuedDeletesHaveOneOfferAndOneTerminalDecisionEach() = runTest {
        model.requestDelete(project, file)
        model.requestDelete(project, file.copy(name = "Other.md", relativePath = "Other.md"))
        advanceUntilIdle()
        val first = model.claimNextOffer()!!
        assertNull(model.claimNextOffer())
        model.finishOffer(first.receipt.id, first.lease, false)
        val second = model.claimNextOffer()!!
        model.finishOffer(second.receipt.id, second.lease, true)
        advanceUntilIdle()
        assertEquals(1, backend.finalizations)
        assertEquals(1, backend.restores)
        assertTrue(model.receipts.value.isEmpty())
    }

    @Test fun restoreConflictKeepsTrashAndCanRetryWithoutRepeatingTrashMove() = runTest {
        model.requestDelete(project, file)
        advanceUntilIdle()
        val offer = model.claimNextOffer()!!
        backend.restoreFailure = true
        model.finishOffer(offer.receipt.id, offer.lease, true)
        advanceUntilIdle()
        assertEquals(trash, model.receipts.value.single().trash)
        assertTrue(model.receipts.value.single().canRetryRestore)
        backend.restoreFailure = false
        model.retryRestore(offer.receipt.id)
        advanceUntilIdle()
        assertEquals(1, backend.moves)
        assertEquals(2, backend.restores)
        assertEquals(0, backend.finalizations)
    }

    @Test fun partialMoveNeverOffersUndoOrAnnouncesConfirmedMove() = runTest {
        backend.trashFailure = DeletionResult.Failed("Refresh .trash to inspect actual data", trash)
        var moved = false
        model.requestDelete(project, file) { moved = true }
        advanceUntilIdle()
        assertFalse(moved)
        assertNull(model.claimNextOffer())
        assertEquals(DeletionPhase.Failed, model.receipts.value.single().phase)
        assertEquals(trash, model.receipts.value.single().trash)
        assertEquals(0, backend.finalizations)
    }

    @Test fun duplicatePendingRequestDoesNotRepeatDiskAndFinalizeFailureRetainsReceipt() = runTest {
        backend.pending = CompletableDeferred()
        model.requestDelete(project, file)
        model.requestDelete(project, file)
        advanceUntilIdle()
        assertEquals(1, backend.moves)
        backend.pending!!.complete(Unit)
        advanceUntilIdle()
        val offer = model.claimNextOffer()!!
        backend.finalizeFailure = true
        model.finishOffer(offer.receipt.id, offer.lease, false)
        advanceUntilIdle()
        assertEquals(DeletionPhase.Failed, model.receipts.value.single().phase)
        assertEquals(trash, model.receipts.value.single().trash)
        assertEquals(0, backend.restores)
    }

    @Test fun providerCancellationLeavesAnInterruptedReceiptInsteadOfPermanentSpinner() = runTest {
        backend.cancelMove = true
        model.requestDelete(project, file)
        advanceUntilIdle()
        val interrupted = model.receipts.value.single()
        assertEquals(DeletionPhase.Failed, interrupted.phase)
        assertNull(model.claimNextOffer())
        assertTrue(interrupted.error!!.contains("interrupted"))
        assertEquals(0, backend.finalizations)
    }

    @Test fun ambiguousRestoreCannotReplayDiskThroughRetryUndo() = runTest {
        model.requestDelete(project, file)
        advanceUntilIdle()
        val offer = model.claimNextOffer()!!
        backend.ambiguousRestore = true
        model.finishOffer(offer.receipt.id, offer.lease, true)
        advanceUntilIdle()
        assertFalse(model.receipts.value.single().canRetryRestore)
        model.retryRestore(offer.receipt.id)
        advanceUntilIdle()
        assertEquals(1, backend.restores)
    }

    private inner class FakeBackend : DeletionBackend {
        var cancelMove = false
        var moves = 0
        var restores = 0
        var finalizations = 0
        var restoreFailure = false
        var ambiguousRestore = false
        var finalizeFailure = false
        var trashFailure: DeletionResult.Failed? = null
        var pending: CompletableDeferred<Unit>? = null
        override suspend fun trash(project: ProjectEntity, node: VfsNode): DeletionResult {
            moves++
            if (cancelMove) throw kotlinx.coroutines.CancellationException("Provider canceled")
            pending?.await()
            return trashFailure ?: DeletionResult.Success(trash)
        }
        override suspend fun restore(project: ProjectEntity, trash: VfsNode, originalPath: String): DeletionResult {
            restores++
            if (ambiguousRestore) return DeletionResult.Failed("Inspect storage", requiresInspection = true)
            return if (restoreFailure) DeletionResult.Failed("Restore destination exists") else DeletionResult.Success(file)
        }
        override suspend fun finalize(project: ProjectEntity, trash: VfsNode): DeletionResult {
            finalizations++
            return if (finalizeFailure) DeletionResult.Failed("Delete failed; file remains in .trash") else DeletionResult.Success(trash)
        }
    }
}
