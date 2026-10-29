package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.AppDatabase
import java.util.WeakHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes a project's disk/metadata mutations with the final index reconciliation commit. */
class ProjectMutationCoordinator private constructor() {
    data class ScanToken internal constructor(val projectId: Long, internal val generation: Long)

    private class ProjectState {
        val mutex = Mutex()
        var generation = 0L
    }

    private val projects = mutableMapOf<Long, ProjectState>()

    private fun state(projectId: Long): ProjectState = synchronized(projects) {
        projects.getOrPut(projectId) { ProjectState() }
    }

    suspend fun beginScan(projectId: Long): ScanToken {
        val state = state(projectId)
        return state.mutex.withLock { ScanToken(projectId, ++state.generation) }
    }

    /** A newer scan or any mutation makes the entire collected snapshot stale. */
    suspend fun <T> commitScan(token: ScanToken, commit: () -> T): T? {
        val state = state(token.projectId)
        return state.mutex.withLock {
            if (state.generation != token.generation) null else commit()
        }
    }

    /** Hold this across both disk and Room work. Do not nest it for the same project. */
    suspend fun <T> withMutation(projectId: Long, mutate: suspend () -> T): T {
        val state = state(projectId)
        return state.mutex.withLock {
            state.generation++
            try {
                mutate()
            } finally {
                // Also invalidates requests attempted while an operation was in flight.
                state.generation++
            }
        }
    }

    companion object {
        private val databases = WeakHashMap<AppDatabase, ProjectMutationCoordinator>()

        /** All existing manually-created managers for a database share one gate. */
        fun forDatabase(db: AppDatabase): ProjectMutationCoordinator = synchronized(databases) {
            databases.getOrPut(db) { ProjectMutationCoordinator() }
        }
    }
}
