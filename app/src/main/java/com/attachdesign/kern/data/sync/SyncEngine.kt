package com.attachdesign.kern.data.sync

import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncProvider {
    NONE, GOOGLE_DRIVE, DROPBOX, ONEDRIVE
}

enum class SyncState {
    IDLE, SYNCING, SUCCESS, ERROR
}

data class SyncStatus(
    val state: SyncState = SyncState.IDLE,
    val provider: SyncProvider = SyncProvider.NONE,
    val message: String = "Sync Idle",
    val lastSyncTime: Long = 0L
)

class SyncEngine(
    private val fileDao: FileDao,
    private val scope: CoroutineScope
) {
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    /**
     * Guards the upload sweep (M13): the mutex is acquired before the sweep is
     * launched and released when it finishes, so two rapid triggerSync calls
     * cannot both run the sweep.
     */
    private val syncMutex = Mutex()

    fun setProvider(provider: SyncProvider) {
        _syncStatus.update { it.copy(provider = provider) }
        addLog("Provider changed to: $provider")
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$timestamp] $message"
        _syncLogs.update { listOf(formatted) + it.take(49) }
    }

    fun triggerSync(project: ProjectEntity) {
        // M13: guard the sweep before launching; a second caller skips instead of
        // racing the first through the in-coroutine state check.
        if (!syncMutex.tryLock()) {
            addLog("Sync already in progress, skipping request")
            return
        }

        val provider = _syncStatus.value.provider
        if (provider == SyncProvider.NONE) {
            syncMutex.unlock()
            // M13: never mark files SYNCED without a real provider round-trip.
            // Files stay PENDING; the status says sync is not configured.
            _syncStatus.update {
                it.copy(
                    state = SyncState.IDLE,
                    message = "Sync not configured: no provider selected"
                )
            }
            addLog("Sync skipped: No provider selected")
            return
        }

        if (project.isExternal) {
            syncMutex.unlock()
            _syncStatus.update {
                it.copy(
                    state = SyncState.IDLE,
                    message = "Sync not configured for Scoped Storage (SAF) projects"
                )
            }
            addLog("Sync skipped: Cloud synchronization is disabled on Scoped Storage (SAF) tier")
            return
        }

        try {
            scope.launch(Dispatchers.IO) {
                try {
                    _syncStatus.update {
                        it.copy(
                            state = SyncState.SYNCING,
                            message = "Initiating upload sweep to $provider..."
                        )
                    }
                    addLog("Sync started for project: ${project.name} via $provider")

                    // Fetch cached files from database
                    val files = fileDao.getFilesForProject(project.id)
                    val pendingFiles = files.filter { it.syncState == "PENDING" && !it.isDirectory }

                    if (pendingFiles.isEmpty()) {
                        delay(800)
                        _syncStatus.update {
                            it.copy(
                                state = SyncState.SUCCESS,
                                message = "Sync complete. All files up to date.",
                                lastSyncTime = System.currentTimeMillis()
                            )
                        }
                        addLog("Sync finished: No files require upload")
                        return@launch
                    }

                    // M13: there is no real provider round-trip yet. Do NOT fake
                    // SYNCED — leave files PENDING and say so explicitly.
                    addLog("Sync unavailable: no provider implementation for $provider; ${pendingFiles.size} file(s) left PENDING")
                    _syncStatus.update {
                        it.copy(
                            state = SyncState.IDLE,
                            message = "Sync not configured: no provider implementation for $provider. ${pendingFiles.size} file(s) pending."
                        )
                    }
                } catch (e: Exception) {
                    _syncStatus.update {
                        it.copy(
                            state = SyncState.ERROR,
                            message = "Sync failed: ${e.localizedMessage}"
                        )
                    }
                    addLog("Sync failed: ${e.localizedMessage}")
                } finally {
                    syncMutex.unlock()
                }
            }
        } catch (e: Exception) {
            syncMutex.unlock()
            throw e
        }
    }
}
