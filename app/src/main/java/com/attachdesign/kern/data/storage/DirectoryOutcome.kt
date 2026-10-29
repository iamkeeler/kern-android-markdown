package com.attachdesign.kern.data.storage

/** Only Success represents a complete enumeration that callers may use for reconciliation. */
sealed interface DirectoryOutcome {
    data class Success(val nodes: List<VfsNode>) : DirectoryOutcome
    data object AccessDenied : DirectoryOutcome
    data object Missing : DirectoryOutcome
    data class Failure(val cause: Throwable) : DirectoryOutcome
}
