@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.attachdesign.kern.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.TextAttribute
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.SettingKeys
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditorTypingPreferencesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val project = ProjectEntity(1, "Typing", "typing_${System.nanoTime()}", false, true)
    private val path = "note.md"
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: EditorViewModel
    private var connection: InputConnection? = null
    private var editorInfo = EditorInfo()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.projectDao().insertProject(project)
        db.fileDao().insertFile(FileEntity(1, project.id, path, path, false, 0, "SYNCED"))
        val storage = StorageManager(context)
        runBlocking { storage.writeFile(project, path, "") }
        viewModel = compose.runOnIdle {
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditorViewModel(db, storage, FileOperationsManager(db, storage, context), context) as T
            })[EditorViewModel::class.java]
        }
        compose.setContent {
            InterceptPlatformTextInput({ request, _ ->
                editorInfo = EditorInfo()
                connection = request.createInputConnection(editorInfo)
                awaitCancellation()
            }) {
                EditorScreen(project.id, path, viewModel, onBackClick = {},
                    companionEnabled = false)
            }
        }
        compose.waitUntil(30_000) { viewModel.uiState.value.isDocumentReady }
        compose.onNodeWithContentDescription("Document editor").performClick()
        compose.waitUntil(15_000) { connection != null }
    }

    @After
    fun cleanup() {
        compose.activityRule.scenario.close()
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        runBlocking { viewModel.finalSaveJob?.join() }
        db.close()
    }

    private fun send(command: (InputConnection) -> Unit) {
        compose.runOnIdle { command(requireNotNull(connection)) }
        compose.waitForIdle()
    }

    private fun source() = compose.runOnIdle { viewModel.documentTextFieldState.text.toString() }

    private fun reset(text: String = "") {
        compose.runOnIdle {
            viewModel.documentTextFieldState.edit { replace(0, length, text); selection = TextRange(length) }
            viewModel.documentTextFieldState.undoState.clearHistory()
        }
        compose.waitForIdle()
    }

    private fun setting(key: String, value: Boolean, ready: (EditorUiState) -> Boolean) {
        db.settingDao().insertSetting(SettingEntity(key, value.toString()))
        compose.waitUntil(15_000) { ready(viewModel.uiState.value) }
        compose.waitForIdle()
    }

    @Test
    fun pairingSwitchesAffectTheActiveNativeEditorWithoutReloadingIt() {
        val cases = listOf<Triple<String, String, (EditorUiState) -> Boolean>>(
            Triple(SettingKeys.AUTO_COMPLETE_PARENS, "(") { it.autoCompleteParens },
            Triple(SettingKeys.AUTO_COMPLETE_BRACKETS, "[") { it.autoCompleteBrackets },
            Triple(SettingKeys.AUTO_COMPLETE_BRACES, "{") { it.autoCompleteBraces },
            Triple(SettingKeys.AUTO_COMPLETE_QUOTES, "\"") { it.autoCompleteQuotes },
            Triple(SettingKeys.AUTO_COMPLETE_SINGLE_QUOTES, "'") { it.autoCompleteSingleQuotes }
        )
        cases.forEach { (key, opening, enabled) ->
            reset()
            send { it.commitText(opening, 1) }
            assertEquals(2, source().length)
            setting(key, false) { !enabled(it) }
            reset()
            send { it.commitText(opening, 1) }
            assertEquals(opening, source())
            setting(key, true, enabled)
        }
        setting(SettingKeys.AUTO_COMPLETE_ENABLED, false) { !it.autoCompleteEnabled }
        reset()
        send { it.commitText("(", 1) }
        assertEquals("(", source())
    }

    @Test
    fun headingSpacingHonorsItsSwitchAndPairingUndoIsOneNativeTransaction() {
        send { it.commitText("#", 1); it.commitText("t", 1) }
        assertEquals("# t", source())
        setting(SettingKeys.AUTO_HEADER_SPACING, false) { !it.autoHeaderSpacing }
        reset()
        send { it.commitText("#", 1); it.commitText("t", 1) }
        assertEquals("#t", source())
        reset()
        send { it.commitText("(", 1) }
        assertEquals("()", source())
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals("", source())
        compose.onNodeWithContentDescription("More formatting actions").performClick()
        compose.onNodeWithText("Redo").performClick()
        assertEquals("()", source())
        assertEquals(TextRange(1), viewModel.documentTextFieldState.selection)
    }

    @Test
    fun composingTextAndBatchedCompositionRemainLiteralWithHelpersEnabled() {
        send { it.setComposingText("(", 1) }
        assertEquals("(", source())
        assertNotNull(viewModel.documentTextFieldState.composition)
        send { it.setComposingText("(日本語", 1); it.commitText("(日本語", 1) }
        assertEquals("(日本語", source())
        send { it.finishComposingText() }
        reset()
        send {
            it.beginBatchEdit()
            it.setComposingText("#", 1)
            it.setComposingText("#t", 1)
            it.endBatchEdit()
        }
        assertEquals("#t", source())
        assertNotNull(viewModel.documentTextFieldState.composition)
        send { it.commitText("#title", 1); it.finishComposingText() }
        assertEquals("# title", source())
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 34)
    fun modernCompositionAndReplacementApisAlsoPreserveLiteralInput() {
        send { it.setComposingText("\"", 1, TextAttribute.Builder().build()) }
        assertEquals("\"", source())
        assertNotNull(viewModel.documentTextFieldState.composition)
        send { it.finishComposingText(); it.replaceText(0, 1, "(", 1, null) }
        assertEquals("(", source())
    }

    @Test
    fun headingSpacingWaitsForAComposedWordToCommit() {
        send { it.commitText("#", 1); it.setComposingText("tit", 1) }
        assertEquals("#tit", source())
        assertNotNull(viewModel.documentTextFieldState.composition)
        send { it.commitText("title", 1) }
        assertEquals("# title", source())
        assertNull(viewModel.documentTextFieldState.composition)
        reset()
        send { it.commitText("#", 1); it.setComposingText("word", 1); it.finishComposingText() }
        assertEquals("#word", source())
        send { it.commitText(" ", 1) }
        assertEquals("# word ", source())
    }

    @Test
    fun nativeEnterContinuesAListAfterAComposingWord() {
        reset("- item")
        send { it.setComposingText("\n", 1) }
        assertEquals("- item\n", source())
        send { it.finishComposingText() }
        reset("- item ")
        send { it.setComposingText("more", 1) }
        send {
            it.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            it.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
        }
        assertEquals("- item more\n- ", source())
        assertNull(viewModel.documentTextFieldState.composition)
    }

    @Test
    fun capitalizationPreferenceUpdatesKeyboardHintsAfterCompositionEnds() {
        assertTrue(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
        send { it.setComposingText("draft", 1) }
        setting(SettingKeys.SENTENCE_CAPITALIZATION, false) { !it.sentenceCapitalization }
        assertTrue(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
        assertEquals("draft", source())
        assertNotNull(viewModel.documentTextFieldState.composition)
        send { it.finishComposingText() }
        compose.waitUntil(15_000) { editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES == 0 }
        assertEquals("draft", source())
        assertEquals(TextRange(5), viewModel.documentTextFieldState.selection)
    }

    @Test
    fun nativeClipboardPastePreservesSingleDelimitersNewlinesAndFullMarkdown() {
        listOf("(", "\"", "\n", "#heading\n\n**exact** 日本語 😀").forEach { pasted ->
            reset(if (pasted == "\n") "- item" else "")
            val before = source()
            compose.runOnIdle {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Fixture", pasted))
            }
            compose.onNodeWithContentDescription("Document editor").performSemanticsAction(SemanticsActions.PasteText) { it() }
            assertEquals(before + pasted, source())
        }
        reset()
        send { it.commitText("(", 1) }
        assertEquals("()", source())
    }
}
