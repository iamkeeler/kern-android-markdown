package com.example.modernandroidmarkdowneditor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.modernandroidmarkdowneditor.data.local.AppDatabase
import com.example.modernandroidmarkdowneditor.data.storage.StorageManager
import com.example.modernandroidmarkdowneditor.ui.editor.EditorScreen
import com.example.modernandroidmarkdowneditor.ui.editor.EditorViewModel
import com.example.modernandroidmarkdowneditor.ui.main.MainScreen

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
                    modifier = Modifier.safeDrawingPadding()
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
        },
    )
}
