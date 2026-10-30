@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.Icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import com.attachdesign.kern.ui.common.AdaptiveMetricPair
import androidx.compose.ui.unit.Dp
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
import com.attachdesign.kern.ui.theme.toFontFamily
import com.attachdesign.kern.ui.theme.EditorTypography
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
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
import com.attachdesign.kern.ui.main.dialogs.*
import com.attachdesign.kern.ui.common.LocalDeletionSession
import androidx.compose.runtime.saveable.rememberSaveable
import com.attachdesign.kern.ui.theme.AppColorTheme
import com.attachdesign.kern.ui.settings.SettingsTabsContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.DocumentEditEngine
import kotlinx.coroutines.flow.collectLatest
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.stateDescription

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    projectId: Long,
    filePath: String,
    viewModel: EditorViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusOnStart: Boolean = false,
    touchOriginX: Float? = null,
    touchOriginY: Float? = null,
    companionEnabled: Boolean = true,
    onChooseFile: (() -> Unit)? = null,
    onIdentityChanged: (String, String) -> Unit = { _, _ -> }
) {
    val deletionSession = LocalDeletionSession.current
    SideEffect { viewModel.attachDeletionSession(deletionSession) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textFieldValues by viewModel.paragraphTextFieldValues.collectAsStateWithLifecycle()
    val theme = uiState.activeTheme
    val companion by viewModel.companion.state.collectAsStateWithLifecycle()
    val secondaryEditor = if (companionEnabled) androidx.lifecycle.viewmodel.compose.viewModel<EditorViewModel>(
        key = "second-editor-${viewModel.sessionId}"
    ) { viewModel.createSecondaryEditor() } else null
    val secondaryState = secondaryEditor?.uiState?.collectAsStateWithLifecycle()?.value
    val secondarySwitching = secondaryEditor?.switchingSecondaryFile?.collectAsStateWithLifecycle()?.value == true
    val foldSetting by viewModel.foldToSplit.collectAsStateWithLifecycle()
    val foldActionSetting by viewModel.foldShortcutAction.collectAsStateWithLifecycle()
    val foldAction = FoldShortcutAction.fromSetting(foldActionSetting?.value)
    val foldHaptics by viewModel.foldHaptics.collectAsStateWithLifecycle()
    val foldSound by viewModel.foldSound.collectAsStateWithLifecycle()
    val hapticsEnabled = foldHaptics?.value != "false"
    val soundEnabled = foldSound?.value == "true"
    val tactileFeedback = rememberTactileFeedbackHelper(soundEnabled)
    val foldEnvironment = rememberFoldEnvironment()
    val foldGeometry by foldEnvironment.geometry.collectAsStateWithLifecycle(initialValue = null)
    var showReferencePicker by remember { mutableStateOf(false) }
    var headerMenuOpen by remember { mutableStateOf(false) }
    var pointerHeld by remember { mutableStateOf(false) }
    val companionSavedState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val platformView = LocalView.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    if (showReferencePicker) ReferencePicker(viewModel,
        onSelect = { secondaryEditor?.switchSecondaryFile(it) },
        onDismiss = { showReferencePicker = false })

    LaunchedEffect(viewModel.documentTextFieldState) {
        snapshotFlow { viewModel.documentTextFieldState.text.toString() }
            .collectLatest(viewModel::onDocumentTextChanged)
    }

    var renameDialog by rememberSaveable(stateSaver = NamingRequest.Saver) { mutableStateOf<NamingRequest?>(null) }
    val showRenameDialog = renameDialog != null
    val renameRecords by viewModel.renameOperations.records.collectAsStateWithLifecycle()
    val openRename = {
        uiState.activeProject?.let { project ->
            renameDialog = viewModel.pendingRenameRequest ?: NamingRequest.create(NamingKind.RenameNode,
                project.id, project.name, uiState.activeFilePath.substringBeforeLast('/', ""),
                uiState.activeFilePath, uiState.fileName)
        }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    renameDialog?.let { request ->
        val record = renameRecords[request.id]
        val interrupted = request.inspectionRequired || (request.submitted && record == null)
        val partial = record is NamingOperationState.NeedsReconciliation
        LaunchedEffect(request.id, record) {
            when (record) {
                is NamingOperationState.Succeeded -> {
                    viewModel.renameOperations.consumeSuccess(request.id)
                    if (renameDialog?.id == request.id) renameDialog = null
                }
                NamingOperationState.Consumed -> if (renameDialog?.id == request.id) renameDialog = null
                is NamingOperationState.Failed -> renameDialog = request.copy(submitted = false)
                is NamingOperationState.NeedsReconciliation -> renameDialog = request.copy(submitted = false, inspectionRequired = true)
                is NamingOperationState.Inspected -> renameDialog = request.copy(submitted = false, inspectionRequired = !record.mayRetry)
                null -> if (request.submitted) {
                    viewModel.holdInterruptedRename(request)
                    renameDialog = request.copy(inspectionRequired = true)
                }
                NamingOperationState.Pending -> Unit
            }
        }
        val error = when (record) {
            is NamingOperationState.Failed -> record.message
            is NamingOperationState.NeedsReconciliation -> "The file may already have moved. Retry indexing before editing or saving."
            is NamingOperationState.Inspected -> record.message
            else -> if (interrupted) "Rename was interrupted. Refresh files before editing or retrying." else null
        }
        val renamed: (String) -> Unit = { path -> onIdentityChanged(request.targetPath, path) }
        InputDialog("Rename File", "Document name", "Rename", theme,
            request.draft, { renameDialog = request.copy(draft = it) }, record == NamingOperationState.Pending,
            error, { renameDialog = null }, {
                viewModel.submitRename(request, renamed)
                renameDialog = request.copy(submitted = true)
            }, canSubmit = !interrupted && !partial,
            recoveryText = if (partial) "Retry indexing" else if (interrupted) "Refresh files" else null,
            onRecovery = if (partial || interrupted) ({ viewModel.recoverRename(request, renamed) }) else null)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete document?", color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
            text = { Text("Delete '${uiState.fileName}'? You can Undo from the confirmation. When that offer ends, the document is permanently deleted.", color = theme.textPrimary, fontSize = theme.typography.body) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCurrentFile {
                        onBackClick()
                    }
                }) {
                    Text("Delete", color = theme.textDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = theme.textSecondary) }
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

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("Editor surface")
            .clip(CircularRevealShape(revealProgress.value, touchOriginX, touchOriginY))
            .background(uiState.activeTheme.background)
    ) {
        val widthDp = maxWidth
        val isDualPane = widthDp >= theme.dimensions.dualPaneBreakpoint
        val density = LocalDensity.current
        var windowBounds by remember { mutableStateOf(Rect.Zero) }
        val geometry = foldGeometry
        val firstPaneDp = geometry?.takeIf { it.isVertical }?.let { (it.left - windowBounds.left) / density.density }
            ?: (widthDp.value / 2)
        val secondPaneDp = geometry?.takeIf { it.isVertical }?.let { (windowBounds.right - it.right) / density.density }
            ?: (widthDp.value / 2)
        val canSplit = CompanionLayoutPolicy.canSplit(widthDp.value, firstPaneDp, secondPaneDp,
            density.fontScale * uiState.editorFontSizeScale, geometry?.isVertical == false)
        val companionVisible = canSplit && companion.isOpen && !uiState.isSidebarOpen
        DisposableEffect(secondaryEditor, companionVisible) {
            onDispose {
                if (companionVisible && secondaryEditor != null) {
                    secondaryEditor.onDocumentTextChanged(secondaryEditor.documentTextFieldState.text.toString())
                }
            }
        }
        val supportingVisible = companionVisible || (isDualPane && uiState.isSidebarOpen)
        val imeVisible = WindowInsets.ime.getBottom(density) > 0
        BackHandler(enabled = companion.isOpen && !imeVisible && !showReferencePicker && !headerMenuOpen) {
            if (uiState.isSidebarOpen) viewModel.toggleSidebar(SidebarMode.CLOSED) else viewModel.toggleCompanion()
        }
        val bend = rememberFoldGestureProgress(
            environment = foldEnvironment, enabled = foldSetting?.value == "true",
            eligible = companionEnabled && windowFocused && geometry?.isVertical != false && !showRenameDialog && !showDeleteDialog &&
                !showReferencePicker && !headerMenuOpen && uiState.sidebarMode != SidebarMode.SETTINGS &&
                (!companionVisible || secondaryState?.sidebarMode != SidebarMode.SETTINGS),
            resetKey = foldAction,
            inputSafe = { !pointerHeld && viewModel.documentTextFieldState.composition == null &&
                secondaryEditor?.documentTextFieldState?.composition == null },
            onToggle = {
                if (viewModel.performFoldShortcut(foldAction, canSplit)) {
                    tactileFeedback.triggerFoldFeedback(hapticsEnabled, soundEnabled)
                    val announcement = when (foldAction) {
                        FoldShortcutAction.METRICS -> if (viewModel.uiState.value.sidebarMode == SidebarMode.METRICS)
                            "Reader metrics opened" else "Reader metrics closed"
                        FoldShortcutAction.SECOND_EDITOR -> if (viewModel.companion.state.value.isOpen)
                            "Split view opened" else "Split view closed"
                    }
                    platformView.announceForAccessibility(announcement)
                } else {
                    Toast.makeText(context, "Second editor needs a wider window. Choose Reader metrics in Settings → Behavior → Fold shortcut.", Toast.LENGTH_LONG).show()
                }
            }
        )

        SupportingPaneScaffold(
            directive = PaneScaffoldDirective.Default.copy(
                maxHorizontalPartitions = if (supportingVisible) 2 else 1,
                horizontalPartitionSpacerSize = theme.dimensions.spacingMedium,
                defaultPanePreferredWidth = if (companionVisible) maxWidth / 2 else theme.dimensions.sidebarWidth,
                excludedBounds = geometry?.takeIf { supportingVisible && it.isSeparating && it.isVertical }?.let {
                    listOf(Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()))
                } ?: emptyList()
            ),
            value = ThreePaneScaffoldValue(
                primary = PaneAdaptedValue.Expanded,
                secondary = if (supportingVisible) PaneAdaptedValue.Expanded else PaneAdaptedValue.Hidden,
                tertiary = PaneAdaptedValue.Hidden
            ),
            modifier = Modifier.fillMaxSize().onGloballyPositioned { windowBounds = it.boundsInWindow() }
                .pointerInput(Unit) {
                    try {
                        awaitPointerEventScope {
                            while (true) pointerHeld = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                        }
                    } finally { pointerHeld = false }
                }
                .drawBehind {
                    if (bend.value > 0f) {
                        val hingeX = geometry?.let { it.left - windowBounds.left } ?: size.width / 2
                        drawLine(theme.accent.copy(alpha = bend.value * 0.6f),
                            androidx.compose.ui.geometry.Offset(hingeX, 0f),
                            androidx.compose.ui.geometry.Offset(hingeX, size.height),
                            theme.dimensions.borderWidth.toPx())
                    }
                },
            mainPane = {
                AnimatedPane {
                    Column(Modifier.fillMaxSize()) {
                        // Header (Breadcrumbs)
                        Box {
                            Column {
                                EditorHeader(
                                    filePath = uiState.activeFilePath.ifEmpty { filePath },
                                    theme = uiState.activeTheme,
                                    isMetricsActive = uiState.sidebarMode == SidebarMode.METRICS,
                                    canSplit = companionEnabled && canSplit,
                                    isCompanionOpen = companion.isOpen,
                                    onChooseFile = onChooseFile,
                                    onSplitToggle = viewModel::toggleCompanion,
                                    onMenuVisibilityChanged = { headerMenuOpen = it },
                                    onBackClick = {
                                        if (companion.isOpen) {
                                            if (uiState.isSidebarOpen) viewModel.toggleSidebar(SidebarMode.CLOSED)
                                            else viewModel.toggleCompanion()
                                        } else onBackClick()
                                    },
                                    onCopyClick = {
                                        val fullText = MarkdownRenderer.copyDocument(uiState.paragraphs.items.map { it.block })
                                        clipboardManager.setText(AnnotatedString(fullText))
                                        Toast.makeText(context, "Copied rendered text", Toast.LENGTH_SHORT).show()
                                    },
                                    onMetricsToggle = {
                                        val currentMode = uiState.sidebarMode
                                        viewModel.toggleSidebar(if (currentMode == SidebarMode.METRICS) SidebarMode.CLOSED else SidebarMode.METRICS)
                                    },
                                    onSettingsToggle = {
                                        val currentMode = uiState.sidebarMode
                                        viewModel.toggleSidebar(if (currentMode == SidebarMode.SETTINGS) SidebarMode.CLOSED else SidebarMode.SETTINGS)
                                    },
                                    onTitleClick = {
                                        openRename()
                                    },
                                    onMoreOptionsAction = { action ->
                                        when(action) {
                                            "Settings" -> {
                                                val currentMode = uiState.sidebarMode
                                                viewModel.toggleSidebar(if (currentMode == SidebarMode.SETTINGS) SidebarMode.CLOSED else SidebarMode.SETTINGS)
                                            }
                                            "Share" -> {
                                                viewModel.shareCurrentFile(context)
                                            }
                                            "Copy Markdown" -> {
                                                val markdown = MarkdownParser.joinParsedDocument(uiState.paragraphs.items.map { it.block })
                                                clipboardManager.setText(AnnotatedString(markdown))
                                                Toast.makeText(context, "Copied Markdown source", Toast.LENGTH_SHORT).show()
                                            }
                                            "Rename" -> {
                                                openRename()
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
                                    thickness = 2.dp,
                                    color = uiState.activeTheme.headerDivider
                                )
                            }
                        }

                        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                        val editorHorizontalPadding = if (!supportingVisible && widthDp >= theme.dimensions.largeScreenBreakpoint) {
                            theme.dimensions.spacingHuge
                        } else {
                            theme.dimensions.spacingExtraLarge
                        }
                        // Main Editor Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                // The editor canvas consumes the IME inset once, so the floating
                                // palette is laid out directly above the keyboard instead of behind it.
                                .imePadding()
                                .then(if (uiState.documentEditorEnabled) Modifier else Modifier
                                    .padding(top = theme.dimensions.spacingSmall,
                                        start = editorHorizontalPadding, end = editorHorizontalPadding)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        focusManager.clearFocus()
                                        viewModel.setParagraphFocus(-1)
                                    })
                        ) {
                            // Floating Formatting Toolbar State
                            var isToolbarMinimized by remember { mutableStateOf(false) }

                            val overscrollState = rememberElasticOverscrollState()

                            ElasticOverscrollContainer(
                                state = overscrollState,
                                accentColor = theme.accent,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (uiState.documentEditorEnabled) {
                                    DocumentEditorField(
                                        state = uiState,
                                        viewModel = viewModel,
                                        isToolbarMinimized = isToolbarMinimized,
                                        horizontalPadding = editorHorizontalPadding
                                    )
                                } else {
                                    EditorCanvas(
                                        state = uiState,
                                        textFieldValues = textFieldValues,
                                        viewModel = viewModel,
                                        isToolbarMinimized = isToolbarMinimized
                                    )
                                }
                            }

                            val activeIndex = uiState.focusedParagraphIndex
                            val activeValue = if (activeIndex != -1) textFieldValues[activeIndex] else null

                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val isImeOpen = WindowInsets.ime.getBottom(density) > 0
                            val targetBottomPadding = if (isImeOpen) {
                                theme.dimensions.spacingMedium
                            } else {
                                theme.dimensions.spacingMedium + 28.dp
                            }
                            val toolbarBottomPadding by androidx.compose.animation.core.animateDpAsState(
                                targetValue = targetBottomPadding,
                                label = "toolbarBottomPadding"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .zIndex(1f)
                                    .padding(horizontal = if (uiState.documentEditorEnabled) editorHorizontalPadding else 0.dp)
                                    .padding(bottom = toolbarBottomPadding)
                            ) {
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
                                    modifier = Modifier.align(if (isToolbarMinimized) Alignment.BottomEnd else Alignment.BottomCenter),
                                    label = "ToolbarMinimizeAnimation"
                                ) { minimized ->
                                    if (minimized) {
                                        FormattingPaletteFab(
                                            theme = uiState.activeTheme,
                                            onClick = { isToolbarMinimized = false }
                                        )
                                    } else {
                                        FloatingFormattingToolbar(
                                            theme = uiState.activeTheme,
                                            canUndo = if (uiState.documentEditorEnabled) viewModel.documentTextFieldState.undoState.canUndo else false,
                                            onUndoClick = { viewModel.undo() },
                                            canRedo = if (uiState.documentEditorEnabled) viewModel.documentTextFieldState.undoState.canRedo else false,
                                            onRedoClick = { viewModel.redo() },
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
                    }

                }
            },
            supportingPane = {
                AnimatedPane {
                    if (!uiState.isSidebarOpen) {
                        companionSavedState.SaveableStateProvider("companion") {
                            val secondaryProject = secondaryState?.activeProject
                            val secondaryPath = secondaryState?.activeFilePath.orEmpty()
                            if (secondarySwitching) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            } else if (secondaryEditor != null && secondaryProject != null && secondaryPath.isNotEmpty()) {
                                EditorScreen(secondaryProject.id, secondaryPath, secondaryEditor,
                                    onBackClick = viewModel::toggleCompanion,
                                    modifier = Modifier.fillMaxSize().statusBarsPadding().semantics { contentDescription = "Second editor pane" },
                                    companionEnabled = false,
                                    onChooseFile = { showReferencePicker = true })
                            } else {
                                Box(Modifier.fillMaxSize().statusBarsPadding().semantics { contentDescription = "Second editor pane" }) {
                                    IconButton(onClick = viewModel::toggleCompanion, modifier = Modifier.align(Alignment.TopEnd)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close split view", tint = theme.textPrimary)
                                    }
                                    Column(Modifier.align(Alignment.Center).padding(theme.dimensions.spacingExtraLarge),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Open a second document", color = theme.textPrimary)
                                        TextButton(onClick = { showReferencePicker = true }) { Text("Choose file", color = theme.textPrimary) }
                                    }
                                }
                            }
                        }
                    } else if (isDualPane && uiState.isSidebarOpen) {
                        SidebarPane(state = uiState, viewModel = viewModel,
                            onCloseClick = { viewModel.toggleSidebar(SidebarMode.CLOSED) },
                            modifier = Modifier.fillMaxSize().background(theme.surface).statusBarsPadding())
                    }
                }
            }
        )

        // Single-pane Readability Sheet (ModalBottomSheet)
        if (!isDualPane && uiState.sidebarMode == SidebarMode.METRICS) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.toggleSidebar(SidebarMode.CLOSED) },
                containerColor = uiState.activeTheme.surface,
                dragHandle = {
                    BottomSheetDefaults.DragHandle(
                        color = uiState.activeTheme.textMuted.copy(alpha = 0.4f)
                    )
                }
            ) {
                MetricsTab(
                    state = uiState,
                    onClose = { viewModel.toggleSidebar(SidebarMode.CLOSED) }
                )
            }
        }

        // Single-pane Collapsible Settings Sidebar (Overlay)
        if (!isDualPane && uiState.sidebarMode == SidebarMode.SETTINGS) {
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
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                SidebarPane(
                    modifier = Modifier
                        .fillMaxSize()
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
    onBackClick: () -> Unit,
    onCopyClick: () -> Unit,
    onMetricsToggle: () -> Unit,
    onSettingsToggle: () -> Unit,
    isMetricsActive: Boolean = false,
    onMoreOptionsAction: (String) -> Unit = {},
    onTitleClick: () -> Unit = {},
    onChooseFile: (() -> Unit)? = null,
    onIdentityChanged: (String, String) -> Unit = { _, _ -> },
    canSplit: Boolean = false,
    isCompanionOpen: Boolean = false,
    onSplitToggle: () -> Unit = {},
    onMenuVisibilityChanged: (Boolean) -> Unit = {}
) {
    val fileName = filePath.split('/').last()

    TopAppBar(
        modifier = Modifier.testTag("Editor header"),
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
            if (onChooseFile != null) {
                IconButton(onClick = onChooseFile) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = "Choose another file", tint = theme.textPrimary)
                }
            }
            if (canSplit || isCompanionOpen) {
                IconButton(onClick = onSplitToggle,
                    modifier = Modifier.semantics { stateDescription = if (isCompanionOpen) "Open" else "Closed" }) {
                    Icon(Icons.Outlined.VerticalSplit,
                        contentDescription = if (isCompanionOpen) "Close split view" else "Open split view",
                        tint = if (isCompanionOpen) theme.accent else theme.textMuted)
                }
            }
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
            IconButton(
                onClick = onMetricsToggle,
                modifier = Modifier.semantics { contentDescription = "Toggle readability metrics popup" }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = "Readability metrics",
                    tint = if (isMetricsActive) theme.accent else theme.textMuted,
                    modifier = Modifier.size(theme.dimensions.iconMedium)
                )
            }
            var showMenu by remember { mutableStateOf(false) }
            LaunchedEffect(showMenu) { onMenuVisibilityChanged(showMenu) }
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
    isToolbarMinimized: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    val theme = state.activeTheme
    val scrollState = rememberScrollState()
    val editorFont = theme.toFontFamily()
    val metrics = EditorTypography.metrics(state.editorFontSizeScale)
    val inputTransformation = remember { MarkdownDocumentInputTransformation() }
    val bottomScrollPadding = if (isToolbarMinimized) 100.dp else 220.dp

    // Capture the finite viewport before verticalScroll supplies unbounded content height.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).testTag("Document scroll")) {
        BasicTextField(
            state = viewModel.documentTextFieldState,
            readOnly = state.isFileOperationPending,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Document editor" }
                .onFocusChanged { focus -> viewModel.onDocumentEditorFocusChanged(focus.isFocused) },
            textStyle = TextStyle(
                color = theme.textPrimary,
                fontFamily = editorFont,
                fontSize = metrics.fontSize,
                lineHeight = metrics.lineHeight,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.None,
                    mode = LineHeightStyle.Mode.Minimum
                )
            ),
            lineLimits = TextFieldLineLimits.MultiLine(),
            keyboardOptions = KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                imeAction = ImeAction.Default
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.controlAccent),
            decorator = TextFieldDecorator { innerTextField ->
                // Native decoration expands the hit area without intercepting text gestures.
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = viewportHeight)
                        .padding(start = horizontalPadding, end = horizontalPadding,
                            top = theme.dimensions.spacingExtraLarge, bottom = bottomScrollPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(Modifier.widthIn(max = theme.dimensions.maxTextLineWidth).fillMaxWidth()
                        .testTag("Document text")) {
                        innerTextField()
                    }
                }
            },
            inputTransformation = inputTransformation,
            outputTransformation = remember(
                state.viewMode,
                metrics.fontSize,
                metrics.lineHeight,
                theme.textSecondary,
                theme.codeBackground
            ) {
                MarkdownDocumentOutputTransformation(
                    viewMode = state.viewMode,
                    bodySize = metrics.fontSize,
                    tokenColor = theme.textSecondary,
                    codeBackgroundColor = theme.codeBackground
                )
            }
        )
        }
    }
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
            tokenColor = state.activeTheme.textSecondary,
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
    val styleProvider = MarkdownSpanStyleProvider(theme.textSecondary, theme.codeBackground)
    val elements = MarkdownParser.parseInline(text, 0)
    val tokensToProcess = elements.filter { it.type.isSyntaxToken }.sortedBy { it.start }

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

        styleProvider.styleForElement(element.type)?.let { style ->
            val tStart = matrix.originalToTransformed(start)
            val tEnd = matrix.originalToTransformed(end)
            if (tStart < tEnd) {
                builder.addStyle(style, tStart, tEnd)
            }
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

    val editorFont = theme.toFontFamily()

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
            MarkdownBlockType.BLOCKQUOTE -> TextStyle(fontFamily = editorFont, fontSize = (theme.typography.bodyLarge.value * fontSizeScale).sp, fontStyle = FontStyle.Italic, color = theme.textSecondary, lineHeight = (theme.typography.h1.value * fontSizeScale).sp)
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
                                style = textStyle.copy(color = theme.textSecondary, fontStyle = FontStyle.Italic)
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
internal fun RenderedMarkdownText(
    rawText: String,
    renderedText: AnnotatedString,
    textStyle: TextStyle,
    resolveImageModel: suspend (String) -> String?,
    imageResolutionKey: String,
    modifier: Modifier = Modifier,
    projectedImageSpans: List<com.attachdesign.kern.parser.RenderSpan>? = null
) {
    val imageSpans = remember(rawText, projectedImageSpans) {
        projectedImageSpans ?: MarkdownRenderer.render(MarkdownParser.parseParagraph(rawText)).spans
            .filter { it.type == MarkdownElementType.IMAGE && !it.extra.isNullOrBlank() }
    }
    if (imageSpans.isEmpty()) {
        Text(text = renderedText, style = textStyle, modifier = modifier)
        return
    }

    val currentResolver = rememberUpdatedState(resolveImageModel)
    val inlineContent = remember(imageSpans, textStyle.fontSize, imageResolutionKey, renderedText) {
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
                    this.value = span.extra?.let { currentResolver.value(it) } ?: span.extra
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
    onUndoClick: () -> Unit = {},
    canUndo: Boolean = false,
    onRedoClick: () -> Unit = {},
    canRedo: Boolean = false,
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fullPaletteWidth = theme.dimensions.buttonHeight * 8 + theme.dimensions.spacingSmall * 10 +
            theme.dimensions.spacingTiny * 2 + theme.dimensions.dividerThickness
        val showHistory = maxWidth >= fullPaletteWidth
        // Six native 48dp controls fit the narrow writer pane; do not shrink touch targets.
        val compactSpacing = if (maxWidth >= theme.dimensions.buttonHeight * 6 + theme.dimensions.spacingTiny * 7)
            theme.dimensions.spacingTiny else 0.dp
        val palettePadding = if (showHistory) theme.dimensions.spacingSmall else compactSpacing
        val paletteSpacing = if (showHistory) theme.dimensions.spacingSmall else compactSpacing
        Surface(
            modifier = Modifier.wrapContentWidth(),
            shape = RoundedCornerShape(theme.dimensions.cornerRadiusLarge),
            color = theme.surface,
            contentColor = theme.textPrimary,
            shadowElevation = theme.dimensions.elevationMedium
        ) {
            PaletteRow(theme, Modifier.padding(palettePadding), spacing = paletteSpacing) {
                if (showHistory) {
                    FormatAction(
                        description = "Undo",
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (canUndo) theme.textPrimary else theme.textMuted.copy(alpha = 0.35f),
                                modifier = Modifier.size(theme.dimensions.iconMedium)
                            )
                        },
                        enabled = canUndo,
                        onClick = onUndoClick
                    )
                    FormatAction(
                        description = "Redo",
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (canRedo) theme.textPrimary else theme.textMuted.copy(alpha = 0.35f),
                                modifier = Modifier.size(theme.dimensions.iconMedium)
                            )
                        },
                        enabled = canRedo,
                        onClick = onRedoClick
                    )
                    VerticalDivider(
                        modifier = Modifier
                            .height(20.dp)
                            .padding(horizontal = 2.dp),
                        thickness = 1.dp,
                        color = theme.textMuted.copy(alpha = 0.2f)
                    )
                }
                HeaderAction(theme, onHeaderClick, onHeaderSet)
                FormatAction("Format selection bold", { Text("B", fontWeight = FontWeight.Bold, fontSize = theme.typography.subtitle) }, onClick = { onFormat("**", "**") })
                FormatAction("Format selection italic", { Text("I", fontStyle = FontStyle.Italic, fontSize = theme.typography.subtitle) }, onClick = { onFormat("*", "*") })
                FormatAction("Toggle bullet list", { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) }, onClick = onBulletClick)
                OverflowFormattingActions(theme, onFormat, onChecklistClick, onIndentClick, onOutdentClick,
                    showHistory = !showHistory, canUndo = canUndo, canRedo = canRedo,
                    onUndoClick = onUndoClick, onRedoClick = onRedoClick)
                FormatAction("Minimize formatting toolbar", { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(theme.dimensions.iconMedium), tint = theme.textMuted) }, onClick = onMinimizeClick)
            }
        }
    }
}

@Composable
private fun PaletteRow(theme: AppColorTheme, modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = theme.dimensions.spacingSmall,
    content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun OverflowFormattingActions(
    theme: AppColorTheme,
    onFormat: (String, String) -> Unit,
    onChecklistClick: () -> Unit,
    onIndentClick: () -> Unit,
    onOutdentClick: () -> Unit,
    showHistory: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FormatAction("More formatting actions", { Icon(Icons.Outlined.MoreVert, null) }, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
        ) {
            if (showHistory) {
                DropdownMenuItem(text = { Text("Undo") }, enabled = canUndo,
                    onClick = { expanded = false; onUndoClick() })
                DropdownMenuItem(text = { Text("Redo") }, enabled = canRedo,
                    onClick = { expanded = false; onRedoClick() })
            }
            DropdownMenuItem(
                text = { Text("Strikethrough") },
                leadingIcon = { Text("S", textDecoration = TextDecoration.LineThrough) },
                onClick = { expanded = false; onFormat("~~", "~~") }
            )
            DropdownMenuItem(
                text = { Text("Inline code") },
                leadingIcon = { Text("C", fontFamily = FontFamily.Monospace) },
                onClick = { expanded = false; onFormat("`", "`") }
            )
            DropdownMenuItem(
                text = { Text("Link") },
                leadingIcon = { Text("L", textDecoration = TextDecoration.Underline) },
                onClick = { expanded = false; onFormat("[", "](url)") }
            )
            DropdownMenuItem(
                text = { Text("Checklist") },
                leadingIcon = { Text("☑") },
                onClick = { expanded = false; onChecklistClick() }
            )
            DropdownMenuItem(
                text = { Text("Outdent") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, null) },
                onClick = { expanded = false; onOutdentClick() }
            )
            DropdownMenuItem(
                text = { Text("Indent") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, null) },
                onClick = { expanded = false; onIndentClick() }
            )
        }
    }
}

@Composable
private fun FormatAction(
    description: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        icon()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderAction(theme: AppColorTheme, onHeaderClick: () -> Unit, onHeaderSet: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(theme.dimensions.iconHuge).combinedClickable(onClick = onHeaderClick, onLongClick = { expanded = true }).semantics { contentDescription = "Toggle header level" },
            contentAlignment = Alignment.Center
        ) {
            Text("H", fontWeight = FontWeight.Black, fontSize = theme.typography.subtitle)
            Icon(Icons.Default.ArrowDropDown, null, Modifier.align(Alignment.BottomEnd).size(16.dp).padding(bottom = 2.dp, end = 2.dp), tint = theme.accent)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
        ) {
            (1..6).forEach { level -> DropdownMenuItem(text = { Text("Header $level") }, onClick = { expanded = false; onHeaderSet(level) }) }
        }
    }
}

@Composable
private fun FormattingPaletteFab(theme: AppColorTheme, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = theme.surface,
        contentColor = theme.accent,
        shadowElevation = theme.dimensions.elevationMedium
    ) {
        Box(Modifier.size(theme.dimensions.iconHuge), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Expand formatting toolbar")
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
    val editorFont = theme.toFontFamily()
    val title = when (state.sidebarMode) {
        SidebarMode.METRICS -> "Readability"
        SidebarMode.SETTINGS -> "Settings"
        else -> ""
    }

    BoxWithConstraints(modifier = modifier) {
    val gutter = if (maxWidth >= 600.dp) 24.dp else 16.dp
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = gutter, vertical = 16.dp)) {
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
            thickness = 2.dp,
            color = theme.headerDivider
        )
        
        Spacer(modifier = Modifier.height(theme.dimensions.spacingExtraLarge))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.sidebarMode) {
                SidebarMode.SETTINGS -> {
                    SettingsTabsContent(
                        db = viewModel.database,
                        theme = theme,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SidebarMode.METRICS -> {
                    MetricsTab(
                        state = state,
                        onClose = onCloseClick,
                        horizontalPadding = 0.dp
                    )
                }
                else -> {}
            }
        }
    }
    }
}

@Composable
fun MetricsTab(
    state: EditorUiState,
    onClose: () -> Unit = {},
    horizontalPadding: Dp? = null
) {
    val theme = state.activeTheme
    val metrics = state.hemingwayMetrics

    if (metrics == null) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(theme.dimensions.spacingMassive),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Analyzing Readability...", color = theme.textSecondary, fontSize = theme.typography.body)
            Spacer(modifier = Modifier.height(theme.dimensions.spacingMedium))
            CircularProgressIndicator(color = theme.accent, strokeWidth = theme.dimensions.spacingTiny, modifier = Modifier.size(theme.dimensions.spacingHuge))
        }
        return
    }

    val readingTime = if (metrics.wordCount < 100) "< 1 min" else "${maxOf(1, metrics.wordCount / 200)} min"
    val countItems = listOf(
        "Words" to metrics.wordCount.toString(),
        "Characters" to metrics.charCount.toString(),
        "Sentences" to metrics.sentenceCount.toString(),
        "Reading Time" to readingTime
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val gutter = horizontalPadding ?: if (maxWidth >= 600.dp) 24.dp else 16.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = gutter, vertical = theme.dimensions.spacingMedium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingLarge)
    ) {
        // Readability section (flat, bookish)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = theme.dimensions.spacingSmall)
        ) {
            Text("READABILITY", color = theme.textSecondary, fontSize = theme.typography.tiny, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(theme.dimensions.spacingSmall))
            Text(metrics.readabilityGrade, color = theme.textAccent, fontSize = theme.typography.h1, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(theme.dimensions.spacingSmall))
            Text(
                text = "Target Grade level is Grade 8-9 for general audience.",
                color = theme.textSecondary,
                fontSize = theme.typography.helper,
                lineHeight = theme.typography.helperLineHeight
            )
        }

        HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))

        // Retain paired tiles where their available pane width supports them.
        countItems.chunked(2).forEach { pair ->
            AdaptiveMetricPair(
                first = { tileModifier -> ReadabilityCount(pair[0], theme, tileModifier) },
                second = { tileModifier -> ReadabilityCount(pair[1], theme, tileModifier) }
            )
        }

        HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))

        // Hemingway highlight stats (flat, bookish)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = theme.dimensions.spacingSmall),
            verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingLarge)
        ) {
            Text("HEMINGWAY SUGGESTIONS", color = theme.textSecondary, fontSize = theme.typography.tiny, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(theme.dimensions.spacingSmall))

            HemingwayStatRow("Very Hard Sentences", metrics.veryHardSentenceCount, theme.danger, theme)
            HemingwayStatRow("Hard Sentences", metrics.hardSentenceCount, theme.warning, theme)
            HemingwayStatRow("Adverbs", metrics.adverbCount, theme.info, theme)
            HemingwayStatRow("Passive Voices", metrics.passiveVoiceCount, theme.success, theme)
        }
        Spacer(modifier = Modifier.height(theme.dimensions.spacingHuge))
    }
    }
}

@Composable
private fun ReadabilityCount(
    item: Pair<String, String>,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    modifier: Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = theme.surface.copy(alpha = 0.75f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.second, color = theme.textPrimary, fontSize = theme.typography.title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.first, color = theme.textSecondary, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight)
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
        Row(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            color = if (count > 0) theme.textPrimary else theme.textSecondary,
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
