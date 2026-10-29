package com.attachdesign.kern.ui.main.dialogs

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.attachdesign.kern.data.storage.MutationFailure
import com.attachdesign.kern.data.storage.MutationOutcome
import com.attachdesign.kern.data.storage.StorageIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class NamingKind { CreateFile, CreateFolder, RenameNode, RenameWorkspaceAlias }

/** Saved by each presentation host; operations and receipts remain owned by its retained VM. */
data class NamingRequest(
    val id: String,
    val kind: NamingKind,
    val projectId: Long,
    val projectName: String,
    val parentPath: String,
    val targetPath: String,
    val targetName: String,
    val directory: Boolean,
    val draft: TextFieldValue,
    val submitted: Boolean = false,
    val inspectionRequired: Boolean = false,
    val openCreatedFile: Boolean = false
) {
    companion object {
        fun create(kind: NamingKind, projectId: Long, projectName: String, parentPath: String,
                   targetPath: String = "", targetName: String = "", directory: Boolean = false,
                   openCreatedFile: Boolean = false): NamingRequest {
            val selectionEnd = if (kind == NamingKind.RenameNode && !directory && targetName.lastIndexOf('.') > 0)
                targetName.lastIndexOf('.') else targetName.length
            return NamingRequest(UUID.randomUUID().toString(), kind, projectId, projectName, parentPath,
                targetPath, targetName, directory, TextFieldValue(targetName, TextRange(0, selectionEnd)),
                openCreatedFile = openCreatedFile)
        }

        val Saver = listSaver<NamingRequest?, Any>(
            save = { request -> request?.let {
                listOf(it.id, it.kind.name, it.projectId, it.projectName, it.parentPath,
                    it.targetPath, it.targetName, it.directory, it.draft.text,
                    it.draft.selection.start, it.draft.selection.end, it.submitted,
                    it.inspectionRequired, it.openCreatedFile)
            } ?: emptyList() },
            restore = { values -> if (values.isEmpty()) null else NamingRequest(
                values[0] as String, NamingKind.valueOf(values[1] as String), values[2] as Long,
                values[3] as String, values[4] as String, values[5] as String, values[6] as String,
                values[7] as Boolean, TextFieldValue(values[8] as String,
                    TextRange(values[9] as Int, values[10] as Int)), values[11] as Boolean,
                values[12] as Boolean, values[13] as Boolean
            ) }
        )
    }
}

sealed interface NamingOperationState {
    data object Pending : NamingOperationState
    data class Succeeded(val actual: StorageIdentity? = null) : NamingOperationState
    data class Failed(val message: String) : NamingOperationState
    data class NeedsReconciliation(val partial: MutationOutcome.ReconciliationRequired) : NamingOperationState
    data class Inspected(val message: String, val mayRetry: Boolean) : NamingOperationState
    data object Consumed : NamingOperationState
}

/** One retained receipt per request. UI disposal neither cancels nor replays a mutation. */
class RetainedNamingOperations(private val scope: CoroutineScope) {
    private val mutableRecords = MutableStateFlow<Map<String, NamingOperationState>>(emptyMap())
    val records = mutableRecords.asStateFlow()

    fun submit(id: String, operation: suspend () -> NamingOperationState): Boolean {
        when (val current = mutableRecords.value[id]) {
            NamingOperationState.Pending, is NamingOperationState.Succeeded,
            is NamingOperationState.NeedsReconciliation, NamingOperationState.Consumed -> return false
            is NamingOperationState.Inspected -> if (!current.mayRetry) return false
            else -> Unit
        }
        run(id, operation)
        return true
    }

    fun recover(id: String, operation: suspend () -> NamingOperationState): Boolean {
        if (mutableRecords.value[id] == NamingOperationState.Pending ||
            mutableRecords.value[id] is NamingOperationState.Succeeded ||
            mutableRecords.value[id] == NamingOperationState.Consumed) return false
        run(id, operation)
        return true
    }

    private fun run(id: String, operation: suspend () -> NamingOperationState) {
        mutableRecords.value = mutableRecords.value + (id to NamingOperationState.Pending)
        scope.launch {
            val result = try {
                operation()
            } catch (cancelled: CancellationException) {
                mutableRecords.value = mutableRecords.value + (id to NamingOperationState.Inspected(
                    "The action was interrupted. Refresh files and inspect the folder before retrying.", false))
                throw cancelled
            } catch (_: Exception) {
                NamingOperationState.Failed("The action could not finish. Your name is preserved. Try again.")
            }
            mutableRecords.value = mutableRecords.value + (id to result)
        }
    }

    fun consumeSuccess(id: String): NamingOperationState.Succeeded? {
        val success = mutableRecords.value[id] as? NamingOperationState.Succeeded ?: return null
        mutableRecords.value = mutableRecords.value + (id to NamingOperationState.Consumed)
        return success
    }
}

fun namingResult(outcome: MutationOutcome): NamingOperationState = when (outcome) {
    is MutationOutcome.Success -> NamingOperationState.Succeeded(outcome.actual)
    is MutationOutcome.ReconciliationRequired -> NamingOperationState.NeedsReconciliation(outcome)
    is MutationOutcome.Failed -> NamingOperationState.Failed(when (outcome.reason) {
        MutationFailure.InvalidName -> "Enter a single name without slashes, dot paths, or control characters."
        MutationFailure.Collision -> "An item with that name already exists. Choose another name."
        MutationFailure.Missing -> "The original item or workspace is no longer available."
        MutationFailure.AccessDenied -> "Access was denied. Check this folder's permission and try again."
        MutationFailure.Unsupported -> "This storage provider does not support this action."
        MutationFailure.Storage -> "The file action failed. Your name is preserved. Try again."
        MutationFailure.Metadata -> "The file index could not be updated. Your name is preserved. Try again."
    })
}

/** A display alias never becomes a filesystem path. */
fun validateWorkspaceAlias(value: String): String {
    require(value.none { it.isISOControl() }) { "Workspace names cannot contain control characters." }
    return value.trim().also { require(it.isNotEmpty()) { "Enter a workspace name." } }
}
