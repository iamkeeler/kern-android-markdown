package com.example.modernandroidmarkdowneditor.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
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
    val state by vm.uiState.collectAsStateWithLifecycle()

    var showCreateFileDialog   by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createName             by remember { mutableStateOf("") }
    var nodeToDelete           by remember { mutableStateOf<Pair<VfsNode, ProjectEntity>?>(null) }

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

        // ── Toolbar: back / path / create ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Back is always shown when drilled into a project
                if (state.activeProject != null) {
                    Text(
                        text = "[back]",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { vm.navigateUp() }
                            .padding(vertical = 4.dp, horizontal = 0.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("/", color = theme.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                }
                val pathLabel = when {
                    state.activeProject == null           -> "ALL FILES"
                    state.currentPath.isEmpty()           -> state.activeProject!!.name.uppercase()
                    else                                  -> state.currentPath.uppercase()
                }
                Text(
                    text = "INDEX: $pathLabel",
                    color = theme.textPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Create actions only available when drilled into a specific project folder
                if (state.activeProject != null) {
                    Text(
                        text = "[+ file]",
                        color = theme.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { showCreateFileDialog = true }.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "[+ folder]",
                        color = theme.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { showCreateFolderDialog = true }.padding(vertical = 4.dp)
                    )
                }
                Text(
                    text = "[+ project]",
                    color = theme.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { vm.setCreateDialogOpen(true) }.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Single unified list ────────────────────────────────────────────────
        if (state.activeProject == null) {
            // Root view: flat list of all projects and their files
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
                            is FileListItem.ProjectHeader -> ProjectSectionHeader(item.project, theme)
                            is FileListItem.FileRow       -> VfsNodeRow(
                                node            = item.node,
                                theme           = theme,
                                isExternalProject = item.project.isExternal,
                                onNodeClick     = { node ->
                                    if (node.isDirectory) vm.navigateToFolder(node, item.project)
                                    else onItemClick(EditorKey(item.project.id, node.relativePath))
                                },
                                onDeleteClick   = { node -> nodeToDelete = Pair(node, item.project) }
                            )
                        }
                    }
                }
            }
        } else {
            // Drill-down view: single project subfolder
            if (state.drillFiles.isEmpty()) {
                EmptyStateHint(
                    title = "Folder is empty",
                    body = "Tap [+ file] or [+ folder] above to add something.",
                    theme = theme
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Top
                ) {
                    items(state.drillFiles) { node ->
                        VfsNodeRow(
                            node              = node,
                            theme             = theme,
                            isExternalProject = state.activeProject!!.isExternal,
                            onNodeClick       = { clicked ->
                                if (clicked.isDirectory) vm.navigateToFolder(clicked, state.activeProject!!)
                                else onItemClick(EditorKey(state.activeProject!!.id, clicked.relativePath))
                            },
                            onDeleteClick     = { node -> nodeToDelete = Pair(node, state.activeProject!!) }
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

    if (showCreateFileDialog) {
        InputDialog(
            title       = "New Markdown File",
            label       = "Filename",
            confirmText = "Create",
            theme       = theme,
            onDismiss   = { showCreateFileDialog = false; createName = "" },
            onConfirm   = { vm.createFile(it); showCreateFileDialog = false; createName = "" }
        )
    }

    if (showCreateFolderDialog) {
        InputDialog(
            title       = "New Folder",
            label       = "Folder name",
            confirmText = "Create",
            theme       = theme,
            onDismiss   = { showCreateFolderDialog = false; createName = "" },
            onConfirm   = { vm.createFolder(it); showCreateFolderDialog = false; createName = "" }
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
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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

@Composable
fun VfsNodeRow(
    node: VfsNode,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    isExternalProject: Boolean,
    onNodeClick: (VfsNode) -> Unit,
    onDeleteClick: (VfsNode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNodeClick(node) }
            .padding(vertical = 8.dp),
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
            Text(
                text     = "[delete]",
                color    = theme.textMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { onDeleteClick(node) }
                    .padding(horizontal = 4.dp)
                    .alignByBaseline()
            )
        }
    }
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
