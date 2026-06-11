package com.attachdesign.kern

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.ui.editor.EditorScreen
import com.attachdesign.kern.ui.editor.EditorViewModel
import com.attachdesign.kern.ui.main.MainScreen
import com.attachdesign.kern.ui.settings.SettingsScreen

@Composable
fun MainNavigation(
    db: AppDatabase,
    storageManager: StorageManager
) {
    val backStack = rememberNavBackStack(Main)
    val context = LocalContext.current.applicationContext

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    onItemClick = { navKey -> backStack.add(navKey) },
                    db = db,
                    storageManager = storageManager,
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<EditorKey> { key ->
                val editorViewModel: EditorViewModel = viewModel {
                    EditorViewModel(db, storageManager, context)
                }
                EditorScreen(
                    projectId = key.projectId,
                    filePath = key.filePath,
                    viewModel = editorViewModel,
                    onBackClick = {
                        editorViewModel.closeFile()
                        backStack.removeLastOrNull()
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
