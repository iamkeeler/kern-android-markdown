package com.example.modernandroidmarkdowneditor.data.sync

import com.example.modernandroidmarkdowneditor.data.local.FileDao
import com.example.modernandroidmarkdowneditor.data.local.ProjectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    fun setProvider(provider: SyncProvider) {
        _syncStatus.value = _syncStatus.value.copy(provider = provider)
        addLog("Provider changed to: $provider")
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$timestamp] $message"
        _syncLogs.value = listOf(formatted) + _syncLogs.value.take(49)
    }

    fun triggerSync(project: ProjectEntity) {
        val provider = _syncStatus.value.provider
        if (provider == SyncProvider.NONE) {
            addLog("Sync skipped: No provider selected")
            return
        }

        if (project.isExternal) {
            addLog("Sync skipped: Cloud synchronization is disabled on Scoped Storage (SAF) tier")
            return
        }

        scope.launch(Dispatchers.IO) {
            if (_syncStatus.value.state == SyncState.SYNCING) {
                addLog("Sync already in progress, skipping request")
                return@launch
            }

            _syncStatus.value = _syncStatus.value.copy(
                state = SyncState.SYNCING,
                message = "Initiating upload sweep to $provider..."
            )
            addLog("Sync started for project: ${project.name} via $provider")

            try {
                // Fetch cached files from database
                val files = fileDao.getFilesForProject(project.id)
                val pendingFiles = files.filter { it.syncState == "PENDING" && !it.isDirectory }

                if (pendingFiles.isEmpty()) {
                    delay(800)
                    _syncStatus.value = _syncStatus.value.copy(
                        state = SyncState.SUCCESS,
                        message = "Sync complete. All files up to date.",
                        lastSyncTime = System.currentTimeMillis()
                    )
                    addLog("Sync finished: No files require upload")
                    return@launch
                }

                addLog("Found ${pendingFiles.size} pending file(s) to upload")

                for (file in pendingFiles) {
                    addLog("Uploading: ${file.name}...")
                    delay(600) // Simulating network delay

                    val updatedFile = file.copy(syncState = "SYNCED")
                    fileDao.updateFile(updatedFile)
                    addLog("Uploaded successfully: ${file.name}")
                }

                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.SUCCESS,
                    message = "Auto-Sync successful. Uploaded ${pendingFiles.size} file(s).",
                    lastSyncTime = System.currentTimeMillis()
                )
                addLog("Sync sweep completed successfully via $provider")

            } catch (e: Exception) {
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.ERROR,
                    message = "Sync failed: ${e.localizedMessage}"
                )
                addLog("Sync failed: ${e.localizedMessage}")
            }
        }
    }
}
