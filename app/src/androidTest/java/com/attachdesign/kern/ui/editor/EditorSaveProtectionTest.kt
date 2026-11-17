package com.attachdesign.kern.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.DraftRecoveryStore
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.RecoveryDraft
import com.attachdesign.kern.data.storage.StorageManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditorSaveProtectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var storage: StorageManager
    private val stores = mutableListOf<ViewModelStore>()
    private val viewModels = mutableListOf<EditorViewModel>()
    private val project = ProjectEntity(1, "Save tests", "save_${System.nanoTime()}", false, true)
    private val path = "note.md"
    private val source = "A **quick thought**\r\n日本語 😀\t "
    private val recovery get() = DraftRecoveryStore(File(context.filesDir, "editor-recovery"))

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        storage = StorageManager(context)
        db.projectDao().insertProject(project)
        db.fileDao().insertFile(FileEntity(1, project.id, path, path, false, 10, "SYNCED"))
        runBlocking { storage.writeFile(project, path, "Original") }
    }

    private fun editor(): EditorViewModel = compose.runOnIdle {
        val store = ViewModelStore().also(stores::add)
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditorViewModel(db, storage, FileOperationsManager(db, storage, context), context) as T
        })[EditorViewModel::class.java].also(viewModels::add)
    }

    private fun show(viewModel: EditorViewModel, onBack: () -> Unit = {}) {
        compose.setContent { EditorScreen(project.id, path, viewModel, onBackClick = onBack, companionEnabled = false) }
        compose.waitUntil(30_000) { viewModel.uiState.value.isDocumentReady }
    }

    private fun flush(viewModel: EditorViewModel) {
        val job = compose.runOnIdle { viewModel.flushPendingSave() }
        runBlocking { job.join() }
        compose.waitForIdle()
    }

    @After
    fun cleanup() {
        compose.activityRule.scenario.close()
        runBlocking { withContext(Dispatchers.Main) { stores.forEach { it.clear() } } }
        runBlocking { viewModels.forEach { it.finalSaveJob?.join() } }
        runBlocking { storage.getAbsoluteFile(project, "").deleteRecursively() }
        recovery.remove(project, path)
        db.close()
    }

    @Test
    fun immediateToolbarBackSavesExactTextBeforeRemovingTheEditor() {
        val viewModel = editor()
        var closed by mutableStateOf(false)
        compose.setContent {
            if (!closed) EditorScreen(project.id, path, viewModel, onBackClick = { closed = true }, companionEnabled = false)
        }
        compose.waitUntil(30_000) { viewModel.uiState.value.isDocumentReady }
        compose.onNodeWithContentDescription("Document editor").performTextReplacement(source)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(15_000) { closed }
        assertEquals(source, runBlocking { storage.readFile(project, path) })
        assertNull(recovery.read(project, path))
    }

    @Test
    fun destinationConflictShowsRecoveryActionsAndFreshEditorRecoversExactDraft() {
        val viewModel = editor()
        show(viewModel)
        runBlocking { storage.writeFile(project, path, "Changed elsewhere") }
        compose.onNodeWithContentDescription("Document editor").performTextReplacement(source)
        compose.runOnIdle { viewModel.documentTextFieldState.edit { selection = TextRange(2, 8) } }
        flush(viewModel)
        compose.onNodeWithTag("Document save error").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText("Save a copy").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { image ->
            File(context.getExternalFilesDir(null), "save-protection.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
        assertEquals("Changed elsewhere", runBlocking { storage.readFile(project, path) })
        assertEquals(source, recovery.read(project, path)?.text)
        assertEquals("SYNCED", db.fileDao().getFileByPath(project.id, path)?.syncState)

        val reopened = editor()
        runBlocking { withContext(Dispatchers.Main) { reopened.loadFile(project.id, path) }?.join() }
        compose.waitUntil(15_000) { reopened.uiState.value.saveStatus == DocumentSaveStatus.ERROR }
        assertEquals(source, reopened.documentTextFieldState.text.toString())
        assertEquals(TextRange(2, 8), reopened.documentTextFieldState.selection)
        assertEquals("Changed elsewhere", runBlocking { storage.readFile(project, path) })
    }

    @Test
    fun missingNoteIsUnavailableInsteadOfAnEditableBlankAndRetryCanOpenIt() {
        runBlocking { storage.deleteFile(project, path) }
        val viewModel = editor()
        compose.setContent { EditorScreen(project.id, path, viewModel, onBackClick = {}, companionEnabled = false) }
        compose.waitUntil(15_000) { viewModel.uiState.value.loadError != null }
        compose.onNodeWithContentDescription("Document editor").assertDoesNotExist()
        compose.onNodeWithContentDescription("Format selection bold").assertDoesNotExist()
        runBlocking { storage.writeFile(project, path, "Restored") }
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(15_000) { viewModel.uiState.value.isDocumentReady }
        assertEquals("Restored", viewModel.documentTextFieldState.text.toString())
    }

    @Test
    fun recoveredDraftRetriesWhenFolderContentsStillMatchItsOriginalBase() {
        recovery.write(project, path, RecoveryDraft(source, "Original", 2, 8))
        val viewModel = editor()
        show(viewModel)
        compose.waitUntil(15_000) { viewModel.uiState.value.saveStatus == DocumentSaveStatus.SAVED }
        assertEquals(source, runBlocking { storage.readFile(project, path) })
        assertEquals(TextRange(2, 8), viewModel.documentTextFieldState.selection)
        assertNull(recovery.read(project, path))
    }

    @Test
    fun clearingViewModelFinishesTheLastEditOutsideItsCancelledScope() {
        val viewModel = editor()
        show(viewModel)
        compose.onNodeWithContentDescription("Document editor").performTextReplacement(source)
        compose.runOnIdle { stores.first().clear() }
        runBlocking { viewModel.finalSaveJob?.join() }
        assertEquals(source, runBlocking { storage.readFile(project, path) })
        assertEquals(DocumentSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
    }

    @Test
    fun backgroundingFlushesTheDraftWithoutWaitingForAutosave() {
        val viewModel = editor()
        show(viewModel)
        compose.onNodeWithContentDescription("Document editor").performTextReplacement(source)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.waitUntil(15_000) { viewModel.uiState.value.saveStatus == DocumentSaveStatus.SAVED }
        assertEquals(source, runBlocking { storage.readFile(project, path) })
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    }

}
