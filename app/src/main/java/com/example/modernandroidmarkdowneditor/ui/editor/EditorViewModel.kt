package com.example.modernandroidmarkdowneditor.ui.editor

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.example.modernandroidmarkdowneditor.utils.TextAnalysisUtils.countWords
import androidx.lifecycle.viewModelScope
import com.example.modernandroidmarkdowneditor.analysis.HemingwayAnalyzer
import com.example.modernandroidmarkdowneditor.analysis.HemingwayMetrics
import com.example.modernandroidmarkdowneditor.data.local.AppDatabase
import com.example.modernandroidmarkdowneditor.data.local.FileEntity
import com.example.modernandroidmarkdowneditor.data.local.ProjectEntity
import com.example.modernandroidmarkdowneditor.data.local.SettingEntity
import com.example.modernandroidmarkdowneditor.data.local.ThemeEntity
import com.example.modernandroidmarkdowneditor.data.storage.StorageManager
import com.example.modernandroidmarkdowneditor.data.sync.SyncEngine
import com.example.modernandroidmarkdowneditor.data.sync.SyncProvider
import com.example.modernandroidmarkdowneditor.parser.MarkdownBlockType
import com.example.modernandroidmarkdowneditor.parser.MarkdownParser
import com.example.modernandroidmarkdowneditor.parser.ParagraphBlock
import com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme
import com.example.modernandroidmarkdowneditor.ui.theme.AppThemeJson
import com.example.modernandroidmarkdowneditor.ui.theme.ThemeEngine
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

data class EditorUiState(
    val activeProject: ProjectEntity? = null,
    val activeFilePath: String = "",
    val fileName: String = "",
    val paragraphs: ImmutableParagraphList = ImmutableParagraphList(emptyList<ImmutableParagraphBlock>().toImmutableList()),
    val focusedParagraphIndex: Int = -1,
    val viewMode: ViewMode = ViewMode.RENDERED,
    val stickySelection: Boolean = true,
    val hemingwayMetrics: HemingwayMetrics? = null,
    val isSidebarOpen: Boolean = false,
    val activeTheme: AppColorTheme = ThemeEngine.DefaultLight.toColorTheme(),
    val syncProvider: SyncProvider = SyncProvider.NONE
)

class EditorViewModel(
    private val db: AppDatabase,
    private val storageManager: StorageManager,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val syncEngine = SyncEngine(db.fileDao(), viewModelScope)

    // Store TextFieldValues for each paragraph to retain selection/cursor state
    private val _paragraphTextFieldValues = MutableStateFlow<Map<Int, TextFieldValue>>(emptyMap())
    val paragraphTextFieldValues: StateFlow<Map<Int, TextFieldValue>> = _paragraphTextFieldValues.asStateFlow()

    private var saveJob: Job? = null

    init {
        // Load default settings
        viewModelScope.launch {
            loadSettings()
            loadSelectedTheme()
        }
    }

    private suspend fun loadSettings() = withContext(Dispatchers.IO) {
        val viewModeSetting = db.settingDao().getSetting("view_mode")?.value ?: "RENDERED"
        val stickySetting = db.settingDao().getSetting("sticky_selection")?.value ?: "true"
        val syncProviderSetting = db.settingDao().getSetting("sync_provider")?.value ?: "NONE"

        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                viewMode = ViewMode.valueOf(viewModeSetting),
                stickySelection = stickySetting.toBoolean(),
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

    fun loadFile(projectId: Long, filePath: String) {
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { db.projectDao().getProjectById(projectId) } ?: return@launch
            val content = storageManager.readFile(project, filePath)
            val rawBlocks = MarkdownParser.splitDocument(content)
            val parsedBlocks = rawBlocks.map { raw ->
                ImmutableParagraphBlock(MarkdownParser.parseParagraph(raw))
            }.toImmutableList()

            // Initialize TextFieldValues
            val initialValues = parsedBlocks.mapIndexed { index, wrapper ->
                index to TextFieldValue(wrapper.block.rawText)
            }.toMap()

            _uiState.value = _uiState.value.copy(
                activeProject = project,
                activeFilePath = filePath,
                fileName = filePath.substringAfterLast('/'),
                paragraphs = ImmutableParagraphList(parsedBlocks),
                focusedParagraphIndex = -1,
                hemingwayMetrics = null // Clear metrics until requested
            )
            _paragraphTextFieldValues.value = initialValues
            
            addLog("Loaded file: $filePath")
        }
    }

    fun updateParagraph(index: Int, newValue: TextFieldValue) {
        val currentState = _uiState.value
        val items = currentState.paragraphs.items.toMutableList()
        val originalRaw = items[index].block.rawText

        if (newValue.text.contains('\n')) {
            val splitIndex = newValue.text.indexOf('\n')
            val firstPart = newValue.text.substring(0, splitIndex)
            val secondPart = newValue.text.substring(splitIndex + 1)

            // Update current paragraph to first part
            val updatedBlock = MarkdownParser.parseParagraph(firstPart, items[index].block.id)
            items[index] = ImmutableParagraphBlock(updatedBlock)

            _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
                put(index, TextFieldValue(firstPart))
            }

            _uiState.value = currentState.copy(
                paragraphs = ImmutableParagraphList(items.toImmutableList())
            )

            // Insert second part as a new paragraph
            insertParagraphAfter(index, secondPart)
            return
        }

        // If raw text hasn't changed, just update the TextFieldValue (cursor/selection change)
        _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
            put(index, newValue)
        }

        if (originalRaw == newValue.text) {
            return
        }

        // Perform O(1) parse differential update
        val updatedBlock = MarkdownParser.parseParagraph(newValue.text, items[index].block.id)
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

    fun cycleHeaderLevel(index: Int) {
        val currentState = _uiState.value
        val items = currentState.paragraphs.items.toMutableList()
        val originalValue = _paragraphTextFieldValues.value[index] ?: TextFieldValue(items[index].block.rawText)
        val text = originalValue.text

        val (newText, newSelectionOffset) = when {
            text.startsWith("### ") -> Pair(text.substring(4), -4)
            text.startsWith("## ") -> Pair("### " + text.substring(3), 1)
            text.startsWith("# ") -> Pair("## " + text.substring(2), 1)
            else -> Pair("# $text", 2)
        }

        val newSelection = androidx.compose.ui.text.TextRange(
            (originalValue.selection.start + newSelectionOffset).coerceIn(0, newText.length),
            (originalValue.selection.end + newSelectionOffset).coerceIn(0, newText.length)
        )

        val updatedBlock = MarkdownParser.parseParagraph(newText, items[index].block.id)
        items[index] = ImmutableParagraphBlock(updatedBlock)

        _paragraphTextFieldValues.value = _paragraphTextFieldValues.value.toMutableMap().apply {
            put(index, TextFieldValue(newText, newSelection))
        }

        _uiState.value = currentState.copy(
            paragraphs = ImmutableParagraphList(items.toImmutableList())
        )
        saveActiveFileAsync()
    }

    fun insertParagraphAfter(index: Int, content: String) {
        val currentState = _uiState.value
        val items = currentState.paragraphs.items.toMutableList()
        
        val newBlock = MarkdownParser.parseParagraph(content)
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
        
        // Update previous paragraph block
        val updatedPrevBlock = MarkdownParser.parseParagraph(mergedText, prevBlock.id)
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

        val rawBlocks = state.paragraphs.items.map { it.block.rawText }
        val documentContent = MarkdownParser.joinDocument(rawBlocks)
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
        if (state.isSidebarOpen) {
            runHemingwayAnalysis(documentContent)
        }
    }

    fun toggleSidebar(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSidebarOpen = open)
        if (open) {
            // Readability analysis triggers *only* when sidebar is opened
            viewModelScope.launch(Dispatchers.Default) {
                val rawBlocks = _uiState.value.paragraphs.items.map { it.block.rawText }
                val documentContent = MarkdownParser.joinDocument(rawBlocks)
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

    private fun addLog(message: String) {
        // Simple internal logging
    }
}
