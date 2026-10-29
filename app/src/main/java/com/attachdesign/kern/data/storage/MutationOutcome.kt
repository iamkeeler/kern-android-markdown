package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.FileEntity

/** The provider's returned URI and name, or the exact filesystem location actually changed. */
data class StorageIdentity(
    val node: VfsNode,
    val documentUri: String? = null,
    val absolutePath: String? = null
)

enum class MutationFailure { InvalidName, Collision, Missing, AccessDenied, Unsupported, Storage, Metadata }

/** A session receipt for retrying only the Room transaction, never the disk mutation. */
data class MetadataReconciliation internal constructor(
    val projectId: Long,
    val projectPath: String,
    internal val pathsToRemove: List<String>,
    internal val rowsToInsert: List<FileEntity>,
    internal val originalPath: String? = null,
    internal val sourceRows: List<FileEntity> = emptyList(),
    internal val preserveSyncState: Boolean = false
)

sealed interface MutationOutcome {
    data class Success(val actual: StorageIdentity, val changed: Boolean = true) : MutationOutcome
    data class Failed(val reason: MutationFailure, val cause: Throwable? = null) : MutationOutcome

    /** Disk may already have changed. Refresh/resolve this location; never blindly resubmit. */
    data class ReconciliationRequired(
        val actual: StorageIdentity?,
        val originalPath: String?,
        val attemptedPath: String,
        val returnedUri: String?,
        val cause: Throwable,
        val returnedAbsolutePath: String? = null,
        val originalUri: String? = null,
        val metadata: MetadataReconciliation? = null
    ) : MutationOutcome
}

/** Keeps legacy Result callers informed without converting a partial mutation into success. */
class MutationOutcomeException(val outcome: MutationOutcome) : Exception(
    when (outcome) {
        is MutationOutcome.Failed -> outcome.reason.name
        is MutationOutcome.ReconciliationRequired -> "Storage changed; metadata reconciliation is required"
        is MutationOutcome.Success -> "Operation succeeded"
    },
    when (outcome) {
        is MutationOutcome.Failed -> outcome.cause
        is MutationOutcome.ReconciliationRequired -> outcome.cause
        is MutationOutcome.Success -> null
    }
)
