package com.attachdesign.kern

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.IncomingFileImporter
import com.attachdesign.kern.ui.editor.EditorScreen
import com.attachdesign.kern.ui.editor.EditorViewModel
import com.attachdesign.kern.ui.main.MainScreen
import com.attachdesign.kern.ui.settings.SettingsScreen

@Composable
fun MainNavigation(
    db: AppDatabase,
    storageManager: StorageManager,
    externalOpenRequest: ExternalOpenRequest? = null,
    onExternalOpenHandled: (ExternalOpenRequest) -> Unit = {}
) {
    val backStack = rememberNavBackStack(Main)
    val context = LocalContext.current
    val appContext = context.applicationContext
    // H2: outlives any single nav entry, so the final save can't be cancelled by a pop.
    val navScope = rememberCoroutineScope()
    val fileOpsManager = androidx.compose.runtime.remember {
        FileOperationsManager(db, storageManager, appContext)
    }

    LaunchedEffect(externalOpenRequest?.id) {
        val request = externalOpenRequest ?: return@LaunchedEffect
        try {
            val importer = IncomingFileImporter(appContext, db, storageManager)
            val imported = importer.import(Uri.parse(request.uriString), request.mimeType)
            // M17: don't stack duplicate entries on rapid external opens.
            val editorKey = EditorKey(imported.projectId, imported.filePath)
            if (backStack.lastOrNull() != editorKey) backStack.add(editorKey)
            Toast.makeText(context, "Imported ${imported.fileName}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Cannot open file", Toast.LENGTH_LONG).show()
        } finally {
            onExternalOpenHandled(request)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            val toKey = targetState.key
            if (toKey is EditorKey) {
                scaleIn(
                    initialScale = 0.3f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) togetherWith fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            } else {
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) togetherWith fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            }
        },
        popTransitionSpec = {
            val fromKey = initialState.key
            if (fromKey is EditorKey) {
                fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) togetherWith (scaleOut(
                    targetScale = 0.3f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ))
            } else {
                fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) togetherWith (scaleOut(
                    targetScale = 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ))
            }
        },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    // M17: ignore rapid taps that would push duplicate entries.
                    onItemClick = { navKey -> if (backStack.lastOrNull() != navKey) backStack.add(navKey) },
                    db = db,
                    storageManager = storageManager,
                    fileOpsManager = fileOpsManager,
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<EditorKey> { key ->
                val editorViewModel: EditorViewModel = viewModel {
                    // H7: SavedStateHandle for the unsaved draft; falls back to an
                    // in-memory handle if the entry owner doesn't provide saved state.
                    val handle = runCatching { createSavedStateHandle() }.getOrDefault(SavedStateHandle())
                    EditorViewModel(db, storageManager, fileOpsManager, appContext, savedStateHandle = handle)
                }
                EditorScreen(
                    projectId = key.projectId,
                    filePath = key.filePath,
                    focusOnStart = key.focusOnStart,
                    viewModel = editorViewModel,
                    onBackClick = {
                        // H2: await the final save on a scope that outlives the entry.
                        navScope.launch {
                            editorViewModel.closeFile()
                            backStack.removeLastOrNull()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<SettingsKey> {
                SettingsScreen(
                    db = db,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
    )
}
