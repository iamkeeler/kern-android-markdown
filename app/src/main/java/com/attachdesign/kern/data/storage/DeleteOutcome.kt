package com.attachdesign.kern.data.storage

/** Metadata cleanup after confirmed disk absence; retrying this receipt never deletes again. */
data class DeleteMetadataReconciliation internal constructor(
    val projectId: Long,
    val projectPath: String,
    val relativePath: String,
    internal val pathsToRemove: List<String>,
    internal val deletedIdentity: StorageIdentity?
)

sealed interface DeleteOutcome {
    data class Success(
        val relativePath: String,
        val alreadyAbsent: Boolean = false,
        /** Historical identity, not an assertion that the deleted document still exists. */
        val deletedIdentity: StorageIdentity? = null
    ) : DeleteOutcome

    data class Failed(
        val reason: MutationFailure,
        val cause: Throwable? = null,
        val remaining: StorageIdentity? = null
    ) : DeleteOutcome

    /** A native/recursive delete may have changed storage, or Room cleanup still needs recovery. */
    data class ReconciliationRequired(
        val relativePath: String,
        val lastKnown: StorageIdentity?,
        val remaining: StorageIdentity?,
        val absenceConfirmed: Boolean,
        val cause: Throwable,
        val metadata: DeleteMetadataReconciliation? = null
    ) : DeleteOutcome
}

class DeleteOutcomeException(val outcome: DeleteOutcome) : Exception(
    when (outcome) {
        is DeleteOutcome.Success -> "Deletion succeeded"
        is DeleteOutcome.Failed -> outcome.reason.name
        is DeleteOutcome.ReconciliationRequired -> "Deletion requires storage or metadata reconciliation"
    },
    when (outcome) {
        is DeleteOutcome.Success -> null
        is DeleteOutcome.Failed -> outcome.cause
        is DeleteOutcome.ReconciliationRequired -> outcome.cause
    }
)
