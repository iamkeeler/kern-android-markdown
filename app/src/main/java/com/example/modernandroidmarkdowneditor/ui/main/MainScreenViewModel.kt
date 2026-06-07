package com.example.modernandroidmarkdowneditor.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.modernandroidmarkdowneditor.data.local.AppDatabase
import com.example.modernandroidmarkdowneditor.data.local.FileEntity
import com.example.modernandroidmarkdowneditor.data.local.ProjectEntity
import com.example.modernandroidmarkdowneditor.data.storage.StorageManager
import com.example.modernandroidmarkdowneditor.data.storage.VfsNode
import com.example.modernandroidmarkdowneditor.data.storage.VfsNodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Flat list item for the unified single-scroll file index. */
sealed class FileListItem {
    data class ProjectHeader(val project: ProjectEntity) : FileListItem()
    data class FileRow(val node: VfsNode, val project: ProjectEntity) : FileListItem()
}

data class ProjectExplorerUiState(
    val projects: List<ProjectEntity> = emptyList(),
    /** null = root combined view; non-null = drilled into a project subfolder */
    val activeProject: ProjectEntity? = null,
    val currentPath: String = "",
    val drillFiles: List<VfsNode> = emptyList(),
    val allItems: List<FileListItem> = emptyList(),
    val isCreateProjectDialogOpen: Boolean = false
)

class MainScreenViewModel(
    private val db: AppDatabase,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    private val _currentPath   = MutableStateFlow("")
    private val _drillFiles    = MutableStateFlow<List<VfsNode>>(emptyList())
    private val _allItems      = MutableStateFlow<List<FileListItem>>(emptyList())
    private val _isCreateDialogOpen = MutableStateFlow(false)

    val uiState: StateFlow<ProjectExplorerUiState> = combine(
        db.projectDao().getAllProjectsFlow(),
        _activeProject,
        _currentPath,
        _drillFiles,
        _allItems,
        _isCreateDialogOpen
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ProjectExplorerUiState(
            projects        = args[0] as List<ProjectEntity>,
            activeProject   = args[1] as ProjectEntity?,
            currentPath     = args[2] as String,
            drillFiles      = args[3] as List<VfsNode>,
            allItems        = args[4] as List<FileListItem>,
            isCreateProjectDialogOpen = args[5] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectExplorerUiState())

    init {
        viewModelScope.launch {
            seedInitialData()
            refreshAllFiles()
        }
    }

    // ── Seeding ────────────────────────────────────────────────────────────────

    private suspend fun seedInitialData() = withContext(Dispatchers.IO) {
        if (db.projectDao().getSelectedProject() != null) return@withContext

        val sandboxId = db.projectDao().insertProject(
            ProjectEntity(name = "Personal Notes", path = "personal_notes", isExternal = false, isSelected = true)
        )
        val sandboxProj = db.projectDao().getSelectedProject()!!

        db.projectDao().insertProject(
            ProjectEntity(name = "External Documents", path = "external_docs", isExternal = true, isSelected = false)
        )

        storageManager.writeFile(sandboxProj, "Welcome.md", """
            # Welcome to Kern!
            A typography-first Markdown editor for mobile and foldables.
            ## Features
            1. **Inline-Reveal WYSIWYG**: Tap to edit.
            2. **Cloud Sync**: Set a provider in the info drawer.
            3. **Hemingway Analyzer**: Check Metrics for readability.
        """.trimIndent())
        db.fileDao().insertFile(FileEntity(projectId = sandboxId, name = "Welcome.md",
            relativePath = "Welcome.md", isDirectory = false,
            lastModified = System.currentTimeMillis(), syncState = "PENDING"))

        storageManager.createDirectory(sandboxProj, "Work")
        db.fileDao().insertFile(FileEntity(projectId = sandboxId, name = "Work",
            relativePath = "Work", isDirectory = true,
            lastModified = System.currentTimeMillis(), syncState = "SYNCED"))

        storageManager.writeFile(sandboxProj, "Work/Notes.md", "## Meeting Notes\n\n- Project explorer architecture")
        db.fileDao().insertFile(FileEntity(projectId = sandboxId, name = "Notes.md",
            relativePath = "Work/Notes.md", isDirectory = false,
            lastModified = System.currentTimeMillis(), syncState = "PENDING"))
    }

    // ── Combined root list ─────────────────────────────────────────────────────

    suspend fun refreshAllFiles() {
        val projectList = withContext(Dispatchers.IO) { db.projectDao().getAllProjects() }
        val items = mutableListOf<FileListItem>()
        for (proj in projectList) {
            val diskFiles = storageManager.listDirectory(proj, "")
            val enriched = withContext(Dispatchers.IO) {
                VfsNodeMapper.enrichFiles(diskFiles, db.fileDao().getFilesForProject(proj.id))
            }
            items += FileListItem.ProjectHeader(proj)
            enriched.forEach { items += FileListItem.FileRow(it, proj) }
        }
        _allItems.value = items
    }

    // ── Folder drill-down ──────────────────────────────────────────────────────

    fun navigateToFolder(node: VfsNode, project: ProjectEntity) {
        _activeProject.value = project
        _currentPath.value   = node.relativePath
        viewModelScope.launch { loadDrillFiles(project, node.relativePath) }
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current.isEmpty()) {
            // Already at root of a project → back to combined view
            _activeProject.value = null
            viewModelScope.launch { refreshAllFiles() }
        } else {
            val parent = current.substringBeforeLast('/', "")
            _currentPath.value = parent
            val proj = _activeProject.value ?: return
            viewModelScope.launch { loadDrillFiles(proj, parent) }
        }
    }

    private suspend fun loadDrillFiles(project: ProjectEntity, path: String) {
        val diskFiles = storageManager.listDirectory(project, path)
        _drillFiles.value = withContext(Dispatchers.IO) {
            VfsNodeMapper.enrichFiles(diskFiles, db.fileDao().getFilesForProject(project.id))
        }
    }

    // ── Dialog / create ────────────────────────────────────────────────────────

    fun setCreateDialogOpen(open: Boolean) { _isCreateDialogOpen.value = open }

    fun createProject(name: String, isExternal: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.projectDao().insertProject(
                    ProjectEntity(name = name,
                        path = name.lowercase().replace(" ", "_"),
                        isExternal = isExternal,
                        isSelected = false)
                )
            }
            refreshAllFiles()
        }
    }

    fun createFile(name: String) {
        val proj = _activeProject.value ?: return
        val fileName = if (name.endsWith(".md")) name else "$name.md"
        val relativePath = if (_currentPath.value.isEmpty()) fileName else "${_currentPath.value}/$fileName"
        viewModelScope.launch {
            storageManager.createFile(proj, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(FileEntity(projectId = proj.id, name = fileName,
                    relativePath = relativePath, isDirectory = false,
                    lastModified = System.currentTimeMillis(),
                    syncState = if (proj.isExternal) "SYNCED" else "PENDING"))
            }
            loadDrillFiles(proj, _currentPath.value)
        }
    }

    fun createFolder(name: String) {
        val proj = _activeProject.value ?: return
        val relativePath = if (_currentPath.value.isEmpty()) name else "${_currentPath.value}/$name"
        viewModelScope.launch {
            storageManager.createDirectory(proj, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(FileEntity(projectId = proj.id, name = name,
                    relativePath = relativePath, isDirectory = true,
                    lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
            }
            loadDrillFiles(proj, _currentPath.value)
        }
    }

    fun deleteNode(node: VfsNode, project: ProjectEntity) {
        viewModelScope.launch {
            storageManager.deleteFile(project, node.relativePath)
            withContext(Dispatchers.IO) { db.fileDao().deleteFile(project.id, node.relativePath) }
            val active = _activeProject.value
            if (active != null) loadDrillFiles(active, _currentPath.value)
            else refreshAllFiles()
        }
    }
}
