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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProjectExplorerUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val selectedProject: ProjectEntity? = null,
    val currentPath: String = "",
    val files: List<VfsNode> = emptyList(),
    val isCreateProjectDialogOpen: Boolean = false
)

class MainScreenViewModel(
    private val db: AppDatabase,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _isCreateDialogOpen = MutableStateFlow(false)
    val isCreateDialogOpen: StateFlow<Boolean> = _isCreateDialogOpen.asStateFlow()

    private val _vfsFiles = MutableStateFlow<List<VfsNode>>(emptyList())
    val vfsFiles: StateFlow<List<VfsNode>> = _vfsFiles.asStateFlow()

    val uiState: StateFlow<ProjectExplorerUiState> = combine(
        db.projectDao().getAllProjectsFlow(),
        db.projectDao().getSelectedProjectFlow(),
        _currentPath,
        _vfsFiles,
        _isCreateDialogOpen
    ) { projects, selectedProject, currentPath, files, isCreateOpen ->
        ProjectExplorerUiState(
            projects = projects,
            selectedProject = selectedProject,
            currentPath = currentPath,
            files = files,
            isCreateProjectDialogOpen = isCreateOpen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectExplorerUiState())

    init {
        viewModelScope.launch {
            seedInitialData()
            refreshFileList()
        }
    }

    private suspend fun seedInitialData() = withContext(Dispatchers.IO) {
        val projects = db.projectDao().getSelectedProject()
        if (projects == null) {
            // Seed Sandbox Project
            val sandboxId = db.projectDao().insertProject(
                ProjectEntity(name = "Personal Notes (Sandbox)", path = "personal_notes", isExternal = false, isSelected = true)
            )
            val sandboxProj = db.projectDao().getSelectedProject()!!

            // Seed SAF Project
            db.projectDao().insertProject(
                ProjectEntity(name = "External Documents (SAF)", path = "external_docs", isExternal = true, isSelected = false)
            )

            // Seed welcome files
            val welcomeContent = """
                # Welcome to Kern!
                
                This is a typography-first Markdown editor designed for mobile and foldables.
                
                ## Key Features
                
                1. **Inline-Reveal WYSIWYG**: Tap on this text block to edit. Unfocused blocks render fully!
                2. **Multi-Field Performance**: Instant recomposition and differential updates.
                3. **Cloud Sync**: Select Google Drive or Dropbox in the info drawer to sync sandbox files.
                4. **Hemingway Analyzer**: Check the Metrics panel for Hemingway grade recommendations.
                
                ```kotlin
                fun main() {
                    println("Typing with zero latency!")
                }
                ```
                
                Enjoy writing!
            """.trimIndent()

            storageManager.writeFile(sandboxProj, "Welcome.md", welcomeContent)
            db.fileDao().insertFile(
                FileEntity(
                    projectId = sandboxId,
                    name = "Welcome.md",
                    relativePath = "Welcome.md",
                    isDirectory = false,
                    lastModified = System.currentTimeMillis(),
                    syncState = "PENDING"
                )
            )

            storageManager.createDirectory(sandboxProj, "Work")
            db.fileDao().insertFile(
                FileEntity(
                    projectId = sandboxId,
                    name = "Work",
                    relativePath = "Work",
                    isDirectory = true,
                    lastModified = System.currentTimeMillis(),
                    syncState = "SYNCED"
                )
            )

            storageManager.writeFile(sandboxProj, "Work/Notes.md", "## Meeting Notes\n\n- Discuss project explorer architecture\n- Enforce decoupled module boundaries")
            db.fileDao().insertFile(
                FileEntity(
                    projectId = sandboxId,
                    name = "Notes.md",
                    relativePath = "Work/Notes.md",
                    isDirectory = false,
                    lastModified = System.currentTimeMillis(),
                    syncState = "PENDING"
                )
            )
        }
    }

    fun selectProject(project: ProjectEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.projectDao().deselectAllProjects()
                db.projectDao().updateProject(project.copy(isSelected = true))
            }
            _currentPath.value = ""
            refreshFileList()
        }
    }

    fun navigateToFolder(path: String) {
        _currentPath.value = path
        viewModelScope.launch {
            refreshFileList()
        }
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current.isNotEmpty()) {
            val parent = current.substringBeforeLast('/', "")
            _currentPath.value = parent
            viewModelScope.launch {
                refreshFileList()
            }
        }
    }

    fun setCreateDialogOpen(open: Boolean) {
        _isCreateDialogOpen.value = open
    }

    fun createProject(name: String, isExternal: Boolean) {
        viewModelScope.launch {
            val path = name.lowercase().replace(" ", "_")
            val project = ProjectEntity(
                name = name,
                path = path,
                isExternal = isExternal,
                isSelected = false
            )
            withContext(Dispatchers.IO) {
                db.projectDao().insertProject(project)
            }
        }
    }

    fun createFile(name: String) {
        val selected = uiState.value.selectedProject ?: return
        val ext = if (name.endsWith(".md")) "" else ".md"
        val fileName = "$name$ext"
        val relativePath = if (_currentPath.value.isEmpty()) fileName else "${_currentPath.value}/$fileName"

        viewModelScope.launch {
            storageManager.createFile(selected, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(
                    FileEntity(
                        projectId = selected.id,
                        name = fileName,
                        relativePath = relativePath,
                        isDirectory = false,
                        lastModified = System.currentTimeMillis(),
                        syncState = if (selected.isExternal) "SYNCED" else "PENDING"
                    )
                )
            }
            refreshFileList()
        }
    }

    fun createFolder(name: String) {
        val selected = uiState.value.selectedProject ?: return
        val relativePath = if (_currentPath.value.isEmpty()) name else "${_currentPath.value}/$name"

        viewModelScope.launch {
            storageManager.createDirectory(selected, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(
                    FileEntity(
                        projectId = selected.id,
                        name = name,
                        relativePath = relativePath,
                        isDirectory = true,
                        lastModified = System.currentTimeMillis(),
                        syncState = "SYNCED"
                    )
                )
            }
            refreshFileList()
        }
    }

    fun deleteNode(node: VfsNode) {
        val selected = uiState.value.selectedProject ?: return
        viewModelScope.launch {
            storageManager.deleteFile(selected, node.relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().deleteFile(selected.id, node.relativePath)
            }
            refreshFileList()
        }
    }

    suspend fun refreshFileList() {
        val selected = withContext(Dispatchers.IO) {
            db.projectDao().getSelectedProject()
        }
        if (selected != null) {
            val files = storageManager.listDirectory(selected, _currentPath.value)
            val enrichedFiles = withContext(Dispatchers.IO) {
                val dbFiles = db.fileDao().getFilesForProject(selected.id)
                VfsNodeMapper.enrichFiles(files, dbFiles)
            }
            _vfsFiles.value = enrichedFiles
        } else {
            _vfsFiles.value = emptyList()
        }
    }
}

