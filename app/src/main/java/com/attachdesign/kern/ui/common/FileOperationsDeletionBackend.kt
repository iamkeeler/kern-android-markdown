package com.attachdesign.kern.ui.common

import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.DeleteOutcome
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.MutationOutcome
import com.attachdesign.kern.data.storage.VfsNode

internal class FileOperationsDeletionBackend(private val operations: FileOperationsManager) : DeletionBackend {
    override suspend fun trash(project: ProjectEntity, node: VfsNode): DeletionResult {
        val result = mutationResult(project, operations.moveToTrashOutcome(project, node))
        if (result is DeletionResult.Success &&
            (!result.node.relativePath.startsWith(".trash/") || result.node.relativePath == node.relativePath)) {
            return DeletionResult.Failed("The item did not reach trash. Refresh and inspect its location.", result.node, true)
        }
        return result
    }

    override suspend fun restore(project: ProjectEntity, trash: VfsNode, originalPath: String): DeletionResult {
        val result = mutationResult(project, operations.restoreFromTrashOutcome(project, trash, originalPath))
        if (result is DeletionResult.Success && (result.node.relativePath == trash.relativePath ||
            result.node.relativePath.substringBeforeLast('/', "") != originalPath.substringBeforeLast('/', ""))) {
            return DeletionResult.Failed("The item did not reach its original folder. Refresh and inspect its location.", result.node, true)
        }
        return result
    }

    override suspend fun finalize(project: ProjectEntity, trash: VfsNode): DeletionResult {
        val result = operations.deleteNodeOutcome(project, trash)
        val resolved = if (result is DeleteOutcome.ReconciliationRequired && result.absenceConfirmed)
            operations.reconcileDeleteMetadata(project, result) else result
        return when (resolved) {
            is DeleteOutcome.Success -> DeletionResult.Success(trash)
            is DeleteOutcome.Failed -> DeletionResult.Failed(
                "Could not permanently delete (${resolved.reason}). Refresh the workspace; recoverable data remains in .trash.", resolved.remaining?.node)
            is DeleteOutcome.ReconciliationRequired -> DeletionResult.Failed(
                "Deletion needs reconciliation. Refresh and inspect ${resolved.remaining?.node?.relativePath ?: resolved.relativePath}.", resolved.remaining?.node)
        }
    }

    private suspend fun mutationResult(project: ProjectEntity, result: MutationOutcome): DeletionResult {
        // Only retry a metadata receipt. Never replay a provider move after an ambiguous result.
        val resolved = if (result is MutationOutcome.ReconciliationRequired && result.metadata != null)
            operations.reconcileMetadata(project, result) else result
        return when (resolved) {
            is MutationOutcome.Success -> DeletionResult.Success(resolved.actual.node)
            is MutationOutcome.Failed -> DeletionResult.Failed(
                "Could not move the item (${resolved.reason}). Refresh the workspace and check its permissions.")
            is MutationOutcome.ReconciliationRequired -> DeletionResult.Failed(
                "Storage may have changed. Refresh and inspect ${resolved.actual?.node?.relativePath ?: resolved.attemptedPath} before trying again.", resolved.actual?.node, requiresInspection = true)
        }
    }
}
