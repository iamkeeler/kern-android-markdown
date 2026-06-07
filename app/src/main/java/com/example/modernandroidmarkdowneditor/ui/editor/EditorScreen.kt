package com.example.modernandroidmarkdowneditor.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modernandroidmarkdowneditor.data.local.ProjectEntity
import com.example.modernandroidmarkdowneditor.data.local.ThemeEntity
import com.example.modernandroidmarkdowneditor.data.sync.SyncProvider
import com.example.modernandroidmarkdowneditor.data.sync.SyncState
import com.example.modernandroidmarkdowneditor.parser.MarkdownBlockType
import com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme
import com.example.modernandroidmarkdowneditor.ui.theme.AppThemeJson
import com.example.modernandroidmarkdowneditor.ui.theme.ThemeEngine

@Composable
fun EditorScreen(
    projectId: Long,
    filePath: String,
    viewModel: EditorViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textFieldValues by viewModel.paragraphTextFieldValues.collectAsStateWithLifecycle()

    // Load file if changed
    LaunchedEffect(projectId, filePath) {
        viewModel.loadFile(projectId, filePath)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(uiState.activeTheme.background)) {
        val widthDp = maxWidth
        val isDualPane = widthDp >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Editor Canvas (Main container)
            Column(
                modifier = Modifier
                    .weight(if (isDualPane && uiState.isSidebarOpen) 0.65f else 1f)
                    .fillMaxHeight()
            ) {
                // Header (Breadcrumbs)
                EditorHeader(
                    projectRoot = uiState.activeProject?.name ?: "",
                    filePath = filePath,
                    theme = uiState.activeTheme,
                    onBackClick = onBackClick,
                    onSidebarToggle = { viewModel.toggleSidebar(!uiState.isSidebarOpen) }
                )

                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                // Main Editor Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = if (widthDp >= 720.dp) 24.dp else 16.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus()
                            viewModel.setParagraphFocus(-1)
                        }
                ) {
                    EditorCanvas(
                        state = uiState,
                        textFieldValues = textFieldValues,
                        viewModel = viewModel
                    )

                    // Floating Formatting Toolbar (appears on text selection)
                    val activeIndex = uiState.focusedParagraphIndex
                    val activeValue = if (activeIndex != -1) textFieldValues[activeIndex] else null
                    val hasSelection = activeValue != null && !activeValue.selection.collapsed

                    if (hasSelection) {
                        FloatingFormattingToolbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp),
                            theme = uiState.activeTheme,
                            onHeaderClick = { viewModel.cycleHeaderLevel(activeIndex) },
                            onFormat = { p, s ->
                                val delimiterStart = p
                                val delimiterEnd = s
                                val value = textFieldValues[activeIndex] ?: return@FloatingFormattingToolbar
                                val selStart = value.selection.start
                                val selEnd = value.selection.end
                                val text = value.text

                                val selectedText = text.substring(selStart, selEnd)
                                val formatted = delimiterStart + selectedText + delimiterEnd
                                val newText = text.substring(0, selStart) + formatted + text.substring(selEnd)

                                val newSelection = if (uiState.stickySelection) {
                                    androidx.compose.ui.text.TextRange(selStart + delimiterStart.length, selStart + delimiterStart.length + selectedText.length)
                                } else {
                                    androidx.compose.ui.text.TextRange(selStart + formatted.length)
                                }

                                viewModel.updateParagraph(activeIndex, TextFieldValue(newText, newSelection))
                            }
                        )
                    }
                }
            }

            // Collapsible Sidebar (Metrics, Settings, Themes, Sync Logs)
            if (uiState.isSidebarOpen) {
                SidebarPane(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (isDualPane) 320.dp else widthDp)
                        .background(uiState.activeTheme.surface),
                    state = uiState,
                    viewModel = viewModel,
                    onCloseClick = { viewModel.toggleSidebar(false) }
                )
            }
        }
    }
}

@Composable
fun EditorHeader(
    projectRoot: String,
    filePath: String,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    onBackClick: () -> Unit,
    onSidebarToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = "~",
                color = theme.accent,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onBackClick() }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
            
            Text(
                text = "/",
                color = theme.textMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${projectRoot}/",
                color = theme.textMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            
            val segments = filePath.split('/')
            val folders = if (segments.size > 1) segments.dropLast(1).joinToString("/") + "/" else ""
            val fileName = segments.last()
            
            if (folders.isNotEmpty()) {
                Text(
                    text = folders,
                    color = theme.textMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = fileName,
                color = theme.textPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(
            onClick = onSidebarToggle,
            modifier = Modifier.semantics { contentDescription = "Toggle info and settings sidebar" }
        ) {
            Text("⚙", color = theme.textMuted, fontSize = 20.sp)
        }
    }
}

@Composable
fun EditorCanvas(
    state: EditorUiState,
    textFieldValues: Map<Int, TextFieldValue>,
    viewModel: EditorViewModel
) {
    val lazyListState = rememberLazyListState()

    LaunchedEffect(state.focusedParagraphIndex) {
        if (state.focusedParagraphIndex != -1) {
            lazyListState.animateScrollToItem(state.focusedParagraphIndex)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp), // Cap max text width
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(state.paragraphs.items, key = { _, item -> item.block.id }) { index, wrapper ->
            val paragraph = wrapper.block
            val value = textFieldValues[index] ?: TextFieldValue(paragraph.rawText)
            val isFocused = state.focusedParagraphIndex == index

            val visualTransformation = remember(isFocused, state.viewMode, state.activeTheme) {
                MarkdownVisualTransformation(
                    isFocused = isFocused,
                    viewMode = state.viewMode,
                    tokenColor = state.activeTheme.textMuted,
                    codeBackgroundColor = state.activeTheme.codeBackground
                )
            }

            ParagraphField(
                value = value,
                onValueChange = { viewModel.updateParagraph(index, it) },
                isFocused = isFocused,
                onFocusChanged = { focused ->
                    if (focused) viewModel.setParagraphFocus(index)
                },
                visualTransformation = visualTransformation,
                theme = state.activeTheme,
                blockType = paragraph.blockType,
                viewMode = state.viewMode,
                onEnterPressed = { cursor ->
                    val text = value.text
                    val first = text.substring(0, cursor)
                    val second = text.substring(cursor)
                    viewModel.updateParagraph(index, TextFieldValue(first))
                    viewModel.insertParagraphAfter(index, second)
                },
                onBackspacePressed = {
                    viewModel.mergeParagraphWithPrevious(index)
                }
            )
        }
    }
}

@Composable
fun ParagraphField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    visualTransformation: MarkdownVisualTransformation,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    blockType: MarkdownBlockType,
    viewMode: ViewMode,
    onEnterPressed: (Int) -> Unit,
    onBackspacePressed: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val editorFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    val textStyle = if (viewMode == ViewMode.RAW_PLAIN_TEXT) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = theme.textPrimary, lineHeight = 20.sp)
    } else {
        when (blockType) {
            MarkdownBlockType.HEADER_1 -> TextStyle(fontFamily = editorFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = 30.sp)
            MarkdownBlockType.HEADER_2 -> TextStyle(fontFamily = editorFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = 26.sp)
            MarkdownBlockType.HEADER_3 -> TextStyle(fontFamily = editorFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = 22.sp)
            MarkdownBlockType.HEADER_4 -> TextStyle(fontFamily = editorFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = 20.sp)
            MarkdownBlockType.HEADER_5 -> TextStyle(fontFamily = editorFont, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = 18.sp)
            MarkdownBlockType.HEADER_6 -> TextStyle(fontFamily = editorFont, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = 17.sp)
            MarkdownBlockType.CODE_BLOCK -> TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = theme.textPrimary, lineHeight = 19.sp)
            MarkdownBlockType.BLOCKQUOTE -> TextStyle(fontFamily = editorFont, fontSize = 15.sp, fontStyle = FontStyle.Italic, color = theme.textMuted, lineHeight = 24.sp)
            else -> TextStyle(fontFamily = editorFont, fontSize = 15.sp, color = theme.textPrimary, lineHeight = 24.sp) // 1.6x Line height (15sp body)
        }
    }

    val paddingModifier = when {
        viewMode == ViewMode.RAW_PLAIN_TEXT -> Modifier.padding(vertical = 4.dp)
        blockType == MarkdownBlockType.BLOCKQUOTE -> Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        else -> Modifier.padding(vertical = 4.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(paddingModifier)
    ) {
        // Render left border for blockquotes
        if (viewMode != ViewMode.RAW_PLAIN_TEXT && blockType == MarkdownBlockType.BLOCKQUOTE) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(theme.accent)
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        if (keyEvent.key == Key.Enter && !keyEvent.isShiftPressed) {
                            onEnterPressed(value.selection.start)
                            true
                        } else if (keyEvent.key == Key.Backspace && value.selection.start == 0 && value.selection.end == 0) {
                            onBackspacePressed()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default
            )
        )
    }

    // Auto-request focus when this is the newly focused element
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isFocused, value.selection, value.text) {
        if (isFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }
}

@Composable
fun FloatingFormattingToolbar(
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    onHeaderClick: () -> Unit,
    onFormat: (prefix: String, suffix: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onHeaderClick,
            modifier = Modifier.semantics { contentDescription = "Toggle header level" }
        ) {
            Text("H", fontWeight = FontWeight.Black, color = theme.accent, fontSize = 16.sp)
        }
        IconButton(
            onClick = { onFormat("**", "**") },
            modifier = Modifier.semantics { contentDescription = "Format selection bold" }
        ) {
            Text("B", fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 16.sp)
        }
        IconButton(
            onClick = { onFormat("*", "*") },
            modifier = Modifier.semantics { contentDescription = "Format selection italic" }
        ) {
            Text("I", fontStyle = FontStyle.Italic, color = theme.textPrimary, fontSize = 16.sp)
        }
        IconButton(
            onClick = { onFormat("~~", "~~") },
            modifier = Modifier.semantics { contentDescription = "Format selection strikethrough" }
        ) {
            Text("S", textDecoration = TextDecoration.LineThrough, color = theme.textPrimary, fontSize = 16.sp)
        }
        IconButton(
            onClick = { onFormat("`", "`") },
            modifier = Modifier.semantics { contentDescription = "Format selection inline code" }
        ) {
            Text("C", fontFamily = FontFamily.Monospace, color = theme.textPrimary, fontSize = 15.sp)
        }
        IconButton(
            onClick = { onFormat("[", "](url)") },
            modifier = Modifier.semantics { contentDescription = "Format selection as link" }
        ) {
            Text("L", textDecoration = TextDecoration.Underline, color = theme.textPrimary, fontSize = 16.sp)
        }
    }
}

@Composable
fun SidebarPane(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val theme = state.activeTheme

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Document Info", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.semantics { contentDescription = "Close sidebar" }
            ) {
                Text("✕", color = theme.textPrimary, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf("Metrics", "Styles", "Themes", "Sync")
            tabs.forEachIndexed { idx, tabName ->
                val selected = activeTab == idx
                Text(
                    text = tabName,
                    color = if (selected) theme.background else theme.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) theme.accent else Color.Transparent)
                        .clickable { activeTab = idx }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Tab Contents
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (activeTab) {
                0 -> MetricsTab(state)
                1 -> ConfigurationTab(state, viewModel)
                2 -> ThemesTab(state, viewModel)
                3 -> SyncTab(state, viewModel)
            }
        }
    }
}

@Composable
fun MetricsTab(state: EditorUiState) {
    val theme = state.activeTheme
    val metrics = state.hemingwayMetrics

    if (metrics == null) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Analyzing Readability...", color = theme.textMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Readability card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("Readability", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(metrics.readabilityGrade, color = theme.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Target Grade level is Grade 8-9 for general audience.",
                color = theme.textMuted,
                fontSize = 12.sp
            )
        }

        // Standard counts
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val countItems = listOf(
                "Words" to metrics.wordCount.toString(),
                "Characters" to metrics.charCount.toString(),
                "Sentences" to metrics.sentenceCount.toString()
            )
            countItems.forEach { (label, value) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.background, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(value, color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(label, color = theme.textMuted, fontSize = 10.sp)
                }
            }
        }

        // Hemingway highlight stats
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Hemingway Suggestions", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            
            HemingwayStatRow("Very Hard Sentences", metrics.veryHardSentenceCount, Color(0xFFFF4D4D), theme)
            HemingwayStatRow("Hard Sentences", metrics.hardSentenceCount, Color(0xFFFFC04D), theme)
            HemingwayStatRow("Adverbs", metrics.adverbCount, Color(0xFF5CD6D6), theme)
            HemingwayStatRow("Passive Voices", metrics.passiveVoiceCount, Color(0xFFD65CD6), theme)
        }
    }
}

@Composable
fun HemingwayStatRow(label: String, count: Int, color: Color, theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = theme.textPrimary, fontSize = 12.sp)
        }
        Text(
            text = count.toString(),
            color = if (count > 0) color else theme.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ConfigurationTab(state: EditorUiState, viewModel: EditorViewModel) {
    val theme = state.activeTheme

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // View Modes configuration
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("View Configuration", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            val modes = listOf(
                ViewMode.RENDERED to "Live Preview",
                ViewMode.SYNTAX_HIGHLIGHTED to "Syntax Highlighted",
                ViewMode.RAW_PLAIN_TEXT to "Raw Plain-Text"
            )
            modes.forEach { (mode, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.changeViewMode(mode) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = theme.textPrimary, fontSize = 13.sp)
                    RadioButton(
                        selected = state.viewMode == mode,
                        onClick = { viewModel.changeViewMode(mode) },
                        colors = RadioButtonDefaults.colors(selectedColor = theme.accent)
                    )
                }
            }
        }

        // Font Family configuration
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("Font Family", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            val fonts = listOf(
                "serif" to "Serif (Book)",
                "sans-serif" to "Sans-Serif (Modern)",
                "monospace" to "Monospace (Code)"
            )
            fonts.forEach { (fontKey, fontLabel) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setEditorFontFamily(fontKey) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fontLabel,
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = when (fontKey) {
                            "serif" -> FontFamily.Serif
                            "sans-serif" -> FontFamily.SansSerif
                            else -> FontFamily.Monospace
                        }
                    )
                    RadioButton(
                        selected = theme.editorFontFamily.lowercase() == fontKey,
                        onClick = { viewModel.setEditorFontFamily(fontKey) },
                        colors = RadioButtonDefaults.colors(selectedColor = theme.accent)
                    )
                }
            }
        }

        // Sticky selection toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .clickable { viewModel.changeStickySelection(!state.stickySelection) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sticky Selection", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Keep selections active when applying toolbar formatting.", color = theme.textMuted, fontSize = 11.sp)
            }
            Switch(
                checked = state.stickySelection,
                onCheckedChange = { viewModel.changeStickySelection(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.accent.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun ThemesTab(state: EditorUiState, viewModel: EditorViewModel) {
    val theme = state.activeTheme
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputThemeJson by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Quick Selection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("Preset Themes", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            val presets = listOf(
                "Cream & Charcoal (Default)" to ThemeEngine.DefaultLight,
                "Inky Charcoal (Default)" to ThemeEngine.DefaultDark
            )
            presets.forEach { (name, presetJson) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val success = viewModel.selectThemeFromJson(ThemeEngine.serialize(presetJson))
                            if (success) Toast.makeText(context, "Applied theme: $name", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = theme.textPrimary, fontSize = 13.sp)
                    val isCurrent = theme.name == presetJson.name
                    if (isCurrent) {
                        Text("Active", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Custom Theme import / export
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("Theme Serialization", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val activePreset = if (theme.isDark) ThemeEngine.DefaultDark else ThemeEngine.DefaultLight
                    // Customise slightly to show serialization works
                    val currentThemeJson = AppThemeJson(
                        name = theme.name,
                        isDark = theme.isDark,
                        backgroundHex = String.format("#%06X", (0xFFFFFF and theme.background.value.toLong().toInt())),
                        surfaceHex = String.format("#%06X", (0xFFFFFF and theme.surface.value.toLong().toInt())),
                        textPrimaryHex = String.format("#%06X", (0xFFFFFF and theme.textPrimary.value.toLong().toInt())),
                        textMutedHex = String.format("#%06X", (0xFFFFFF and theme.textMuted.value.toLong().toInt())),
                        accentHex = String.format("#%06X", (0xFFFFFF and theme.accent.value.toLong().toInt())),
                        codeBackgroundHex = String.format("#%06X", (0xFFFFFF and theme.codeBackground.value.toLong().toInt())),
                        editorFontFamily = theme.editorFontFamily
                    )
                    val serialized = ThemeEngine.serialize(currentThemeJson)
                    clipboardManager.setText(AnnotatedString(serialized))
                    Toast.makeText(context, "Theme JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Export Current Theme", color = theme.background, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputThemeJson,
                onValueChange = { inputThemeJson = it },
                label = { Text("Paste Theme JSON", fontSize = 11.sp) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.textMuted,
                    focusedLabelColor = theme.accent
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (inputThemeJson.isNotBlank()) {
                        val success = viewModel.selectThemeFromJson(inputThemeJson)
                        if (success) {
                            Toast.makeText(context, "Custom theme applied successfully!", Toast.LENGTH_SHORT).show()
                            inputThemeJson = ""
                        } else {
                            Toast.makeText(context, "Invalid Theme JSON schema!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Import Custom Theme", color = theme.background, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SyncTab(state: EditorUiState, viewModel: EditorViewModel) {
    val theme = state.activeTheme
    val context = LocalContext.current
    val syncStatus by viewModel.syncEngine.syncStatus.collectAsState()
    val syncLogs by viewModel.syncEngine.syncLogs.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Sync Provider Selection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("Cloud Provider", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val providers = listOf(
                SyncProvider.NONE to "Local-Only (None)",
                SyncProvider.GOOGLE_DRIVE to "Google Drive",
                SyncProvider.DROPBOX to "Dropbox",
                SyncProvider.ONEDRIVE to "Microsoft OneDrive"
            )
            providers.forEach { (prov, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.changeSyncProvider(prov) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = theme.textPrimary, fontSize = 13.sp)
                    RadioButton(
                        selected = state.syncProvider == prov,
                        onClick = { viewModel.changeSyncProvider(prov) },
                        colors = RadioButtonDefaults.colors(selectedColor = theme.accent)
                    )
                }
            }
        }

        // Action Panel
        if (state.syncProvider != SyncProvider.NONE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.background, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text("Sync Controller", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(syncStatus.message, color = theme.textMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(12.dp))

                val isSyncing = syncStatus.state == SyncState.SYNCING
                Button(
                    onClick = { viewModel.triggerCloudSyncSweep() },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = theme.background,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Syncing...", color = theme.background, fontSize = 12.sp)
                        }
                    } else {
                        Text("Trigger Upload Sweep", color = theme.background, fontSize = 12.sp)
                    }
                }
            }

            // Sync logs console
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.background, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text("Sync Logs Console", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(theme.surface, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(syncLogs.size) { idx ->
                            Text(
                                text = syncLogs[idx],
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = theme.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}


