package com.attachdesign.kern.ui.main

import com.attachdesign.kern.data.storage.BundledReleaseNotesInstaller
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.attachdesign.kern.ui.common.launchShare
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.attachdesign.kern.EditorKey
import com.attachdesign.kern.SettingsKey
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.SettingKeys
import kotlinx.coroutines.Dispatchers
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.ui.theme.toFontFamily
import com.attachdesign.kern.ui.theme.AppColorTheme
import com.attachdesign.kern.ui.main.dialogs.InputDialog
import com.attachdesign.kern.ui.main.dialogs.NamingRequest
import com.attachdesign.kern.ui.main.dialogs.NamingKind
import com.attachdesign.kern.ui.main.dialogs.NamingOperationState
import com.attachdesign.kern.ui.common.LocalDeletionSession
import com.attachdesign.kern.ui.main.dialogs.NodeActionsDialog
import com.attachdesign.kern.ui.main.dialogs.CreateProjectDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress

enum class SortMode(val displayName: String) {
    ALPHA_ASC("[A-Z]"),
    ALPHA_DESC("[Z-A]"),
    RECENT("[REC]")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    db: AppDatabase,
    storageManager: StorageManager,
    fileOpsManager: com.attachdesign.kern.data.storage.FileOperationsManager,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val applicationContext = context.applicationContext
    val vm: MainScreenViewModel = viewModel {
        MainScreenViewModel(db, storageManager, fileOpsManager, installReleaseNotes = {
            val installer = BundledReleaseNotesInstaller(db, storageManager)
            val version = BundledReleaseNotesInstaller.currentVersion(applicationContext)
            installer.install(version, BundledReleaseNotesInstaller.currentMarkdown(storageManager, version))
        })
    }
    val state by vm.explorerState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.snackbarEvent.collect { event ->
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

    var isIntroDialogOpen by remember { mutableStateOf(false) }
    var dontShowAgainCheck by remember { mutableStateOf(false) }
    val showWorkspaceIntroSetting by db.settingDao().getSettingFlow(SettingKeys.SHOW_WORKSPACE_INTRO).collectAsState(initial = null)
    val showWorkspaceIntro = showWorkspaceIntroSetting?.value?.toBoolean() ?: true

    var namingDialog by rememberSaveable(stateSaver = NamingRequest.Saver) { mutableStateOf<NamingRequest?>(null) }
    val namingRecords by vm.namingOperations.records.collectAsStateWithLifecycle()
    val deletionSession = LocalDeletionSession.current
    SideEffect { vm.attachDeletionSession(deletionSession) }
    LaunchedEffect(deletionSession) {
        deletionSession?.revision?.collect { revision ->
            if (revision > 0) vm.refreshAfterExternalMutation()
        }
    }
    var nodeToDelete           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }
    var projectToDelete        by remember { mutableStateOf<ProjectEntity?>(null) }
    var activeNodeActionsTarget by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }

    var selectedFolderUri by remember { mutableStateOf("") }
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                val doc = DocumentFile.fromTreeUri(context, it)
                val folderName = doc?.name ?: "Local Folder"
                vm.createProject(folderName, isExternal = true, path = it.toString())
            } catch (e: Exception) {
                vm.reportFolderLinkFailure()
            }
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSearchImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var sortMode by remember { mutableStateOf(SortMode.ALPHA_ASC) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var draggedNode by remember { mutableStateOf<VfsNode?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }

    var backPressedOnce by remember { mutableStateOf(false) }
    val closeSearch = {
        isSearchActive = false
        searchQuery = ""
        requestSearchFocus = false
        backPressedOnce = false
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }
    BackHandler(enabled = !isSearchActive) {
        if (backPressedOnce) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    // Android owns the first Back while its IME is visible. The next closes search.
    BackHandler(enabled = isSearchActive && !isSearchImeVisible) { closeSearch() }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000)
            backPressedOnce = false
        }
    }

    val launchNewFileSetting by db.settingDao().getSettingFlow(SettingKeys.LAUNCH_NEW_FILE).collectAsState(initial = null)
    val shouldLaunchNewFile = launchNewFileSetting?.value?.toBoolean() ?: true
    val theme by ThemeEngine.observeActiveTheme(db).collectAsState(initial = ThemeEngine.DefaultLight.toColorTheme())

    val typedText = remember { mutableStateOf("K") }
    val splashAlpha = remember { Animatable(1f) }

    val infiniteTransition = rememberInfiniteTransition(label = "blinkingK")
    val blinkingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkingAlpha"
    )

    LaunchedEffect(state.isLoading, state.showSplash) {
        if (state.showSplash && !state.isLoading) {
            delay(300)
            val word = "ern"
            for (i in 1..word.length) {
                typedText.value = "K" + word.take(i)
                delay(150)
            }
            delay(500)
            splashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300)
            )
            vm.dismissSplash()
        }
    }

    fun openNaming(kind: NamingKind, project: ProjectEntity, node: VfsNode? = null) {
        namingDialog = NamingRequest.create(
            kind, project.id, project.name,
            parentPath = if (kind == NamingKind.RenameNode) node?.relativePath?.substringBeforeLast('/', "") ?: "" else state.currentPath,
            targetPath = node?.relativePath ?: "",
            targetName = if (kind == NamingKind.RenameWorkspaceAlias) project.name else node?.name ?: "",
            directory = node?.isDirectory ?: false,
            openCreatedFile = kind == NamingKind.CreateFile && shouldLaunchNewFile
        )
    }

    val appFont = theme.toFontFamily()

    Box(modifier = modifier.fillMaxSize()) {
        if (!state.showSplash || splashAlpha.value < 1f) {
            val targetProj = state.activeProject ?: state.projects.find { it.isSelected } ?: state.projects.firstOrNull()
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (state.showSplash) (1f - splashAlpha.value) else 1f },
                containerColor = theme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    FlexibleBottomAppBar(
                        containerColor = if (!theme.isDark) Color(0xFF1C1C1A) else theme.surface,
                        contentPadding = PaddingValues(horizontal = theme.dimensions.spacingLarge),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val contentColor = if (!theme.isDark) Color(0xFFF7F3EB) else theme.textPrimary
                        val disabledColor = if (!theme.isDark) Color(0xFF7A7060) else theme.textMuted.copy(alpha = 0.4f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Arrow
                            IconButton(
                                onClick = { vm.navigateBack() },
                                enabled = state.canNavigateBack,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (state.canNavigateBack) contentColor else disabledColor
                                )
                            }

                            // Forward Arrow
                            IconButton(
                                onClick = { vm.navigateForward() },
                                enabled = state.canNavigateForward,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (state.canNavigateForward) contentColor else disabledColor
                                )
                            }

                            // Centered highlighted New Document Button
                            val docBgColor = if (targetProj != null) theme.controlAccent else disabledColor.copy(alpha = 0.12f)
                            val docIconColor = if (targetProj != null) theme.onControlAccent else disabledColor
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(docBgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { targetProj?.let { openNaming(NamingKind.CreateFile, it) } },
                                    enabled = targetProj != null,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.NoteAdd,
                                        contentDescription = "New Document",
                                        tint = docIconColor
                                    )
                                }
                            }

                            // New Folder Button
                            IconButton(
                                onClick = { targetProj?.let { openNaming(NamingKind.CreateFolder, it) } },
                                enabled = targetProj != null,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = "New Folder",
                                    tint = if (targetProj != null) contentColor else disabledColor
                                )
                            }

                            // Search Button
                            IconButton(
                                onClick = {
                                    backPressedOnce = false
                                    if (isSearchActive) {
                                        closeSearch()
                                    } else {
                                        requestSearchFocus = true
                                        isSearchActive = true
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = contentColor
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Kern",
                                fontSize = theme.typography.h1,
                                fontFamily = appFont,
                                fontWeight = FontWeight.Light,
                                color = theme.textPrimary,
                                letterSpacing = (theme.typography.h1.value * -0.02f).sp
                            )
                        },
                        actions = {
                            IconButton(onClick = { onItemClick(SettingsKey) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = theme.indicator,
                                    modifier = Modifier.size(theme.dimensions.iconMedium)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = theme.textPrimary,
                            actionIconContentColor = theme.indicator
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.fillMaxWidth()
                    )

                    state.activeQuote?.let { quote ->
                        Text(
                            text = "“${quote.text}” — ${quote.author}, ${quote.year}",
                            fontSize = theme.typography.helper,
                            fontFamily = appFont,
                            fontWeight = FontWeight.Normal,
                            color = theme.textSecondary,
                            lineHeight = theme.typography.helperLineHeight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = theme.dimensions.spacingExtraLarge)
                                .padding(bottom = theme.dimensions.spacingMedium)
                        )
                    }

                    HorizontalDivider(
                        thickness = 2.dp,
                        color = theme.headerDivider
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = theme.dimensions.spacingExtraLarge)
                    ) {
        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))
        ) {
            LaunchedEffect(requestSearchFocus) {
                if (requestSearchFocus) {
                    searchFocusRequester.requestFocus()
                    keyboardController?.show()
                    requestSearchFocus = false
                }
            }
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        placeholder = { Text("Search files...", color = theme.textSecondary, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight) },
                        leadingIcon = {
                            IconButton(onClick = closeSearch) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close search", tint = theme.indicator)
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; backPressedOnce = false }) {
                                    Icon(Icons.Default.Close, "Clear search", tint = theme.indicator)
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            cursorColor = theme.controlAccent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                },
                expanded = false,
                onExpandedChange = {},
                colors = SearchBarDefaults.colors(
                    containerColor = theme.surface,
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        cursorColor = theme.controlAccent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                ),
                modifier = Modifier.fillMaxWidth().padding(top = theme.dimensions.spacingMedium, bottom = theme.dimensions.spacingMedium)
            ) {}
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = theme.dimensions.spacingMedium)
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val activeProjectLocal = state.activeProject
                val showBackArrow = activeProjectLocal != null &&
                    (activeProjectLocal.isExternal || state.currentPath.isNotEmpty())
                if (showBackArrow) {
                    IconButton(onClick = { vm.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Up", tint = theme.controlAccent)
                    }
                }
                Row(
                    modifier = Modifier.weight(1f)
                        .testTag("explorer-breadcrumbs")
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingSmall)
                ) {
                    val isAtFilesRoot = activeProjectLocal == null ||
                        (!activeProjectLocal.isExternal && activeProjectLocal.path == "root" && state.currentPath.isEmpty())

                    TextButton(onClick = { if (activeProjectLocal != null) vm.navigateUpToRoot() }) {
                        Text("files", color = if (isAtFilesRoot) theme.textPrimary else theme.textAccent,
                            fontSize = theme.typography.small, fontFamily = FontFamily.Monospace,
                            fontWeight = if (isAtFilesRoot) FontWeight.Bold else FontWeight.Normal)
                    }

                    activeProjectLocal?.let { proj ->
                        val isSandboxRoot = !proj.isExternal && proj.path == "root"
                        if (!isSandboxRoot) {
                            Text("/", color = theme.textSecondary, fontSize = theme.typography.small, fontFamily = FontFamily.Monospace)

                            val isProjRoot = state.currentPath.isEmpty()
                            TextButton(onClick = { if (!isProjRoot) vm.navigateToFolderRoot(proj) }) {
                                Text(proj.name, color = if (isProjRoot) theme.textPrimary else theme.textAccent,
                                    fontSize = theme.typography.small, fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isProjRoot) FontWeight.Bold else FontWeight.Normal)
                            }
                        }

                        if (state.currentPath.isNotEmpty()) {
                            val segments = state.currentPath.split('/')
                            segments.forEachIndexed { index, segment ->
                                Text("/", color = theme.textSecondary, fontSize = theme.typography.small, fontFamily = FontFamily.Monospace)
                                val isLast = index == segments.lastIndex
                                val segmentPath = segments.take(index + 1).joinToString("/")
                                TextButton(onClick = { if (!isLast) vm.navigateToSegment(proj, segmentPath) }) {
                                    Text(segment, color = if (isLast) theme.textPrimary else theme.textAccent,
                                        fontSize = theme.typography.small, fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium), verticalAlignment = Alignment.CenterVertically) {
                    val isAtRoot = state.activeProject?.let { !it.isExternal && it.path == "root" && state.currentPath.isEmpty() } ?: false
                    if (isAtRoot) {
                        TextButton(onClick = {
                                if (showWorkspaceIntro) {
                                    isIntroDialogOpen = true
                                } else {
                                    openDocumentTreeLauncher.launch(null)
                                }
                            }) { Text("Add workspace", color = theme.textAccent, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace) }
                    }

                    Box {
                        TextButton(onClick = { isSortMenuOpen = true }, modifier = Modifier.semantics { contentDescription = "Sort" }) { Text(sortMode.displayName, color = theme.textAccent, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace) }
                        DropdownMenu(expanded = isSortMenuOpen, onDismissRequest = { isSortMenuOpen = false }) {
                            SortMode.entries.forEach { mode ->
                                val label = when (mode) {
                                    SortMode.ALPHA_ASC -> "Name (A–Z)"
                                    SortMode.ALPHA_DESC -> "Name (Z–A)"
                                    SortMode.RECENT -> if (state.activeProject == null && !(isSearchActive && searchQuery.isNotEmpty())) "Recently added" else "Recently modified"
                                }
                                DropdownMenuItem(text = { Text(label) }, onClick = { sortMode = mode; isSortMenuOpen = false })
                            }
                        }
                    }
                }
            }
        }

        state.directoryError?.let { message ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(message, color = theme.textSecondary, fontSize = theme.typography.helper,
                    lineHeight = theme.typography.helperLineHeight)
                TextButton(onClick = vm::refreshCurrentDirectory) { Text("Refresh", color = theme.textAccent) }
            }
        }

        // ── Single unified list and floating buttons container ─────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = Triple(state.activeProject, state.currentPath, isSearchActive && searchQuery.isNotEmpty()),
                transitionSpec = {
                    val (initProj, initPath, initSearching) = initialState
                    val (targetProj, targetPath, targetSearching) = targetState

                    if (initSearching != targetSearching) {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    } else {
                        fun getDepth(proj: ProjectEntity?, path: String): Int {
                            if (proj == null) return 0
                            val projectBaseDepth = if (proj.isExternal) 1 else 0
                            val pathDepth = if (path.isEmpty()) 0 else path.count { it == '/' } + 1
                            return projectBaseDepth + pathDepth
                        }

                        val initDepth = getDepth(initProj, initPath)
                        val targetDepth = getDepth(targetProj, targetPath)

                        if (targetDepth > initDepth) {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) togetherWith
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        } else if (targetDepth < initDepth) {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) togetherWith
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                        } else {
                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                        }
                    }
                },
                label = "FileExplorerTransition"
            ) { (activeProj, currentPath, isSearching) ->
                if (isSearching) {
                    val filteredItems = remember(state.allItems, searchQuery, sortMode) {
                        state.allItems.filterIsInstance<FileListItem.FileRow>().filter {
                            it.node.name.contains(searchQuery, ignoreCase = true)
                        }.let { list ->
                            when (sortMode) {
                                SortMode.ALPHA_ASC -> list.sortedBy { it.node.name.lowercase() }
                                SortMode.ALPHA_DESC -> list.sortedByDescending { it.node.name.lowercase() }
                                SortMode.RECENT -> list.sortedByDescending { (it.node as? com.attachdesign.kern.data.storage.VfsNode.File)?.lastModified ?: 0L }
                            }
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        EmptyStateHint(
                            title = "No matches found",
                            body = "Try another filename in Search.",
                            theme = theme
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top,
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            items(filteredItems) { item ->
                                SearchOverflowRow(
                                    node = item.node,
                                    project = item.project,
                                    theme = theme,
                                    onClick = {
                                        if (item.node.isDirectory) {
                                            vm.navigateToFolder(item.node, item.project)
                                            isSearchActive = false
                                            searchQuery = ""
                                        } else {
                                            onItemClick(EditorKey(item.project.id, item.node.relativePath))
                                        }
                                    },
                                    onShare = { coroutineScope.launch { fileOpsManager.shareNode(item.project, item.node).onSuccess { context.launchShare(it) }.onFailure { vm.reportShareFailure(item.node.name) } } },
                                    onEdit = { openNaming(NamingKind.RenameNode, item.project, item.node) },
                                    onDelete = { nodeToDelete = Pair(item.node, item.project) }
                                )
                            }
                        }
                    }
                } else if (activeProj == null) {
                    // Root view: flat list of watched project folders
                    val sortedProjects = remember(state.projects, sortMode) {
                        when (sortMode) {
                            SortMode.ALPHA_ASC -> state.projects.sortedBy { it.name.lowercase() }
                            SortMode.ALPHA_DESC -> state.projects.sortedByDescending { it.name.lowercase() }
                            SortMode.RECENT -> state.projects.sortedByDescending { it.id }
                        }
                    }

                    if (sortedProjects.isEmpty()) {
                        EmptyStateHint(
                            title = "No workspaces",
                            body = "Use Add workspace to open a folder on your device.",
                            theme = theme,
                            actionLabel = "Add workspace",
                            onAction = {
                                if (showWorkspaceIntro) {
                                    isIntroDialogOpen = true
                                } else {
                                    openDocumentTreeLauncher.launch(null)
                                }
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top,
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            items(sortedProjects, key = { it.id }) { proj ->
                                ProjectOverflowRow(
                                    project = proj,
                                    theme = theme,
                                    appFont = appFont,
                                    onClick = { vm.navigateToFolderRoot(proj) },
                                    onShare = { coroutineScope.launch { fileOpsManager.shareNode(proj, VfsNode.Directory(name = proj.name, relativePath = "")).onSuccess { context.launchShare(it) }.onFailure { vm.reportShareFailure(proj.name) } } },
                                    onEdit = { openNaming(NamingKind.RenameWorkspaceAlias, proj) },
                                    onDelete = { projectToDelete = proj }
                                )
                            }
                        }
                    }
                } else {
                    // Drill-down view: list subfolders and files inside active project directory
                    val sortedFiles = remember(state.drillFiles, sortMode) {
                        when (sortMode) {
                            SortMode.ALPHA_ASC -> state.drillFiles.sortedBy { it.name.lowercase() }
                            SortMode.ALPHA_DESC -> state.drillFiles.sortedByDescending { it.name.lowercase() }
                            SortMode.RECENT -> state.drillFiles.sortedByDescending { (it as? com.attachdesign.kern.data.storage.VfsNode.File)?.lastModified ?: 0L }
                        }
                    }

                    if (sortedFiles.isEmpty() && state.directoryError != null) {
                        EmptyStateHint("Folder unavailable", "Restore folder permission or choose another workspace.", theme,
                            actionLabel = "Refresh", onAction = vm::refreshCurrentDirectory)
                    } else if (sortedFiles.isEmpty()) {
                        EmptyStateHint(
                            title = "Folder is empty",
                            body = "Use New Document or New Folder to add content.",
                            theme = theme,
                            actionLabel = "New Document",
                            onAction = { targetProj?.let { openNaming(NamingKind.CreateFile, it) } }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top,
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            if (currentPath.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        AnimatedVisibility(
                                            visible = draggedNode != null,
                                            enter = fadeIn(animationSpec = tween(150)) + slideInVertically(animationSpec = tween(150), initialOffsetY = { -it / 2 }),
                                            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(animationSpec = tween(150), targetOffsetY = { -it / 2 })
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = theme.dimensions.spacingMedium)
                                                    .height(54.dp)
                                                    .background(theme.surface.copy(alpha = 0.5f))
                                                    .border(BorderStroke(1.dp, theme.indicator), RoundedCornerShape(8.dp))
                                                    .onGloballyPositioned { coordinates ->
                                                        folderBounds["..parent.."] = coordinates.boundsInRoot()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("📁 Drop here to move up one level", color = theme.textSecondary, fontSize = theme.typography.body)
                                            }
                                        }
                                    }
                                }
                            }
                            items(sortedFiles, key = { it.relativePath }) { node ->
                                val rowModifier = Modifier.onGloballyPositioned { coordinates ->
                                    folderBounds[node.relativePath] = coordinates.boundsInRoot()
                                }
                                Box(modifier = rowModifier) {
                                    FileOverflowRow(
                                        node = node,
                                        theme = theme,
                                        appFont = appFont,
                                        isExternalProject = activeProj.isExternal,
                                        onClick = {
                                            if (node.relativePath.startsWith("external:")) {
                                                val extId = node.relativePath.substringAfter("external:").toLong()
                                                val extProj = state.projects.find { it.id == extId }
                                                if (extProj != null) {
                                                    vm.selectProject(extProj)
                                                }
                                            } else if (node.isDirectory) {
                                                vm.navigateToFolder(node, activeProj)
                                            } else {
                                                val bounds = folderBounds[node.relativePath]
                                                onItemClick(EditorKey(activeProj.id, node.relativePath, touchOriginX = bounds?.center?.x, touchOriginY = bounds?.center?.y))
                                            }
                                        },
                                        onShare = {
                                            if (!node.relativePath.startsWith("external:")) {
                                                coroutineScope.launch { fileOpsManager.shareNode(activeProj, node).onSuccess { context.launchShare(it) }.onFailure { vm.reportShareFailure(node.name) } }
                                            }
                                        },
                                        onEdit = {
                                            if (node.relativePath.startsWith("external:")) {
                                                val extId = node.relativePath.substringAfter("external:").toLong()
                                                val extProj = state.projects.find { it.id == extId }
                                                if (extProj != null) {
                                                    openNaming(NamingKind.RenameWorkspaceAlias, extProj)
                                                }
                                            } else {
                                                openNaming(NamingKind.RenameNode, activeProj, node)
                                            }
                                        },
                                        onDelete = {
                                            if (node.relativePath.startsWith("external:")) {
                                                val extId = node.relativePath.substringAfter("external:").toLong()
                                                val extProj = state.projects.find { it.id == extId }
                                                if (extProj != null) {
                                                    projectToDelete = extProj
                                                }
                                            } else {
                                                nodeToDelete = Pair(node, activeProj)
                                            }
                                        },
                                        onDragStart = { offset ->
                                            if (!node.relativePath.startsWith("external:")) {
                                                draggedNode = node
                                                dragOffset = Offset.Zero
                                            }
                                        },
                                        onDrag = { dragAmount ->
                                            dragOffset += dragAmount
                                        },
                                        onDragEnd = {
                                            val currentItemBounds = folderBounds[node.relativePath]
                                            if (currentItemBounds != null) {
                                                val dropPoint = currentItemBounds.topLeft + dragOffset
                                                val targetFolderKey = folderBounds.entries.find { entry ->
                                                    entry.key != node.relativePath && !entry.key.startsWith(node.relativePath + "/") && entry.value.contains(dropPoint)
                                                }?.key
                                                
                                                if (targetFolderKey != null && !targetFolderKey.startsWith("external:")) {
                                                    if (targetFolderKey == "..parent..") {
                                                        vm.moveNodeUp(node, activeProj)
                                                    } else {
                                                        val targetNode = VfsNode.Directory(
                                                            name = targetFolderKey.substringAfterLast('/'),
                                                            relativePath = targetFolderKey
                                                        )
                                                        vm.moveNode(node, targetNode, activeProj)
                                                    }
                                                }
                                            }
                                            draggedNode = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }



            // Drag preview overlay
            if (draggedNode != null) {
                val currentItemBounds = folderBounds[draggedNode!!.relativePath]
                if (currentItemBounds != null) {
                    val previewX = currentItemBounds.left + dragOffset.x
                    val previewY = currentItemBounds.top + dragOffset.y
                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(previewX.toInt(), previewY.toInt()) }
                            .background(theme.surface.copy(alpha = 0.9f))
                            .border(BorderStroke(1.dp, theme.controlAccent), RoundedCornerShape(4.dp))
                            .padding(theme.dimensions.spacingMedium)
                    ) {
                        Text(
                            text = draggedNode!!.name,
                            color = theme.textPrimary,
                            fontSize = theme.typography.body,
                            fontFamily = appFont
                        )
                    }
                }
            }
        }
    }
}
}
}

    // ── Dialogs ────────────────────────────────────────────────────────────────
    if (isIntroDialogOpen) {
        AlertDialog(
            onDismissRequest = { isIntroDialogOpen = false },
            title = { Text("Open Local Folder", color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Add local folders on your device to the file structure.",
                        fontSize = theme.typography.body,
                        fontFamily = appFont,
                        color = theme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(theme.dimensions.spacingMedium))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgainCheck = !dontShowAgainCheck }
                    ) {
                        Checkbox(
                            checked = dontShowAgainCheck,
                            onCheckedChange = { dontShowAgainCheck = it },
                            colors = CheckboxDefaults.colors(checkedColor = theme.controlAccent, uncheckedColor = theme.indicator, checkmarkColor = theme.onControlAccent)
                        )
                        Spacer(modifier = Modifier.width(theme.dimensions.spacingSmall))
                        Text(
                            text = "Don't show again",
                            fontSize = theme.typography.small,
                            fontFamily = appFont,
                            color = theme.textPrimary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isIntroDialogOpen = false
                        if (dontShowAgainCheck) {
                            coroutineScope.launch(Dispatchers.IO) {
                                db.settingDao().insertSetting(SettingEntity(SettingKeys.SHOW_WORKSPACE_INTRO, "false"))
                            }
                        }
                        openDocumentTreeLauncher.launch(null)
                    }
                ) {
                    Text("Select Folder", color = theme.textAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { isIntroDialogOpen = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            },
            containerColor = theme.surface
        )
    }

    namingDialog?.let { request ->
        val record = namingRecords[request.id]
        LaunchedEffect(request.id, record) {
            when (record) {
                is NamingOperationState.Succeeded -> {
                    val success = vm.namingOperations.consumeSuccess(request.id)
                    if (success != null && namingDialog?.id == request.id) {
                        namingDialog = null
                        if (request.kind == NamingKind.CreateFile && request.openCreatedFile) {
                            success.actual?.node?.relativePath?.let { path ->
                                onItemClick(EditorKey(request.projectId, path, focusOnStart = true))
                            }
                        }
                    }
                }
                NamingOperationState.Consumed -> if (namingDialog?.id == request.id) namingDialog = null
                is NamingOperationState.Failed -> namingDialog = request.copy(submitted = false)
                is NamingOperationState.NeedsReconciliation -> namingDialog = request.copy(submitted = false, inspectionRequired = true)
                is NamingOperationState.Inspected -> namingDialog = request.copy(submitted = false, inspectionRequired = !record.mayRetry)
                null -> if (request.submitted) namingDialog = request.copy(inspectionRequired = true)
                NamingOperationState.Pending -> Unit
            }
        }
        val interrupted = request.inspectionRequired || (request.submitted && record == null)
        val partial = record is NamingOperationState.NeedsReconciliation
        val message = when (record) {
            is NamingOperationState.Failed -> record.message
            is NamingOperationState.NeedsReconciliation -> "Storage may already have changed. Retry indexing to finish without repeating the file action."
            is NamingOperationState.Inspected -> record.message
            else -> if (interrupted) "This action was interrupted. Refresh files to check what changed before retrying." else null
        }
        val title = when (request.kind) {
            NamingKind.CreateFile -> "New File in ${request.projectName}"
            NamingKind.CreateFolder -> "New Folder in ${request.projectName}"
            NamingKind.RenameNode -> "Rename ${if (request.directory) "Folder" else "Document"}"
            NamingKind.RenameWorkspaceAlias -> "Rename Workspace"
        }
        InputDialog(
            title = title,
            label = if (request.kind == NamingKind.RenameWorkspaceAlias) "Workspace name" else if (request.directory || request.kind == NamingKind.CreateFolder) "Folder name" else "Document name",
            confirmText = if (request.kind == NamingKind.CreateFile || request.kind == NamingKind.CreateFolder) "Create" else "Rename",
            theme = theme,
            value = request.draft,
            onValueChange = { value -> namingDialog = request.copy(draft = value) },
            isSubmitting = record == NamingOperationState.Pending,
            error = message,
            canSubmit = !interrupted && !partial && (record !is NamingOperationState.Inspected || record.mayRetry),
            onDismiss = { namingDialog = null },
            onConfirm = {
                vm.submitNaming(request)
                namingDialog = request.copy(submitted = true)
            },
            recoveryText = if (partial) "Retry indexing" else if (interrupted) "Refresh files" else null,
            onRecovery = if (partial || interrupted) ({ vm.recoverNaming(request) }) else null
        )
    }

    nodeToDelete?.let { (node, project) ->
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text("Delete ${if (node.isDirectory) "folder" else "document"}?",
                color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
            text  = { Text("Delete '${node.name}'? You can Undo from the confirmation. When that offer ends, the item is permanently deleted.",
                color = theme.textPrimary, fontSize = theme.typography.body) },
            confirmButton = {
                TextButton(onClick = { vm.deleteNode(node, project); nodeToDelete = null }) {
                    Text("Delete", color = theme.textDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) { Text("Cancel", color = theme.textSecondary) }
            },
            containerColor = theme.surface
        )
    }

    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Remove workspace?",
                color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
            text  = { Text("Remove '${proj.name}' from Kern? Its files stay in the linked folder. You can add the workspace again.",
                color = theme.textPrimary, fontSize = theme.typography.body) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProject(proj); projectToDelete = null }) {
                    Text("Remove", color = theme.textDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel", color = theme.textSecondary) }
            },
            containerColor = theme.surface
        )
    }

    activeNodeActionsTarget?.let { (node, project) ->
        NodeActionsDialog(
            node = node,
            theme = theme,
            onAction = { action ->
                activeNodeActionsTarget = null
                when (action) {
                    "duplicate" -> vm.duplicateNode(node, project)
                    "rename" -> openNaming(NamingKind.RenameNode, project, node)
                    "share" -> coroutineScope.launch { fileOpsManager.shareNode(project, node).onSuccess { context.launchShare(it) }.onFailure { vm.reportShareFailure(node.name) } }
                    "delete" -> nodeToDelete = Pair(node, project)
                }
            },
            onDismiss = { activeNodeActionsTarget = null }
        )
    }

    if (state.showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .graphicsLayer { alpha = splashAlpha.value }
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            val textAlpha = if (state.isLoading) blinkingAlpha else 1f
            Text(
                text = typedText.value,
                fontSize = 80.sp,
                fontFamily = appFont,
                fontWeight = FontWeight.Light,
                color = theme.textPrimary,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
                letterSpacing = (80 * -0.02f).sp
            )
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun ProjectSectionHeader(
    project: ProjectEntity,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    isSelected: Boolean,
    onHeaderClick: () -> Unit,
    onCreateFileClick: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = theme.dimensions.spacingExtraLarge, bottom = theme.dimensions.spacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onHeaderClick) {
            Text(
                text = buildString {
                    append(project.name)
                    if (project.isExternal) append("  ☁️")
                },
                color = if (isSelected) theme.textAccent else theme.textSecondary,
                fontSize = theme.typography.tiny,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = (theme.typography.tiny.value * 0.136f).sp
            )
            if (isSelected) {
                Text(
                    text = "• active",
                    color = theme.textAccent,
                    fontSize = theme.typography.tiny,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCreateFileClick) {
                Text("Add file", color = theme.textAccent, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = onCreateFolderClick) {
                Text("Add folder", color = theme.textAccent, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun EmptyStateHint(
    title: String,
    body: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val appFont = theme.toFontFamily()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = theme.dimensions.iconHuge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "[ empty ]",
            color = theme.textSecondary,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (theme.typography.tiny.value * 0.1f).sp,
            modifier = Modifier.padding(bottom = theme.dimensions.spacingSmall)
        )
        Text(title, color = theme.textPrimary, fontFamily = appFont,
            fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(theme.dimensions.spacingSmall))
        Text(body, color = theme.textSecondary, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight, textAlign = TextAlign.Center)

        if (!actionLabel.isNullOrEmpty() && onAction != null) {
            Spacer(Modifier.height(theme.dimensions.spacingLarge))
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, theme.controlAccent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textAccent)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = theme.typography.small,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OverflowActions(
    theme: AppColorTheme,
    itemDescription: String,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String = "Delete",
    allowDelete: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More actions for $itemDescription", tint = theme.indicator)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { expanded = false; onShare() })
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { expanded = false; onEdit() })
            if (allowDelete) DropdownMenuItem(text = { Text(deleteLabel, color = theme.textDanger) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = theme.textDanger) }, onClick = { expanded = false; onDelete() })
        }
    }
}

@Composable
private fun ExplorerLeadingIcon(glyph: String, theme: AppColorTheme) {
    Box(
        modifier = Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = theme.typography.bodyLarge)
    }
}

@Composable
private fun FileOverflowRow(
    node: VfsNode,
    theme: AppColorTheme,
    appFont: FontFamily,
    isExternalProject: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val isExternal = node.relativePath.startsWith("external:")
    val details = if (node.isDirectory) "Directory" else "${(node as? VfsNode.File)?.size?.div(1024) ?: 0} KB"
    ListItem(
        headlineContent = { Text(node.name, fontFamily = appFont, fontSize = theme.typography.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (isExternal || node.isDirectory) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = { Text(details, fontFamily = FontFamily.Monospace, color = theme.textSecondary, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { ExplorerLeadingIcon(if (isExternal || node.isDirectory) "📁" else "📄", theme) },
        trailingContent = { OverflowActions(theme, "${node.name} (${node.relativePath})", onShare, onEdit, onDelete, deleteLabel = if (isExternal) "Remove workspace" else "Delete") },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(node.relativePath) {
                detectDragGesturesAfterLongPress(
                    onDragStart = onDragStart,
                    onDrag = { change, amount -> change.consume(); onDrag(amount) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = theme.textPrimary)
    )
}

@Composable
private fun ProjectOverflowRow(
    project: ProjectEntity,
    theme: AppColorTheme,
    appFont: FontFamily,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(project.name, fontFamily = appFont, fontSize = theme.typography.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(if (project.isExternal) "Linked workspace" else "Workspace", color = theme.textSecondary, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { ExplorerLeadingIcon("📁", theme) },
        trailingContent = { OverflowActions(theme, "workspace ${project.name}", onShare, onEdit, onDelete, deleteLabel = "Remove workspace", allowDelete = project.isExternal) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = theme.textPrimary)
    )
}

@Composable
private fun SearchOverflowRow(
    node: VfsNode,
    project: ProjectEntity,
    theme: AppColorTheme,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(node.name, fontFamily = theme.toFontFamily(), fontSize = theme.typography.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = { Text("${project.name}/${node.relativePath.substringBeforeLast('/', "")}", color = theme.textSecondary, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { ExplorerLeadingIcon(if (node.isDirectory) "📁" else "📄", theme) },
        trailingContent = { OverflowActions(theme, "${node.name} in ${project.name}/${node.relativePath}", onShare, onEdit, onDelete) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = theme.textPrimary)
    )
}

// ── Swipe-to-reveal helpers ───────────────────────────────────────────────────

@Composable
fun SwipeableFileRow(
    node: VfsNode,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    appFont: FontFamily,
    isExternalProject: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { theme.dimensions.swipeActionRevealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < -revealWidthPx / 2) {
                                offsetX.animateTo(-revealWidthPx, spring(stiffness = Spring.StiffnessMediumLow))
                            } else {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onHorizontalDrag = { _, delta ->
                        scope.launch {
                            val target = (offsetX.value + delta).coerceIn(-revealWidthPx, 0f)
                            offsetX.snapTo(target)
                        }
                    }
                )
            }
    ) {
        // ── Action strip (behind) ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(theme.dimensions.swipeActionRevealWidth)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeAction(label = "Share", color = theme.controlAccent, contentColor = theme.onControlAccent, theme = theme, onClick = {
                onShare()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Edit",  color = theme.surface, contentColor = theme.textSecondary, theme = theme, onClick = {
                onEdit()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Delete",color = theme.surface, contentColor = theme.textDanger, theme = theme, onClick = {
                onDelete()
                scope.launch { offsetX.animateTo(0f) }
            })
        }

        // ── Content row (on top, slides left) ─────────────────────────────────
        var rowPositionInRoot by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .background(theme.background)
                .onGloballyPositioned { coordinates ->
                    rowPositionInRoot = coordinates.positionInRoot()
                }
                .semantics {
                    customActions = listOf(
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Share") { onShare(); true },
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Rename") { onEdit(); true },
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Delete") { onDelete(); true }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offsetX.value != 0f) {
                                scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            } else {
                                onClick()
                            }
                        },
                        onLongPress = {
                            onLongClick?.invoke()
                        }
                    )
                }
                .padding(vertical = theme.dimensions.spacingMedium)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = theme.dimensions.spacingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    val isExternal = node.relativePath.startsWith("external:")
                    val icon = if (isExternal || node.isDirectory) "📁" else "📄"
                    Text(icon, fontSize = theme.typography.bodyLarge, modifier = Modifier.padding(bottom = theme.dimensions.borderWidth))
                    Spacer(Modifier.width(theme.dimensions.spacingMedium))
                    Text(
                        text       = node.name,
                        color      = theme.textPrimary,
                        fontSize   = theme.typography.bodyLarge,
                        fontFamily = appFont,
                        fontWeight = if (isExternal || node.isDirectory) FontWeight.Bold else FontWeight.Normal,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.alignByBaseline()
                    )
                    val isSynced = node is VfsNode.File && node.syncState == "SYNCED" && !isExternalProject
                    if (isSynced) {
                        Spacer(Modifier.width(theme.dimensions.spacingSmall))
                        Text("☁️", fontSize = theme.typography.small, modifier = Modifier.alignByBaseline())
                    } else if (isExternal) {
                        Spacer(Modifier.width(theme.dimensions.spacingSmall))
                        Text("🔗", fontSize = theme.typography.small, modifier = Modifier.alignByBaseline())
                    }
                    Spacer(Modifier.width(theme.dimensions.spacingSmall))
                    Text(
                        text     = " . ".repeat(50),
                        color    = theme.textMuted.copy(alpha = 0.4f),
                        fontSize = theme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(theme.dimensions.spacingMedium))
                val details = if (node.isDirectory) "DIR"
                else "${(node as? VfsNode.File)?.size?.div(1024) ?: 0}KB"
                Text(details, color = theme.textSecondary, fontSize = theme.typography.tiny,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

@Composable
fun SwipeableProjectRow(
    project: ProjectEntity,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    appFont: FontFamily,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { theme.dimensions.swipeActionRevealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < -revealWidthPx / 2) {
                                offsetX.animateTo(-revealWidthPx, spring(stiffness = Spring.StiffnessMediumLow))
                            } else {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onHorizontalDrag = { _, delta ->
                        scope.launch {
                            val target = (offsetX.value + delta).coerceIn(-revealWidthPx, 0f)
                            offsetX.snapTo(target)
                        }
                    }
                )
            }
    ) {
        // ── Action strip (behind) ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(theme.dimensions.swipeActionRevealWidth)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeAction(label = "Share",  color = theme.controlAccent, contentColor = theme.onControlAccent, theme = theme, onClick = {
                onShare()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Edit",   color = theme.surface, contentColor = theme.textSecondary, theme = theme, onClick = {
                onEdit()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Delete", color = theme.surface, contentColor = theme.textDanger, theme = theme, onClick = {
                onDelete()
                scope.launch { offsetX.animateTo(0f) }
            })
        }

        // ── Content row (on top, slides left) ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .background(theme.background)
                .semantics {
                    customActions = listOf(
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Share") { onShare(); true },
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Rename") { onEdit(); true },
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Delete") { onDelete(); true }
                    )
                }
                .clickable {
                    if (offsetX.value != 0f) {
                        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    } else {
                        onClick()
                    }
                }
                .padding(vertical = theme.dimensions.spacingMedium)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = theme.dimensions.spacingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text("📁", fontSize = theme.typography.bodyLarge, modifier = Modifier.padding(bottom = theme.dimensions.borderWidth))
                    Spacer(Modifier.width(theme.dimensions.spacingMedium))
                    Text(
                        text = project.name,
                        color = theme.textPrimary,
                        fontSize = theme.typography.bodyLarge,
                        fontFamily = appFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline()
                    )
                    if (project.isExternal) {
                        Spacer(Modifier.width(theme.dimensions.spacingSmall))
                        Text("🔗", fontSize = theme.typography.small, modifier = Modifier.alignByBaseline())
                    }
                    Spacer(Modifier.width(theme.dimensions.spacingSmall))
                    Text(
                        text = " . ".repeat(50),
                        color = theme.textMuted.copy(alpha = 0.4f),
                        fontSize = theme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(theme.dimensions.spacingMedium))
                Text("DIR", color = theme.textSecondary, fontSize = theme.typography.tiny,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    color: Color,
    contentColor: Color,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(theme.dimensions.swipeActionWidth)
            .fillMaxHeight()
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = theme.typography.tiny,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )
    }
}

// Keep original VfsNodeRow as internal alias so SearchVfsNodeRow still compiles
@Composable
fun VfsNodeRow(
    node: VfsNode,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    isExternalProject: Boolean,
    onNodeClick: (VfsNode) -> Unit,
    onShareClick: (VfsNode) -> Unit,
    onEditClick: (VfsNode) -> Unit,
    onDeleteClick: (VfsNode) -> Unit
) {
    SwipeableFileRow(
        node = node,
        theme = theme,
        appFont = theme.toFontFamily(),
        isExternalProject = isExternalProject,
        onClick = { onNodeClick(node) },
        onShare = { onShareClick(node) },
        onEdit = { onEditClick(node) },
        onDelete = { onDeleteClick(node) }
    )
}

@Composable
fun SearchVfsNodeRow(
    node: VfsNode,
    project: ProjectEntity,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    onNodeClick: (VfsNode) -> Unit,
    onShareClick: (VfsNode) -> Unit,
    onEditClick: (VfsNode) -> Unit,
    onDeleteClick: (VfsNode) -> Unit
) {
    val appFont = theme.toFontFamily()
    val density = LocalDensity.current
    val revealWidthPx = with(density) { theme.dimensions.swipeActionRevealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.height(IntrinsicSize.Min)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        kotlinx.coroutines.GlobalScope.launch {
                            if (offsetX.value < -revealWidthPx / 2)
                                offsetX.animateTo(-revealWidthPx, spring(stiffness = Spring.StiffnessMediumLow))
                            else
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = { kotlinx.coroutines.GlobalScope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) } },
                    onHorizontalDrag = { _, delta ->
                        kotlinx.coroutines.GlobalScope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealWidthPx, 0f))
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(theme.dimensions.swipeActionRevealWidth)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeAction(label = "Share",  color = theme.controlAccent, contentColor = theme.onControlAccent, theme = theme, onClick = { onShareClick(node) })
            SwipeAction(label = "Edit",   color = theme.surface, contentColor = theme.textSecondary, theme = theme, onClick = { onEditClick(node) })
            SwipeAction(label = "Delete", color = theme.surface, contentColor = theme.textDanger, theme = theme, onClick = { onDeleteClick(node) })
        }

        var rowPositionInRoot by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .background(theme.background)
                .onGloballyPositioned { coordinates ->
                    rowPositionInRoot = coordinates.positionInRoot()
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offsetX.value != 0f) {
                                scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            } else {
                                onNodeClick(node)
                            }
                        }
                    )
                }
                .padding(vertical = theme.dimensions.spacingMedium)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = theme.dimensions.spacingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    val icon = if (node.isDirectory) "📁" else "📄"
                    Text(icon, fontSize = theme.typography.bodyLarge, modifier = Modifier.padding(bottom = theme.dimensions.borderWidth))
                    Spacer(Modifier.width(theme.dimensions.spacingMedium))
                    Column(modifier = Modifier.alignByBaseline()) {
                        Text(
                            text       = node.name,
                            color      = theme.textPrimary,
                            fontSize   = theme.typography.bodyLarge,
                            fontFamily = appFont,
                            fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Normal,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "in ${project.name}/${node.relativePath.substringBeforeLast('/', "")}",
                            color = theme.textSecondary,
                            fontSize = theme.typography.helper,
                            lineHeight = theme.typography.helperLineHeight,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.width(theme.dimensions.spacingSmall))
                    Text(
                        text     = " . ".repeat(50),
                        color    = theme.textMuted.copy(alpha = 0.4f),
                        fontSize = theme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(theme.dimensions.spacingMedium))
                val details = if (node.isDirectory) "DIR"
                else "${(node as? VfsNode.File)?.size?.div(1024) ?: 0}KB"
                Text(details, color = theme.textSecondary, fontSize = theme.typography.tiny,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}
