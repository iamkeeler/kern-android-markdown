package com.attachdesign.kern.ui.main.dialogs

import com.attachdesign.kern.data.storage.MutationOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetainedNamingOperationsTest {
    @Test fun pendingRequestDispatchesOnceAndSuccessConsumesOnce() = runTest {
        val operations = RetainedNamingOperations(backgroundScope)
        val pending = CompletableDeferred<Unit>()
        var mutations = 0
        assertTrue(operations.submit("first") { mutations++; pending.await(); NamingOperationState.Succeeded() })
        assertFalse(operations.submit("first") { error("Duplicate disk mutation") })
        testScheduler.runCurrent()
        assertEquals(1, mutations)
        pending.complete(Unit)
        testScheduler.runCurrent()
        assertNotNull(operations.consumeSuccess("first"))
        assertNull(operations.consumeSuccess("first"))
        assertFalse(operations.submit("first") { error("Consumed mutation replayed") })
    }

    @Test fun partialCanOnlyRunExplicitMetadataRecoveryAndDoesNotCloseNewRequest() = runTest {
        val operations = RetainedNamingOperations(backgroundScope)
        val partial = MutationOutcome.ReconciliationRequired(null, "old.md", "new.md", null, IllegalStateException())
        operations.submit("old") { NamingOperationState.NeedsReconciliation(partial) }
        testScheduler.runCurrent()
        assertFalse(operations.submit("old") { error("Disk replay") })
        operations.submit("new") { NamingOperationState.Failed("Keep the newer draft") }
        testScheduler.runCurrent()
        assertTrue(operations.recover("old") { NamingOperationState.Succeeded() })
        testScheduler.runCurrent()
        assertNotNull(operations.consumeSuccess("old"))
        assertEquals(NamingOperationState.Failed("Keep the newer draft"), operations.records.value["new"])
    }

    @Test fun failureAllowsCorrectionButProviderCancellationRequiresInspection() = runTest {
        val operations = RetainedNamingOperations(backgroundScope)
        operations.submit("name") { NamingOperationState.Failed("Collision") }
        testScheduler.runCurrent()
        assertTrue(operations.submit("name") { throw CancellationException("Provider canceled") })
        testScheduler.runCurrent()
        val interrupted = operations.records.value["name"] as NamingOperationState.Inspected
        assertFalse(interrupted.mayRetry)
        assertFalse(operations.submit("name") { error("Unknown disk state replayed") })
        operations.recover("name") { NamingOperationState.Inspected("Source and destination checked", true) }
        testScheduler.runCurrent()
        assertTrue(operations.submit("name") { NamingOperationState.Succeeded() })
    }

    @Test fun workspaceAliasNeverUsesLeafNameRules() {
        assertEquals("Writing / Research", validateWorkspaceAlias(" Writing / Research "))
        assertTrue(runCatching { validateWorkspaceAlias("\tHidden") }.isFailure)
        assertTrue(runCatching { validateWorkspaceAlias("  ") }.isFailure)
    }
}
