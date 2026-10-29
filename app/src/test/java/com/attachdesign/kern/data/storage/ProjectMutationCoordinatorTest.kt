package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.AppDatabase
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectMutationCoordinatorTest {
    @Test
    fun oneDatabaseSharesGateAndNewerScanInvalidatesOnlyItsOwnProject() = runTest {
        val db = mockk<AppDatabase>()
        val gate = ProjectMutationCoordinator.forDatabase(db)
        assertSame(gate, ProjectMutationCoordinator.forDatabase(db))
        val first = gate.beginScan(1)
        val other = gate.beginScan(2)
        val newer = gate.beginScan(1)
        assertNull(gate.commitScan(first) { "stale" })
        assertEquals("newer", gate.commitScan(newer) { "newer" })
        assertEquals("other", gate.commitScan(other) { "other" })
    }

    @Test
    fun commitWaitsForInFlightMutationAndRejectsItsOldSnapshot() = runTest {
        val gate = ProjectMutationCoordinator.forDatabase(mockk())
        val token = gate.beginScan(1)
        val mutationEntered = CompletableDeferred<Unit>()
        val finishMutation = CompletableDeferred<Unit>()
        val mutation = launch {
            gate.withMutation(1) {
                mutationEntered.complete(Unit)
                finishMutation.await()
            }
        }
        mutationEntered.await()
        var committed = false
        val commit = async { gate.commitScan(token) { committed = true } }
        runCurrent()
        assertFalse(commit.isCompleted)
        assertFalse(committed)
        finishMutation.complete(Unit)
        mutation.join()
        assertNull(commit.await())
        assertFalse(committed)
    }

    @Test
    fun canceledAndFailedMutationsStillInvalidateEarlierScans() = runTest {
        val gate = ProjectMutationCoordinator.forDatabase(mockk())
        for (failure in listOf(IllegalStateException("disk failure"), CancellationException("canceled"))) {
            val token = gate.beginScan(1)
            try {
                gate.withMutation(1) { throw failure }
                fail("Mutation should throw")
            } catch (caught: Exception) {
                assertSame(failure, caught)
            }
            assertNull(gate.commitScan(token) { "stale" })
            val retry = gate.beginScan(1)
            assertEquals("retry", gate.commitScan(retry) { "retry" })
        }
    }
}
