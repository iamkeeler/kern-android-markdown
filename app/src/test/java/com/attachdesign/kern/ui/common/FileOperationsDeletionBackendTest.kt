package com.attachdesign.kern.ui.common

import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FileOperationsDeletionBackendTest {
    private val project = ProjectEntity(1, "Linked", "content://test/tree/root", true, true)
    private val source = VfsNode.File("draft.md", "draft.md", 1, 1)
    private val trash = source.copy(name = "unique_draft.md", relativePath = ".trash/unique_draft.md")
    private val operations = mockk<FileOperationsManager>()
    private val backend = FileOperationsDeletionBackend(operations)

    @Test fun metadataRecoveryAtUnchangedSourceNeverBecomesSuccessfulDeletion() = runTest {
        val partial = MutationOutcome.ReconciliationRequired(StorageIdentity(source), source.relativePath,
            trash.relativePath, null, IllegalStateException(), metadata = MetadataReconciliation(project.id, project.path, emptyList(), emptyList()))
        coEvery { operations.moveToTrashOutcome(project, source) } returns partial
        coEvery { operations.reconcileMetadata(project, partial) } returns MutationOutcome.Success(StorageIdentity(source))
        val result = backend.trash(project, source) as DeletionResult.Failed
        assertTrue(result.requiresInspection)
        assertEquals(source, result.actual)
        coVerify(exactly = 1) { operations.moveToTrashOutcome(project, source) }
        coVerify(exactly = 0) { operations.deleteNodeOutcome(any(), any()) }
    }

    @Test fun metadataRecoveryStillInTrashNeverAnnouncesSuccessfulRestoration() = runTest {
        val partial = MutationOutcome.ReconciliationRequired(StorageIdentity(trash), trash.relativePath,
            source.relativePath, null, IllegalStateException(), metadata = MetadataReconciliation(project.id, project.path, emptyList(), emptyList()))
        coEvery { operations.restoreFromTrashOutcome(project, trash, source.relativePath) } returns partial
        coEvery { operations.reconcileMetadata(project, partial) } returns MutationOutcome.Success(StorageIdentity(trash))
        val result = backend.restore(project, trash, source.relativePath) as DeletionResult.Failed
        assertTrue(result.requiresInspection)
        assertEquals(trash, result.actual)
    }
}
