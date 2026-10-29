@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.attachdesign.kern.ui.editor
 
import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import com.attachdesign.kern.ui.common.launchShare
import com.attachdesign.kern.ui.common.SnackbarEvent
import com.attachdesign.kern.ui.common.DeletionSessionViewModel
import com.attachdesign.kern.ui.main.dialogs.*
import com.attachdesign.kern.data.storage.MutationOutcome
import com.attachdesign.kern.data.storage.ProjectMutationCoordinator
import com.attachdesign.kern.data.storage.DirectoryOutcome
import kotlinx.coroutines.CancellationException
import com.attachdesign.kern.data.local.SettingKeys
import com.attachdesign.kern.utils.TextAnalysisUtils
import com.attachdesign.kern.utils.TextAnalysisUtils.countWords
import androidx.lifecycle.viewModelScope
import com.attachdesign.kern.analysis.HemingwayAnalyzer
import com.attachdesign.kern.analysis.HemingwayMetrics
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.flow.asSharedFlow
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.ThemeEntity
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.data.storage.VfsNodeMapper
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.sync.SyncEngine
import com.attachdesign.kern.data.sync.SyncProvider
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownParser
import com.attachdesign.kern.parser.DocumentEditEngine
import com.attachdesign.kern.parser.ParagraphBlock
import com.attachdesign.kern.parser.MarkdownEditorEngine
import com.attachdesign.kern.ui.theme.AppColorTheme
import com.attachdesign.kern.ui.theme.AppThemeJson
import com.attachdesign.kern.ui.theme.EditorTypography
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.data.stats.StatsRepository
import com.attachdesign.kern.data.analytics.AnalyticsTracker
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import android.content.Intent

enum class SidebarMode {
    CLOSED,
    METRICS,
    SETTINGS
}

data class EditorUiState(
    val activeProject: ProjectEntity? = null,
    val activeFilePath: String = "",
    val fileName: String = "",
    val paragraphs: ImmutableParagraphList = ImmutableParagraphList(emptyList<ImmutableParagraphBlock>().toImmutableList()),
    val focusedParagraphIndex: Int = -1,
    val documentEditorEnabled: Boolean = true,
    val isDocumentEditorFocused: Boolean = false,
    val viewMode: ViewMode = ViewMode.RENDERED,
    val stickySelection: Boolean = true,
    val autoHeaderSpacing: Boolean = true,
    val autoCompleteEnabled: Boolean = true,
    val autoCompleteQuotes: Boolean = true,
    val autoCompleteSingleQuotes: Boolean = true,
    val autoCompleteBraces: Boolean = true,
    val autoCompleteParens: Boolean = true,
    val autoCompleteBrackets: Boolean = true,
    val sentenceCapitalization: Boolean = true,
    val editorFontSizeScale: Float = 1.0f,
    val hemingwayMetrics: HemingwayMetrics? = null,
    val sidebarMode: SidebarMode = SidebarMode.CLOSED,
    val activeTheme: AppColorTheme = ThemeEngine.DefaultLight.toColorTheme(),
    val syncProvider: SyncProvider = SyncProvider.NONE,
    val projectFiles: List<VfsNode> = emptyList(),
    val explorerCurrentPath: String = "",
    val isFileOperationPending: Boolean = false,
    val isReadabilityPopupOpen: Boolean = false
) {
    val isSidebarOpen: Boolean get() = sidebarMode != SidebarMode.CLOSED
}


class EditorViewModel(
    private val db: AppDatabase,
    private val storageManager: StorageManager,
    private val fileOpsManager: FileOperationsManager,
    private val context: Context,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
) : ViewModel() {
    internal val sessionId = UUID.randomUUID().toString()
    private var deletionSession: DeletionSessionViewModel? = null
    internal fun attachDeletionSession(session: DeletionSessionViewModel?) { deletionSession = session }
    val renameOperations = RetainedNamingOperations(viewModelScope)
    var pendingRenameRequest: NamingRequest? = null
        private set
    val database: AppDatabase get() = db
    fun createSecondaryEditor() = EditorViewModel(db, storageManager, fileOpsManager, context, defaultDispatcher)

    private val _switchingSecondaryFile = MutableStateFlow(false)
    val switchingSecondaryFile = _switchingSecondaryFile.asStateFlow()

    fun switchSecondaryFile(key: ReferenceKey) {
        if (_switchingSecondaryFile.value) return
        _switchingSecondaryFile.value = true
        viewModelScope.launch {
            try {
                onDocumentTextChanged(documentTextFieldState.text.toString())
                saveJob?.cancelAndJoin()
                saveActiveFile()
                loadFile(key.projectId, key.path)?.join()
            } finally {
                _switchingSecondaryFile.value = false
            }
        }
    }
    val statsRepository = StatsRepository(db)
    val analyticsTracker = AnalyticsTracker(context)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val syncEngine = SyncEngine(db.fileDao(), viewModelScope)

    val companion = CompanionWorkspace(viewModelScope, read = { key ->
        val project = withContext(Dispatchers.IO) { db.projectDao().getProjectById(key.projectId) }
            ?: error("Reference project unavailable")
        storageManager.readReferenceFile(project, key.path)
    }, dispatcher = defaultDispatcher)
    val foldToSplit = db.settingDao().getSettingFlow(SettingKeys.FOLD_TO_SPLIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val foldShortcutAction = db.settingDao().getSettingFlow(SettingKeys.FOLD_SHORTCUT_ACTION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val foldHaptics = db.settingDao().getSettingFlow(SettingKeys.FOLD_HAPTICS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val foldSound = db.settingDao().getSettingFlow(SettingKeys.FOLD_SOUND)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private var sidebarBeforeCompanion = SidebarMode.CLOSED

    fun toggleCompanion() {
        val opening = !companion.state.value.isOpen
        if (opening) {
            sidebarBeforeCompanion = _uiState.value.sidebarMode
            toggleSidebar(SidebarMode.CLOSED)
        } else toggleSidebar(sidebarBeforeCompanion)
        companion.setOpen(opening)
    }

    /** Returns false when the selected second-editor action cannot fit the current window. */
    fun performFoldShortcut(action: FoldShortcutAction, canSplit: Boolean): Boolean {
        when (action) {
            FoldShortcutAction.METRICS -> toggleSidebar(
                if (_uiState.value.sidebarMode == SidebarMode.METRICS) SidebarMode.CLOSED else SidebarMode.METRICS
            )
            FoldShortcutAction.SECOND_EDITOR -> {
                if (!canSplit) return false
                if (companion.state.value.isOpen && _uiState.value.isSidebarOpen) {
                    toggleSidebar(SidebarMode.CLOSED)
                } else toggleCompanion()
            }
        }
        return true
    }

    suspend fun listReferenceFiles(projectId: Long, path: String): kotlinx.collections.immutable.ImmutableList<VfsNode> {
        val project = withContext(Dispatchers.IO) { db.projectDao().getProjectById(projectId) }
            ?: error("Project unavailable")
        return when (val result = storageManager.listDirectoryOutcome(project, path)) {
            is DirectoryOutcome.Success -> result.nodes.toImmutableList()
            DirectoryOutcome.AccessDenied -> error("Folder access was denied. Restore permission or choose another workspace.")
            DirectoryOutcome.Missing -> error("This folder is no longer available. Choose another folder.")
            is DirectoryOutcome.Failure -> throw java.io.IOException("Could not read this folder. Refresh to try again.", result.cause)
        }
    }

    suspend fun resolveCompanionImage(target: String): String? {
        val key = companion.state.value.reference ?: return null
        val project = withContext(Dispatchers.IO) { db.projectDao().getProjectById(key.projectId) } ?: return null
        return storageManager.resolveMarkdownImage(project, key.path, target)
    }

    suspend fun resolveMarkdownImage(target: String): String? {
        val state = _uiState.value
        val project = state.activeProject ?: return null
        return storageManager.resolveMarkdownImage(project, state.activeFilePath, target)
    }

    // Store TextFieldValues for each paragraph to retain selection/cursor state
    private val _paragraphTextFieldValues = MutableStateFlow<Map<Int, TextFieldValue>>(emptyMap())
    val paragraphTextFieldValues: StateFlow<Map<Int, TextFieldValue>> = _paragraphTextFieldValues.asStateFlow()
    val documentTextFieldState = TextFieldState()

    private var documentText: String = ""

    val allProjects: StateFlow<List<ProjectEntity>> = db.projectDao().getAllProjectsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var saveJob: Job? = null
    private var fileLoadJob: Job? = null
    private var loadingFile: Pair<Long, String>? = null


    private val _snackbarEvent = kotlinx.coroutines.flow.MutableSharedFlow<SnackbarEvent>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    init {
        // Load default settings
        viewModelScope.launch {
            loadSettings()
            loadSelectedTheme()
        }
        // Reactively observe setting changes to keep ViewModel in sync
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.EDITOR_FONT_SIZE_SCALE).collect { setting ->
                val scale = EditorTypography.normalizeScale(setting?.value)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(editorFontSizeScale = scale)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            ThemeEngine.observeActiveTheme(db).collect { activeTheme ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(activeTheme = activeTheme)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.AUTO_HEADER_SPACING).collect { setting ->
                val autoHeader = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoHeaderSpacing = autoHeader)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.SENTENCE_CAPITALIZATION).collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(sentenceCapitalization = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_ENABLED).collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteEnabled = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_QUOTES).collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteQuotes = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_SINGLE_QUOTES).collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteSingleQuotes = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_BRACES).collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteBraces = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_PARENS).collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteParens = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_BRACKETS).collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteBrackets = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.VIEW_MODE).collect { setting ->
                val mode = setting?.value ?: "RENDERED"
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(viewMode = ViewMode.valueOf(mode))
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.STICKY_SELECTION).collect { setting ->
                val sticky = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(stickySelection = sticky)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow(SettingKeys.SYNC_PROVIDER).collect { setting ->
                val provider = setting?.value ?: "NONE"
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(syncProvider = SyncProvider.valueOf(provider))
                }
            }
        }
    }

    private suspend fun loadSettings() = withContext(Dispatchers.IO) {
        val viewModeSetting = db.settingDao().getSetting(SettingKeys.VIEW_MODE)?.value ?: "RENDERED"
        val stickySetting = db.settingDao().getSetting(SettingKeys.STICKY_SELECTION)?.value ?: "true"
        val syncProviderSetting = db.settingDao().getSetting(SettingKeys.SYNC_PROVIDER)?.value ?: "NONE"
        val autoHeaderSetting = db.settingDao().getSetting(SettingKeys.AUTO_HEADER_SPACING)?.value ?: "true"
        val autoCompleteSetting = db.settingDao().getSetting(SettingKeys.AUTO_COMPLETE_ENABLED)?.value ?: "true"
        val autoCompleteQuotesSetting = db.settingDao().getSetting(SettingKeys.AUTO_COMPLETE_QUOTES)?.value ?: "true"
        val autoCompleteSingleQuotesSetting = db.settingDao().getSetting(SettingKeys.AUTO_COMPLETE_SINGLE_QUOTES)?.value ?: "true"
        val autoCompleteBracesSetting = db.settingDao().getSetting(SettingKeys.AUTO_COMPLETE_BRACES)?.value ?: "true"
        val autoCompleteParensSetting = db.settingDao().getSetting(SettingKeys.AUTO_COMPLETE_PARENS)?.value ?: "true"
        val autoCompleteBracketsSetting = db.settingDao().getSetting(SettingKeys.AUTO_COMPLETE_BRACKETS)?.value ?: "true"
        val sentenceCapitalizationSetting = db.settingDao().getSetting(SettingKeys.SENTENCE_CAPITALIZATION)?.value ?: "true"
        val editorFontSizeScaleSetting = db.settingDao().getSetting(SettingKeys.EDITOR_FONT_SIZE_SCALE)?.value

        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                viewMode = ViewMode.valueOf(viewModeSetting),
                stickySelection = stickySetting.toBoolean(),
                autoHeaderSpacing = autoHeaderSetting.toBoolean(),
                autoCompleteEnabled = autoCompleteSetting.toBoolean(),
                autoCompleteQuotes = autoCompleteQuotesSetting.toBoolean(),
                autoCompleteSingleQuotes = autoCompleteSingleQuotesSetting.toBoolean(),
                autoCompleteBraces = autoCompleteBracesSetting.toBoolean(),
                autoCompleteParens = autoCompleteParensSetting.toBoolean(),
                autoCompleteBrackets = autoCompleteBracketsSetting.toBoolean(),
                sentenceCapitalization = sentenceCapitalizationSetting.toBoolean(),
                editorFontSizeScale = EditorTypography.normalizeScale(editorFontSizeScaleSetting),
                syncProvider = SyncProvider.valueOf(syncProviderSetting)
            )
            syncEngine.setProvider(SyncProvider.valueOf(syncProviderSetting))
        }
    }

    private suspend fun loadSelectedTheme() = withContext(Dispatchers.IO) {
        val activeTheme = ThemeEngine.getActiveTheme(db)
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(activeTheme = activeTheme)
        }
    }

    fun setEditorFontFamily(fontFamily: String) {
        val currentState = _uiState.value
        val updatedTheme = currentState.activeTheme.copy(editorFontFamily = fontFamily)
        _uiState.value = currentState.copy(activeTheme = updatedTheme)
        
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.EDITOR_FONT_FAMILY, fontFamily))
            }
        }
    }

    fun loadFile(projectId: Long, filePath: String, focusOnStart: Boolean = false): Job? {
        // Re-entering composition after rotation must not reload over the live draft.
        if (_uiState.value.activeProject?.id == projectId && _uiState.value.activeFilePath == filePath) {
            if (loadingFile != (projectId to filePath)) fileLoadJob?.cancel()
            return fileLoadJob
        }
        val requestedFile = projectId to filePath
        if (loadingFile == requestedFile && fileLoadJob?.isActive == true) return fileLoadJob
        fileLoadJob?.cancel()
        loadingFile = requestedFile
        return viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { db.projectDao().getProjectById(projectId) } ?: return@launch
            val content = storageManager.readFile(project, filePath)
            
            // Track document open and words read stats
            statsRepository.incrementDocumentsOpened()
            analyticsTracker.logDocumentOpened()
            val wordCount = countWords(content)
            statsRepository.addWordsRead(wordCount.toLong())
            analyticsTracker.logWordsRead(wordCount.toLong())

            val parsedBlocks = MarkdownParser.parseDocument(content).map { block ->
                ImmutableParagraphBlock(block)
            }.toImmutableList()

            // Initialize TextFieldValues
            val initialValues = parsedBlocks.mapIndexed { index, wrapper ->
                index to TextFieldValue(wrapper.block.rawText)
            }.toMap()

            val parentPath = if (filePath.contains('/')) filePath.substringBeforeLast('/') else ""
            val diskFiles = storageManager.listDirectory(project, parentPath)
            val enriched = withContext(Dispatchers.IO) {
                VfsNodeMapper.enrichFiles(diskFiles, db.fileDao().getFilesForProject(project.id))
            }

            _uiState.value = _uiState.value.copy(
                activeProject = project,
                activeFilePath = filePath,
                fileName = filePath.substringAfterLast('/'),
                isFileOperationPending = pendingRenameRequest?.let { it.projectId == projectId && it.targetPath == filePath } == true,
                paragraphs = ImmutableParagraphList(parsedBlocks),
                focusedParagraphIndex = if (focusOnStart) 0 else -1,
                hemingwayMetrics = null, // Clear metrics until requested
                explorerCurrentPath = parentPath,
                projectFiles = enriched
            )
            _paragraphTextFieldValues.value = initialValues
            documentText = content
            documentTextFieldState.edit {
                replace(0, length, content)
                selection = TextRange(if (focusOnStart) 0 else content.length)
            }
        }.also { fileLoadJob = it }
    }

    fun navigateExplorerToFolder(node: VfsNode) {
        if (!node.isDirectory) return
        val project = _uiState.value.activeProject ?: return
        loadExplorerFiles(project, node.relativePath)
    }

    fun navigateExplorerUp() {
        val project = _uiState.value.activeProject ?: return
        val current = _uiState.value.explorerCurrentPath
        if (current.isEmpty()) return
        val parent = current.substringBeforeLast('/', "")
        loadExplorerFiles(project, parent)
    }

    fun deleteExplorerNode(node: VfsNode) {
        val project = _uiState.value.activeProject ?: return
        val session = deletionSession ?: return
        session.requestDelete(project, node) { loadExplorerFiles(project, _uiState.value.explorerCurrentPath) }
    }

    fun loadExplorerFiles(path: String) {
        val project = _uiState.value.activeProject ?: return
        loadExplorerFiles(project, path)
    }

    private fun loadExplorerFiles(project: ProjectEntity, path: String) {
        viewModelScope.launch {
            val diskFiles = storageManager.listDirectory(project, path)
            val enriched = withContext(Dispatchers.IO) {
                VfsNodeMapper.enrichFiles(diskFiles, db.fileDao().getFilesForProject(project.id))
            }
            _uiState.value = _uiState.value.copy(
                explorerCurrentPath = path,
                projectFiles = enriched
            )
        }
    }

    fun insertParagraphAfterWithSelection(index: Int, content: String, selectionOffset: Int) {
        val currentState = _uiState.value
        val items = currentState.paragraphs.items.toMutableList()
        
        val previous = items[index].block
        val separator = separatorFor(previous.rawText, content)
        items[index] = ImmutableParagraphBlock(previous.copy(separatorAfter = separator))
        val newBlock = MarkdownParser.parseParagraph(content, separatorAfter = previous.separatorAfter)
        val newIndex = index + 1
        items.add(newIndex, ImmutableParagraphBlock(newBlock))
        
        // Re-index map
        val newValues = mutableMapOf<Int, TextFieldValue>()
        var offset = 0
        for (i in 0 until items.size) {
            if (i == newIndex) {
                newValues[i] = TextFieldValue(
                    text = content,
                    selection = androidx.compose.ui.text.TextRange(selectionOffset.coerceIn(0, content.length))
                )
                offset = 1
            } else {
                newValues[i] = _paragraphTextFieldValues.value[i - offset] ?: TextFieldValue(items[i].block.rawText)
            }
        }
        
        _uiState.value = currentState.copy(
            paragraphs = ImmutableParagraphList(items.toImmutableList()),
            focusedParagraphIndex = newIndex
        )
        _paragraphTextFieldValues.value = newValues
        saveActiveFileAsync()
    }

    fun updateParagraph(index: Int, newValue: TextFieldValue) {
        val currentState = _uiState.value.let {
            if (it.isReadabilityPopupOpen) it.copy(isReadabilityPopupOpen = false) else it
        }
        val items = currentState.paragraphs.items.toMutableList()
        val originalRaw = items[index].block.rawText
        val oldValue = _paragraphTextFieldValues.value[index] ?: TextFieldValue(originalRaw)

        val charDelta = (newValue.text.length - oldValue.text.length).toLong()
        if (charDelta > 0) {
            val oldWords = countWords(oldValue.text)
            val newWords = countWords(newValue.text)
            val wordDelta = (newWords - oldWords).toLong()
            viewModelScope.launch {
                statsRepository.addCharactersWritten(charDelta)
                analyticsTracker.logCharactersWritten(charDelta)
                if (wordDelta > 0) {
                    statsRepository.addWordsWritten(wordDelta)
                    analyticsTracker.logWordsWritten(wordDelta)
                }
            }
        }

        // 1. Check if it's a newline split
        if (newValue.text.contains('\n')) {
            val splitIndex = newValue.text.indexOf('\n')
            val firstPart = newValue.text.substring(0, splitIndex)
            val secondPart = newValue.text.substring(splitIndex + 1)

            val continuation = MarkdownEditorEngine.checkContinuation(firstPart)
            if (continuation.isContinuation) {
                if (continuation.isExit) {
                    val updatedBlock = reparse(items[index].block, continuation.newCurrentText)
                    items[index] = ImmutableParagraphBlock(updatedBlock)

                    _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
                        put(index, TextFieldValue(continuation.newCurrentText))
                    }

                    _uiState.value = currentState.copy(
                        paragraphs = ImmutableParagraphList(items.toImmutableList())
                    )

                    insertParagraphAfter(index, secondPart)
                } else {
                    val updatedBlock = reparse(items[index].block, continuation.newCurrentText)
                    items[index] = ImmutableParagraphBlock(updatedBlock)

                    _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
                        put(index, TextFieldValue(continuation.newCurrentText))
                    }

                    _uiState.value = currentState.copy(
                        paragraphs = ImmutableParagraphList(items.toImmutableList())
                    )

                    val newContent = continuation.nextLinePrefix + secondPart
                    insertParagraphAfterWithSelection(index, newContent, continuation.nextLinePrefix.length)
                }
                return
            }

            // Normal split behavior if not a bullet list/blockquote/etc.
            val updatedBlock = reparse(items[index].block, firstPart)
            items[index] = ImmutableParagraphBlock(updatedBlock)

            _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
                put(index, TextFieldValue(firstPart))
            }

            _uiState.value = currentState.copy(
                paragraphs = ImmutableParagraphList(items.toImmutableList())
            )

            insertParagraphAfter(index, secondPart)
            return
        }

        // 2. Perform inline typing adjustments (overtype skipping, selection wrapping, smart typography, auto header spacing)
        val transformResult = MarkdownEditorEngine.handleTextChange(
            oldText = oldValue.text,
            oldSelStart = oldValue.selection.start,
            oldSelEnd = oldValue.selection.end,
            newText = newValue.text,
            newSelStart = newValue.selection.start,
            newSelEnd = newValue.selection.end,
            autoHeaderSpacing = currentState.autoHeaderSpacing,
            autoCompleteEnabled = currentState.autoCompleteEnabled,
            autoCompleteQuotes = currentState.autoCompleteQuotes,
            autoCompleteSingleQuotes = currentState.autoCompleteSingleQuotes,
            autoCompleteBraces = currentState.autoCompleteBraces,
            autoCompleteParens = currentState.autoCompleteParens,
            autoCompleteBrackets = currentState.autoCompleteBrackets,
            sentenceCapitalization = currentState.sentenceCapitalization
        )

        val finalValue = TextFieldValue(
            text = transformResult.text,
            selection = androidx.compose.ui.text.TextRange(transformResult.selectionStart, transformResult.selectionEnd)
        )

        // If raw text hasn't changed, just update the TextFieldValue (cursor/selection change)
        _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
            put(index, finalValue)
        }

        if (originalRaw == finalValue.text) {
            return
        }

        // Perform O(1) parse differential update
        val updatedBlock = reparse(items[index].block, finalValue.text)
        items[index] = ImmutableParagraphBlock(updatedBlock)

        _uiState.value = currentState.copy(
            paragraphs = ImmutableParagraphList(items.toImmutableList())
        )

        // Debounce auto-save to VFS sandbox
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000) // 1 second debounce
            saveActiveFile()
        }
    }

    fun setHeaderLevel(index: Int, level: Int) {
        applyParagraphCommand(index, DocumentEditEngine.Command.SetHeading(level))
    }

    fun cycleHeaderLevel(index: Int) {
        applyParagraphCommand(index, DocumentEditEngine.Command.CycleHeading)
    }

    fun toggleChecklist(index: Int) {
        applyParagraphCommand(index, DocumentEditEngine.Command.ToggleChecklist)
    }

    fun toggleBulletList(index: Int) {
        applyParagraphCommand(index, DocumentEditEngine.Command.ToggleBulletList)
    }

    fun indentParagraph(index: Int) {
        applyParagraphCommand(index, DocumentEditEngine.Command.Indent)
    }

    fun outdentParagraph(index: Int) {
        applyParagraphCommand(index, DocumentEditEngine.Command.Outdent)
    }

    fun formatParagraph(index: Int, opening: String, closing: String) {
        applyParagraphCommand(index, DocumentEditEngine.Command.Wrap(opening, closing))
    }

    fun applyDocumentCommand(command: DocumentEditEngine.Command) {
        if (_uiState.value.isFileOperationPending) return
        val selection = documentTextFieldState.selection
        val result = DocumentEditEngine.apply(
            text = documentTextFieldState.text.toString(),
            selectionStart = selection.start,
            selectionEnd = selection.end,
            command = command,
            stickySelection = _uiState.value.stickySelection
        )
        documentTextFieldState.edit {
            replace(0, length, result.text)
            this.selection = TextRange(result.selectionStart, result.selectionEnd)
        }
    }

    fun undo() {
        if (_uiState.value.isFileOperationPending) return
        if (_uiState.value.documentEditorEnabled) {
            if (documentTextFieldState.undoState.canUndo) {
                documentTextFieldState.undoState.undo()
            }
        }
    }

    fun redo() {
        if (_uiState.value.isFileOperationPending) return
        if (_uiState.value.documentEditorEnabled) {
            if (documentTextFieldState.undoState.canRedo) {
                documentTextFieldState.undoState.redo()
            }
        }
    }

    fun onDocumentEditorFocusChanged(isFocused: Boolean) {
        if (_uiState.value.isDocumentEditorFocused == isFocused) return
        _uiState.value = _uiState.value.copy(isDocumentEditorFocused = isFocused)
        if (!isFocused) triggerCloudSyncSweep()
    }

    fun onDocumentTextChanged(content: String) {
        if (content == documentText) return
        val previous = documentText
        documentText = content
        val blocks = MarkdownParser.parseDocument(content)
        _uiState.value = _uiState.value.copy(
            paragraphs = ImmutableParagraphList(blocks.map(::ImmutableParagraphBlock).toImmutableList())
        )
        _paragraphTextFieldValues.value = blocks.mapIndexed { index, block ->
            index to TextFieldValue(block.rawText)
        }.toMap()

        val characterDelta = (content.length - previous.length).coerceAtLeast(0).toLong()
        val wordDelta = (countWords(content) - countWords(previous)).coerceAtLeast(0).toLong()
        if (characterDelta > 0L || wordDelta > 0L) {
            viewModelScope.launch {
                if (characterDelta > 0L) {
                    statsRepository.addCharactersWritten(characterDelta)
                    analyticsTracker.logCharactersWritten(characterDelta)
                }
                if (wordDelta > 0L) {
                    statsRepository.addWordsWritten(wordDelta)
                    analyticsTracker.logWordsWritten(wordDelta)
                }
            }
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000)
            saveActiveFile()
        }
    }

    private fun applyParagraphCommand(index: Int, command: DocumentEditEngine.Command) {
        if (_uiState.value.isFileOperationPending) return
        val currentState = _uiState.value
        val items = currentState.paragraphs.items.toMutableList()
        if (index < 0 || index >= items.size) return

        val originalValue = _paragraphTextFieldValues.value[index] ?: TextFieldValue(items[index].block.rawText)
        val result = DocumentEditEngine.apply(
            text = originalValue.text,
            selectionStart = originalValue.selection.start,
            selectionEnd = originalValue.selection.end,
            command = command,
            stickySelection = currentState.stickySelection
        )
        if (result.text == originalValue.text &&
            result.selectionStart == originalValue.selection.start &&
            result.selectionEnd == originalValue.selection.end
        ) return

        val newValue = TextFieldValue(
            text = result.text,
            selection = androidx.compose.ui.text.TextRange(result.selectionStart, result.selectionEnd)
        )
        items[index] = ImmutableParagraphBlock(reparse(items[index].block, result.text))
        _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
            put(index, newValue)
        }
        _uiState.value = currentState.copy(paragraphs = ImmutableParagraphList(items.toImmutableList()))
        saveActiveFileAsync()
    }

    fun insertParagraphAfter(index: Int, content: String) {
        val currentState = _uiState.value
        val items = currentState.paragraphs.items.toMutableList()
        
        val previous = items[index].block
        val separator = separatorFor(previous.rawText, content)
        items[index] = ImmutableParagraphBlock(previous.copy(separatorAfter = separator))
        val newBlock = MarkdownParser.parseParagraph(content, separatorAfter = previous.separatorAfter)
        val newIndex = index + 1
        items.add(newIndex, ImmutableParagraphBlock(newBlock))
        
        // Re-index map
        val newValues = mutableMapOf<Int, TextFieldValue>()
        var offset = 0
        for (i in 0 until items.size) {
            if (i == newIndex) {
                newValues[i] = TextFieldValue(content)
                offset = 1
            } else {
                newValues[i] = _paragraphTextFieldValues.value[i - offset] ?: TextFieldValue(items[i].block.rawText)
            }
        }
        
        _uiState.value = currentState.copy(
            paragraphs = ImmutableParagraphList(items.toImmutableList()),
            focusedParagraphIndex = newIndex
        )
        _paragraphTextFieldValues.value = newValues
        saveActiveFileAsync()
    }

    fun mergeParagraphWithPrevious(index: Int) {
        if (index <= 0) return
        val currentState = _uiState.value
        val items = currentState.paragraphs.items.toMutableList()
        
        val currentText = _paragraphTextFieldValues.value[index]?.text ?: ""
        val prevText = _paragraphTextFieldValues.value[index - 1]?.text ?: ""
        
        // Concatenate text
        val mergedText = prevText + currentText
        val prevBlock = items[index - 1].block
        val currentBlock = items[index].block
        
        // Update previous paragraph block
        val updatedPrevBlock = MarkdownParser.parseParagraph(
            mergedText,
            prevBlock.id,
            separatorAfter = currentBlock.separatorAfter
        )
        items[index - 1] = ImmutableParagraphBlock(updatedPrevBlock)
        
        // Remove current paragraph
        items.removeAt(index)
        
        // Rebuild values map
        val newValues = mutableMapOf<Int, TextFieldValue>()
        var offset = 0
        for (i in 0 until items.size) {
            if (i == index - 1) {
                // Position cursor at the junction
                newValues[i] = TextFieldValue(mergedText, androidx.compose.ui.text.TextRange(prevText.length))
            } else {
                val lookupIndex = if (i >= index) i + 1 else i
                newValues[i] = _paragraphTextFieldValues.value[lookupIndex] ?: TextFieldValue(items[i].block.rawText)
            }
        }
        
        _uiState.value = currentState.copy(
            paragraphs = ImmutableParagraphList(items.toImmutableList()),
            focusedParagraphIndex = index - 1
        )
        _paragraphTextFieldValues.value = newValues
        saveActiveFileAsync()
    }

    fun setParagraphFocus(index: Int) {
        val previousFocus = _uiState.value.focusedParagraphIndex
        _uiState.value = _uiState.value.copy(focusedParagraphIndex = index)
        
        // Auto-sync sweeps trigger on file close or losing editor focus
        if (previousFocus != -1 && index == -1) {
            triggerCloudSyncSweep()
        }
    }

    private fun saveActiveFileAsync() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            saveActiveFile()
        }
    }

    private suspend fun saveActiveFile(allowPending: Boolean = false) = withContext(Dispatchers.IO) {
        val state = _uiState.value
        val project = state.activeProject ?: return@withContext
        val filePath = state.activeFilePath
        if (filePath.isEmpty()) return@withContext

        val documentContent = if (state.documentEditorEnabled) {
            documentText
        } else {
            MarkdownParser.joinParsedDocument(state.paragraphs.items.map { it.block })
        }
        ProjectMutationCoordinator.forDatabase(db).withMutation(project.id) {
        if (_uiState.value.isFileOperationPending && !allowPending) return@withMutation
        if (db.projectDao().getProjectById(project.id)?.path != project.path) return@withMutation
        storageManager.writeFile(project, filePath, documentContent)

        // Calculate metrics for DB metadata update
        val wordCount = countWords(documentContent)
        val charCount = documentContent.length

        // Readability ARI approximation for caching
        val sentenceCount = maxOf(1, TextAnalysisUtils.countSentences(documentContent))
        val nonWhitespaceCharCount = documentContent.count { !it.isWhitespace() }
        val gradeLevel = TextAnalysisUtils.calculateAriGrade(
            wordCount = wordCount,
            charCount = nonWhitespaceCharCount,
            sentenceCount = sentenceCount
        )

        // Update database FileEntity
        val fileEntity = db.fileDao().getFileByPath(project.id, filePath)
        if (fileEntity != null) {
            val updatedFile = fileEntity.copy(
                lastModified = System.currentTimeMillis(),
                syncState = "PENDING", // Mark pending sync
                wordCount = wordCount,
                characterCount = charCount,
                readabilityGrade = gradeLevel
            )
            db.fileDao().updateFile(updatedFile)
        }

        }
        // If Hemingway analyzer is active, recalculate on save
        if (state.sidebarMode == SidebarMode.METRICS || state.isReadabilityPopupOpen) {
            runHemingwayAnalysis(documentContent)
        }
    }

    fun getFullDocumentContent(): String {
        return if (_uiState.value.documentEditorEnabled) {
            documentTextFieldState.text.toString()
        } else {
            MarkdownParser.joinParsedDocument(_uiState.value.paragraphs.items.map { it.block })
        }
    }

    fun toggleSidebar(mode: SidebarMode) {
        _uiState.value = _uiState.value.copy(sidebarMode = mode)
        if (mode == SidebarMode.METRICS) {
            // Readability analysis triggers when sidebar or sheet is opened in METRICS mode
            viewModelScope.launch(defaultDispatcher) {
                val documentContent = getFullDocumentContent()
                runHemingwayAnalysis(documentContent)
            }
        }
    }

    fun toggleReadabilityPopup() {
        val currentState = _uiState.value
        val willOpen = !currentState.isReadabilityPopupOpen
        _uiState.value = currentState.copy(isReadabilityPopupOpen = willOpen)

        if (willOpen) {
            viewModelScope.launch(defaultDispatcher) {
                val documentContent = getFullDocumentContent()
                runHemingwayAnalysis(documentContent)
            }
        }
    }

    private suspend fun runHemingwayAnalysis(content: String) {
        val metrics = HemingwayAnalyzer.analyze(content)
        _uiState.value = _uiState.value.copy(hemingwayMetrics = metrics)
    }

    fun changeViewMode(viewMode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = viewMode)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.VIEW_MODE, viewMode.name))
            }
        }
    }

    fun changeStickySelection(sticky: Boolean) {
        _uiState.value = _uiState.value.copy(stickySelection = sticky)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.STICKY_SELECTION, sticky.toString()))
            }
        }
    }

    fun changeAutoHeaderSpacing(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoHeaderSpacing = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_HEADER_SPACING, enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteEnabled = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_ENABLED, enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteQuotes(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteQuotes = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_QUOTES, enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteSingleQuotes(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteSingleQuotes = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_SINGLE_QUOTES, enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteBraces(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteBraces = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_BRACES, enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteParens(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteParens = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_PARENS, enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteBrackets(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteBrackets = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_BRACKETS, enabled.toString()))
            }
        }
    }

    fun changeSentenceCapitalization(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(sentenceCapitalization = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.SENTENCE_CAPITALIZATION, enabled.toString()))
            }
        }
    }


    fun selectTheme(themeEntity: ThemeEntity) {
        viewModelScope.launch {
            val themeJson = ThemeEngine.deserialize(themeEntity.jsonString)
            if (themeJson != null) {
                _uiState.value = _uiState.value.copy(activeTheme = themeJson.toColorTheme())
                withContext(Dispatchers.IO) {
                    db.settingDao().insertSetting(SettingEntity(SettingKeys.SELECTED_THEME_ID, themeEntity.id.toString()))
                }
            }
        }
    }

    fun selectThemeFromJson(jsonString: String): Boolean {
        val themeJson = ThemeEngine.deserialize(jsonString)
        return if (themeJson != null) {
            viewModelScope.launch {
                val id = withContext(Dispatchers.IO) {
                    db.themeDao().insertTheme(ThemeEntity(name = themeJson.name, jsonString = jsonString))
                }
                _uiState.value = _uiState.value.copy(activeTheme = themeJson.toColorTheme())
                withContext(Dispatchers.IO) {
                    db.settingDao().insertSetting(SettingEntity(SettingKeys.SELECTED_THEME_ID, id.toString()))
                }
            }
            true
        } else {
            false
        }
    }

    fun changeSyncProvider(provider: SyncProvider) {
        _uiState.value = _uiState.value.copy(syncProvider = provider)
        syncEngine.setProvider(provider)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity(SettingKeys.SYNC_PROVIDER, provider.name))
            }
        }
    }

    fun closeFile() {
        saveJob?.cancel()
        viewModelScope.launch {
            saveActiveFile()
            triggerCloudSyncSweep()
            _uiState.value = _uiState.value.copy(
                activeFilePath = "",
                paragraphs = ImmutableParagraphList(emptyList<ImmutableParagraphBlock>().toImmutableList()),
                hemingwayMetrics = null
            )
        }
    }

    fun triggerCloudSyncSweep() {
        val project = _uiState.value.activeProject ?: return
        syncEngine.triggerSync(project)
    }

    fun moveCurrentFileToCloud(targetProject: ProjectEntity) {
        val currentState = _uiState.value
        val currentProj = currentState.activeProject ?: return
        val currentPath = currentState.activeFilePath
        if (currentPath.isEmpty()) return
        
        viewModelScope.launch {
            saveActiveFile() // Save current state first
            
            val success = withContext(Dispatchers.IO) {
                // Move on disk
                val moved = storageManager.moveNode(
                    fromProject = currentProj,
                    fromPath = currentPath,
                    toProject = targetProject,
                    toPath = currentPath.substringAfterLast('/') // Move to the root of target cloud folder
                )
                if (moved) {
                    val fileEntity = db.fileDao().getFileByPath(currentProj.id, currentPath)
                    if (fileEntity != null) {
                        val updated = fileEntity.copy(
                            projectId = targetProject.id,
                            relativePath = currentPath.substringAfterLast('/'),
                            syncState = "SYNCED"
                        )
                        db.fileDao().updateFile(updated)
                    }
                }
                moved
            }
            
            if (success) {
                // Reload the file from the new project/path
                loadFile(targetProject.id, currentPath.substringAfterLast('/'))
            }
        }
    }

    fun shareCurrentFile(context: Context) {
        val currentState = _uiState.value
        val project = currentState.activeProject ?: return
        val filePath = currentState.activeFilePath
        if (filePath.isEmpty()) return

        viewModelScope.launch {
            statsRepository.incrementTimesShared()
            analyticsTracker.logDocumentShared()
            saveActiveFile()
            val fileNode = VfsNode.File(
                name = filePath.substringAfterLast('/'),
                relativePath = filePath,
                size = 0,
                lastModified = System.currentTimeMillis()
            )
            fileOpsManager.shareNode(project, fileNode).onSuccess { payload ->
                context.launchShare(payload)
            }.onFailure { _snackbarEvent.emit(SnackbarEvent("Could not share ${fileNode.name}. Check folder access and try again.")) }
        }
    }

    fun deleteCurrentFile(onDeleted: () -> Unit) {
        val state = _uiState.value
        val project = state.activeProject ?: return
        val path = state.activeFilePath.takeIf { it.isNotEmpty() } ?: return
        val session = deletionSession ?: return
        if (state.isFileOperationPending) return
        _uiState.value = state.copy(isFileOperationPending = true)
        viewModelScope.launch {
            try {
                saveJob?.cancelAndJoin()
                documentText = documentTextFieldState.text.toString()
                saveActiveFile(allowPending = true)
                session.requestDelete(project, VfsNode.File(path.substringAfterLast('/'), path, 0, 0),
                    onFailure = { needsInspection -> if (_uiState.value.activeProject?.id == project.id && _uiState.value.activeFilePath == path)
                        _uiState.value = _uiState.value.copy(isFileOperationPending = needsInspection) }) {
                    if (_uiState.value.activeProject?.id == project.id && _uiState.value.activeFilePath == path) {
                        _uiState.value = _uiState.value.copy(activeFilePath = "", isFileOperationPending = false)
                        onDeleted()
                    }
                }
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(isFileOperationPending = false)
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isFileOperationPending = false)
                _snackbarEvent.emit(SnackbarEvent("Could not save the document before deletion. Your draft is preserved."))
            }
        }
    }

    fun duplicateCurrentFile(onDuplicated: (String) -> Unit) {
        val currentState = _uiState.value
        val project = currentState.activeProject ?: return
        val filePath = currentState.activeFilePath
        if (filePath.isEmpty()) return

        viewModelScope.launch {
            saveActiveFile() // save active changes first
            val fileNode = VfsNode.File(
                name = filePath.substringAfterLast('/'),
                relativePath = filePath,
                size = 0,
                lastModified = System.currentTimeMillis()
            )
            val result = fileOpsManager.duplicateNode(project, fileNode)
            if (result.isSuccess) {
                val dupNode = result.getOrThrow()
                withContext(Dispatchers.Main) {
                    onDuplicated(dupNode.relativePath)
                }
            }
        }
    }

    fun renameCurrentFile(newName: String, onRenamed: (String) -> Unit) {
        val state = _uiState.value
        val project = state.activeProject ?: return
        val request = NamingRequest.create(NamingKind.RenameNode, project.id, project.name,
            state.activeFilePath.substringBeforeLast('/', ""), state.activeFilePath, state.fileName)
            .copy(draft = TextFieldValue(newName))
        submitRename(request, onRenamed)
    }

    fun submitRename(request: NamingRequest, onRenamed: (String) -> Unit = {}) {
        if (request.inspectionRequired || (request.submitted && renameOperations.records.value[request.id] == null)) return
        val state = _uiState.value
        if (state.activeProject?.id != request.projectId || state.activeFilePath != request.targetPath) return
        if (state.isFileOperationPending && pendingRenameRequest?.id != request.id) return
        pendingRenameRequest = request
        renameOperations.submit(request.id) {
            _uiState.value = _uiState.value.copy(isFileOperationPending = true)
            try {
                saveJob?.cancelAndJoin()
                if (_uiState.value.documentEditorEnabled) documentText = documentTextFieldState.text.toString()
                saveActiveFile(allowPending = true)
                val result = fileOpsManager.renameNodeOutcome(state.activeProject,
                    VfsNode.File(request.targetName, request.targetPath, 0, 0), request.draft.text)
                finishRename(result, request, onRenamed)
            } catch (failure: Exception) {
                if (failure !is CancellationException) _uiState.value = _uiState.value.copy(isFileOperationPending = false)
                throw failure
            }
        }
    }

    fun holdInterruptedRename(request: NamingRequest) {
        pendingRenameRequest = request
        _uiState.value = _uiState.value.copy(isFileOperationPending = true)
    }

    fun recoverRename(request: NamingRequest, onRenamed: (String) -> Unit = {}) {
        val partial = (renameOperations.records.value[request.id] as? NamingOperationState.NeedsReconciliation)?.partial
        renameOperations.recover(request.id) {
            val project = withContext(Dispatchers.IO) { db.projectDao().getProjectById(request.projectId) }
                ?: return@recover NamingOperationState.Inspected("Workspace unavailable. Restore access and refresh again.", false)
            if (partial != null) return@recover finishRename(fileOpsManager.reconcileMetadata(project, partial), request, onRenamed)
            // After process interruption, inspect only. Never infer that re-running a rename is safe.
            val listing = storageManager.listDirectoryOutcome(project, request.parentPath)
            if (listing !is DirectoryOutcome.Success) return@recover NamingOperationState.Inspected("Folder unavailable. Restore access and refresh again.", false)
            val source = listing.nodes.any { it.relativePath == request.targetPath }
            val destination = listing.nodes.any { it.name == request.draft.text.trim() }
            val safe = source && !destination
            if (safe) _uiState.value = _uiState.value.copy(isFileOperationPending = false)
            NamingOperationState.Inspected(if (safe) "Folder checked. Review the name before retrying." else
                "The rename may already have happened. Close this document and inspect the workspace before editing.", safe)
        }
    }

    private fun finishRename(result: MutationOutcome, request: NamingRequest, onRenamed: (String) -> Unit): NamingOperationState {
        when (result) {
            is MutationOutcome.Success -> {
                if (_uiState.value.activeProject?.id == request.projectId && _uiState.value.activeFilePath == request.targetPath) {
                    val actual = result.actual.node
                    _uiState.value = _uiState.value.copy(activeFilePath = actual.relativePath, fileName = actual.name,
                        explorerCurrentPath = actual.relativePath.substringBeforeLast('/', ""), isFileOperationPending = false)
                    pendingRenameRequest = null
                    onRenamed(actual.relativePath)
                }
            }
            is MutationOutcome.Failed -> _uiState.value = _uiState.value.copy(isFileOperationPending = false)
            is MutationOutcome.ReconciliationRequired -> Unit // Block autosave to an identity that may have moved.
        }
        return namingResult(result)
    }

    private fun reparse(block: com.attachdesign.kern.parser.ParagraphBlock, rawText: String) =
        MarkdownParser.parseParagraph(rawText, block.id, block.separatorAfter)

    private fun separatorFor(previousText: String, nextText: String): String =
        if (MarkdownParser.isListLine(previousText) && MarkdownParser.isListLine(nextText)) "\n" else "\n\n"

    // Testing Helpers
    internal fun setDocumentEditorEnabledForTest(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(documentEditorEnabled = enabled)
    }

    internal fun setTestParagraphs(paragraphs: List<ImmutableParagraphBlock>) {
        _uiState.value = _uiState.value.copy(paragraphs = ImmutableParagraphList(paragraphs.toImmutableList()))
    }

    internal fun setTestTextFieldValue(index: Int, value: TextFieldValue) {
        _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
            put(index, value)
        }
    }
}
