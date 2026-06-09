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
    data class FileRow(
        val node: VfsNode,
        val project: ProjectEntity,
        val depth: Int,
        val isExpanded: Boolean
    ) : FileListItem()
}

data class ProjectExplorerUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val allItems: List<FileListItem> = emptyList(),
    val expandedFolders: Set<String> = emptySet(), // Format: "projectId:relativePath"
    val isCreateProjectDialogOpen: Boolean = false
)

class MainScreenViewModel(
    private val db: AppDatabase,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _allItems = MutableStateFlow<List<FileListItem>>(emptyList())
    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    private val _isCreateDialogOpen = MutableStateFlow(false)

    val explorerState: StateFlow<ProjectExplorerUiState> = combine(
        db.projectDao().getAllProjectsFlow(),
        _allItems,
        _expandedFolders,
        _isCreateDialogOpen
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ProjectExplorerUiState(
            projects        = args[0] as List<ProjectEntity>,
            allItems        = args[1] as List<FileListItem>,
            expandedFolders = args[2] as Set<String>,
            isCreateProjectDialogOpen = args[3] as Boolean
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
            ProjectEntity(name = "Notes", path = "notes", isExternal = false, isSelected = true)
        )
        val sandboxProj = db.projectDao().getSelectedProject()!!

        db.projectDao().insertProject(
            ProjectEntity(name = "Documents", path = "documents", isExternal = true, isSelected = false)
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
        val allProjectIds = projectList.map { it.id }
        val allDbFiles = withContext(Dispatchers.IO) { db.fileDao().getFilesForProjects(allProjectIds) }
        val dbFilesByProject = allDbFiles.groupBy { it.projectId }
        val expanded = _expandedFolders.value

        val items = mutableListOf<FileListItem>()
        for (proj in projectList) {
            items += FileListItem.ProjectHeader(proj)
            val projDbFiles = dbFilesByProject[proj.id] ?: emptyList()

            suspend fun buildTree(relativePath: String, depth: Int) {
                val diskFiles = storageManager.listDirectory(proj, relativePath)
                val enriched = VfsNodeMapper.enrichFiles(diskFiles, projDbFiles)
                for (node in enriched) {
                    val folderKey = "\${proj.id}:\${node.relativePath}"
                    val isExpanded = expanded.contains(folderKey)
                    items += FileListItem.FileRow(node, proj, depth, isExpanded)
                    if (node.isDirectory && isExpanded) {
                        buildTree(node.relativePath, depth + 1)
                    }
                }
            }

            buildTree("", 0)
        }
        _allItems.value = items
    }

    // ── Folder drill-down ──────────────────────────────────────────────────────

    fun toggleFolder(node: VfsNode, project: ProjectEntity) {
        if (!node.isDirectory) return
        val folderKey = "\${project.id}:\${node.relativePath}"
        val current = _expandedFolders.value.toMutableSet()
        if (current.contains(folderKey)) {
            // Also remove all sub-folders from expanded set so they collapse
            current.removeAll { it.startsWith("\$folderKey/") }
            current.remove(folderKey)
        } else {
            current.add(folderKey)
        }
        _expandedFolders.value = current
        viewModelScope.launch { refreshAllFiles() }
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

    fun createFile(project: ProjectEntity, parentPath: String, name: String) {
        val fileName = if (name.endsWith(".md")) name else "$name.md"
        val relativePath = if (parentPath.isEmpty()) fileName else "\$parentPath/\$fileName"
        viewModelScope.launch {
            storageManager.createFile(project, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(FileEntity(projectId = project.id, name = fileName,
                    relativePath = relativePath, isDirectory = false,
                    lastModified = System.currentTimeMillis(),
                    syncState = if (project.isExternal) "SYNCED" else "PENDING"))
            }
            refreshAllFiles()
        }
    }

    fun createFolder(project: ProjectEntity, parentPath: String, name: String) {
        val relativePath = if (parentPath.isEmpty()) name else "\$parentPath/\$name"
        viewModelScope.launch {
            storageManager.createDirectory(project, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(FileEntity(projectId = project.id, name = name,
                    relativePath = relativePath, isDirectory = true,
                    lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
            }
            refreshAllFiles()
        }
    }

    fun deleteNode(node: VfsNode, project: ProjectEntity) {
        viewModelScope.launch {
            storageManager.deleteFile(project, node.relativePath)
            withContext(Dispatchers.IO) {
                if (node.isDirectory) {
                    // we need to delete all files inside the directory as well from DB
                    val allFiles = db.fileDao().getFilesForProject(project.id)
                    allFiles.filter { it.relativePath.startsWith(node.relativePath) }.forEach {
                        db.fileDao().deleteFile(project.id, it.relativePath)
                    }
                } else {
                    db.fileDao().deleteFile(project.id, node.relativePath)
                }
            }
            refreshAllFiles()
        }
    }

    fun renameNode(node: VfsNode, project: ProjectEntity, newName: String) {
        viewModelScope.launch {
            val oldPath = node.relativePath
            val parentPath = oldPath.substringBeforeLast('/', "")
            val finalNewName = if (!node.isDirectory && !newName.endsWith(".md")) "$newName.md" else newName
            val newPath = if (parentPath.isEmpty()) finalNewName else "\$parentPath/\$finalNewName"

            // Read content if it's a file, we can just rewrite it and delete the old one,
            // or we could use java.io.File.renameTo but StorageManager doesn't expose it.
            // Let's add rename in StorageManager or just read/write here.
            // A simple implementation without changing StorageManager:
            withContext(Dispatchers.IO) {
                if (!node.isDirectory) {
                    val content = storageManager.readFile(project, oldPath)
                    storageManager.createFile(project, newPath)
                    storageManager.writeFile(project, newPath, content)
                    storageManager.deleteFile(project, oldPath)

                    db.fileDao().deleteFile(project.id, oldPath)
                    db.fileDao().insertFile(FileEntity(projectId = project.id, name = finalNewName,
                        relativePath = newPath, isDirectory = false,
                        lastModified = System.currentTimeMillis(), syncState = if(project.isExternal) "SYNCED" else "PENDING"))
                } else {
                    // Renaming a directory requires moving all contents.
                    // This is more complex, let's just update the db and maybe move it in storage manager.
                    // For now, let's just keep it simple or implement rename in storage manager.
                }
            }

            // Note: need to implement recursive rename or use `java.io.File.renameTo` for directories.
            // Let's modify StorageManager next to support rename.

            refreshAllFiles()
        }
    }

    fun performRenameWithStorageManager(node: VfsNode, project: ProjectEntity, newName: String) {
        viewModelScope.launch {
            val oldPath = node.relativePath
            val parentPath = oldPath.substringBeforeLast('/', "")
            val finalNewName = if (!node.isDirectory && !newName.endsWith(".md")) "$newName.md" else newName
            val newPath = if (parentPath.isEmpty()) finalNewName else "\$parentPath/\$finalNewName"

            if (oldPath == newPath) return@launch

            val success = withContext(Dispatchers.IO) {
                storageManager.renameFile(project, oldPath, newPath)
            }

            if (success) {
                withContext(Dispatchers.IO) {
                    // Update DB
                    if (!node.isDirectory) {
                        db.fileDao().deleteFile(project.id, oldPath)
                        db.fileDao().insertFile(FileEntity(projectId = project.id, name = finalNewName,
                            relativePath = newPath, isDirectory = false,
                            lastModified = System.currentTimeMillis(), syncState = if(project.isExternal) "SYNCED" else "PENDING"))
                    } else {
                        // For directory, we need to update the path of the directory itself
                        db.fileDao().deleteFile(project.id, oldPath)
                        db.fileDao().insertFile(FileEntity(projectId = project.id, name = finalNewName,
                            relativePath = newPath, isDirectory = true,
                            lastModified = System.currentTimeMillis(), syncState = "SYNCED"))

                        // And all its children
                        val allFiles = db.fileDao().getFilesForProject(project.id)
                        val prefix = "\$oldPath/"
                        allFiles.filter { it.relativePath.startsWith(prefix) }.forEach { child ->
                            val newChildPath = newPath + "/" + child.relativePath.removePrefix(prefix)
                            db.fileDao().deleteFile(project.id, child.relativePath)
                            db.fileDao().insertFile(child.copy(id = 0, relativePath = newChildPath, name = newChildPath.substringAfterLast('/')))
                        }
                    }
                }

                // Update expanded folders if a directory was renamed
                if (node.isDirectory) {
                    val oldFolderKey = "\${project.id}:\$oldPath"
                    val newFolderKey = "\${project.id}:\$newPath"
                    val currentExpanded = _expandedFolders.value.toMutableSet()

                    val keysToUpdate = currentExpanded.filter { it == oldFolderKey || it.startsWith("\$oldFolderKey/") }
                    for (key in keysToUpdate) {
                        currentExpanded.remove(key)
                        val newKey = key.replaceFirst(oldFolderKey, newFolderKey)
                        currentExpanded.add(newKey)
                    }
                    _expandedFolders.value = currentExpanded
                }
            }
            refreshAllFiles()
        }
    }
}
