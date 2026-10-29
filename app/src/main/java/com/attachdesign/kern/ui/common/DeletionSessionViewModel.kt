package com.attachdesign.kern.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.VfsNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** No timer or persisted receipt: an interrupted session leaves recoverable trash on disk. */
internal interface DeletionBackend {
    suspend fun trash(project: ProjectEntity, node: VfsNode): DeletionResult
    suspend fun restore(project: ProjectEntity, trash: VfsNode, originalPath: String): DeletionResult
    suspend fun finalize(project: ProjectEntity, trash: VfsNode): DeletionResult
}

internal sealed interface DeletionResult {
    data class Success(val node: VfsNode) : DeletionResult
    data class Failed(val message: String, val actual: VfsNode? = null, val requiresInspection: Boolean = false) : DeletionResult
}

internal enum class DeletionPhase { Moving, Queued, Offered, Restoring, Finalizing, Failed }

internal data class DeletionReceipt(
    val id: Long,
    val project: ProjectEntity,
    val original: VfsNode,
    val trash: VfsNode? = null,
    val phase: DeletionPhase = DeletionPhase.Moving,
    val lease: Long? = null,
    val error: String? = null,
    val canRetryRestore: Boolean = false
)

internal data class DeletionOffer(val receipt: DeletionReceipt, val lease: Long)

/** Activity-owned across routes and rotation. Presentation owns a revocable lease, never a timer. */
internal class DeletionSessionViewModel(private val backend: DeletionBackend) : ViewModel() {
    private val mutableReceipts = MutableStateFlow<List<DeletionReceipt>>(emptyList())
    val receipts = mutableReceipts.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private var nextId = 0L
    private var nextLease = 0L

    fun requestDelete(project: ProjectEntity, node: VfsNode, onFailure: (Boolean) -> Unit = {}, onMoved: () -> Unit = {}) {
        if (receipts.value.any { it.project.id == project.id && it.original.relativePath == node.relativePath }) {
            onFailure(true)
            return
        }
        val receipt = DeletionReceipt(++nextId, project, node)
        mutableReceipts.value += receipt
        viewModelScope.launch {
            val result = try { safely { backend.trash(project, node) } } catch (cancelled: CancellationException) {
                interrupted(receipt.id)
                onFailure(true)
                throw cancelled
            }
            when (result) {
                is DeletionResult.Success -> {
                    update(receipt.id) { it.copy(trash = result.node, phase = DeletionPhase.Queued) }
                    changed()
                    onMoved()
                }
                is DeletionResult.Failed -> {
                    update(receipt.id) { it.copy(trash = result.actual, phase = DeletionPhase.Failed, error = result.message) }
                    onFailure(result.requiresInspection)
                }
            }
        }
    }

    fun claimNextOffer(): DeletionOffer? {
        if (receipts.value.any { it.phase == DeletionPhase.Offered }) return null
        val receipt = receipts.value.firstOrNull { it.phase == DeletionPhase.Queued } ?: return null
        val lease = ++nextLease
        update(receipt.id) { it.copy(phase = DeletionPhase.Offered, lease = lease) }
        return DeletionOffer(receipt, lease)
    }

    fun releaseOffer(id: Long, lease: Long) {
        update(id) {
            if (it.phase == DeletionPhase.Offered && it.lease == lease)
                it.copy(phase = DeletionPhase.Queued, lease = null) else it
        }
    }

    fun finishOffer(id: Long, lease: Long, undo: Boolean) {
        val receipt = receipts.value.firstOrNull {
            it.id == id && it.phase == DeletionPhase.Offered && it.lease == lease
        } ?: return
        resolve(receipt, undo)
    }

    fun retryRestore(id: Long) {
        val receipt = receipts.value.firstOrNull {
            it.id == id && it.phase == DeletionPhase.Failed && it.canRetryRestore
        } ?: return
        resolve(receipt, undo = true)
    }

    fun dismissError(id: Long) {
        // Dismissing an error never finalizes recoverable trash.
        mutableReceipts.value = receipts.value.filterNot { it.id == id && it.phase == DeletionPhase.Failed }
    }

    private fun resolve(receipt: DeletionReceipt, undo: Boolean) {
        val trash = receipt.trash ?: return
        update(receipt.id) {
            it.copy(phase = if (undo) DeletionPhase.Restoring else DeletionPhase.Finalizing,
                lease = null, error = null, canRetryRestore = false)
        }
        viewModelScope.launch {
            val result = try { safely {
                if (undo) backend.restore(receipt.project, trash, receipt.original.relativePath)
                else backend.finalize(receipt.project, trash)
            } } catch (cancelled: CancellationException) {
                interrupted(receipt.id)
                throw cancelled
            }
            when (result) {
                is DeletionResult.Success -> {
                    mutableReceipts.value = receipts.value.filterNot { it.id == receipt.id }
                    changed()
                }
                is DeletionResult.Failed -> update(receipt.id) {
                    it.copy(phase = DeletionPhase.Failed, error = result.message,
                        trash = result.actual ?: trash, canRetryRestore = undo && !result.requiresInspection && result.actual == null)
                }
            }
        }
    }

    private suspend fun safely(action: suspend () -> DeletionResult): DeletionResult = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        DeletionResult.Failed(failure.message ?: "Operation failed. Refresh the workspace to inspect the file.", requiresInspection = true)
    }

    private fun update(id: Long, transform: (DeletionReceipt) -> DeletionReceipt) {
        mutableReceipts.value = receipts.value.map { if (it.id == id) transform(it) else it }
    }

    private fun interrupted(id: Long) {
        update(id) { it.copy(phase = DeletionPhase.Failed, lease = null, canRetryRestore = false,
            error = "Operation interrupted. Refresh the workspace and inspect the source and .trash before trying again.") }
    }

    private fun changed() { mutableRevision.value++ }
}
