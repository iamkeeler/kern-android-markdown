@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.attachdesign.kern.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownElementType
import com.attachdesign.kern.ui.theme.AppColorTheme
import com.attachdesign.kern.ui.theme.toFontFamily
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CompanionPane(
    state: CompanionState,
    editorState: EditorUiState,
    viewModel: EditorViewModel,
    onChooseReference: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = editorState.activeTheme
    val previewScroll = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }
    // Kept in the scaffold's saveable pane slot, including when suspended by compact width.
    val referenceScroll = key(state.reference) { rememberScrollState() }
    Column(modifier.background(theme.surface).semantics { paneTitle = "Companion" }) {
        TopAppBar(
            title = { Text("Companion", color = theme.textPrimary, fontSize = theme.typography.subtitle) },
            actions = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close companion", tint = theme.textPrimary)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.surface)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = theme.dimensions.spacingExtraLarge),
            horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium)) {
            FilterChip(
                selected = state.mode == CompanionMode.PREVIEW,
                onClick = { viewModel.companion.showPreview() },
                label = { Text("Preview") },
                colors = companionChipColors(theme)
            )
            FilterChip(
                selected = state.mode == CompanionMode.REFERENCE,
                onClick = onChooseReference,
                label = { Text(if (state.reference == null) "Open reference" else "Reference file") },
                colors = companionChipColors(theme)
            )
        }
        val fileName = if (state.mode == CompanionMode.PREVIEW) editorState.fileName else state.reference?.path?.substringAfterLast('/')
        Text("${fileName.orEmpty()} · Read only", color = theme.textPrimary, fontSize = theme.typography.small,
            modifier = Modifier.padding(theme.dimensions.spacingExtraLarge))
        when {
            state.mode == CompanionMode.REFERENCE && state.isLoading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(theme.dimensions.spacingExtraLarge)
                    .semantics { contentDescription = "Loading reference" }, color = theme.accent)
            }
            state.mode == CompanionMode.REFERENCE && state.error != null -> {
                Column(Modifier.padding(theme.dimensions.spacingExtraLarge)
                    .semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(state.error, color = theme.textPrimary)
                    TextButton(onClick = { viewModel.companion.retry() }) { Text("Retry", color = theme.textPrimary) }
                    TextButton(onClick = onChooseReference) { Text("Choose another file", color = theme.textPrimary) }
                }
            }
            else -> {
                val preview = state.mode == CompanionMode.PREVIEW
                ReadOnlyMarkdownDocument(
                    blocks = if (preview) editorState.paragraphs else state.blocks,
                    theme = theme,
                    fontScale = editorState.editorFontSizeScale,
                    resolveImage = if (preview) viewModel::resolveMarkdownImage else viewModel::resolveCompanionImage,
                    resolutionKey = if (preview) editorState.activeFilePath else state.reference.toString(),
                    modifier = Modifier.weight(1f).fillMaxWidth().imePadding()
                        .verticalScroll(if (preview) previewScroll else referenceScroll)
                        .padding(theme.dimensions.spacingExtraLarge)
                        .semantics { contentDescription = if (preview) "Reading preview" else "Reference document" }
                )
            }
        }
    }
}

@Composable
private fun companionChipColors(theme: AppColorTheme) = FilterChipDefaults.filterChipColors(
    containerColor = theme.surface, labelColor = theme.textPrimary,
    selectedContainerColor = theme.textPrimary, selectedLabelColor = theme.background
)

@Immutable
private data class ReadingBlock(val block: ImmutableParagraphBlock, val text: AnnotatedString)

@Composable
private fun ReadOnlyMarkdownDocument(
    blocks: ImmutableParagraphList,
    theme: AppColorTheme,
    fontScale: Float,
    resolveImage: suspend (String) -> String?,
    resolutionKey: String,
    modifier: Modifier
) {
    // Projection is disposable and cancellable. It never writes back to TextFieldState.
    val rendered by produceState<ImmutableList<ReadingBlock>>(persistentEmptyBlocks(), blocks, theme) {
        value = withContext(Dispatchers.Default) {
            val transform = MarkdownVisualTransformation(false, viewMode = ViewMode.RENDERED,
                tokenColor = theme.textMuted, codeBackgroundColor = theme.codeBackground)
            blocks.items.map { ReadingBlock(it, transform.filter(AnnotatedString(it.block.rawText)).text) }.toImmutableList()
        }
    }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium)) {
            if (blocks.items.isEmpty()) Text("Empty document", color = theme.textPrimary)
            rendered.forEach { reading ->
                val block = reading.block.block
                val image = block.elements.firstOrNull {
                    it.type == MarkdownElementType.IMAGE && it.constructStart == 0 && it.constructEnd == block.rawText.length
                }
                when {
                    block.blockType == MarkdownBlockType.TABLE -> TableRender(block.rawText, theme)
                    block.blockType == MarkdownBlockType.HORIZONTAL_RULE -> HorizontalDivider(color = theme.textPrimary)
                    image != null -> {
                        val model by produceState<String?>(null, image.extra, resolutionKey) {
                            value = image.extra?.let { resolveImage(it) }
                        }
                        val description = block.rawText.substring(image.start, image.end)
                        SubcomposeAsyncImage(model, contentDescription = description,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = theme.dimensions.popupMaxHeight),
                            error = { Text(description.ifBlank { "Image unavailable" }, color = theme.textPrimary) })
                    }
                    else -> {
                        val size = when (block.blockType) {
                            MarkdownBlockType.HEADER_1 -> theme.typography.h1
                            MarkdownBlockType.HEADER_2 -> theme.typography.h2
                            MarkdownBlockType.HEADER_3 -> theme.typography.h3
                            MarkdownBlockType.HEADER_4 -> theme.typography.h4
                            MarkdownBlockType.HEADER_5 -> theme.typography.h5
                            MarkdownBlockType.HEADER_6 -> theme.typography.h6
                            else -> theme.typography.body
                        } * fontScale
                        RenderedMarkdownText(block.rawText, reading.text,
                            TextStyle(color = theme.textPrimary, fontFamily = theme.toFontFamily(), fontSize = size,
                                lineHeight = size * 1.6f,
                                fontWeight = if (block.blockType.name.startsWith("HEADER_")) FontWeight.Bold else FontWeight.Normal),
                            resolveImage, resolutionKey, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun persistentEmptyBlocks(): ImmutableList<ReadingBlock> = emptyList<ReadingBlock>().toImmutableList()

@Composable
fun ReferencePicker(viewModel: EditorViewModel, onDismiss: () -> Unit) {
    val editor by viewModel.uiState.collectAsStateWithLifecycle()
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()
    var projectId by rememberSaveable { mutableLongStateOf(editor.activeProject?.id ?: 0L) }
    var path by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val files by produceState<ImmutableList<VfsNode>?>(null, projectId, path, retry) {
        value = null
        value = try { viewModel.listReferenceFiles(projectId, path) } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { emptyList<VfsNode>().toImmutableList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open reference", color = editor.activeTheme.textPrimary) },
        text = {
            Column(Modifier.heightIn(max = editor.activeTheme.dimensions.popupMaxHeight).verticalScroll(rememberScrollState())) {
                projects.forEach { project ->
                    TextButton(onClick = { projectId = project.id; path = "" }) {
                        Text(project.name, color = editor.activeTheme.textPrimary,
                            fontWeight = if (project.id == projectId) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Text(path.ifEmpty { "Project root" }, color = editor.activeTheme.textPrimary)
                if (path.isNotEmpty()) TextButton(onClick = { path = path.substringBeforeLast('/', "") }) { Text("Parent folder", color = editor.activeTheme.textPrimary) }
                if (files == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (files!!.isEmpty()) {
                    Text("No files available. Check folder access or select another project.", color = editor.activeTheme.textPrimary)
                    TextButton(onClick = { retry++ }) { Text("Refresh", color = editor.activeTheme.textPrimary) }
                }
                files?.forEach { node ->
                    if (node.isDirectory || node.name.endsWith(".md", true) || node.name.endsWith(".markdown", true) || node.name.endsWith(".txt", true)) {
                        TextButton(onClick = {
                            if (node.isDirectory) path = node.relativePath
                            else {
                                if (projectId == editor.activeProject?.id && node.relativePath == editor.activeFilePath) viewModel.companion.showPreview()
                                else viewModel.companion.showReference(ReferenceKey(projectId, node.relativePath))
                                onDismiss()
                            }
                        }) { Text(if (node.isDirectory) "${node.name}/" else node.name, color = editor.activeTheme.textPrimary) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = editor.activeTheme.textPrimary) } },
        containerColor = editor.activeTheme.surface
    )
}

@Composable
fun FoldShortcutDialog(
    theme: AppColorTheme,
    enabled: Boolean,
    available: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    soundEnabled: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onTryFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Fold to toggle split view") },
        text = {
            Column(Modifier.heightIn(max = theme.dimensions.popupMaxHeight).verticalScroll(rememberScrollState())) {
                Text("With the inner screen open, bend slightly and open flat again to show or hide the companion. Hold a bend to keep reading in book posture.")
                FoldFeedbackToggle("Fold shortcut", enabled, theme, onEnabledChange, available)
                if (!available) Text("Hinge-angle sensing isn't available on this device. Use Split view from the document toolbar.")
                FoldFeedbackToggle("Fold haptics", hapticsEnabled, theme, onHapticsChanged)
                FoldFeedbackToggle("Paper crease sound", soundEnabled, theme, onSoundChanged)
                Text("Sound follows your device's touch-sound, volume, silent-mode and Do Not Disturb settings.",
                    fontSize = theme.typography.small)
                TextButton(onClick = onTryFeedback, enabled = hapticsEnabled || soundEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.textPrimary)) { Text("Try feedback") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = theme.textPrimary) } },
        containerColor = theme.surface, titleContentColor = theme.textPrimary, textContentColor = theme.textPrimary
    )
}

@Composable
private fun FoldFeedbackToggle(label: String, checked: Boolean, theme: AppColorTheme,
    onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = theme.dimensions.buttonHeight)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = theme.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = theme.background, checkedTrackColor = theme.textPrimary,
                uncheckedThumbColor = theme.textPrimary, uncheckedTrackColor = theme.surface,
                uncheckedBorderColor = theme.textPrimary))
    }
}
