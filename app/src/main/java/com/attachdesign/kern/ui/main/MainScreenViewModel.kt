package com.attachdesign.kern.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.data.storage.VfsNodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val activeQuote: QuoteEntity? = null
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
    private val _activeQuote   = MutableStateFlow<QuoteEntity?>(null)

    val explorerState: StateFlow<ProjectExplorerUiState> = combine(
        db.projectDao().getAllProjectsFlow(),
        _activeProject,
        _currentPath,
        _drillFiles,
        _allItems,
        _isCreateDialogOpen,
        _activeQuote
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ProjectExplorerUiState(
            projects        = args[0] as List<ProjectEntity>,
            activeProject   = args[1] as ProjectEntity?,
            currentPath     = args[2] as String,
            drillFiles      = args[3] as List<VfsNode>,
            allItems        = args[4] as List<FileListItem>,
            isCreateProjectDialogOpen = args[5] as Boolean,
            activeQuote     = args[6] as QuoteEntity?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectExplorerUiState())

    init {
        viewModelScope.launch {
            seedInitialData()
            seedQuotes()
            refreshAllFiles()
            selectRandomQuote()
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
            2. **Cloud Sync**: Cloud sync features are coming soon!
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

        val items = mutableListOf<FileListItem>()
        for (proj in projectList) {
            val diskFiles = storageManager.listDirectory(proj, "")
            val projDbFiles = dbFilesByProject[proj.id] ?: emptyList()
            val enriched = VfsNodeMapper.enrichFiles(diskFiles, projDbFiles)
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
        if (current.isEmpty() || !current.contains('/')) {
            // Already at project root or a root-level folder → back to combined view
            _activeProject.value = null
            _currentPath.value = ""
            viewModelScope.launch { refreshAllFiles() }
        } else {
            val parent = current.substringBeforeLast('/', "")
            _currentPath.value = parent
            val proj = _activeProject.value ?: return
            viewModelScope.launch { loadDrillFiles(proj, parent) }
        }
    }

    fun navigateUpToRoot() {
        _activeProject.value = null
        _currentPath.value = ""
        viewModelScope.launch { refreshAllFiles() }
    }

    fun navigateToFolderRoot(project: ProjectEntity) {
        _activeProject.value = project
        _currentPath.value = ""
        viewModelScope.launch { loadDrillFiles(project, "") }
    }

    fun navigateToSegment(project: ProjectEntity, path: String) {
        _activeProject.value = project
        _currentPath.value = path
        viewModelScope.launch { loadDrillFiles(project, path) }
    }

    fun selectProject(project: ProjectEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.projectDao().deselectAllProjects()
                db.projectDao().updateProject(project.copy(isSelected = true))
            }
            refreshAllFiles()
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

    fun createFile(name: String, targetProject: ProjectEntity? = null) {
        viewModelScope.launch {
            val proj = targetProject ?: _activeProject.value ?: withContext(Dispatchers.IO) { db.projectDao().getSelectedProject() } ?: return@launch
            val fileName = if (name.endsWith(".md")) name else "$name.md"
            val isRootCreation = targetProject != null || _activeProject.value == null
            val relativePath = if (isRootCreation || _currentPath.value.isEmpty()) fileName else "${_currentPath.value}/$fileName"
            storageManager.createFile(proj, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(FileEntity(projectId = proj.id, name = fileName,
                    relativePath = relativePath, isDirectory = false,
                    lastModified = System.currentTimeMillis(),
                    syncState = if (proj.isExternal) "SYNCED" else "PENDING"))
            }
            if (_activeProject.value != null) {
                loadDrillFiles(proj, _currentPath.value)
            } else {
                refreshAllFiles()
            }
        }
    }

    fun createFolder(name: String, targetProject: ProjectEntity? = null) {
        viewModelScope.launch {
            val proj = targetProject ?: _activeProject.value ?: withContext(Dispatchers.IO) { db.projectDao().getSelectedProject() } ?: return@launch
            val isRootCreation = targetProject != null || _activeProject.value == null
            val relativePath = if (isRootCreation || _currentPath.value.isEmpty()) name else "${_currentPath.value}/$name"
            storageManager.createDirectory(proj, relativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().insertFile(FileEntity(projectId = proj.id, name = name,
                    relativePath = relativePath, isDirectory = true,
                    lastModified = System.currentTimeMillis(), syncState = "SYNCED"))
            }
            if (_activeProject.value != null) {
                loadDrillFiles(proj, _currentPath.value)
            } else {
                refreshAllFiles()
            }
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

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.projectDao().deleteProjectById(project.id)
            }
            refreshAllFiles()
        }
    }

    fun renameNode(node: VfsNode, newName: String, project: ProjectEntity) {
        viewModelScope.launch {
            val dir = node.relativePath.substringBeforeLast('/', "")
            val newRelativePath = if (dir.isEmpty()) newName else "$dir/$newName"
            storageManager.renameFile(project, node.relativePath, newRelativePath)
            withContext(Dispatchers.IO) {
                db.fileDao().deleteFile(project.id, node.relativePath)
                db.fileDao().insertFile(
                    com.attachdesign.kern.data.local.FileEntity(
                        projectId = project.id,
                        name = newName,
                        relativePath = newRelativePath,
                        isDirectory = node.isDirectory,
                        lastModified = System.currentTimeMillis(),
                        syncState = "PENDING"
                    )
                )
            }
            val active = _activeProject.value
            if (active != null) loadDrillFiles(active, _currentPath.value)
            else refreshAllFiles()
        }
    }

    fun renameProject(project: ProjectEntity, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.projectDao().updateProject(project.copy(name = newName))
            }
            refreshAllFiles()
        }
    }

    private suspend fun seedQuotes() = withContext(Dispatchers.IO) {
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
        val quotes = withContext(Dispatchers.IO) { db.quoteDao().getAllQuotes() }
        if (quotes.isNotEmpty()) {
            _activeQuote.value = quotes.random()
        }
    }
}
