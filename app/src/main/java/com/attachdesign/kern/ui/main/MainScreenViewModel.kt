package com.attachdesign.kern.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.flow.asSharedFlow
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.data.storage.VfsNodeMapper
import com.attachdesign.kern.data.storage.FileOperationsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.attachdesign.kern.ui.common.SnackbarEvent
import com.attachdesign.kern.data.local.QuoteEntity

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
    val isCreateProjectDialogOpen: Boolean = false,
    val activeQuote: QuoteEntity? = null,
    val isLoading: Boolean = true,
    val canNavigateBack: Boolean = false,
    val canNavigateForward: Boolean = false,
    val showSplash: Boolean = true
)

class MainScreenViewModel(
    private val db: AppDatabase,
    private val storageManager: StorageManager,
    private val fileOpsManager: FileOperationsManager,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    companion object {
        private var hasSplashRun = false
    }

    private val _uiState = MutableStateFlow(
        ProjectExplorerUiState(
            showSplash = !hasSplashRun,
            isLoading = true
        )
    )

    private val _snackbarEvent = kotlinx.coroutines.flow.MutableSharedFlow<SnackbarEvent>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    private val backStack = mutableListOf<Pair<ProjectEntity?, String>>()
    private val forwardStack = mutableListOf<Pair<ProjectEntity?, String>>()

    val explorerState: StateFlow<ProjectExplorerUiState> = combine(
        db.projectDao().getAllProjectsFlow(),
        _uiState
    ) { projects, state ->
        state.copy(projects = projects)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _uiState.value)

    fun dismissSplash() {
        hasSplashRun = true
        _uiState.update { it.copy(showSplash = false) }
    }

    init {
        viewModelScope.launch {
            seedInitialData()
            seedQuotes()
            refreshAllFiles()
            selectRandomQuote()
            val selected = withContext(ioDispatcher) { db.projectDao().getSelectedProject() }
            if (selected != null) {
                _uiState.update { it.copy(activeProject = selected) }
                if (selected.isExternal) {
                    scanProject(selected)
                } else {
                    loadDrillFiles(selected, "")
                }
            } else {
                val defaultProj = withContext(ioDispatcher) { db.projectDao().getAllProjects().find { !it.isExternal && it.path == "root" } }
                if (defaultProj != null) {
                    _uiState.update { it.copy(activeProject = defaultProj) }
                    loadDrillFiles(defaultProj, "")
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ── Seeding ────────────────────────────────────────────────────────────────

    private suspend fun seedInitialData() = withContext(ioDispatcher) {
        // Clean up legacy "Documents" project if it exists
        db.projectDao().getAllProjects().find { it.name == "Documents" || it.path == "documents" }?.let { legacyProj ->
            db.projectDao().deleteProjectById(legacyProj.id)
            db.fileDao().deleteFilesForProject(legacyProj.id)
        }

        // Migration logic for old default workspace names
        db.projectDao().getAllProjects().forEach { proj ->
            if (!proj.isExternal && (proj.path == "notes" || proj.path == "root")) {
                if (proj.path == "notes") {
                    try {
                        val oldDir = storageManager.getAbsoluteFile(proj, "")
                        val newDir = java.io.File(oldDir.parentFile, "root")
                        if (oldDir.exists() && oldDir.isDirectory) {
                            if (newDir.exists()) {
                                oldDir.copyRecursively(newDir, overwrite = true)
                                oldDir.deleteRecursively()
                            } else {
                                oldDir.renameTo(newDir)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                db.projectDao().updateProject(proj.copy(name = "Files", path = "root"))
            }
        }

        val allProjects = db.projectDao().getAllProjects()
        if (allProjects.isEmpty()) {
            val sandboxId = db.projectDao().insertProject(
                ProjectEntity(name = "Files", path = "root", isExternal = false, isSelected = true)
            )
            val sandboxProj = ProjectEntity(id = sandboxId, name = "Files", path = "root", isExternal = false, isSelected = true)

            // A Play/Firebase reinstall can restore the workspace files without restoring the
            // Room database. Rebuild the project index before seeding defaults so existing user
            // documents remain visible and editable.
            val existingRootItems = storageManager.listDirectory(sandboxProj, "")
            if (existingRootItems.isNotEmpty()) {
                indexRecoveredWorkspace(sandboxProj, existingRootItems)
            }

            try {
                if (!storageManager.fileExists(sandboxProj, "Welcome.md")) {
                    val welcomeContent = try {
                        storageManager.readAssetFile("Welcome.md")
                    } catch (e: Exception) {
                        ""
                    }
                    storageManager.writeFile(sandboxProj, "Welcome.md", welcomeContent)
                    db.fileDao().insertFile(FileEntity(projectId = sandboxId, name = "Welcome.md",
                        relativePath = "Welcome.md", isDirectory = false,
                        lastModified = System.currentTimeMillis(), syncState = "PENDING"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                if (!storageManager.fileExists(sandboxProj, "Formatting Examples.md")) {
                    val examplesContent = try {
                        storageManager.readAssetFile("Formatting Examples.md")
                    } catch (e: Exception) {
                        ""
                    }
                    storageManager.writeFile(sandboxProj, "Formatting Examples.md", examplesContent)
                    db.fileDao().insertFile(FileEntity(projectId = sandboxId, name = "Formatting Examples.md",
                        relativePath = "Formatting Examples.md", isDirectory = false,
                        lastModified = System.currentTimeMillis(), syncState = "PENDING"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                if (!storageManager.fileExists(sandboxProj, "Notes")) {
                    storageManager.createDirectory(sandboxProj, "Notes")
                    db.fileDao().insertFile(FileEntity(projectId = sandboxId, name = "Notes",
                        relativePath = "Notes", isDirectory = true,
                        lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Ensure Welcome.md and Notes exist in the default sandbox project (handles Auto Backup sync issues)
            val defaultProj = allProjects.find { !it.isExternal && it.path == "root" }
            if (defaultProj != null) {
                try {
                    val welcomeFile = db.fileDao().getFileByPath(defaultProj.id, "Welcome.md")
                    val diskFiles = storageManager.listDirectory(defaultProj, "")
                    val welcomeOnDisk = diskFiles.any { it.name == "Welcome.md" && !it.isDirectory }
                    if (welcomeFile == null || !welcomeOnDisk) {
                        val welcomeContent = try {
                            storageManager.readAssetFile("Welcome.md")
                        } catch (e: Exception) {
                            ""
                        }
                        storageManager.writeFile(defaultProj, "Welcome.md", welcomeContent)
                        if (welcomeFile == null) {
                            db.fileDao().insertFile(FileEntity(projectId = defaultProj.id, name = "Welcome.md",
                                relativePath = "Welcome.md", isDirectory = false,
                                lastModified = System.currentTimeMillis(), syncState = "PENDING"))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    val examplesFile = db.fileDao().getFileByPath(defaultProj.id, "Formatting Examples.md")
                    val diskFiles = storageManager.listDirectory(defaultProj, "")
                    val examplesOnDisk = diskFiles.any { it.name == "Formatting Examples.md" && !it.isDirectory }
                    if (examplesFile == null || !examplesOnDisk) {
                        val examplesContent = try {
                            storageManager.readAssetFile("Formatting Examples.md")
                        } catch (e: Exception) {
                            ""
                        }
                        storageManager.writeFile(defaultProj, "Formatting Examples.md", examplesContent)
                        if (examplesFile == null) {
                            db.fileDao().insertFile(FileEntity(projectId = defaultProj.id, name = "Formatting Examples.md",
                                relativePath = "Formatting Examples.md", isDirectory = false,
                                lastModified = System.currentTimeMillis(), syncState = "PENDING"))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    val notesFolder = db.fileDao().getFileByPath(defaultProj.id, "Notes")
                    val diskFiles = storageManager.listDirectory(defaultProj, "")
                    val notesOnDisk = diskFiles.any { it.name == "Notes" && it.isDirectory }
                    if (notesFolder == null || !notesOnDisk) {
                        storageManager.createDirectory(defaultProj, "Notes")
                        if (notesFolder == null) {
                            db.fileDao().insertFile(FileEntity(projectId = defaultProj.id, name = "Notes",
                                relativePath = "Notes", isDirectory = true,
                                lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // If there's no selected project now, select the first available one
            if (db.projectDao().getSelectedProject() == null) {
                db.projectDao().updateProject(allProjects.first().copy(isSelected = true))
            }
        }
    }

    private suspend fun indexRecoveredWorkspace(project: ProjectEntity, nodes: List<VfsNode>) {
        nodes.forEach { node ->
            db.fileDao().insertFile(
                FileEntity(
                    projectId = project.id,
                    name = node.name,
                    relativePath = node.relativePath,
                    isDirectory = node.isDirectory,
                    lastModified = (node as? VfsNode.File)?.lastModified ?: System.currentTimeMillis(),
                    syncState = "SYNCED"
                )
            )
            if (node is VfsNode.Directory) {
                indexRecoveredWorkspace(project, storageManager.listDirectory(project, node.relativePath))
            }
        }
    }

    // ── Combined root list ─────────────────────────────────────────────────────

    suspend fun refreshAllFiles() {
        val projectList = withContext(ioDispatcher) { db.projectDao().getAllProjects() }

        val allProjectIds = projectList.map { it.id }
        val allDbFiles = withContext(ioDispatcher) { db.fileDao().getFilesForProjects(allProjectIds) }
        val dbFilesByProject = allDbFiles.groupBy { it.projectId }

        val items = mutableListOf<FileListItem>()
        for (proj in projectList) {
            val diskFiles = storageManager.listDirectory(proj, "")
            val projDbFiles = dbFilesByProject[proj.id] ?: emptyList()
            val enriched = VfsNodeMapper.enrichFiles(diskFiles, projDbFiles)
            items += FileListItem.ProjectHeader(proj)
            enriched.forEach { items += FileListItem.FileRow(it, proj) }
        }
        _uiState.update { it.copy(allItems = items) }
    }

    // ── Folder drill-down ──────────────────────────────────────────────────────

    private fun changeDestination(project: ProjectEntity?, path: String, saveToBackStack: Boolean = true, clearForward: Boolean = true) {
        val currentState = _uiState.value
        val prevProj = currentState.activeProject
        val prevPath = currentState.currentPath

        if (saveToBackStack) {
            val prevDest = Pair(prevProj, prevPath)
            if (backStack.isEmpty() || backStack.last() != prevDest) {
                backStack.add(prevDest)
            }
        }
        if (clearForward) {
            forwardStack.clear()
        }

        _uiState.update {
            it.copy(
                activeProject = project,
                currentPath = path,
                canNavigateBack = backStack.isNotEmpty(),
                canNavigateForward = forwardStack.isNotEmpty()
            )
        }

        viewModelScope.launch {
            if (project == null) {
                refreshAllFiles()
            } else {
                if (path.isEmpty() && project.isExternal && saveToBackStack) {
                    scanProject(project)
                } else {
                    loadDrillFiles(project, path)
                }
            }
        }
    }

    fun navigateBack() {
        if (backStack.isEmpty()) return
        val currentDest = Pair(_uiState.value.activeProject, _uiState.value.currentPath)
        forwardStack.add(currentDest)

        val prevDest = backStack.removeAt(backStack.lastIndex)
        changeDestination(prevDest.first, prevDest.second, saveToBackStack = false, clearForward = false)
    }

    fun navigateForward() {
        if (forwardStack.isEmpty()) return
        val currentDest = Pair(_uiState.value.activeProject, _uiState.value.currentPath)
        backStack.add(currentDest)

        val nextDest = forwardStack.removeAt(forwardStack.lastIndex)
        changeDestination(nextDest.first, nextDest.second, saveToBackStack = false, clearForward = false)
    }

    fun navigateToFolder(node: VfsNode, project: ProjectEntity) {
        changeDestination(project, node.relativePath)
    }

    fun navigateUp() {
        viewModelScope.launch {
            val current = _uiState.value.currentPath
            val proj = _uiState.value.activeProject
            if (proj == null || (proj.isExternal && current.isEmpty())) {
                val defaultProj = withContext(ioDispatcher) {
                    db.projectDao().getAllProjects().find { !it.isExternal && it.path == "root" }
                }
                changeDestination(defaultProj, "")
            } else if (current.isEmpty() || !current.contains('/')) {
                changeDestination(proj, "")
            } else {
                val parent = current.substringBeforeLast('/', "")
                changeDestination(proj, parent)
            }
        }
    }

    fun navigateUpToRoot() {
        viewModelScope.launch {
            val defaultProj = withContext(ioDispatcher) {
                db.projectDao().getAllProjects().find { !it.isExternal && it.path == "root" }
            }
            changeDestination(defaultProj, "")
        }
    }

    fun navigateToFolderRoot(project: ProjectEntity) {
        changeDestination(project, "")
    }

    fun navigateToSegment(project: ProjectEntity, path: String) {
        changeDestination(project, path)
    }

    fun selectProject(project: ProjectEntity) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                db.projectDao().deselectAllProjects()
                db.projectDao().updateProject(project.copy(isSelected = true))
            }
            changeDestination(project, "")
        }
    }

    private suspend fun loadDrillFiles(project: ProjectEntity, path: String) {
        val diskFiles = storageManager.listDirectory(project, path)
        var enriched = withContext(ioDispatcher) {
            VfsNodeMapper.enrichFiles(diskFiles, db.fileDao().getFilesForProject(project.id))
        }

        // If we are at the root level of the default sandbox project, append external projects as directories
        if (!project.isExternal && project.path == "root" && path.isEmpty()) {
            val allProjects = withContext(ioDispatcher) { db.projectDao().getAllProjects() }
            val externalDirs = allProjects.filter { it.isExternal }.map { extProj ->
                VfsNode.Directory(name = extProj.name, relativePath = "external:${extProj.id}")
            }
            enriched = externalDirs + enriched
        }

        _uiState.update { it.copy(drillFiles = enriched) }
    }

    fun scanProject(project: ProjectEntity) {
        viewModelScope.launch {
            val foundPaths = mutableSetOf<String>()
            scanProjectFilesRecursively(project, "", foundPaths)
            
            // Prune files in DB that no longer exist on disk
            withContext(ioDispatcher) {
                val dbFiles = db.fileDao().getFilesForProject(project.id)
                for (dbFile in dbFiles) {
                    if (!foundPaths.contains(dbFile.relativePath)) {
                        db.fileDao().deleteFile(project.id, dbFile.relativePath)
                    }
                }
            }
            
            refreshAllFiles()
            val active = _uiState.value.activeProject
            if (active != null && active.id == project.id) {
                loadDrillFiles(active, _uiState.value.currentPath)
            }
        }
    }

    private suspend fun scanProjectFilesRecursively(project: ProjectEntity, relativePath: String, foundPaths: MutableSet<String>) {
        val diskFiles = storageManager.listDirectory(project, relativePath)
        for (node in diskFiles) {
            foundPaths.add(node.relativePath)
            val dbFile = withContext(ioDispatcher) { db.fileDao().getFileByPath(project.id, node.relativePath) }
            if (dbFile == null) {
                withContext(ioDispatcher) {
                    db.fileDao().insertFile(
                        FileEntity(
                            projectId = project.id,
                            name = node.name,
                            relativePath = node.relativePath,
                            isDirectory = node.isDirectory,
                            lastModified = if (node is VfsNode.File) node.lastModified else System.currentTimeMillis(),
                            syncState = "SYNCED"
                        )
                    )
                }
            }
            if (node.isDirectory) {
                scanProjectFilesRecursively(project, node.relativePath, foundPaths)
            }
        }
    }

    // ── Dialog / create ────────────────────────────────────────────────────────

    fun setCreateDialogOpen(open: Boolean) { _uiState.update { it.copy(isCreateProjectDialogOpen = open) } }

    fun createProject(name: String, isExternal: Boolean, path: String? = null) {
        viewModelScope.launch {
            val projectPath = path ?: name.lowercase().replace(" ", "_")
            val id = withContext(ioDispatcher) {
                db.projectDao().insertProject(
                    ProjectEntity(
                        name = name,
                        path = projectPath,
                        isExternal = isExternal,
                        isSelected = false
                    )
                )
            }
            val insertedProject = withContext(ioDispatcher) {
                db.projectDao().getProjectById(id)
            }
            if (insertedProject != null && isExternal) {
                scanProject(insertedProject)
            }
            val defaultProj = withContext(ioDispatcher) { db.projectDao().getAllProjects().find { !it.isExternal && it.path == "root" } }
            if (defaultProj != null) {
                loadDrillFiles(defaultProj, "")
            }
        }
    }

    fun createFile(name: String, targetProject: ProjectEntity? = null, onCreated: ((String) -> Unit)? = null) {
        val currentState = _uiState.value
        val destinationProject = targetProject ?: currentState.activeProject
        val destinationPath = currentState.currentPath
        val isDrillDown = currentState.activeProject != null
        viewModelScope.launch {
            val proj = destinationProject ?: withContext(ioDispatcher) { db.projectDao().getSelectedProject() } ?: return@launch
            val fileName = if (name.endsWith(".md")) name else "$name.md"
            val relativePath = if (destinationPath.isEmpty()) fileName else "$destinationPath/$fileName"
            val created = storageManager.createFile(proj, relativePath)
            if (!created) {
                return@launch
            }
            withContext(ioDispatcher) {
                db.fileDao().insertFile(FileEntity(projectId = proj.id, name = fileName,
                    relativePath = relativePath, isDirectory = false,
                    lastModified = System.currentTimeMillis(),
                    syncState = if (proj.isExternal) "SYNCED" else "PENDING"))
            }
            if (isDrillDown) {
                loadDrillFiles(proj, destinationPath)
            } else {
                refreshAllFiles()
            }
            onCreated?.invoke(relativePath)
        }
    }

    fun createFolder(name: String, targetProject: ProjectEntity? = null) {
        val currentState = _uiState.value
        val destinationProject = targetProject ?: currentState.activeProject
        val destinationPath = currentState.currentPath
        val isDrillDown = currentState.activeProject != null
        viewModelScope.launch {
            val proj = destinationProject ?: withContext(ioDispatcher) { db.projectDao().getSelectedProject() } ?: return@launch
            val relativePath = if (destinationPath.isEmpty()) name else "$destinationPath/$name"
            storageManager.createDirectory(proj, relativePath)
            withContext(ioDispatcher) {
                db.fileDao().insertFile(FileEntity(projectId = proj.id, name = name,
                    relativePath = relativePath, isDirectory = true,
                    lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
            }
            if (isDrillDown) {
                loadDrillFiles(proj, destinationPath)
            } else {
                refreshAllFiles()
            }
        }
    }

    fun deleteNode(node: VfsNode, project: ProjectEntity) {
        viewModelScope.launch {
            val result = fileOpsManager.moveToTrash(project, node)
            val trashNode = result.getOrNull() ?: return@launch
            
            val active = _uiState.value.activeProject
            if (active != null) loadDrillFiles(active, _uiState.value.currentPath)
            else refreshAllFiles()

            val deleteJob = viewModelScope.launch {
                kotlinx.coroutines.delay(5000)
                fileOpsManager.deleteNode(project, trashNode)
            }

            _snackbarEvent.emit(SnackbarEvent("File deleted", "Undo") {
                deleteJob.cancel()
                viewModelScope.launch {
                    fileOpsManager.restoreFromTrash(project, trashNode, node.relativePath)
                    val act = _uiState.value.activeProject
                    if (act != null) loadDrillFiles(act, _uiState.value.currentPath)
                    else refreshAllFiles()
                }
            })
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                db.projectDao().deleteProjectById(project.id)
                db.fileDao().deleteFilesForProject(project.id)
            }
            val defaultProj = withContext(ioDispatcher) { db.projectDao().getAllProjects().find { !it.isExternal && it.path == "root" } }
            if (defaultProj != null) {
                loadDrillFiles(defaultProj, "")
            }
        }
    }

    fun renameNode(node: VfsNode, newName: String, project: ProjectEntity) {
        viewModelScope.launch {
            fileOpsManager.renameNode(project, node, newName)
            val active = _uiState.value.activeProject
            if (active != null) loadDrillFiles(active, _uiState.value.currentPath)
            else refreshAllFiles()
        }
    }

    fun duplicateNode(node: VfsNode, project: ProjectEntity) {
        viewModelScope.launch {
            fileOpsManager.duplicateNode(project, node)
            val active = _uiState.value.activeProject
            if (active != null) loadDrillFiles(active, _uiState.value.currentPath)
            else refreshAllFiles()
        }
    }

    fun renameProject(project: ProjectEntity, newName: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                db.projectDao().updateProject(project.copy(name = newName))
            }
            val defaultProj = withContext(ioDispatcher) { db.projectDao().getAllProjects().find { !it.isExternal && it.path == "root" } }
            if (defaultProj != null) {
                loadDrillFiles(defaultProj, "")
            }
        }
    }

    fun moveNode(fromNode: VfsNode, toDir: VfsNode, project: ProjectEntity) {
        viewModelScope.launch {
            val fromPath = fromNode.relativePath
            val toPath = if (toDir.relativePath.isEmpty()) fromNode.name else "${toDir.relativePath}/${fromNode.name}"
            val success = storageManager.moveNode(project, fromPath, project, toPath)
            if (success) {
                withContext(ioDispatcher) {
                    db.fileDao().deleteFile(project.id, fromPath)
                    db.fileDao().insertFile(
                        com.attachdesign.kern.data.local.FileEntity(
                            projectId = project.id,
                            name = fromNode.name,
                            relativePath = toPath,
                            isDirectory = fromNode.isDirectory,
                            lastModified = System.currentTimeMillis(),
                            syncState = "PENDING"
                        )
                    )
                }
            }
            val active = _uiState.value.activeProject
            if (active != null) loadDrillFiles(active, _uiState.value.currentPath)
            else refreshAllFiles()
        }
    }

    fun moveNodeUp(fromNode: VfsNode, project: ProjectEntity) {
        viewModelScope.launch {
            val fromPath = fromNode.relativePath
            val parts = fromPath.split('/')
            if (parts.size > 1) {
                val newPath = if (parts.size <= 2) {
                    parts.last()
                } else {
                    parts.dropLast(2).joinToString("/") + "/" + parts.last()
                }
                val success = storageManager.moveNode(project, fromPath, project, newPath)
                if (success) {
                    withContext(ioDispatcher) {
                        db.fileDao().deleteFile(project.id, fromPath)
                        db.fileDao().insertFile(
                            com.attachdesign.kern.data.local.FileEntity(
                                projectId = project.id,
                                name = fromNode.name,
                                relativePath = newPath,
                                isDirectory = fromNode.isDirectory,
                                lastModified = System.currentTimeMillis(),
                                syncState = "PENDING"
                            )
                        )
                    }
                }
            }
            val active = _uiState.value.activeProject
            if (active != null) loadDrillFiles(active, _uiState.value.currentPath)
            else refreshAllFiles()
        }
    }

    private suspend fun seedQuotes() = withContext(ioDispatcher) {
        if (db.quoteDao().getCount() == 0) {
            val quotes = listOf(
                QuoteEntity(text = "There is nothing to writing. All you do is sit down at a typewriter and bleed.", author = "Ernest Hemingway", year = 1949),
                QuoteEntity(text = "The first draft of anything is shit.", author = "Ernest Hemingway", year = 1926),
                QuoteEntity(text = "Substitute 'damn' every time you're inclined to write 'very'; your editor will delete it and the writing will be just as it should be.", author = "Mark Twain", year = 1880),
                QuoteEntity(text = "Writing is a prescription for those who have trouble reading the world.", author = "Mary Oliver", year = 1992),
                QuoteEntity(text = "If a writer knows enough about what he is writing about he may omit things that he knows and the reader... will feel those things as strongly as though the writer had stated them.", author = "Ernest Hemingway", year = 1932),
                QuoteEntity(text = "We are all apprentices in a craft where no one ever becomes a master.", author = "Ernest Hemingway", year = 1934),
                QuoteEntity(text = "The road to hell is paved with adverbs.", author = "Stephen King", year = 2000),
                QuoteEntity(text = "If you don't have time to read, you don't have the time (or the tools) to write.", author = "Stephen King", year = 2000),
                QuoteEntity(text = "Write with the door closed, rewrite with the door open.", author = "Stephen King", year = 2000),
                QuoteEntity(text = "A professional writer is an amateur who didn't quit.", author = "Richard Bach", year = 1979),
                QuoteEntity(text = "You can fix anything but a blank page.", author = "Nora Roberts", year = 2006),
                QuoteEntity(text = "You don’t start out writing good stuff. You start out writing crap and thinking it’s good stuff, and then gradually you get better at it.", author = "Octavia E. Butler", year = 1989),
                QuoteEntity(text = "Easy reading is damn hard writing.", author = "Nathaniel Hawthorne", year = 1851),
                QuoteEntity(text = "We write to taste life twice, in the moment and in retrospect.", author = "Anaïs Nin", year = 1974),
                QuoteEntity(text = "Every secret of a writer’s soul, every experience of his life, every quality of his mind, is written large in his works.", author = "Virginia Woolf", year = 1940),
                QuoteEntity(text = "A word after a word after a word is power.", author = "Margaret Atwood", year = 1981),
                QuoteEntity(text = "Writing, to me, is simply thinking through my fingers.", author = "Isaac Asimov", year = 1980),
                QuoteEntity(text = "Don't tell me the moon is shining; show me the glint of light on broken glass.", author = "Anton Chekhov", year = 1886),
                QuoteEntity(text = "I love deadlines. I love the whooshing noise they make as they go by.", author = "Douglas Adams", year = 1985),
                QuoteEntity(text = "The business of burning books is less dangerous than the business of writing them.", author = "Walter Benjamin", year = 1928),
                QuoteEntity(text = "Fill your paper with the breathings of your heart.", author = "William Wordsworth", year = 1798),
                QuoteEntity(text = "There is no greater agony than bearing an untold story inside you.", author = "Maya Angelou", year = 1969),
                QuoteEntity(text = "You can always edit a bad page. You can't edit a blank page.", author = "Jodi Picoult", year = 2011),
                QuoteEntity(text = "The first draft is just you telling yourself the story.", author = "Terry Pratchett", year = 1999),
                QuoteEntity(text = "If there's a book that you want to read, but it hasn't been written yet, then you must write it.", author = "Toni Morrison", year = 1970)
            )
            db.quoteDao().insertQuotes(quotes)
        }
    }

    private suspend fun selectRandomQuote() {
        val quotes = withContext(ioDispatcher) { db.quoteDao().getAllQuotes() }
        if (quotes.isNotEmpty()) {
            _uiState.update { it.copy(activeQuote = quotes.random()) }
        }
    }
}
