package com.attachdesign.kern.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

internal val LocalDeletionSession = staticCompositionLocalOf<DeletionSessionViewModel?> { null }

/** The host belongs above routes so leaving a deleted document cannot lose its Undo offer. */
@Composable
internal fun DeletionFeedback(session: DeletionSessionViewModel, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            session.receipts.collect { receipts ->
                val offer = session.claimNextOffer()
                if (offer != null) {
                    var finished = false
                    try {
                        val result = snackbar.showSnackbar(
                            message = "Deleted ${offer.receipt.original.name}",
                            actionLabel = "Undo",
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        session.finishOffer(offer.receipt.id, offer.lease, result == SnackbarResult.ActionPerformed)
                        finished = true
                    } finally {
                        if (!finished) session.releaseOffer(offer.receipt.id, offer.lease)
                    }
                } else {
                    val failure = receipts.firstOrNull { it.phase == DeletionPhase.Failed } ?: return@collect
                    val result = snackbar.showSnackbar(
                        message = "${failure.original.name}: ${failure.error}",
                        actionLabel = if (failure.canRetryRestore) "Retry Undo" else null,
                        withDismissAction = true,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) session.retryRestore(failure.id)
                    else session.dismissError(failure.id)
                }
            }
        }
    }
    SnackbarHost(snackbar, modifier)
}
