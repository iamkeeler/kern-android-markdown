package com.attachdesign.kern.ui.editor
 
import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
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
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.data.stats.StatsRepository
import com.attachdesign.kern.data.analytics.AnalyticsTracker
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val database: AppDatabase get() = db
    val statsRepository = StatsRepository(db)
    val analyticsTracker = AnalyticsTracker(context)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val syncEngine = SyncEngine(db.fileDao(), viewModelScope)

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

    data class SnackbarEvent(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)
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
            db.settingDao().getSettingFlow("editor_font_size_scale").collect { setting ->
                val scale = setting?.value?.toFloatOrNull() ?: 1.0f
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(editorFontSizeScale = scale)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("selected_theme_id").collect { _ ->
                loadSelectedTheme()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("editor_font_family").collect { _ ->
                loadSelectedTheme()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("auto_header_spacing").collect { setting ->
                val autoHeader = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoHeaderSpacing = autoHeader)
                }
            }
        }
                viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("sentence_capitalization").collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(sentenceCapitalization = enabled)
                }
            }
        }
viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("auto_complete_enabled").collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteEnabled = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("auto_complete_quotes").collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteQuotes = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("auto_complete_single_quotes").collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteSingleQuotes = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("auto_complete_braces").collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteBraces = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("auto_complete_parens").collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteParens = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("auto_complete_brackets").collect { setting ->
                val enabled = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(autoCompleteBrackets = enabled)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("view_mode").collect { setting ->
                val mode = setting?.value ?: "RENDERED"
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(viewMode = ViewMode.valueOf(mode))
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("sticky_selection").collect { setting ->
                val sticky = setting?.value?.toBoolean() ?: true
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(stickySelection = sticky)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("sync_provider").collect { setting ->
                val provider = setting?.value ?: "NONE"
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(syncProvider = SyncProvider.valueOf(provider))
                }
            }
        }
    }

    private suspend fun loadSettings() = withContext(Dispatchers.IO) {
        val viewModeSetting = db.settingDao().getSetting("view_mode")?.value ?: "RENDERED"
        val stickySetting = db.settingDao().getSetting("sticky_selection")?.value ?: "true"
        val syncProviderSetting = db.settingDao().getSetting("sync_provider")?.value ?: "NONE"
        val autoHeaderSetting = db.settingDao().getSetting("auto_header_spacing")?.value ?: "true"
        val autoCompleteSetting = db.settingDao().getSetting("auto_complete_enabled")?.value ?: "true"
        val autoCompleteQuotesSetting = db.settingDao().getSetting("auto_complete_quotes")?.value ?: "true"
        val autoCompleteSingleQuotesSetting = db.settingDao().getSetting("auto_complete_single_quotes")?.value ?: "true"
        val autoCompleteBracesSetting = db.settingDao().getSetting("auto_complete_braces")?.value ?: "true"
        val autoCompleteParensSetting = db.settingDao().getSetting("auto_complete_parens")?.value ?: "true"
        val autoCompleteBracketsSetting = db.settingDao().getSetting("auto_complete_brackets")?.value ?: "true"
        val sentenceCapitalizationSetting = db.settingDao().getSetting("sentence_capitalization")?.value ?: "true"
        val editorFontSizeScaleSetting = db.settingDao().getSetting("editor_font_size_scale")?.value ?: "1.0"

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
                editorFontSizeScale = editorFontSizeScaleSetting.toFloatOrNull() ?: 1.0f,
                syncProvider = SyncProvider.valueOf(syncProviderSetting)
            )
            syncEngine.setProvider(SyncProvider.valueOf(syncProviderSetting))
        }
    }

    private suspend fun loadSelectedTheme() = withContext(Dispatchers.IO) {
        val themeId = db.settingDao().getSetting("selected_theme_id")?.value?.toLongOrNull()
        val savedFontFamily = db.settingDao().getSetting("editor_font_family")?.value
        var activeTheme = ThemeEngine.DefaultLight.toColorTheme()
        if (themeId != null) {
            val dbTheme = db.themeDao().getThemeById(themeId)
            if (dbTheme != null) {
                val themeJson = ThemeEngine.deserialize(dbTheme.jsonString)
                if (themeJson != null) {
                    activeTheme = themeJson.toColorTheme()
                }
            }
        }
        if (savedFontFamily != null) {
            activeTheme = activeTheme.copy(editorFontFamily = savedFontFamily)
        }
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
                db.settingDao().insertSetting(SettingEntity("editor_font_family", fontFamily))
            }
        }
    }

    fun loadFile(projectId: Long, filePath: String, focusOnStart: Boolean = false) {
        viewModelScope.launch {
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
            
            addLog("Loaded file: $filePath")
        }
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
        viewModelScope.launch {
            storageManager.deleteFile(project, node.relativePath)
            withContext(Dispatchers.IO) { db.fileDao().deleteFile(project.id, node.relativePath) }
            loadExplorerFiles(project, _uiState.value.explorerCurrentPath)
        }
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
        if (_uiState.value.documentEditorEnabled) {
            if (documentTextFieldState.undoState.canUndo) {
                documentTextFieldState.undoState.undo()
            }
        }
    }

    fun redo() {
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

    private suspend fun saveActiveFile() = withContext(Dispatchers.IO) {
        val state = _uiState.value
        val project = state.activeProject ?: return@withContext
        val filePath = state.activeFilePath
        if (filePath.isEmpty()) return@withContext

        val documentContent = if (state.documentEditorEnabled) {
            documentText
        } else {
            MarkdownParser.joinParsedDocument(state.paragraphs.items.map { it.block })
        }
        storageManager.writeFile(project, filePath, documentContent)

        // Calculate metrics for DB metadata update
        val wordCount = countWords(documentContent)
        val charCount = documentContent.length

        // Readability ARI approximation for caching
        val sentenceCount = countSentences(documentContent)
        val ari = 4.71 * (charCount.toDouble() / maxOf(1, wordCount)) + 0.5 * (wordCount.toDouble() / sentenceCount) - 21.43
        val gradeLevel = when {
            ari <= 0 -> "N/A"
            ari >= 14 -> "Post-Graduate"
            else -> "Grade ${Math.round(ari)}"
        }

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
                db.settingDao().insertSetting(SettingEntity("view_mode", viewMode.name))
            }
        }
    }

    fun changeStickySelection(sticky: Boolean) {
        _uiState.value = _uiState.value.copy(stickySelection = sticky)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("sticky_selection", sticky.toString()))
            }
        }
    }

    fun changeAutoHeaderSpacing(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoHeaderSpacing = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("auto_header_spacing", enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteEnabled = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("auto_complete_enabled", enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteQuotes(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteQuotes = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("auto_complete_quotes", enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteSingleQuotes(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteSingleQuotes = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("auto_complete_single_quotes", enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteBraces(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteBraces = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("auto_complete_braces", enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteParens(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteParens = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("auto_complete_parens", enabled.toString()))
            }
        }
    }

    fun changeAutoCompleteBrackets(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoCompleteBrackets = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("auto_complete_brackets", enabled.toString()))
            }
        }
    }

    fun changeSentenceCapitalization(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(sentenceCapitalization = enabled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.settingDao().insertSetting(SettingEntity("sentence_capitalization", enabled.toString()))
            }
        }
    }


    fun selectTheme(themeEntity: ThemeEntity) {
        viewModelScope.launch {
            val themeJson = ThemeEngine.deserialize(themeEntity.jsonString)
            if (themeJson != null) {
                _uiState.value = _uiState.value.copy(activeTheme = themeJson.toColorTheme())
                withContext(Dispatchers.IO) {
                    db.settingDao().insertSetting(SettingEntity("selected_theme_id", themeEntity.id.toString()))
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
                    db.settingDao().insertSetting(SettingEntity("selected_theme_id", id.toString()))
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
                db.settingDao().insertSetting(SettingEntity("sync_provider", provider.name))
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

    fun shareCurrentFile() {
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
            fileOpsManager.shareNode(project, fileNode)
        }
    }

    fun deleteCurrentFile(onDeleted: () -> Unit) {
        val currentState = _uiState.value
        val project = currentState.activeProject ?: return
        val filePath = currentState.activeFilePath
        if (filePath.isEmpty()) return

        saveJob?.cancel()
        viewModelScope.launch {
            val fileNode = VfsNode.File(
                name = filePath.substringAfterLast('/'),
                relativePath = filePath,
                size = 0,
                lastModified = System.currentTimeMillis()
            )
            
            val result = fileOpsManager.moveToTrash(project, fileNode)
            val trashNode = result.getOrNull() ?: return@launch
            
            _uiState.value = _uiState.value.copy(
                activeFilePath = "",
                paragraphs = ImmutableParagraphList(emptyList<ImmutableParagraphBlock>().toImmutableList()),
                hemingwayMetrics = null
            )
            withContext(Dispatchers.Main) {
                onDeleted()
            }
            
            val deleteJob = viewModelScope.launch {
                delay(5000)
                fileOpsManager.deleteNode(project, trashNode)
            }
            
            _snackbarEvent.emit(SnackbarEvent("File deleted", "Undo") {
                deleteJob.cancel()
                viewModelScope.launch {
                    fileOpsManager.restoreFromTrash(project, trashNode, filePath)
                    loadFile(project.id, filePath)
                }
            })
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
        val currentState = _uiState.value
        val project = currentState.activeProject ?: return
        val filePath = currentState.activeFilePath
        if (filePath.isEmpty()) return

        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return

        viewModelScope.launch {
            saveActiveFile()
            val fileNode = VfsNode.File(
                name = filePath.substringAfterLast('/'),
                relativePath = filePath,
                size = 0,
                lastModified = System.currentTimeMillis()
            )
            val result = fileOpsManager.renameNode(project, fileNode, cleanName)
            if (result.isSuccess) {
                val renamedNode = result.getOrThrow()
                _uiState.value = _uiState.value.copy(activeFilePath = renamedNode.relativePath)
                withContext(Dispatchers.Main) {
                    onRenamed(renamedNode.relativePath)
                }
            }
        }
    }




    private fun countSentences(s: CharSequence): Int {
        var count = 0
        var hasContent = false
        for (i in 0 until s.length) {
            val c = s[i]
            if (c == '.' || c == '!' || c == '?') {
                if (hasContent) {
                    count++
                    hasContent = false
                }
            } else if (!c.isWhitespace()) {
                hasContent = true
            }
        }
        if (hasContent) {
            count++
        }
        return maxOf(1, count)
    }

    private fun reparse(block: com.attachdesign.kern.parser.ParagraphBlock, rawText: String) =
        MarkdownParser.parseParagraph(rawText, block.id, block.separatorAfter)

    private fun separatorFor(previousText: String, nextText: String): String =
        if (MarkdownParser.isListLine(previousText) && MarkdownParser.isListLine(nextText)) "\n" else "\n\n"

    private fun addLog(message: String) {
        // Simple internal logging
    }

    // Testing Helpers
    internal fun setTestParagraphs(paragraphs: List<ImmutableParagraphBlock>) {
        _uiState.value = _uiState.value.copy(paragraphs = ImmutableParagraphList(paragraphs.toImmutableList()))
    }

    internal fun setTestTextFieldValue(index: Int, value: TextFieldValue) {
        _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
            put(index, value)
        }
    }
}
