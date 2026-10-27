package com.attachdesign.kern.ui.editor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionWorkspaceTest {
    @Test fun `closing and reopening retains chosen reference and source`() = runTest {
        val key = ReferenceKey(1, "outline.md")
        val workspace = CompanionWorkspace(this, { "# Reference\n\nOriginal **Markdown**" }, StandardTestDispatcher(testScheduler))
        workspace.setOpen(true)
        workspace.showReference(key)
        advanceUntilIdle()
        workspace.setOpen(false); workspace.setOpen(true)
        assertEquals(key, workspace.state.value.reference)
        assertEquals(CompanionMode.REFERENCE, workspace.state.value.mode)
        assertEquals("Original **Markdown**", workspace.state.value.blocks.items.last().block.rawText)
    }
    @Test fun `latest reference wins and returning to preview cancels pending reads`() = runTest {
        val slow = CompletableDeferred<String>()
        val workspace = CompanionWorkspace(this, { if (it.path == "slow.md") slow.await() else "New reference" }, StandardTestDispatcher(testScheduler))
        workspace.showReference(ReferenceKey(1, "slow.md"))
        testScheduler.runCurrent()
        workspace.showReference(ReferenceKey(1, "new.md"))
        advanceUntilIdle()
        slow.complete("Stale reference")
        advanceUntilIdle()
        assertEquals("new.md", workspace.state.value.reference?.path)
        assertEquals("New reference", workspace.state.value.blocks.items.single().block.rawText)
        workspace.showReference(ReferenceKey(1, "slow.md"))
        workspace.showPreview()
        advanceUntilIdle()
        assertEquals(CompanionMode.PREVIEW, workspace.state.value.mode)
        assertFalse(workspace.state.value.isLoading)
    }
    @Test fun `reference failure is recoverable and does not close workspace`() = runTest {
        var fail = true
        val workspace = CompanionWorkspace(this, { if (fail) error("revoked permission") else "Recovered" }, StandardTestDispatcher(testScheduler))
        workspace.setOpen(true)
        workspace.showReference(ReferenceKey(2, "missing.md"))
        advanceUntilIdle()
        assertNotNull(workspace.state.value.error)
        assertTrue(workspace.state.value.isOpen)
        fail = false
        workspace.retry()
        advanceUntilIdle()
        assertNull(workspace.state.value.error)
        assertEquals("Recovered", workspace.state.value.blocks.items.single().block.rawText)
    }
}
