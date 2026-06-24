package com.attachdesign.kern.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
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
import com.attachdesign.kern.ui.settings.MinimalOutlinedButton
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import kotlinx.coroutines.Dispatchers
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.ui.theme.AppColorTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    db: AppDatabase,
    storageManager: StorageManager,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: MainScreenViewModel = viewModel { MainScreenViewModel(db, storageManager) }
    val state by vm.explorerState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var isIntroDialogOpen by remember { mutableStateOf(false) }
    var dontShowAgainCheck by remember { mutableStateOf(false) }
    val showWorkspaceIntroSetting by db.settingDao().getSettingFlow("show_workspace_intro").collectAsState(initial = null)
    val showWorkspaceIntro = showWorkspaceIntroSetting?.value?.toBoolean() ?: true

    var createFileDialogTargetProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var createFolderDialogTargetProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var nodeToDelete           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }
    var projectToDelete        by remember { mutableStateOf<ProjectEntity?>(null) }
    var nodeToRename           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }
    var projectToRename        by remember { mutableStateOf<ProjectEntity?>(null) }

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
                e.printStackTrace()
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSortAscending by remember { mutableStateOf(true) }
    var draggedNode by remember { mutableStateOf<VfsNode?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }

    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler {
        if (backPressedOnce) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000)
            backPressedOnce = false
        }
    }

    val selectedThemeIdSetting by db.settingDao().getSettingFlow("selected_theme_id").collectAsState(initial = null)
    val editorFontSetting by db.settingDao().getSettingFlow("editor_font_family").collectAsState(initial = null)
    val launchNewFileSetting by db.settingDao().getSettingFlow("launch_new_file").collectAsState(initial = null)
    val shouldLaunchNewFile = launchNewFileSetting?.value?.toBoolean() ?: true
    var theme by remember { mutableStateOf(ThemeEngine.DefaultLight.toColorTheme()) }

    LaunchedEffect(selectedThemeIdSetting, editorFontSetting) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val themeId = selectedThemeIdSetting?.value?.toLongOrNull()
            val savedFont = editorFontSetting?.value ?: "serif"
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
            activeTheme = activeTheme.copy(editorFontFamily = savedFont)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                theme = activeTheme
            }
        }
    }

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

    val appFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!state.showSplash || splashAlpha.value < 1f) {
            val targetProj = state.activeProject ?: state.projects.find { it.isSelected } ?: state.projects.firstOrNull()
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (state.showSplash) (1f - splashAlpha.value) else 1f },
                containerColor = theme.background,
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
                                modifier = Modifier.size(44.dp)
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
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (state.canNavigateForward) contentColor else disabledColor
                                )
                            }

                            // Centered highlighted New Document Button
                            val docBgColor = if (targetProj != null) theme.accent else disabledColor.copy(alpha = 0.12f)
                            val docIconColor = if (targetProj != null) Color.White else disabledColor
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(docBgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { targetProj?.let { createFileDialogTargetProject = it } },
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
                                onClick = { targetProj?.let { createFolderDialogTargetProject = it } },
                                enabled = targetProj != null,
                                modifier = Modifier.size(44.dp)
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
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) searchQuery = ""
                                },
                                modifier = Modifier.size(44.dp)
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
                        .padding(horizontal = theme.dimensions.spacingHuge)
                        .padding(top = theme.dimensions.spacingExtraLarge)
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
                        tint = theme.textMuted,
                        modifier = Modifier.size(theme.dimensions.iconMedium)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = theme.textPrimary,
                actionIconContentColor = theme.textMuted
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxWidth()
        )

        state.activeQuote?.let { quote ->
            Spacer(Modifier.height(theme.dimensions.spacingSmall))
            Text(
                text = "“${quote.text}” — ${quote.author}, ${quote.year}",
                fontSize = theme.typography.small,
                fontFamily = appFont,
                fontWeight = FontWeight.Normal,
                color = theme.textMuted,
                lineHeight = theme.typography.subtitle,
                modifier = Modifier.padding(bottom = theme.dimensions.spacingSmall)
            )
        }

        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))
        ) {
            DockedSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search files...", color = theme.textMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = theme.textMuted
                    )
                },
                trailingIcon = {
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textMuted
                        )
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = theme.surface,
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        cursorColor = theme.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = theme.dimensions.spacingMedium)
            ) {}
        }

        HorizontalDivider(
            color = theme.textMuted.copy(alpha = 0.15f),
            thickness = theme.dimensions.borderWidth,
            modifier = Modifier.padding(top = theme.dimensions.spacingSmall, bottom = theme.dimensions.spacingMedium)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(theme.dimensions.iconHuge)
                .padding(bottom = theme.dimensions.spacingMedium),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingSmall)
                ) {
                    val activeProjectLocal = state.activeProject
                    val showBackArrow = activeProjectLocal != null &&
                        (activeProjectLocal.isExternal || state.currentPath.isNotEmpty())

                    if (showBackArrow) {
                        Text(
                            text = "←",
                            color = theme.accent,
                            fontSize = theme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { vm.navigateUp() }
                                .padding(end = theme.dimensions.spacingMedium)
                        )
                    }

                    val isAtFilesRoot = activeProjectLocal == null ||
                        (!activeProjectLocal.isExternal && activeProjectLocal.path == "root" && state.currentPath.isEmpty())

                    Text(
                        text = "files",
                        color = if (isAtFilesRoot) theme.textPrimary else theme.accent,
                        fontSize = theme.typography.small,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isAtFilesRoot) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable {
                            if (activeProjectLocal != null) {
                                vm.navigateUpToRoot()
                            }
                        }
                    )

                    activeProjectLocal?.let { proj ->
                        val isSandboxRoot = !proj.isExternal && proj.path == "root"
                        if (!isSandboxRoot) {
                            Text("/", color = theme.textMuted, fontSize = theme.typography.small, fontFamily = FontFamily.Monospace)

                            val isProjRoot = state.currentPath.isEmpty()
                            Text(
                                text = proj.name.lowercase(),
                                color = if (isProjRoot) theme.textPrimary else theme.accent,
                                fontSize = theme.typography.small,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isProjRoot) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable {
                                    if (!isProjRoot) {
                                        vm.navigateToFolderRoot(proj)
                                    }
                                }
                            )
                        }

                        if (state.currentPath.isNotEmpty()) {
                            val segments = state.currentPath.split('/')
                            segments.forEachIndexed { index, segment ->
                                Text("/", color = theme.textMuted, fontSize = theme.typography.small, fontFamily = FontFamily.Monospace)
                                val isLast = index == segments.lastIndex
                                val segmentPath = segments.take(index + 1).joinToString("/")
                                Text(
                                    text = segment,
                                    color = if (isLast) theme.textPrimary else theme.accent,
                                    fontSize = theme.typography.small,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clickable {
                                        if (!isLast) {
                                            vm.navigateToSegment(proj, segmentPath)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium), verticalAlignment = Alignment.CenterVertically) {
                    val isAtRoot = state.activeProject?.let { !it.isExternal && it.path == "root" && state.currentPath.isEmpty() } ?: false
                    if (isAtRoot) {
                        Text(
                            text = "[+ workspace]",
                            color = theme.accent,
                            fontSize = theme.typography.tiny,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                if (showWorkspaceIntro) {
                                    isIntroDialogOpen = true
                                } else {
                                    openDocumentTreeLauncher.launch(null)
                                }
                            }
                        )
                    }

                    Text(
                        text = if (isSortAscending) "[A-Z]" else "[Z-A]",
                        color = theme.accent,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { isSortAscending = !isSortAscending }
                            .padding(vertical = theme.dimensions.spacingSmall, horizontal = theme.dimensions.spacingMedium)
                    )
                }
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
                    val filteredItems = remember(state.allItems, searchQuery, isSortAscending) {
                        state.allItems.filterIsInstance<FileListItem.FileRow>().filter {
                            it.node.name.contains(searchQuery, ignoreCase = true)
                        }.let { list ->
                            if (isSortAscending) list.sortedBy { it.node.name.lowercase() }
                            else list.sortedByDescending { it.node.name.lowercase() }
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        EmptyStateHint(
                            title = "No matches found",
                            body = "Try searching for another filename.",
                            theme = theme
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top,
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            items(filteredItems) { item ->
                                SearchVfsNodeRow(
                                    node = item.node,
                                    project = item.project,
                                    theme = theme,
                                    onNodeClick = { clicked ->
                                        if (clicked.isDirectory) {
                                            vm.navigateToFolder(clicked, item.project)
                                            isSearchActive = false
                                            searchQuery = ""
                                        } else {
                                            onItemClick(EditorKey(item.project.id, clicked.relativePath))
                                        }
                                    },
                                    onShareClick = { clicked -> shareNode(context, clicked, item.project, storageManager) },
                                    onEditClick = { clicked -> nodeToRename = Pair(clicked, item.project) },
                                    onDeleteClick = { clicked -> nodeToDelete = Pair(clicked, item.project) }
                                )
                            }
                        }
                    }
                } else if (activeProj == null) {
                    // Root view: flat list of watched project folders
                    val sortedProjects = remember(state.projects, isSortAscending) {
                        if (isSortAscending) state.projects.sortedBy { it.name.lowercase() }
                        else state.projects.sortedByDescending { it.name.lowercase() }
                    }

                    if (sortedProjects.isEmpty()) {
                        EmptyStateHint(
                            title = "No workspaces",
                            body = "Tap [+ workspace] above to add a local folder.",
                            theme = theme
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top,
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            items(sortedProjects, key = { it.id }) { proj ->
                                SwipeableProjectRow(
                                    project = proj,
                                    theme = theme,
                                    appFont = appFont,
                                    onClick = { vm.navigateToFolderRoot(proj) },
                                    onShare = { shareNode(context, VfsNode.Directory(name = proj.name, relativePath = ""), proj, storageManager) },
                                    onEdit = { projectToRename = proj },
                                    onDelete = { projectToDelete = proj }
                                )
                            }
                        }
                    }
                } else {
                    // Drill-down view: list subfolders and files inside active project directory
                    val sortedFiles = remember(state.drillFiles, isSortAscending) {
                        if (isSortAscending) state.drillFiles.sortedBy { it.name.lowercase() }
                        else state.drillFiles.sortedByDescending { it.name.lowercase() }
                    }

                    if (sortedFiles.isEmpty()) {
                        EmptyStateHint(
                            title = "Folder is empty",
                            body = "Tap [+ file] or [+ folder] to add content.",
                            theme = theme
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
                                                    .border(BorderStroke(1.dp, theme.textMuted.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                                                    .onGloballyPositioned { coordinates ->
                                                        folderBounds["..parent.."] = coordinates.boundsInRoot()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("📁 Drop here to move up one level", color = theme.textMuted, fontSize = theme.typography.body)
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
                                    SwipeableFileRow(
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
                                                onItemClick(EditorKey(activeProj.id, node.relativePath))
                                            }
                                        },
                                        onShare = {
                                            if (!node.relativePath.startsWith("external:")) {
                                                shareNode(context, node, activeProj, storageManager)
                                            }
                                        },
                                        onEdit = {
                                            if (node.relativePath.startsWith("external:")) {
                                                val extId = node.relativePath.substringAfter("external:").toLong()
                                                val extProj = state.projects.find { it.id == extId }
                                                if (extProj != null) {
                                                    projectToRename = extProj
                                                }
                                            } else {
                                                nodeToRename = Pair(node, activeProj)
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
                            .border(BorderStroke(1.dp, theme.accent), RoundedCornerShape(4.dp))
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
            title = { Text("Add Local Folder", color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
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
                            colors = CheckboxDefaults.colors(checkedColor = theme.accent)
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
                                db.settingDao().insertSetting(SettingEntity("show_workspace_intro", "false"))
                            }
                        }
                        openDocumentTreeLauncher.launch(null)
                    }
                ) {
                    Text("Select Folder", color = theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { isIntroDialogOpen = false }) {
                    Text("Cancel", color = theme.textMuted)
                }
            },
            containerColor = theme.surface
        )
    }

    createFileDialogTargetProject?.let { targetProj ->
        InputDialog(
            title       = "New File in ${targetProj.name}",
            label       = "Filename",
            confirmText = "Create",
            theme       = theme,
            onDismiss   = { createFileDialogTargetProject = null },
            onConfirm   = { name ->
                if (shouldLaunchNewFile) {
                    vm.createFile(name, targetProj) { relativePath ->
                        onItemClick(EditorKey(targetProj.id, relativePath, focusOnStart = true))
                    }
                } else {
                    vm.createFile(name, targetProj)
                }
                createFileDialogTargetProject = null
            }
        )
    }

    createFolderDialogTargetProject?.let { targetProj ->
        InputDialog(
            title       = "New Folder in ${targetProj.name}",
            label       = "Folder name",
            confirmText = "Create",
            theme       = theme,
            onDismiss   = { createFolderDialogTargetProject = null },
            onConfirm   = { name ->
                vm.createFolder(name, targetProj)
                createFolderDialogTargetProject = null
            }
        )
    }

    nodeToDelete?.let { (node, project) ->
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text("Delete ${if (node.isDirectory) "Folder" else "File"}?",
                color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
            text  = { Text("Are you sure you want to delete '${node.name}'? This cannot be undone.",
                color = theme.textPrimary, fontSize = theme.typography.body) },
            confirmButton = {
                TextButton(onClick = { vm.deleteNode(node, project); nodeToDelete = null }) {
                    Text("Delete", color = theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) { Text("Cancel", color = theme.textMuted) }
            },
            containerColor = theme.surface
        )
    }

    nodeToRename?.let { (node, project) ->
        InputDialog(
            title       = "Rename ${if (node.isDirectory) "Folder" else "File"}",
            label       = "New name",
            confirmText = "Rename",
            theme       = theme,
            onDismiss   = { nodeToRename = null },
            onConfirm   = { newName ->
                vm.renameNode(node, newName, project)
                nodeToRename = null
            }
        )
    }

    projectToRename?.let { proj ->
        InputDialog(
            title       = "Rename Workspace",
            label       = "New name",
            confirmText = "Rename",
            theme       = theme,
            onDismiss   = { projectToRename = null },
            onConfirm   = { newName ->
                vm.renameProject(proj, newName)
                projectToRename = null
            }
        )
    }

    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Workspace?",
                color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
            text  = { Text("Are you sure you want to delete '${proj.name}'? This cannot be undone.",
                color = theme.textPrimary, fontSize = theme.typography.body) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProject(proj); projectToDelete = null }) {
                    Text("Delete", color = theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel", color = theme.textMuted) }
            },
            containerColor = theme.surface
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(theme.dimensions.elevationMedium),
            modifier = Modifier
                .clickable { onHeaderClick() }
                .padding(vertical = theme.dimensions.spacingSmall)
        ) {
            Text(
                text = buildString {
                    append(project.name.uppercase())
                    if (project.isExternal) append("  ☁️")
                },
                color = if (isSelected) theme.accent else theme.textMuted,
                fontSize = theme.typography.tiny,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = (theme.typography.tiny.value * 0.136f).sp
            )
            if (isSelected) {
                Text(
                    text = "• active",
                    color = theme.accent,
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
            Text(
                text = "[+ file]",
                color = theme.accent,
                fontSize = theme.typography.tiny,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { onCreateFileClick() }
                    .padding(horizontal = theme.dimensions.spacingSmall, vertical = theme.dimensions.spacingTiny)
            )
            Text(
                text = "[+ folder]",
                color = theme.accent,
                fontSize = theme.typography.tiny,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { onCreateFolderClick() }
                    .padding(horizontal = theme.dimensions.spacingSmall, vertical = theme.dimensions.spacingTiny)
            )
        }
    }
}

@Composable
private fun EmptyStateHint(
    title: String,
    body: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme
) {
    val appFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = theme.dimensions.iconHuge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = theme.textPrimary, fontFamily = appFont,
            fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(theme.dimensions.spacingSmall))
        Text(body, color = theme.textMuted, fontSize = theme.typography.small, textAlign = TextAlign.Center)
    }
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
            SwipeAction(label = "Share", color = theme.accent.copy(alpha = 0.85f), theme = theme, onClick = {
                onShare()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Edit",  color = theme.textMuted.copy(alpha = 0.55f), theme = theme, onClick = {
                onEdit()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Delete",color = theme.danger, theme = theme, onClick = {
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offsetX.value != 0f) {
                                scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            } else {
                                com.attachdesign.kern.TouchTracker.lastTouchPosition = offset
                                onClick()
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
                Text(details, color = theme.textMuted, fontSize = theme.typography.tiny,
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
            SwipeAction(label = "Share",  color = theme.accent.copy(alpha = 0.85f), theme = theme, onClick = {
                onShare()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Edit",   color = theme.textMuted.copy(alpha = 0.55f), theme = theme, onClick = {
                onEdit()
                scope.launch { offsetX.animateTo(0f) }
            })
            SwipeAction(label = "Delete", color = theme.danger, theme = theme, onClick = {
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
                Text("DIR", color = theme.textMuted, fontSize = theme.typography.tiny,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    color: Color,
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
            color = Color.White,
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
        appFont = when (theme.editorFontFamily.lowercase()) {
            "serif" -> FontFamily.Serif
            "sans-serif", "sansserif" -> FontFamily.SansSerif
            "monospace" -> FontFamily.Monospace
            else -> FontFamily.Default
        },
        isExternalProject = isExternalProject,
        onClick = { onNodeClick(node) },
        onShare = { onShareClick(node) },
        onEdit = { onEditClick(node) },
        onDelete = { onDeleteClick(node) }
    )
}

@Composable
fun InputDialog(
    title: String,
    label: String,
    confirmText: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value         = value,
                onValueChange = { value = it },
                label         = { Text(label) },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    focusedLabelColor  = theme.accent
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmText, color = theme.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } },
        containerColor = theme.surface
    )
}

@Composable
fun CreateProjectDialog(
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    selectedUri: String,
    onSelectFolder: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, uri: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(selectedUri) {
        if (name.isBlank() && selectedUri.isNotEmpty()) {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(selectedUri))
            doc?.name?.let { name = it }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Project Workspace", color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingLarge)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Project Name") },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        focusedLabelColor  = theme.accent
                    )
                )
                Column(verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingSmall)) {
                    Text(
                        text = if (selectedUri.isEmpty()) "Link local directory" else "Linked folder: ${Uri.parse(selectedUri).lastPathSegment?.substringAfterLast(":") ?: ""}",
                        color = theme.accent,
                        fontSize = theme.typography.body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onSelectFolder() }
                            .padding(vertical = theme.dimensions.spacingSmall)
                    )
                    if (selectedUri.isNotEmpty()) {
                        Text(
                            text = "Files in this directory will be loaded.",
                            color = theme.textMuted,
                            fontSize = theme.typography.tiny
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name, selectedUri) }, enabled = name.isNotBlank()) {
                Text("Create", color = theme.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } },
        containerColor = theme.surface
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
    val appFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
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
            SwipeAction(label = "Share",  color = theme.accent.copy(alpha = 0.85f), theme = theme, onClick = { onShareClick(node) })
            SwipeAction(label = "Edit",   color = theme.textMuted.copy(alpha = 0.55f), theme = theme, onClick = { onEditClick(node) })
            SwipeAction(label = "Delete", color = theme.danger, theme = theme, onClick = { onDeleteClick(node) })
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .background(theme.background)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offsetX.value != 0f) {
                                scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            } else {
                                com.attachdesign.kern.TouchTracker.lastTouchPosition = offset
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
                            color = theme.textMuted,
                            fontSize = theme.typography.tiny,
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
                Text(details, color = theme.textMuted, fontSize = theme.typography.tiny,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}


private fun shareNode(
    context: android.content.Context,
    node: VfsNode,
    project: ProjectEntity,
    storageManager: StorageManager
) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    scope.launch {
        val cacheDir = context.cacheDir
        val shareDir = java.io.File(cacheDir, "shared_exports")
        // Clean up stale temp files older than 1 hour
        if (shareDir.exists()) {
            shareDir.listFiles()?.forEach { file ->
                if (file.lastModified() < System.currentTimeMillis() - 3_600_000) {
                    file.delete()
                }
            }
        } else {
            shareDir.mkdirs()
        }

        val uriToShare: android.net.Uri? = if (node.isDirectory) {
            val zipFile = java.io.File(shareDir, "${node.name}_${System.currentTimeMillis()}.zip")

            try {
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                    suspend fun addDirToZip(dirNode: VfsNode, parentPath: String) {
                        val children = storageManager.listDirectory(project, dirNode.relativePath)
                        for (child in children) {
                            val entryName = if (parentPath.isEmpty()) child.name else "$parentPath/${child.name}"
                            if (child.isDirectory) {
                                zos.putNextEntry(java.util.zip.ZipEntry("$entryName/"))
                                zos.closeEntry()
                                addDirToZip(child, entryName)
                            } else {
                                zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                                val contentBytes = storageManager.readFile(project, child.relativePath).toByteArray(kotlin.text.Charsets.UTF_8)
                                zos.write(contentBytes)
                                zos.closeEntry()
                            }
                        }
                    }
                    addDirToZip(node, "")
                }
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
            } catch (e: Exception) {
                android.util.Log.e("ShareNode", "Failed to zip directory", e)
                null
            }
        } else {
            val shareFile = java.io.File(shareDir, node.name)
            val contentStr = storageManager.readFile(project, node.relativePath)
            shareFile.writeText(contentStr, kotlin.text.Charsets.UTF_8)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
        }

        uriToShare?.let { uri ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    type = if (node.isDirectory) "application/zip" else "text/plain"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${node.name}"))
            }
        }
    }
}
