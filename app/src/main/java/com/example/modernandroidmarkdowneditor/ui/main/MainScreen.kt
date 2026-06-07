package com.example.modernandroidmarkdowneditor.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
    val explorerViewModel: MainScreenViewModel = viewModel { MainScreenViewModel(db, storageManager) }
    val uiState by explorerViewModel.uiState.collectAsStateWithLifecycle()

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var nodeToDelete by remember { mutableStateOf<VfsNode?>(null) }
    
    val theme = ThemeEngine.DefaultLight.toColorTheme() // Shell typography uses default light fallback

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(24.dp)
    ) {
        // Book Index Style Title
        Text(
            text = "Kern",
            fontSize = 26.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            color = theme.textPrimary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = "A typography-first index of text works",
            fontSize = 11.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = theme.textMuted,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Project Switcher
        ProjectSelectionRow(
            projects = uiState.projects,
            selectedProject = uiState.selectedProject,
            onProjectSelected = { explorerViewModel.selectProject(it) },
            onAddProjectClick = { explorerViewModel.setCreateDialogOpen(true) },
            theme = theme
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Index Path & Creation Tools
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.currentPath.isNotEmpty()) {
                    Text(
                        text = "[back]",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { explorerViewModel.navigateUp() }
                            .padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("/", color = theme.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "INDEX: " + if (uiState.currentPath.isEmpty()) "ROOT" else uiState.currentPath.uppercase(),
                    color = theme.textPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            // Create actions
            if (uiState.selectedProject != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "[+ new file]",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { showCreateFileDialog = true }
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        text = "[+ new folder]",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { showCreateFolderDialog = true }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Book-index style list
        if (uiState.selectedProject == null) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No project workspace selected", color = theme.textPrimary, fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Select an existing project above or create a new workspace to start.", color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else if (uiState.files.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Workspace is empty", color = theme.textPrimary, fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Create a new file or folder above to start writing markdown.", color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Top
            ) {
                items(uiState.files) { node ->
                    VfsNodeRow(
                        node = node,
                        theme = theme,
                        isExternalProject = uiState.selectedProject?.isExternal == true,
                        onNodeClick = { clickedNode ->
                            if (clickedNode.isDirectory) {
                                explorerViewModel.navigateToFolder(clickedNode.relativePath)
                            } else {
                                onItemClick(EditorKey(uiState.selectedProject!!.id, clickedNode.relativePath))
                            }
                        },
                        onDeleteClick = { nodeToDelete = it }
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.isCreateProjectDialogOpen) {
        CreateProjectDialog(
            theme = theme,
            onDismiss = { explorerViewModel.setCreateDialogOpen(false) },
            onCreate = { name, isExternal ->
                explorerViewModel.createProject(name, isExternal)
                explorerViewModel.setCreateDialogOpen(false)
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false; createName = "" },
            title = { Text("New Markdown File", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text("Filename") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = theme.accent, focusedLabelColor = theme.accent)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createName.isNotBlank()) {
                            explorerViewModel.createFile(createName)
                            showCreateFileDialog = false
                            createName = ""
                        }
                    },
                    enabled = createName.isNotBlank()
                ) {
                    Text("Create", color = theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false; createName = "" }) {
                    Text("Cancel", color = theme.textMuted)
                }
            },
            containerColor = theme.surface
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false; createName = "" },
            title = { Text("New Folder", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text("Folder Name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = theme.accent, focusedLabelColor = theme.accent)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createName.isNotBlank()) {
                            explorerViewModel.createFolder(createName)
                            showCreateFolderDialog = false
                            createName = ""
                        }
                    },
                    enabled = createName.isNotBlank()
                ) {
                    Text("Create", color = theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false; createName = "" }) {
                    Text("Cancel", color = theme.textMuted)
                }
            },
            containerColor = theme.surface
        )
    }

    if (nodeToDelete != null) {
        val node = nodeToDelete!!
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text("Delete ${if (node.isDirectory) "Folder" else "File"}?", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${node.name}'? This action cannot be undone.", color = theme.textPrimary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    explorerViewModel.deleteNode(node)
                    nodeToDelete = null
                }) {
                    Text("Delete", color = theme.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) {
                    Text("Cancel", color = theme.textMuted)
                }
            },
            containerColor = theme.surface
        )
    }
}

@Composable
fun ProjectSelectionRow(
    projects: List<ProjectEntity>,
    selectedProject: ProjectEntity?,
    onProjectSelected: (ProjectEntity) -> Unit,
    onAddProjectClick: () -> Unit,
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        projects.forEach { proj ->
            val isSelected = proj.id == selectedProject?.id
            Text(
                text = proj.name.uppercase(),
                color = if (isSelected) theme.accent else theme.textMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (isSelected) TextDecoration.Underline else TextDecoration.None,
                modifier = Modifier
                    .clickable { onProjectSelected(proj) }
                    .padding(vertical = 4.dp)
            )
        }
        Text(
            text = "[+ ADD PROJECT]",
            color = theme.accent,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { onAddProjectClick() }
                .padding(vertical = 4.dp)
        )
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
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom
        ) {
            val icon = if (node.isDirectory) "📁" else "📄"
            Text(icon, fontSize = 14.sp, modifier = Modifier.padding(bottom = 1.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = node.name,
                color = theme.textPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline()
            )
            val isSynced = node is VfsNode.File && node.syncState == "SYNCED" && !isExternalProject
            if (isSynced) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "☁️",
                    fontSize = 12.sp,
                    modifier = Modifier.alignByBaseline()
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = " . ".repeat(50),
                color = theme.textMuted.copy(alpha = 0.4f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f).alignByBaseline()
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        val details = if (node.isDirectory) {
            "DIR"
        } else {
            val sizeInKb = (node as? VfsNode.File)?.size?.let { it / 1024 } ?: 0
            "${sizeInKb}KB"
        }
        
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = details,
                color = theme.textMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "[delete]",
                color = theme.textMuted,
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
fun CreateProjectDialog(
    theme: com.example.modernandroidmarkdowneditor.ui.theme.AppColorTheme,
    onDismiss: () -> Unit,
    onCreate: (name: String, isExternal: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isExternal by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Project Workspace", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = theme.accent, focusedLabelColor = theme.accent)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isExternal = !isExternal }
                ) {
                    Checkbox(
                        checked = isExternal,
                        onCheckedChange = { isExternal = it },
                        colors = CheckboxDefaults.colors(checkedColor = theme.accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("External Scoped Storage (SAF)", color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Simulated SD Card workspace. Disables Cloud Sync.", color = theme.textMuted, fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, isExternal)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = theme.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textMuted)
            }
        },
        containerColor = theme.surface
    )
}
