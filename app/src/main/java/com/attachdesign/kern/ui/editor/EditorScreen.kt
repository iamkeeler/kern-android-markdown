package com.attachdesign.kern.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import kotlinx.collections.immutable.toImmutableList
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.attachdesign.kern.parser.MarkdownParser
import com.attachdesign.kern.parser.MarkdownRenderer
import com.attachdesign.kern.parser.MarkdownElementType
import com.attachdesign.kern.parser.IndexRange
import com.attachdesign.kern.parser.IndexTransformationMatrix
import com.attachdesign.kern.ui.main.InputDialog
import com.attachdesign.kern.ui.theme.AppColorTheme
import com.attachdesign.kern.ui.settings.SettingsTabsContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.DocumentEditEngine
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditorScreen(
    projectId: Long,
    filePath: String,
    viewModel: EditorViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusOnStart: Boolean = false
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textFieldValues by viewModel.paragraphTextFieldValues.collectAsStateWithLifecycle()
    val theme = uiState.activeTheme

    LaunchedEffect(viewModel.documentTextFieldState) {
        snapshotFlow { viewModel.documentTextFieldState.text.toString() }
            .collectLatest(viewModel::onDocumentTextChanged)
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        InputDialog(
            title = "Rename File",
            label = "Document name",
            confirmText = "Rename",
            theme = theme,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameCurrentFile(newName) { newPath ->
                    Toast.makeText(context, "Document renamed", Toast.LENGTH_SHORT).show()
                }
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete document?", color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove this document. You cannot undo this action.", color = theme.textPrimary, fontSize = theme.typography.body) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCurrentFile {
                        onBackClick()
                    }
                }) {
                    Text("Delete", color = theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = theme.textMuted) }
            },
            containerColor = theme.surface
        )
    }

    val revealProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(projectId, filePath) {
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 500,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    // Load file if changed
    LaunchedEffect(projectId, filePath) {
        viewModel.loadFile(projectId, filePath, focusOnStart)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    val touchPos = remember(projectId, filePath) { com.attachdesign.kern.TouchTracker.lastTouchPosition }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(CircularRevealShape(revealProgress.value, touchPos.x, touchPos.y))
            .background(uiState.activeTheme.background)
    ) {
        val widthDp = maxWidth
        val isDualPane = widthDp >= theme.dimensions.dualPaneBreakpoint

        Row(modifier = Modifier.fillMaxSize()) {
            val editorWeight by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isDualPane && uiState.isSidebarOpen) 0.65f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                ),
                label = "editorWeight"
            )

            // Editor Canvas (Main container)
            Column(
                modifier = Modifier
                    .weight(editorWeight)
                    .fillMaxHeight()
            ) {
                // Header (Breadcrumbs)
                Box {
                    Column {
                        EditorHeader(
                            filePath = filePath,
                            theme = uiState.activeTheme,
                            isMetricsOpen = uiState.isReadabilityPopupOpen,
                            metricsContent = {
                                MetricsTab(
                                    state = uiState,
                                    onClose = { viewModel.toggleReadabilityPopup() }
                                )
                            },
                            onBackClick = onBackClick,
                            onCopyClick = {
                                val fullText = MarkdownRenderer.copyDocument(uiState.paragraphs.items.map { it.block })
                                clipboardManager.setText(AnnotatedString(fullText))
                                Toast.makeText(context, "Copied rendered text", Toast.LENGTH_SHORT).show()
                            },
                            onMetricsToggle = {
                                viewModel.toggleReadabilityPopup()
                            },
                            onSettingsToggle = {
                                val currentMode = uiState.sidebarMode
                                viewModel.toggleSidebar(if (currentMode == SidebarMode.SETTINGS) SidebarMode.CLOSED else SidebarMode.SETTINGS)
                            },
                            onTitleClick = {
                                showRenameDialog = true
                            },
                            onMoreOptionsAction = { action ->
                                when(action) {
                                    "Settings" -> {
                                        val currentMode = uiState.sidebarMode
                                        viewModel.toggleSidebar(if (currentMode == SidebarMode.SETTINGS) SidebarMode.CLOSED else SidebarMode.SETTINGS)
                                    }
                                    "Share" -> {
                                        viewModel.shareCurrentFile()
                                    }
                                    "Copy Markdown" -> {
                                        val markdown = MarkdownParser.joinParsedDocument(uiState.paragraphs.items.map { it.block })
                                        clipboardManager.setText(AnnotatedString(markdown))
                                        Toast.makeText(context, "Copied Markdown source", Toast.LENGTH_SHORT).show()
                                    }
                                    "Rename" -> {
                                        showRenameDialog = true
                                    }
                                    "Duplicate" -> {
                                        viewModel.duplicateCurrentFile { newPath ->
                                            Toast.makeText(context, "Document duplicated", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "Delete" -> {
                                        showDeleteDialog = true
                                    }
                                }
                            }
                        )

                        HorizontalDivider(
                            thickness = theme.dimensions.borderWidth,
                            color = uiState.activeTheme.textMuted.copy(alpha = 0.15f)
                        )
                    }
                }

                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                // Main Editor Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = if (widthDp >= theme.dimensions.largeScreenBreakpoint) theme.dimensions.spacingHuge else theme.dimensions.spacingExtraLarge)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus()
                            viewModel.setParagraphFocus(-1)
                        }
                ) {
                    // Floating Formatting Toolbar State
                    var isToolbarMinimized by remember { mutableStateOf(false) }

                    if (uiState.documentEditorEnabled) {
                        DocumentEditorField(
                            state = uiState,
                            viewModel = viewModel,
                            isToolbarMinimized = isToolbarMinimized
                        )
                    } else {
                        EditorCanvas(
                            state = uiState,
                            textFieldValues = textFieldValues,
                            viewModel = viewModel,
                            isToolbarMinimized = isToolbarMinimized
                        )
                    }

                    val activeIndex = uiState.focusedParagraphIndex
                    val activeValue = if (activeIndex != -1) textFieldValues[activeIndex] else null

                    androidx.compose.animation.AnimatedContent(
                        targetState = isToolbarMinimized,
                        transitionSpec = {
                            (fadeIn(animationSpec = androidx.compose.animation.core.tween(150, delayMillis = 50)) +
                             scaleIn(initialScale = 0.92f, animationSpec = androidx.compose.animation.core.tween(150, delayMillis = 50)))
                                .togetherWith(fadeOut(animationSpec = androidx.compose.animation.core.tween(100)) +
                                              scaleOut(targetScale = 0.92f, animationSpec = androidx.compose.animation.core.tween(100)))
                                .using(SizeTransform(clip = false) { _, _ ->
                                    androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    )
                                })
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = theme.dimensions.spacingExtraLarge, bottom = theme.dimensions.spacingHuge, top = theme.dimensions.spacingExtraLarge, start = theme.dimensions.spacingExtraLarge),
                        label = "ToolbarMinimizeAnimation"
                    ) { minimized ->
                        if (minimized) {
                            Box(
                                modifier = Modifier
                                    .shadow(elevation = theme.dimensions.elevationMedium, shape = RoundedCornerShape(theme.dimensions.spacingLarge), clip = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(theme.dimensions.spacingLarge))
                                        .background(uiState.activeTheme.surface)
                                        .clickable { isToolbarMinimized = false }
                                        .padding(theme.dimensions.spacingLarge)
                                ) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Expand formatting toolbar", tint = uiState.activeTheme.accent)
                                }
                            }
                        } else {
                            FloatingFormattingToolbar(
                                theme = uiState.activeTheme,
                                onHeaderClick = {
                                    if (uiState.documentEditorEnabled) viewModel.applyDocumentCommand(DocumentEditEngine.Command.CycleHeading)
                                    else if (activeIndex != -1) viewModel.cycleHeaderLevel(activeIndex)
                                },
                                onHeaderSet = { level ->
                                    if (uiState.documentEditorEnabled) viewModel.applyDocumentCommand(DocumentEditEngine.Command.SetHeading(level))
                                    else if (activeIndex != -1) viewModel.setHeaderLevel(activeIndex, level)
                                },
                                onIndentClick = {
                                    if (uiState.documentEditorEnabled) viewModel.applyDocumentCommand(DocumentEditEngine.Command.Indent)
                                    else if (activeIndex != -1) viewModel.indentParagraph(activeIndex)
                                },
                                onOutdentClick = {
                                    if (uiState.documentEditorEnabled) viewModel.applyDocumentCommand(DocumentEditEngine.Command.Outdent)
                                    else if (activeIndex != -1) viewModel.outdentParagraph(activeIndex)
                                },
                                onChecklistClick = {
                                    if (uiState.documentEditorEnabled) viewModel.applyDocumentCommand(DocumentEditEngine.Command.ToggleChecklist)
                                    else if (activeIndex != -1) viewModel.toggleChecklist(activeIndex)
                                },
                                onBulletClick = {
                                    if (uiState.documentEditorEnabled) viewModel.applyDocumentCommand(DocumentEditEngine.Command.ToggleBulletList)
                                    else if (activeIndex != -1) viewModel.toggleBulletList(activeIndex)
                                },
                                onMinimizeClick = { isToolbarMinimized = true },
                                onFormat = { p, s ->
                                    if (uiState.documentEditorEnabled) {
                                        viewModel.applyDocumentCommand(DocumentEditEngine.Command.Wrap(p, s))
                                    } else {
                                        if (activeIndex == -1) return@FloatingFormattingToolbar
                                        viewModel.formatParagraph(activeIndex, p, s)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Collapsible Sidebar (Metrics, Settings, Themes, Sync Logs)
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.isSidebarOpen,
                enter = androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                ),
                exit = androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                )
            ) {
                SidebarPane(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (isDualPane) theme.dimensions.sidebarWidth else widthDp)
                        .background(uiState.activeTheme.surface)
                        .statusBarsPadding(),
                    state = uiState,
                    viewModel = viewModel,
                    onCloseClick = { viewModel.toggleSidebar(SidebarMode.CLOSED) }
                )
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = theme.dimensions.spacingHuge)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorHeader(
    filePath: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    isMetricsOpen: Boolean,
    metricsContent: @Composable () -> Unit,
    onBackClick: () -> Unit,
    onCopyClick: () -> Unit,
    onMetricsToggle: () -> Unit,
    onSettingsToggle: () -> Unit,
    onMoreOptionsAction: (String) -> Unit = {},
    onTitleClick: () -> Unit = {}
) {
    val fileName = filePath.split('/').last()

    TopAppBar(
        title = {
            Text(
                text = fileName,
                color = theme.textPrimary,
                fontSize = theme.typography.subtitle,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(theme.dimensions.cornerRadiusSmall))
                    .clickable { onTitleClick() }
                    .padding(horizontal = theme.dimensions.spacingSmall, vertical = theme.dimensions.spacingTiny)
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(end = theme.dimensions.spacingSmall)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.accent,
                    modifier = Modifier.size(theme.dimensions.iconMedium)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onCopyClick,
                modifier = Modifier.semantics { contentDescription = "Copy all document text" }
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy document text",
                    tint = theme.textMuted,
                    modifier = Modifier.size(theme.dimensions.iconMedium)
                )
            }
            Box {
                IconButton(
                    onClick = onMetricsToggle,
                    modifier = Modifier.semantics { contentDescription = "Toggle readability metrics popup" }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Analytics,
                        contentDescription = "Readability metrics",
                        tint = theme.textMuted,
                        modifier = Modifier.size(theme.dimensions.iconMedium)
                    )
                }
                DropdownMenu(
                    expanded = isMetricsOpen,
                    onDismissRequest = onMetricsToggle,
                    modifier = Modifier
                        .width(280.dp)
                        .background(theme.surface)
                        .padding(theme.dimensions.spacingLarge)
                ) {
                    metricsContent()
                }
            }
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More Options",
                        tint = theme.textMuted,
                        modifier = Modifier.size(theme.dimensions.iconMedium)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { showMenu = false; onMoreOptionsAction("Settings") })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { showMenu = false; onMoreOptionsAction("Share") })
                    DropdownMenuItem(text = { Text("Copy Markdown") }, onClick = { showMenu = false; onMoreOptionsAction("Copy Markdown") })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onMoreOptionsAction("Rename") })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { showMenu = false; onMoreOptionsAction("Duplicate") })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onMoreOptionsAction("Delete") })
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = theme.textPrimary,
            navigationIconContentColor = theme.accent,
            actionIconContentColor = theme.textMuted
        )
    )
}

@Composable
fun EditorCanvas(
    state: EditorUiState,
    textFieldValues: Map<Int, TextFieldValue>,
    viewModel: EditorViewModel,
    isToolbarMinimized: Boolean = false
) {
    val theme = state.activeTheme
    val documentScrollState = rememberScrollState()

    val bottomPadding = if (isToolbarMinimized) theme.dimensions.spacingMassive else theme.dimensions.editorBottomPadding
    val documentSelectionManaged = state.viewMode == ViewMode.RENDERED && state.focusedParagraphIndex == -1

    val documentContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = theme.dimensions.maxTextLineWidth)
                .verticalScroll(documentScrollState)
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium)
        ) {
            state.paragraphs.items.forEachIndexed { index, item ->
                Column {
                    EditorParagraph(
                        state = state,
                        textFieldValues = textFieldValues,
                        viewModel = viewModel,
                        index = index,
                        documentSelectionManaged = documentSelectionManaged
                    )
                    if (documentSelectionManaged) {
                        val separatorLineBreaks = item.block.separatorAfter
                            .split(Regex("\\r\\n|\\r|\\n"))
                            .size - 1
                        if (separatorLineBreaks >= 2) {
                            Text(
                                // SelectionContainer inserts a line break between selectable Text
                                // nodes. Keep an empty selectable node for a blank paragraph and
                                // only add line breaks beyond the two structural boundaries.
                                text = "\n".repeat(separatorLineBreaks - 2),
                                color = Color.Transparent,
                                fontSize = 1.sp,
                                lineHeight = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (documentSelectionManaged) {
        SelectionContainer(content = documentContent)
    } else {
        documentContent()
    }
}

@Composable
private fun DocumentEditorField(
    state: EditorUiState,
    viewModel: EditorViewModel,
    isToolbarMinimized: Boolean
) {
    val theme = state.activeTheme
    val scrollState = rememberScrollState()
    val bottomPadding = if (isToolbarMinimized) {
        theme.dimensions.spacingMassive
    } else {
        // Leave only the space needed for the overlay so normal text remains visible behind it.
        theme.dimensions.spacingTitan
    }
    val editorFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    BasicTextField(
        state = viewModel.documentTextFieldState,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = theme.dimensions.maxTextLineWidth)
            .padding(bottom = bottomPadding)
            .semantics { contentDescription = "Document editor" }
            .onFocusChanged { focus -> viewModel.onDocumentEditorFocusChanged(focus.isFocused) },
        textStyle = TextStyle(
            color = theme.textPrimary,
            fontFamily = editorFont,
            fontSize = theme.typography.body * state.editorFontSizeScale,
            lineHeight = theme.typography.body * state.editorFontSizeScale * 1.55f
        ),
        lineLimits = TextFieldLineLimits.MultiLine(),
        keyboardOptions = KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
            imeAction = ImeAction.Default
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
        outputTransformation = remember(
            state.viewMode,
            state.editorFontSizeScale,
            theme.textMuted,
            theme.codeBackground
        ) {
            MarkdownDocumentOutputTransformation(
                viewMode = state.viewMode,
                bodySize = theme.typography.body * state.editorFontSizeScale,
                tokenColor = theme.textMuted,
                codeBackgroundColor = theme.codeBackground
            )
        },
        scrollState = scrollState
    )
}

@Composable
private fun EditorParagraph(
    state: EditorUiState,
    textFieldValues: Map<Int, TextFieldValue>,
    viewModel: EditorViewModel,
    index: Int,
    documentSelectionManaged: Boolean
) {
    val paragraph = state.paragraphs.items[index].block
    val value = textFieldValues[index] ?: TextFieldValue(paragraph.rawText)
    val isFocused = state.focusedParagraphIndex == index
    val visualTransformation = remember(paragraph.rawText, isFocused, value.selection, state.viewMode, state.activeTheme) {
        MarkdownVisualTransformation(
            isFocused = isFocused,
            selection = value.selection,
            viewMode = state.viewMode,
            tokenColor = state.activeTheme.textMuted,
            codeBackgroundColor = state.activeTheme.codeBackground
        )
    }

    ParagraphField(
        value = value,
        onValueChange = { viewModel.updateParagraph(index, it) },
        isFocused = isFocused,
        onFocusChanged = { focused -> if (focused) viewModel.setParagraphFocus(index) },
        visualTransformation = visualTransformation,
        theme = state.activeTheme,
        blockType = paragraph.blockType,
        viewMode = state.viewMode,
        fontSizeScale = state.editorFontSizeScale,
        paragraphIndex = index,
        totalParagraphs = state.paragraphs.items.size,
        documentSelectionManaged = documentSelectionManaged,
        resolveImageModel = viewModel::resolveMarkdownImage,
        imageResolutionKey = "${state.activeProject?.id}:${state.activeFilePath}",
        onEnterPressed = { cursor ->
            val first = value.text.substring(0, cursor)
            val second = value.text.substring(cursor)
            viewModel.updateParagraph(
                index,
                TextFieldValue(
                    text = first + "\n" + second,
                    selection = androidx.compose.ui.text.TextRange(cursor + 1)
                )
            )
        },
        onBackspacePressed = { viewModel.mergeParagraphWithPrevious(index) },
        onChecklistToggle = { viewModel.toggleChecklist(index) }
    )
}

fun splitTableLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var i = 0
    val n = line.length
    while (i < n) {
        val c = line[i]
        if (c == '\\' && i + 1 < n && line[i + 1] == '|') {
            sb.append('|')
            i += 2
        } else if (c == '|') {
            result.add(sb.toString())
            sb.clear()
            i++
        } else {
            sb.append(c)
            i++
        }
    }
    result.add(sb.toString())
    return result
}

fun renderCellText(text: String, theme: com.attachdesign.kern.ui.theme.AppColorTheme): AnnotatedString {
    val elements = MarkdownParser.parseInline(text, 0)
    val tokensToProcess = elements.filter {
        when (it.type) {
            MarkdownElementType.TOKEN_HEADER,
            MarkdownElementType.TOKEN_BOLD,
            MarkdownElementType.TOKEN_ITALIC,
            MarkdownElementType.TOKEN_STRIKETHROUGH,
            MarkdownElementType.TOKEN_INLINE_CODE,
            MarkdownElementType.TOKEN_LINK_TEXT,
            MarkdownElementType.TOKEN_LINK_URL,
            MarkdownElementType.TOKEN_BLOCKQUOTE,
            MarkdownElementType.TOKEN_LIST_BULLET,
            MarkdownElementType.TOKEN_ESCAPE_CHAR -> true
            else -> false
        }
    }.sortedBy { it.start }

    val strippedRanges = mutableListOf<IndexRange>()
    val sb = StringBuilder()
    var lastIdx = 0
    for (token in tokensToProcess) {
        if (token.start >= lastIdx) {
            sb.append(text.substring(lastIdx, token.start))
            strippedRanges.add(IndexRange(token.start, token.end))
            lastIdx = token.end
        }
    }
    if (lastIdx < text.length) {
        sb.append(text.substring(lastIdx))
    }
    val strippedText = sb.toString()
    val matrix = IndexTransformationMatrix(strippedRanges)

    val builder = AnnotatedString.Builder(strippedText)
    for (element in elements) {
        val start = element.start
        val end = element.end
        if (start >= end) continue

        when (element.type) {
            MarkdownElementType.BOLD -> {
                val tStart = matrix.originalToTransformed(start)
                val tEnd = matrix.originalToTransformed(end)
                if (tStart < tEnd) {
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), tStart, tEnd)
                }
            }
            MarkdownElementType.ITALIC -> {
                val tStart = matrix.originalToTransformed(start)
                val tEnd = matrix.originalToTransformed(end)
                if (tStart < tEnd) {
                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), tStart, tEnd)
                }
            }
            MarkdownElementType.STRIKETHROUGH -> {
                val tStart = matrix.originalToTransformed(start)
                val tEnd = matrix.originalToTransformed(end)
                if (tStart < tEnd) {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), tStart, tEnd)
                }
            }
            MarkdownElementType.INLINE_CODE -> {
                val tStart = matrix.originalToTransformed(start)
                val tEnd = matrix.originalToTransformed(end)
                if (tStart < tEnd) {
                    builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = theme.codeBackground), tStart, tEnd)
                }
            }
            MarkdownElementType.LINK -> {
                val tStart = matrix.originalToTransformed(start)
                val tEnd = matrix.originalToTransformed(end)
                if (tStart < tEnd) {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = theme.textMuted), tStart, tEnd)
                }
            }
            MarkdownElementType.IMAGE -> {
                val tStart = matrix.originalToTransformed(start)
                val tEnd = matrix.originalToTransformed(end)
                if (tStart < tEnd) {
                    builder.addStyle(SpanStyle(color = theme.textMuted, fontStyle = FontStyle.Italic, textDecoration = TextDecoration.Underline), tStart, tEnd)
                }
            }
            else -> {}
        }
    }
    return builder.toAnnotatedString()
}

@Composable
fun TableRender(
    rawText: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme
) {
    val immutableRows = remember(rawText) {
        val lines = rawText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        lines.filter { line ->
            !line.replace(" ", "").matches("^\\|[-:|]+\\|$".toRegex())
        }.map { line ->
            val splitResult = splitTableLine(line)
            splitResult.map { it.trim() }.filterIndexed { index, _ ->
                index > 0 && index < splitResult.lastIndex
            }.toImmutableList()
        }.toImmutableList()
    }

    if (immutableRows.isEmpty()) return

    val columnCount = remember(immutableRows) {
        immutableRows.maxOfOrNull { it.size } ?: 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = theme.dimensions.spacingMedium)
    ) {
        HorizontalDivider(thickness = 2.dp, color = theme.textPrimary)

        immutableRows.forEachIndexed { rowIndex, cells ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = theme.dimensions.spacingSmall),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (colIndex in 0 until columnCount) {
                    val cellText = cells.getOrNull(colIndex) ?: ""
                    Text(
                        text = remember(cellText, theme) { renderCellText(cellText, theme) },
                        color = theme.textPrimary,
                        fontSize = theme.typography.body,
                        fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (rowIndex == 0) {
                HorizontalDivider(thickness = 1.dp, color = theme.textPrimary.copy(alpha = 0.5f))
            }
        }

        HorizontalDivider(thickness = 2.dp, color = theme.textPrimary)
    }
}

@Composable
fun ParagraphField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    visualTransformation: MarkdownVisualTransformation,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    blockType: MarkdownBlockType,
    viewMode: ViewMode,
    fontSizeScale: Float = 1.0f,
    paragraphIndex: Int = 0,
    totalParagraphs: Int = 1,
    documentSelectionManaged: Boolean = false,
    resolveImageModel: suspend (String) -> String? = { it },
    imageResolutionKey: String = "",
    onEnterPressed: (Int) -> Unit,
    onBackspacePressed: () -> Unit,
    onChecklistToggle: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    val editorFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    val textStyle = if (viewMode == ViewMode.RAW_PLAIN_TEXT) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = (theme.typography.h4.value * fontSizeScale).sp, color = theme.textPrimary, lineHeight = (theme.typography.h2.value * fontSizeScale).sp)
    } else {
        when (blockType) {
            MarkdownBlockType.HEADER_1 -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.h1.value * fontSizeScale).sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = ((theme.typography.h1.value * 1.25f) * fontSizeScale).sp)
            MarkdownBlockType.HEADER_2 -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.h2.value * fontSizeScale).sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = ((theme.typography.h2.value * 1.3f) * fontSizeScale).sp)
            MarkdownBlockType.HEADER_3 -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.h3.value * fontSizeScale).sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = ((theme.typography.h3.value * 1.3f) * fontSizeScale).sp)
            MarkdownBlockType.HEADER_4 -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.h4.value * fontSizeScale).sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = (theme.typography.h2.value * fontSizeScale).sp)
            MarkdownBlockType.HEADER_5 -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.h5.value * fontSizeScale).sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = (theme.typography.title.value * fontSizeScale).sp)
            MarkdownBlockType.HEADER_6 -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.h6.value * fontSizeScale).sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = ((theme.typography.h6.value + 5f) * fontSizeScale).sp)
            MarkdownBlockType.CODE_BLOCK -> TextStyle(fontFamily = FontFamily.Monospace, fontSize = (theme.typography.body.value * fontSizeScale).sp, color = theme.textPrimary, lineHeight = ((theme.typography.body.value + 6f) * fontSizeScale).sp)
            MarkdownBlockType.BLOCKQUOTE -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.bodyLarge.value * fontSizeScale).sp, fontStyle = FontStyle.Italic, color = theme.textMuted, lineHeight = (theme.typography.h1.value * fontSizeScale).sp)
            else -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.bodyLarge.value * fontSizeScale).sp, color = theme.textPrimary, lineHeight = (theme.typography.h1.value * fontSizeScale).sp) // 1.6x Line height (15sp body)
        }
    }

    val paddingModifier = when {
        viewMode == ViewMode.RAW_PLAIN_TEXT -> Modifier.padding(vertical = theme.dimensions.spacingSmall)
        blockType == MarkdownBlockType.BLOCKQUOTE -> Modifier.padding(top = theme.dimensions.spacingSmall, bottom = theme.dimensions.spacingSmall)
        else -> Modifier.padding(vertical = theme.dimensions.spacingSmall)
    }

    val blockquoteBorderModifier = if (
        viewMode != ViewMode.RAW_PLAIN_TEXT && blockType == MarkdownBlockType.BLOCKQUOTE
    ) {
        Modifier.drawBehind {
            val strokeWidth = 4.dp.toPx()
            drawLine(
                color = theme.accent,
                start = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, 0f),
                end = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, size.height),
                strokeWidth = strokeWidth
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(blockquoteBorderModifier)
            .then(paddingModifier)
    ) {

        val contentModifier = if (viewMode != ViewMode.RAW_PLAIN_TEXT && blockType == MarkdownBlockType.BLOCKQUOTE) {
            Modifier.padding(start = theme.dimensions.spacingLarge)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(contentModifier)
        ) {
            val fullImage = remember(value.text) { fullBlockImage(value.text) }
            if (viewMode == ViewMode.RENDERED && !isFocused && blockType == MarkdownBlockType.HORIZONTAL_RULE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clickable { onFocusChanged(true) },
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.6f),
                        thickness = 1.dp,
                        color = theme.textMuted.copy(alpha = 0.3f)
                    )
                }
            } else if (viewMode == ViewMode.RENDERED && !isFocused && blockType == MarkdownBlockType.TABLE) {
                BlockSelectionBoundary(documentSelectionManaged) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFocusChanged(true) }
                    ) {
                        TableRender(rawText = value.text, theme = theme)
                    }
                }
            } else if (viewMode == ViewMode.RENDERED && !isFocused && fullImage != null) {
                val image = fullImage
                val imageModel by produceState<String?>(image.extra, image.extra, imageResolutionKey) {
                    this.value = image.extra?.let { resolveImageModel(it) } ?: image.extra
                }
                SubcomposeAsyncImage(
                    model = imageModel,
                    contentDescription = value.text.substring(image.start, image.end),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .clickable { onFocusChanged(true) }
                ) {
                    val imageState by painter.state.collectAsState()
                    when (imageState) {
                        is coil3.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                        is coil3.compose.AsyncImagePainter.State.Loading -> Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                        else -> BlockSelectionBoundary(documentSelectionManaged) {
                            Text(
                                text = value.text.substring(image.start, image.end),
                                style = textStyle.copy(color = theme.textMuted, fontStyle = FontStyle.Italic)
                            )
                        }
                    }
                }
            } else if (viewMode == ViewMode.RENDERED && !isFocused) {
                val renderedText = remember(value.text, visualTransformation) {
                    visualTransformation.filter(AnnotatedString(value.text)).text
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    BlockSelectionBoundary(documentSelectionManaged) {
                        RenderedMarkdownText(
                            rawText = value.text,
                            renderedText = renderedText,
                            textStyle = textStyle,
                            resolveImageModel = resolveImageModel,
                            imageResolutionKey = imageResolutionKey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFocusChanged(true) }
                                .semantics {
                                    contentDescription = "Block ${paragraphIndex + 1} of ${totalParagraphs}: ${blockType.name.lowercase().replace('_', ' ')}"
                                }
                        )
                    }
                    if (blockType == MarkdownBlockType.TASK_LIST) {
                        DisableSelection {
                            ChecklistToggleOverlay(onChecklistToggle)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = textStyle,
                        visualTransformation = visualTransformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Block ${paragraphIndex + 1} of ${totalParagraphs}: ${blockType.name.lowercase().replace('_', ' ')}"
                            }
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
            }
        }
    }

    // Auto-request focus when this is the newly focused element
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

}

@Composable
private fun RenderedMarkdownText(
    rawText: String,
    renderedText: AnnotatedString,
    textStyle: TextStyle,
    resolveImageModel: suspend (String) -> String?,
    imageResolutionKey: String,
    modifier: Modifier = Modifier
) {
    val imageSpans = remember(rawText) {
        MarkdownRenderer.render(MarkdownParser.parseParagraph(rawText)).spans
            .filter { it.type == MarkdownElementType.IMAGE && !it.extra.isNullOrBlank() }
    }
    if (imageSpans.isEmpty()) {
        Text(text = renderedText, style = textStyle, modifier = modifier)
        return
    }

    val inlineContent = remember(imageSpans, textStyle.fontSize) {
        imageSpans.mapIndexed { index, span ->
            val id = "markdown-image-$index"
            id to InlineTextContent(
                placeholder = Placeholder(
                    width = textStyle.fontSize * 2.5f,
                    height = textStyle.fontSize * 1.6f,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                val imageModel by produceState<String?>(span.extra, span.extra, imageResolutionKey) {
                    this.value = span.extra?.let { resolveImageModel(it) } ?: span.extra
                }
                SubcomposeAsyncImage(
                    model = imageModel,
                    contentDescription = renderedText.text.substring(span.start, span.end),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }.toMap()
    }
    val textWithImages = remember(renderedText, imageSpans) {
        buildAnnotatedString {
            var cursor = 0
            imageSpans.forEachIndexed { index, span ->
                if (cursor < span.start) append(renderedText.subSequence(cursor, span.start))
                appendInlineContent(
                    id = "markdown-image-$index",
                    alternateText = renderedText.text.substring(span.start, span.end)
                )
                cursor = span.end
            }
            if (cursor < renderedText.length) append(renderedText.subSequence(cursor, renderedText.length))
        }
    }
    Text(
        text = textWithImages,
        inlineContent = inlineContent,
        style = textStyle,
        modifier = modifier
    )
}

@Composable
private fun BoxScope.ChecklistToggleOverlay(onChecklistToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(28.dp)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onChecklistToggle
                )
                .semantics { contentDescription = "Toggle task list checkmark" }
        )
    }
}

private fun fullBlockImage(rawText: String) = MarkdownParser.parseParagraph(rawText).elements
    .firstOrNull { element ->
        element.type == MarkdownElementType.IMAGE &&
            element.constructStart == 0 &&
            element.constructEnd == rawText.length
    }

@Composable
private fun BlockSelectionBoundary(
    documentSelectionManaged: Boolean,
    content: @Composable () -> Unit
) {
    if (documentSelectionManaged) content() else SelectionContainer(content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingFormattingToolbar(
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    onHeaderClick: () -> Unit,
    onHeaderSet: (Int) -> Unit = {},
    onIndentClick: () -> Unit = {},
    onOutdentClick: () -> Unit = {},
    onChecklistClick: () -> Unit = {},
    onBulletClick: () -> Unit = {},
    onFormat: (prefix: String, suffix: String) -> Unit,
    onMinimizeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(max = 440.dp)
            .shadow(elevation = theme.dimensions.elevationMedium, shape = RoundedCornerShape(theme.dimensions.spacingLarge), clip = false)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.dimensions.spacingLarge))
                .background(theme.surface.copy(alpha = 0.88f))
                .padding(vertical = theme.dimensions.elevationMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Min)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = theme.dimensions.spacingMedium, end = theme.dimensions.spacingLarge),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium)
                ) {
                    var isHeaderMenuExpanded by remember { mutableStateOf(false) }
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(theme.dimensions.iconHuge)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .combinedClickable(
                                    onClick = onHeaderClick,
                                    onLongClick = { isHeaderMenuExpanded = true }
                                )
                                .semantics { contentDescription = "Toggle header level" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("H", fontWeight = FontWeight.Black, color = theme.accent, fontSize = theme.typography.subtitle)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).padding(bottom = 2.dp, end = 2.dp),
                                tint = theme.accent
                            )
                        }

                        DropdownMenu(
                            expanded = isHeaderMenuExpanded,
                            onDismissRequest = { isHeaderMenuExpanded = false }
                        ) {
                            (1..6).forEach { level ->
                                DropdownMenuItem(
                                    text = { Text("Header $level", color = theme.textPrimary) },
                                    onClick = {
                                        isHeaderMenuExpanded = false
                                        onHeaderSet(level)
                                    }
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .width(1.dp)
                            .background(theme.textMuted.copy(alpha = 0.25f))
                    )
                    IconButton(
                        onClick = { onFormat("**", "**") },
                        modifier = Modifier.semantics { contentDescription = "Format selection bold" }
                    ) {
                        Text("B", fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = theme.typography.subtitle)
                    }
                    IconButton(
                        onClick = { onFormat("*", "*") },
                        modifier = Modifier.semantics { contentDescription = "Format selection italic" }
                    ) {
                        Text("I", fontStyle = FontStyle.Italic, color = theme.textPrimary, fontSize = theme.typography.subtitle)
                    }
                    IconButton(
                        onClick = { onFormat("~~", "~~") },
                        modifier = Modifier.semantics { contentDescription = "Format selection strikethrough" }
                    ) {
                        Text("S", textDecoration = TextDecoration.LineThrough, color = theme.textPrimary, fontSize = theme.typography.subtitle)
                    }
                    IconButton(
                        onClick = { onFormat("`", "`") },
                        modifier = Modifier.semantics { contentDescription = "Format selection inline code" }
                    ) {
                        Text("C", fontFamily = FontFamily.Monospace, color = theme.textPrimary, fontSize = theme.typography.bodyLarge)
                    }
                    IconButton(
                        onClick = { onFormat("[", "](url)") },
                        modifier = Modifier.semantics { contentDescription = "Format selection as link" }
                    ) {
                        Text("L", textDecoration = TextDecoration.Underline, color = theme.textPrimary, fontSize = theme.typography.subtitle)
                    }
                    IconButton(
                        onClick = onChecklistClick,
                        modifier = Modifier.semantics { contentDescription = "Toggle checklist item" }
                    ) {
                        Text("☑", color = theme.textPrimary, fontSize = theme.typography.subtitle)
                    }
                    IconButton(
                        onClick = onBulletClick,
                        modifier = Modifier.semantics { contentDescription = "Toggle bullet list" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "Toggle bullet list",
                            tint = theme.textPrimary
                        )
                    }
                    IconButton(
                        onClick = onOutdentClick,
                        modifier = Modifier.semantics { contentDescription = "Outdent paragraph" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatIndentDecrease,
                            contentDescription = "Outdent paragraph",
                            tint = theme.textPrimary
                        )
                    }
                    IconButton(
                        onClick = onIndentClick,
                        modifier = Modifier.semantics { contentDescription = "Indent paragraph" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatIndentIncrease,
                            contentDescription = "Indent paragraph",
                            tint = theme.textPrimary
                        )
                    }
                }

                Spacer(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(24.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, theme.surface.copy(alpha = 0.88f))
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .background(theme.surface.copy(alpha = 0.88f))
                    .padding(end = theme.dimensions.spacingMedium, start = theme.dimensions.spacingSmall),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onMinimizeClick,
                    modifier = Modifier.semantics { contentDescription = "Minimize formatting toolbar" }
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(theme.dimensions.iconMedium))
                }
            }
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
    val theme = state.activeTheme
    val editorFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    val title = when (state.sidebarMode) {
        SidebarMode.METRICS -> "Readability"
        SidebarMode.SETTINGS -> "Settings"
        else -> ""
    }

    Column(modifier = modifier.padding(theme.dimensions.spacingExtraLarge)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = theme.textPrimary, fontSize = theme.typography.title, fontFamily = editorFont, fontWeight = FontWeight.Light)
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.semantics { contentDescription = "Close sidebar" }
            ) {
                Text("✕", color = theme.textPrimary, fontSize = theme.typography.subtitle)
            }
        }
        
        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )
        
        Spacer(modifier = Modifier.height(theme.dimensions.spacingExtraLarge))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.sidebarMode) {
                SidebarMode.SETTINGS -> {
                    SettingsTabsContent(
                        db = viewModel.database,
                        theme = theme,
                        modifier = Modifier.fillMaxSize(),

                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MetricsTab(
    state: EditorUiState,
    onClose: () -> Unit = {}
) {
    val theme = state.activeTheme
    val metrics = state.hemingwayMetrics

    if (metrics == null) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = theme.dimensions.spacingMassive),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Analyzing Readability...", color = theme.textMuted, fontSize = theme.typography.body)
            Spacer(modifier = Modifier.height(theme.dimensions.spacingMedium))
            CircularProgressIndicator(color = theme.accent, strokeWidth = theme.dimensions.spacingTiny, modifier = Modifier.size(theme.dimensions.spacingHuge))
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = theme.dimensions.popupMaxHeight).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingLarge)
    ) {
        // Top bar with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = theme.textMuted
                )
            }
        }
        // Readability section (flat, bookish)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = theme.dimensions.spacingLarge)
        ) {
            Text("READABILITY", color = theme.textMuted, fontSize = theme.typography.tiny, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(theme.dimensions.spacingSmall))
            Text(metrics.readabilityGrade, color = theme.accent, fontSize = theme.typography.h1, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(theme.dimensions.elevationMedium))
            Text(
                text = "Target Grade level is Grade 8-9 for general audience.",
                color = theme.textMuted,
                fontSize = theme.typography.small,
                lineHeight = theme.typography.subtitle
            )
        }

        HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))

        // Standard counts (flat, bookish)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = theme.dimensions.spacingLarge),
            horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingExtraLarge)
        ) {
            val countItems = listOf(
                "Words" to metrics.wordCount.toString(),
                "Characters" to metrics.charCount.toString(),
                "Sentences" to metrics.sentenceCount.toString()
            )
            countItems.forEach { (label, value) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(value, color = theme.textPrimary, fontSize = theme.typography.title, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(theme.dimensions.spacingTiny))
                    Text(label, color = theme.textMuted, fontSize = theme.typography.tiny)
                }
            }
        }

        HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))

        // Hemingway highlight stats (flat, bookish)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = theme.dimensions.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingLarge)
        ) {
            Text("HEMINGWAY SUGGESTIONS", color = theme.textMuted, fontSize = theme.typography.tiny, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(theme.dimensions.spacingSmall))

            HemingwayStatRow("Very Hard Sentences", metrics.veryHardSentenceCount, theme.danger, theme)
            HemingwayStatRow("Hard Sentences", metrics.hardSentenceCount, theme.warning, theme)
            HemingwayStatRow("Adverbs", metrics.adverbCount, theme.info, theme)
            HemingwayStatRow("Passive Voices", metrics.passiveVoiceCount, theme.success, theme)
        }
    }
}

@Composable
fun HemingwayStatRow(label: String, count: Int, color: Color, theme: com.attachdesign.kern.ui.theme.AppColorTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(theme.dimensions.spacingLarge)
                    .background(color, RoundedCornerShape(theme.dimensions.spacingTiny))
            )
            Spacer(modifier = Modifier.width(theme.dimensions.spacingMedium))
            Text(label, color = theme.textPrimary, fontSize = theme.typography.small)
        }
        Text(
            text = count.toString(),
            color = if (count > 0) color else theme.textMuted,
            fontSize = theme.typography.small,
            fontWeight = FontWeight.Bold
        )
    }
}

class CircularRevealShape(
    private val progress: Float,
    private val centerX: Float? = null,
    private val centerY: Float? = null
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val cx = centerX ?: (size.width / 2f)
        val cy = centerY ?: (size.height / 2f)
        val maxRadius = java.lang.Math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
        val radius = progress * maxRadius
        val path = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius))
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
