package com.example.modernandroidmarkdowneditor.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*

import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.modernandroidmarkdowneditor.data.local.AppDatabase
import com.example.modernandroidmarkdowneditor.data.local.ProjectEntity
import com.example.modernandroidmarkdowneditor.data.storage.StorageManager
import com.example.modernandroidmarkdowneditor.data.storage.VfsNode
import com.example.modernandroidmarkdowneditor.ui.theme.ThemeEngine

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    db: AppDatabase,
    storageManager: StorageManager,
    modifier: Modifier = Modifier
) {
    val vm: MainScreenViewModel = viewModel { MainScreenViewModel(db, storageManager) }
    val state by vm.explorerState.collectAsStateWithLifecycle()

    var showCreateFileDialog   by remember { mutableStateOf<ProjectEntity?>(null) }

    var showCreateFolderDialog by remember { mutableStateOf<ProjectEntity?>(null) }
    var nodeToDelete           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }
    var nodeToRename           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }


    val theme = ThemeEngine.DefaultLight.toColorTheme()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // ── App title ──────────────────────────────────────────────────────────
        Text(
            text = "Kern",
            fontSize = 26.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            color = theme.textPrimary,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Text(
            text = "A typography-first index of text works",
            fontSize = 11.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = theme.textMuted,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // ── Toolbar: path / create ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "INDEX: ALL FILES",
                color = theme.textPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[+ project]",
                    color = theme.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { vm.setCreateDialogOpen(true) }.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Single unified list ────────────────────────────────────────────────
        if (state.allItems.isEmpty()) {
            EmptyStateHint(
                title = "No files yet",
                body = "Tap [+ project] above to create your first workspace.",
                theme = theme
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Top
            ) {
                items(state.allItems) { item ->
                    when (item) {
                        is FileListItem.ProjectHeader -> ProjectSectionHeader(
                            project = item.project,
                            theme = theme,
                            onCreateFile = { showCreateFileDialog = item.project },
                            onCreateFolder = { showCreateFolderDialog = item.project }
                        )
                        is FileListItem.FileRow       -> VfsNodeRow(
                            node            = item.node,
                            theme           = theme,
                            isExternalProject = item.project.isExternal,
                            depth           = item.depth,
                            isExpanded      = item.isExpanded,
                            onNodeClick     = { node ->
                                if (node.isDirectory) vm.toggleFolder(node, item.project)
                                else onItemClick(EditorKey(item.project.id, node.relativePath))
                            },
                            onDeleteClick   = { node -> nodeToDelete = Pair(node, item.project) },
                            onRenameClick   = { node -> nodeToRename = Pair(node, item.project) }
                        )
                    }
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

    showCreateFileDialog?.let { project ->
        InputDialog(
            title       = "New Markdown File",
            label       = "Filename",
            confirmText = "Create",
            theme       = theme,
            onDismiss   = { showCreateFileDialog = null },
            onConfirm   = { name ->
                vm.createFile(project, "", name)
                showCreateFileDialog = null
            }
        )
    }

    showCreateFolderDialog?.let { project ->
        InputDialog(
            title       = "New Folder",
            label       = "Folder name",
            confirmText = "Create",
            theme       = theme,
            onDismiss   = { showCreateFolderDialog = null },
            onConfirm   = { name ->
                vm.createFolder(project, "", name)
                showCreateFolderDialog = null
            }
        )
    }


    nodeToRename?.let { (node, project) ->
        InputDialog(
            title       = "Rename",
            label       = "New name",
            confirmText = "Rename",
            theme       = theme,
            initialValue = node.name,
            onDismiss   = { nodeToRename = null },
            onConfirm   = { name ->
                vm.performRenameWithStorageManager(node, project, name)
                nodeToRename = null
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
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun ProjectSectionHeader(
    project: ProjectEntity,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append(project.name.uppercase())
                    if (project.isExternal) append("  ☁️")
                },
                color = theme.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "[+ file]",
                color = theme.accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onCreateFile() }
            )
            Text(
                text = "[+ folder]",
                color = theme.accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onCreateFolder() }
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = theme.textPrimary, fontFamily = FontFamily.Serif,
            fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VfsNodeRow(
    node: VfsNode,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    isExternalProject: Boolean,
    depth: Int,
    isExpanded: Boolean,
    onNodeClick: (VfsNode) -> Unit,
    onDeleteClick: (VfsNode) -> Unit,
    onRenameClick: (VfsNode) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onRenameClick(node)
                    false // Don't actually dismiss
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteClick(node)
                    false // Don't actually dismiss
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction == SwipeToDismissBoxValue.StartToEnd) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.accent.copy(alpha = 0.2f))
                        .padding(start = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = theme.accent)
                }
            } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.accent.copy(alpha = 0.2f))
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = theme.accent)
                }
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.background)
                    .clickable { onNodeClick(node) }
                    .padding(vertical = 8.dp)
                    .padding(start = (depth * 16).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    val icon = if (node.isDirectory) {
                        if (isExpanded) "v" else ">" // Twirl down indicator
                    } else {
                        "📄"
                    }
                    Text(icon, fontSize = 14.sp, modifier = Modifier.padding(bottom = 1.dp).width(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = node.name,
                        color      = theme.textPrimary,
                        fontSize   = 14.sp,
                        fontFamily = FontFamily.Serif,
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
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    val details = if (node.isDirectory) "DIR"
                    else "${(node as? VfsNode.File)?.size?.div(1024) ?: 0}KB"
                    Text(details, color = theme.textMuted, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
                    Spacer(Modifier.width(16.dp))
                }
            }
        }
    )
}

@Composable
fun InputDialog(
    title: String,
    label: String,
    confirmText: String,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
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
