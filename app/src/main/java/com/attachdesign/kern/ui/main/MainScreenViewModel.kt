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
    val activeQuote: QuoteEntity? = null,
    val isLoading: Boolean = true
)

class MainScreenViewModel(
    private val db: AppDatabase,
    private val storageManager: StorageManager,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    private val _currentPath   = MutableStateFlow("")
    private val _drillFiles    = MutableStateFlow<List<VfsNode>>(emptyList())
    private val _allItems      = MutableStateFlow<List<FileListItem>>(emptyList())
    private val _isCreateDialogOpen = MutableStateFlow(false)
    private val _activeQuote   = MutableStateFlow<QuoteEntity?>(null)
    private val _isLoading     = MutableStateFlow(true)

    val explorerState: StateFlow<ProjectExplorerUiState> = combine(
        db.projectDao().getAllProjectsFlow(),
        _activeProject,
        _currentPath,
        _drillFiles,
        _allItems,
        _isCreateDialogOpen,
        _activeQuote,
        _isLoading
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ProjectExplorerUiState(
            projects        = args[0] as List<ProjectEntity>,
            activeProject   = args[1] as ProjectEntity?,
            currentPath     = args[2] as String,
            drillFiles      = args[3] as List<VfsNode>,
            allItems        = args[4] as List<FileListItem>,
            isCreateProjectDialogOpen = args[5] as Boolean,
            activeQuote     = args[6] as QuoteEntity?,
            isLoading       = args[7] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectExplorerUiState())

    init {
        viewModelScope.launch {
            seedInitialData()
            seedQuotes()
            refreshAllFiles()
            selectRandomQuote()
            val selected = withContext(ioDispatcher) { db.projectDao().getSelectedProject() }
            if (selected != null) {
                _activeProject.value = selected
                if (selected.isExternal) {
                    scanProject(selected)
                } else {
                    loadDrillFiles(selected, "")
                }
            }
            _isLoading.value = false
        }
    }

    // ── Seeding ────────────────────────────────────────────────────────────────

    private suspend fun seedInitialData() = withContext(ioDispatcher) {
        // Clean up legacy "Documents" project if it exists
        db.projectDao().getAllProjects().find { it.name == "Documents" || it.path == "documents" }?.let { legacyProj ->
            db.projectDao().deleteProjectById(legacyProj.id)
            db.fileDao().deleteFilesForProject(legacyProj.id)
        }

        val allProjects = db.projectDao().getAllProjects()
        if (allProjects.isEmpty()) {
            val sandboxId = db.projectDao().insertProject(
                ProjectEntity(name = "Notes", path = "notes", isExternal = false, isSelected = true)
            )
            val sandboxProj = db.projectDao().getSelectedProject()!!

            storageManager.writeFile(sandboxProj, "Welcome.md", """
                # Welcome to Kern

                Kern is a typography-first, high-performance Markdown editor designed for mobile and foldable devices. It gives you absolute control over your writing with local-first file sovereignty and a beautiful reading experience.

                ## Key Features

                ### 1. Inline-Reveal Live Preview (WYSIWYG)
                Kern features an **inline-reveal layout engine**. By default, your text displays fully rendered with high-fidelity typography. The moment you tap on a paragraph to edit, the raw Markdown formatting syntax (such as `#`, `**`, `*`, `>`) reveals itself inline, allowing zero-friction editing.

                Try it yourself:
                * This is a **bold** statement.
                * This is an *italic* emphasis.
                * You can write `inline code` or block formatting.

                > "Simplicity is the ultimate sophistication." — Leonardo da Vinci

                ---

                ### 2. Three-State View Configurations
                Toggle between three view modes via settings to suit your writing style:
                1. **Rendered (Live Preview):** Clean, bookish reading layout with inline-reveal editing.
                2. **Syntax-Highlighted:** Keeps formatting symbols visible while retaining typography size, colors, and structural spacing.
                3. **Raw Plain-Text:** A completely clean monospace writing environment with zero styling or decorations.

                ---

                ### 3. Sharing & Editing
                * **Context Formatting Toolbar:** Select any word or sentence to reveal the context toolbar.
                * **Sticky Selection:** Keep text selected when applying formatting to stack styles seamlessly.
                * **Sharing:** Easily export or share your Markdown documents.

                ---

                ### 4. Linking Local Folders (Scoped Storage)
                Own your files completely.
                * **App-Sandbox Storage:** Fast, local-first internal storage.
                * **External Scoped Storage (SAF):** Link arbitrary local folders on your device or SD card using Android's Storage Access Framework. Keep your files local, private, and compatible with other text editors.

                ---

                ### 5. Hemingway Readability Analyzer
                Polish your prose on demand. Open the **Metrics** sidebar to run asynchronous readability analysis:
                * Evaluates reading grade levels.
                * Detects complex words, passive voice, and redundant adverbs.
                * Highlights hard-to-read sentences inline so you can refine them.

                ---

                ### 6. Dynamic Theme & Typography
                Designed like a well-lit architecture studio, Kern prioritizes reading endurance:
                * Locked 1.6x line-height for optimal reading scan lines.
                * Support for premium Monospace system fonts (`JetBrains Mono` / `Roboto Mono`) and elegant Serifs.
                * Seamless split-screen support on tablets and foldables (35% directory rail, 65% central workspace).
            """.trimIndent())
            db.fileDao().insertFile(FileEntity(projectId = sandboxId, name = "Welcome.md",
                relativePath = "Welcome.md", isDirectory = false,
                lastModified = System.currentTimeMillis(), syncState = "PENDING"))
        } else {
            // If there's no selected project now, select the first available one
            if (db.projectDao().getSelectedProject() == null) {
                db.projectDao().updateProject(allProjects.first().copy(isSelected = true))
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
        viewModelScope.launch {
            if (project.isExternal) {
                scanProject(project)
            } else {
                loadDrillFiles(project, "")
            }
        }
    }

    fun navigateToSegment(project: ProjectEntity, path: String) {
        _activeProject.value = project
        _currentPath.value = path
        viewModelScope.launch { loadDrillFiles(project, path) }
    }

    fun selectProject(project: ProjectEntity) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                db.projectDao().deselectAllProjects()
                db.projectDao().updateProject(project.copy(isSelected = true))
            }
            _activeProject.value = project
            _currentPath.value = ""
            if (project.isExternal) {
                scanProject(project)
            } else {
                loadDrillFiles(project, "")
            }
            refreshAllFiles()
        }
    }

    private suspend fun loadDrillFiles(project: ProjectEntity, path: String) {
        val diskFiles = storageManager.listDirectory(project, path)
        _drillFiles.value = withContext(ioDispatcher) {
            VfsNodeMapper.enrichFiles(diskFiles, db.fileDao().getFilesForProject(project.id))
        }
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
            val active = _activeProject.value
            if (active != null && active.id == project.id) {
                loadDrillFiles(active, _currentPath.value)
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

    fun setCreateDialogOpen(open: Boolean) { _isCreateDialogOpen.value = open }

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
            } else {
                refreshAllFiles()
            }
        }
    }

    fun createFile(name: String, targetProject: ProjectEntity? = null, onCreated: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val proj = targetProject ?: _activeProject.value ?: withContext(ioDispatcher) { db.projectDao().getSelectedProject() } ?: return@launch
            val fileName = if (name.endsWith(".md")) name else "$name.md"
            val isRootCreation = targetProject != null || _activeProject.value == null
            val relativePath = if (isRootCreation || _currentPath.value.isEmpty()) fileName else "${_currentPath.value}/$fileName"
            storageManager.createFile(proj, relativePath)
            withContext(ioDispatcher) {
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
            onCreated?.invoke(relativePath)
        }
    }

    fun createFolder(name: String, targetProject: ProjectEntity? = null) {
        viewModelScope.launch {
            val proj = targetProject ?: _activeProject.value ?: withContext(ioDispatcher) { db.projectDao().getSelectedProject() } ?: return@launch
            val isRootCreation = targetProject != null || _activeProject.value == null
            val relativePath = if (isRootCreation || _currentPath.value.isEmpty()) name else "${_currentPath.value}/$name"
            storageManager.createDirectory(proj, relativePath)
            withContext(ioDispatcher) {
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
            withContext(ioDispatcher) { db.fileDao().deleteFile(project.id, node.relativePath) }
            val active = _activeProject.value
            if (active != null) loadDrillFiles(active, _currentPath.value)
            else refreshAllFiles()
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
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
            withContext(ioDispatcher) {
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
            withContext(ioDispatcher) {
                db.projectDao().updateProject(project.copy(name = newName))
            }
            refreshAllFiles()
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
            val active = _activeProject.value
            if (active != null) loadDrillFiles(active, _currentPath.value)
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
            val active = _activeProject.value
            if (active != null) loadDrillFiles(active, _currentPath.value)
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
            _activeQuote.value = quotes.random()
        }
    }
}
