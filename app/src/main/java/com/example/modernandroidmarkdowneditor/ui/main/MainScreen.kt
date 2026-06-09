package com.example.modernandroidmarkdowneditor.ui.main

import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.modernandroidmarkdowneditor.EditorKey
import com.example.modernandroidmarkdowneditor.SettingsKey
import com.example.modernandroidmarkdowneditor.ui.settings.MinimalOutlinedButton
import com.example.modernandroidmarkdowneditor.data.local.AppDatabase
import com.example.modernandroidmarkdowneditor.data.local.ProjectEntity
import com.example.modernandroidmarkdowneditor.data.storage.StorageManager
import com.example.modernandroidmarkdowneditor.data.storage.VfsNode
import com.example.modernandroidmarkdowneditor.ui.theme.ThemeEngine
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle

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

    var createFileDialogTargetProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var createFolderDialogTargetProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var nodeToDelete           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }
    var projectToDelete        by remember { mutableStateOf<ProjectEntity?>(null) }
    var nodeToRename           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }
    var projectToRename        by remember { mutableStateOf<ProjectEntity?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSortAscending by remember { mutableStateOf(true) }

    val selectedThemeIdSetting by db.settingDao().getSettingFlow("selected_theme_id").collectAsState(initial = null)
    val editorFontSetting by db.settingDao().getSettingFlow("editor_font_family").collectAsState(initial = null)
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

    val appFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // ── Brand Header (always visible) ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Kern title + quote — always shown
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kern",
                    fontSize = 28.sp,
                    fontFamily = appFont,
                    fontWeight = FontWeight.Light,
                    color = theme.textPrimary,
                    letterSpacing = (-0.5).sp
                )
                state.activeQuote?.let { quote ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "“${quote.text}” — ${quote.author}, ${quote.year}",
                        fontSize = 12.sp,
                        fontFamily = appFont,
                        fontWeight = FontWeight.Normal,
                        color = theme.textMuted,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }

            // Icon row: search toggle + settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(onClick = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                }) {
                    Text(if (isSearchActive) "✕" else "🔍", fontSize = 18.sp)
                }
                IconButton(onClick = { onItemClick(SettingsKey) }) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = theme.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── Action Row (Breadcrumbs or Search) ─────────────────────────────────
        HorizontalDivider(
            color = theme.textMuted.copy(alpha = 0.15f),
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = isSearchActive,
                label = "SearchBreadcrumbsTransition"
            ) { searching ->
                if (searching) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search files...", color = theme.textMuted, fontSize = 13.sp) },
                        textStyle = TextStyle(fontSize = 13.sp, color = theme.textPrimary),
                        modifier = Modifier.fillMaxSize(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.textMuted.copy(alpha = 0.3f),
                            cursorColor = theme.accent
                        )
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Clear back button
                            if (state.activeProject != null) {
                                Text(
                                    text = "←",
                                    color = theme.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { vm.navigateUp() }
                                        .padding(end = 8.dp)
                                )
                            }

                            // files / notes / work
                            Text(
                                text = "files",
                                color = if (state.activeProject == null) theme.textPrimary else theme.accent,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (state.activeProject == null) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable {
                                    if (state.activeProject != null) {
                                        vm.navigateUpToRoot()
                                    }
                                }
                            )

                            state.activeProject?.let { proj ->
                                Text("/", color = theme.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)

                                val isProjRoot = state.currentPath.isEmpty()
                                Text(
                                    text = proj.name.lowercase(),
                                    color = if (isProjRoot) theme.textPrimary else theme.accent,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isProjRoot) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clickable {
                                        if (!isProjRoot) {
                                            vm.navigateToFolderRoot(proj)
                                        }
                                    }
                                )

                                if (state.currentPath.isNotEmpty()) {
                                    val segments = state.currentPath.split('/')
                                    segments.forEachIndexed { index, segment ->
                                        Text("/", color = theme.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                        val isLast = index == segments.lastIndex
                                        val segmentPath = segments.take(index + 1).joinToString("/")
                                        Text(
                                            text = segment,
                                            color = if (isLast) theme.textPrimary else theme.accent,
                                            fontSize = 12.sp,
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

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (state.activeProject == null) {
                                Text(
                                    text = "[+ workspace]",
                                    color = theme.accent,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.clickable { vm.setCreateDialogOpen(true) }
                                )
                            }

                            Text(
                                text = if (isSortAscending) "[A-Z]" else "[Z-A]",
                                color = theme.accent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable { isSortAscending = !isSortAscending }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Single unified list and floating buttons container ─────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = Triple(state.activeProject, state.currentPath, isSearchActive && searchQuery.isNotEmpty()),
                transitionSpec = {
                    fadeIn(animationSpec = tween(50)) togetherWith fadeOut(animationSpec = tween(50))
                },
                label = "FileExplorerTransition"
            ) { (activeProj, currentPath, isSearchingLocal) ->
                if (isSearchingLocal) {
                    // Global search results view (intermingled files & folders)
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
                            verticalArrangement = Arrangement.Top
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
                            body = "Tap [+ workspace] above to add a local or cloud folder.",
                            theme = theme
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top
                        ) {
                            items(sortedProjects, key = { it.id }) { proj ->
                                SwipeableProjectRow(
                                    project = proj,
                                    theme = theme,
                                    appFont = appFont,
                                    onClick = { vm.navigateToFolderRoot(proj) },
                                    onShare = { /* TODO: zip & share */ },
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
                            verticalArrangement = Arrangement.Top
                        ) {
                            items(sortedFiles, key = { it.relativePath }) { node ->
                                SwipeableFileRow(
                                    node = node,
                                    theme = theme,
                                    appFont = appFont,
                                    isExternalProject = activeProj.isExternal,
                                    onClick = {
                                        if (node.isDirectory) vm.navigateToFolder(node, activeProj)
                                        else onItemClick(EditorKey(activeProj.id, node.relativePath))
                                    },
                                    onShare = { /* TODO: share */ },
                                    onEdit = { nodeToRename = Pair(node, activeProj) },
                                    onDelete = { nodeToDelete = Pair(node, activeProj) }
                                )
                            }
                        }
                    }
                }
            }

            // Floating action buttons: always shown if a project is active or selected
            val targetProj = state.activeProject ?: state.projects.find { it.isSelected } ?: state.projects.firstOrNull()
            if (targetProj != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    MinimalOutlinedButton(
                        text = "+ Folder",
                        onClick = { createFolderDialogTargetProject = targetProj },
                        theme = theme
                    )
                    MinimalOutlinedButton(
                        text = "+ File",
                        onClick = { createFileDialogTargetProject = targetProj },
                        theme = theme
                    )
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────
    if (state.isCreateProjectDialogOpen) {
        CreateProjectDialog(
            theme     = theme,
            onDismiss = { vm.setCreateDialogOpen(false) },
            onCreate  = { name, isExternal ->
                vm.createProject(name, isExternal)
                vm.setCreateDialogOpen(false)
            }
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
                vm.createFile(name, targetProj)
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
                color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text  = { Text("Are you sure you want to delete '${node.name}'? This cannot be undone.",
                color = theme.textPrimary, fontSize = 13.sp) },
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
                color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text  = { Text("Are you sure you want to delete '${proj.name}'? This cannot be undone.",
                color = theme.textPrimary, fontSize = 13.sp) },
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
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun ProjectSectionHeader(
    project: ProjectEntity,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    isSelected: Boolean,
    onHeaderClick: () -> Unit,
    onCreateFileClick: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clickable { onHeaderClick() }
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = buildString {
                    append(project.name.uppercase())
                    if (project.isExternal) append("  ☁️")
                },
                color = if (isSelected) theme.accent else theme.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            if (isSelected) {
                Text(
                    text = "• active",
                    color = theme.accent,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[+ file]",
                color = theme.accent,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { onCreateFileClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = "[+ folder]",
                color = theme.accent,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { onCreateFolderClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateHint(
    title: String,
    body: String,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme
) {
    val appFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = theme.textPrimary, fontFamily = appFont,
            fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

// ── Swipe-to-reveal helpers ───────────────────────────────────────────────────

private val SWIPE_REVEAL_WIDTH = 216.dp // 3 × 72dp action buttons

@Composable
fun SwipeableFileRow(
    node: VfsNode,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    appFont: FontFamily,
    isExternalProject: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { SWIPE_REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
                .width(SWIPE_REVEAL_WIDTH)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeAction(label = "Share", color = theme.accent.copy(alpha = 0.85f), onClick = onShare)
            SwipeAction(label = "Edit",  color = theme.textMuted.copy(alpha = 0.55f), onClick = onEdit)
            SwipeAction(label = "Delete",color = Color(0xFFCC3333), onClick = onDelete)
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
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    val icon = if (node.isDirectory) "📁" else "📄"
                    Text(icon, fontSize = 14.sp, modifier = Modifier.padding(bottom = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = node.name,
                        color      = theme.textPrimary,
                        fontSize   = 14.sp,
                        fontFamily = appFont,
                        fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Normal,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.alignByBaseline()
                    )
                    val isSynced = node is VfsNode.File && node.syncState == "SYNCED" && !isExternalProject
                    if (isSynced) {
                        Spacer(Modifier.width(4.dp))
                        Text("☁️", fontSize = 12.sp, modifier = Modifier.alignByBaseline())
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = " . ".repeat(50),
                        color    = theme.textMuted.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(8.dp))
                val details = if (node.isDirectory) "DIR"
                else "${(node as? VfsNode.File)?.size?.div(1024) ?: 0}KB"
                Text(details, color = theme.textMuted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

@Composable
fun SwipeableProjectRow(
    project: ProjectEntity,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    appFont: FontFamily,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { SWIPE_REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
                .width(SWIPE_REVEAL_WIDTH)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeAction(label = "Share",  color = theme.accent.copy(alpha = 0.85f), onClick = onShare)
            SwipeAction(label = "Edit",   color = theme.textMuted.copy(alpha = 0.55f), onClick = onEdit)
            SwipeAction(label = "Delete", color = Color(0xFFCC3333), onClick = onDelete)
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
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text("📁", fontSize = 14.sp, modifier = Modifier.padding(bottom = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = project.name,
                        color = theme.textPrimary,
                        fontSize = 14.sp,
                        fontFamily = appFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline()
                    )
                    if (project.isExternal) {
                        Spacer(Modifier.width(4.dp))
                        Text("☁️", fontSize = 12.sp, modifier = Modifier.alignByBaseline())
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = " . ".repeat(50),
                        color = theme.textMuted.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("DIR", color = theme.textMuted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
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
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
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
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
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
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    onDismiss: () -> Unit,
    onCreate: (name: String, isExternal: Boolean) -> Unit
) {
    var name       by remember { mutableStateOf("") }
    var isExternal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Project Workspace", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Project Name") },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        focusedLabelColor  = theme.accent
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isExternal = !isExternal }
                ) {
                    Checkbox(checked = isExternal, onCheckedChange = { isExternal = it },
                        colors = CheckboxDefaults.colors(checkedColor = theme.accent))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Cloud project ☁️", color = theme.textPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Files stored in external / scoped storage.",
                            color = theme.textMuted, fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name, isExternal) }, enabled = name.isNotBlank()) {
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
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
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
    val revealWidthPx = with(density) { SWIPE_REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < -revealWidthPx / 2)
                                offsetX.animateTo(-revealWidthPx, spring(stiffness = Spring.StiffnessMediumLow))
                            else
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) } },
                    onHorizontalDrag = { _, delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealWidthPx, 0f))
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(SWIPE_REVEAL_WIDTH)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeAction(label = "Share",  color = theme.accent.copy(alpha = 0.85f), onClick = { onShareClick(node) })
            SwipeAction(label = "Edit",   color = theme.textMuted.copy(alpha = 0.55f), onClick = { onEditClick(node) })
            SwipeAction(label = "Delete", color = Color(0xFFCC3333), onClick = { onDeleteClick(node) })
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .background(theme.background)
                .clickable {
                    if (offsetX.value != 0f) {
                        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    } else {
                        onNodeClick(node)
                    }
                }
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    val icon = if (node.isDirectory) "📁" else "📄"
                    Text(icon, fontSize = 14.sp, modifier = Modifier.padding(bottom = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.alignByBaseline()) {
                        Text(
                            text       = node.name,
                            color      = theme.textPrimary,
                            fontSize   = 14.sp,
                            fontFamily = appFont,
                            fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Normal,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "in ${project.name}/${node.relativePath.substringBeforeLast('/', "")}",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = " . ".repeat(50),
                        color    = theme.textMuted.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(8.dp))
                val details = if (node.isDirectory) "DIR"
                else "${(node as? VfsNode.File)?.size?.div(1024) ?: 0}KB"
                Text(details, color = theme.textMuted, fontSize = 11.sp,
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
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val cacheDir = context.cacheDir
        val shareDir = java.io.File(cacheDir, "share_temp")
        if (!shareDir.exists()) shareDir.mkdirs()

        val uriToShare: android.net.Uri? = if (node.isDirectory) {
            // Zip the folder
            val zipFile = java.io.File(shareDir, "${node.name}.zip")
            if (zipFile.exists()) zipFile.delete()

            // Simple zip implementation is non-trivial without ZipOutputStream.
            // Let's use ZipOutputStream
            try {
                val zos = java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile))
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
                            val contentBytes = storageManager.readFile(project, child.relativePath).toByteArray()
                            zos.write(contentBytes)
                            zos.closeEntry()
                        }
                    }
                }
                addDirToZip(node, "")
                zos.close()
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    zipFile
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            val shareFile = java.io.File(shareDir, node.name)
            val contentStr = storageManager.readFile(project, node.relativePath)
            shareFile.writeText(contentStr)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                shareFile
            )
        }

        uriToShare?.let { uri ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    type = if (node.isDirectory) "application/zip" else "text/markdown"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${node.name}"))
            }
        }
    }
}
