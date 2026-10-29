package com.attachdesign.kern.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.attachdesign.kern.ui.common.*
import com.attachdesign.kern.ui.main.dialogs.NamingOperationState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.SettingKeys
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.StorageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Measures the production field, including its output transformation and setting observers. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class EditorTypographyInstrumentedTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var storage: StorageManager
    private lateinit var fileOperations: FileOperationsManager
    private lateinit var viewModel: EditorViewModel
    private lateinit var textMeasurer: TextMeasurer
    private lateinit var deletionSession: DeletionSessionViewModel
    private val project = ProjectEntity(
        id = 1L, name = "Typography", path = "typography_${System.nanoTime()}",
        isExternal = false, isSelected = true
    )
    private val path = "document.md"

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        storage = StorageManager(context)
        fileOperations = FileOperationsManager(db, storage, context)
        db.projectDao().insertProject(project)
        db.fileDao().insertFile(FileEntity(
            id = 1L, projectId = project.id, name = path, relativePath = path,
            isDirectory = false, lastModified = 0L, syncState = "SYNCED"
        ))
        db.settingDao().insertSetting(SettingEntity(SettingKeys.EDITOR_FONT_SIZE_SCALE, "1.0"))
        db.settingDao().insertSetting(SettingEntity(SettingKeys.EDITOR_FONT_FAMILY, "serif"))
        viewModel = EditorViewModel(db, storage, fileOperations, context)
        deletionSession = DeletionSessionViewModel(FileOperationsDeletionBackend(fileOperations))
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.close()
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        runBlocking { deletionSession.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        db.close()
    }

    private fun showDocument(source: String, restoration: StateRestorationTester? = null) {
        runBlocking { storage.writeFile(project, path, source) }
        val content: @Composable () -> Unit = {
            var activePath by rememberSaveable { mutableStateOf(path) }
            var showEditor by rememberSaveable { mutableStateOf(true) }
            val measurer = rememberTextMeasurer()
            SideEffect { textMeasurer = measurer }
            CompositionLocalProvider(LocalDeletionSession provides deletionSession) {
                Box(Modifier.fillMaxSize()) {
                    if (showEditor) EditorScreen(project.id, activePath, viewModel,
                        onBackClick = { showEditor = false },
                        onIdentityChanged = { _, newPath -> activePath = newPath })
                    else Text("Browser destination")
                    DeletionFeedback(deletionSession, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        if (restoration == null) composeTestRule.setContent(content) else restoration.setContent(content)
        composeTestRule.waitUntil(30_000) {
            viewModel.uiState.value.activeFilePath == path &&
                viewModel.documentTextFieldState.text.toString() == source
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun failedRenameRetainsDraftAndSuccessfulRenameKeepsSessionAcrossRestoration() {
        val restoration = StateRestorationTester(composeTestRule)
        showDocument("Original draft\r\n", restoration)
        runBlocking { storage.writeFile(project, "existing.md", "keep existing") }
        val draft = "Unsaved **draft**\r\n\tend  "
        composeTestRule.runOnIdle {
            viewModel.documentTextFieldState.edit { replace(0, length, draft); selection = TextRange(2, 9) }
        }
        val selection = viewModel.documentTextFieldState.selection
        composeTestRule.onNodeWithContentDescription("More Options").performClick()
        composeTestRule.onNodeWithText("Rename", useUnmergedTree = false).performClick()
        val input = composeTestRule.onNode(hasSetTextAction() and hasText(path))
        input.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange, TextRange(0, 8)))
        input.performTextReplacement("existing.md")
        composeTestRule.onNodeWithText("Rename", useUnmergedTree = false).performClick()
        composeTestRule.waitUntil(15_000) { viewModel.renameOperations.records.value.values.any { it is NamingOperationState.Failed } }
        composeTestRule.onNodeWithText("Rename File").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText("existing.md")).performTextReplacement("renamed.markdown")
        composeTestRule.onNodeWithText("Rename", useUnmergedTree = false).performClick()
        composeTestRule.waitUntil(15_000) { viewModel.uiState.value.activeFilePath == "renamed.markdown" }
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.runOnIdle {
            assertEquals(draft, viewModel.documentTextFieldState.text.toString())
            assertEquals(selection, viewModel.documentTextFieldState.selection)
            assertTrue(viewModel.documentTextFieldState.undoState.canUndo)
        }
        assertEquals(draft, runBlocking { storage.readFile(project, "renamed.markdown") })
        assertEquals("keep existing", runBlocking { storage.readFile(project, "existing.md") })
        assertTrue(!runBlocking { storage.fileExists(project, path) })
    }

    @Test
    fun deleteUndoSurvivesLeavingEditorAndRestoringPresentation() {
        val restoration = StateRestorationTester(composeTestRule)
        val source = "Recover exactly\r\n\t  "
        showDocument(source, restoration)
        composeTestRule.onNodeWithContentDescription("More Options").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitUntil(15_000) { deletionSession.receipts.value.any { it.phase == DeletionPhase.Offered } }
        composeTestRule.onNodeWithText("Browser destination").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntil(15_000) { deletionSession.receipts.value.isEmpty() }
        assertEquals(source, runBlocking { storage.readFile(project, path) })
        assertTrue(db.fileDao().getFileByPath(project.id, path) != null)
        composeTestRule.onNodeWithText("Browser destination").assertIsDisplayed()
    }

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithContentDescription("Document editor")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun setScale(scale: Float) {
        db.settingDao().insertSetting(SettingEntity(SettingKeys.EDITOR_FONT_SIZE_SCALE, scale.toString()))
        composeTestRule.waitUntil(15_000) { viewModel.uiState.value.editorFontSizeScale == scale }
        composeTestRule.waitForIdle()
    }

    private fun spanAt(layout: TextLayoutResult, offset: Int): SpanStyle =
        layout.layoutInput.text.spanStyles
            .filter { offset >= it.start && offset < it.end }
            .fold(layout.layoutInput.style.toSpanStyle()) { style, range -> style.merge(range.item) }

    private fun assertRun(layout: TextLayoutResult, text: String, check: (SpanStyle) -> Unit) {
        val start = layout.layoutInput.text.text.indexOf(text)
        assertTrue("Missing visible run: $text", start >= 0)
        (start until start + text.length).forEach { check(spanAt(layout, it)) }
    }

    @Test
    fun documentFieldUsesEditorMetricsForEveryPresetAndViewMode() {
        val source = "Body anchor\n\n# Heading\n\n`code`\n\nEnd"
        showDocument(source)
        ViewMode.entries.forEach { mode ->
            composeTestRule.runOnIdle { viewModel.changeViewMode(mode) }
            listOf(0.8f, 1f, 1.2f, 1.4f).forEach { scale ->
                setScale(scale)
                val result = layout()
                assertEquals(16f * scale, result.layoutInput.style.fontSize.value, 0.0001f)
                assertEquals(25.6f * scale, result.layoutInput.style.lineHeight.value, 0.0001f)
                assertRun(result, "Body anchor") {
                    assertEquals(16f * scale, it.fontSize.value, 0.0001f)
                    assertEquals(if (mode == ViewMode.RAW_PLAIN_TEXT) FontFamily.Monospace else FontFamily.Serif,
                        it.fontFamily)
                }
                if (mode == ViewMode.RAW_PLAIN_TEXT) {
                    assertEquals(source, result.layoutInput.text.text)
                    assertRun(result, "# Heading") { assertEquals(16f * scale, it.fontSize.value, 0.0001f) }
                } else {
                    assertRun(result, "Heading") { assertEquals(28f * scale, it.fontSize.value, 0.0001f) }
                    assertRun(result, "code") { assertEquals(14.4f * scale, it.fontSize.value, 0.0001f) }
                }
                composeTestRule.runOnIdle { assertEquals(source, viewModel.documentTextFieldState.text.toString()) }
            }
        }
    }

    @Test
    fun renderedRunsKeepCompleteStylesAndHeadingsHaveSafeMeasuredLineBounds() {
        showDocument("Body anchor\n\n# Heading one\n\n## Heading two\n\n### Heading three\n\n" +
            "#### Heading four\n\n##### Heading five\n\n###### Heading six\n\n" +
            "**bold tail** and *italic tail* and `code tail`\n\n~~~kotlin\nval alpha = 1\n}\n~~~\n\nEnd")
        val result = layout()
        listOf(
            Triple("Heading one", 28f, FontWeight.Bold),
            Triple("Heading two", 24.8f, FontWeight.Bold),
            Triple("Heading three", 21.6f, FontWeight.SemiBold),
            Triple("Heading four", 18.4f, FontWeight.SemiBold),
            Triple("Heading five", 18.4f, FontWeight.SemiBold),
            Triple("Heading six", 18.4f, FontWeight.SemiBold)
        ).forEach { (heading, size, weight) ->
            assertRun(result, heading) {
                assertEquals(size, it.fontSize.value, 0.0001f)
                assertEquals(weight, it.fontWeight)
            }
            val line = result.getLineForOffset(result.layoutInput.text.text.indexOf(heading))
            lateinit var natural: TextLayoutResult
            composeTestRule.runOnIdle {
                natural = textMeasurer.measure(AnnotatedString(heading),
                    style = TextStyle(fontFamily = FontFamily.Serif, fontSize = size.sp, fontWeight = weight))
            }
            val ascent = result.getLineBaseline(line) - result.getLineTop(line)
            val descent = result.getLineBottom(line) - result.getLineBaseline(line)
            assertTrue("$heading ascent $ascent was below natural ${natural.firstBaseline - natural.getLineTop(0)}",
                ascent + 1f >= natural.firstBaseline - natural.getLineTop(0))
            assertTrue("$heading descent $descent was below natural ${natural.getLineBottom(0) - natural.firstBaseline}",
                descent + 1f >= natural.getLineBottom(0) - natural.firstBaseline)
        }
        assertRun(result, "bold tail") { assertEquals(FontWeight.Bold, it.fontWeight) }
        assertRun(result, "italic tail") { assertEquals(FontStyle.Italic, it.fontStyle) }
        listOf("code tail", "val alpha = 1\n}").forEach { code ->
            assertRun(result, code) {
                assertEquals(FontFamily.Monospace, it.fontFamily)
                assertEquals(14.4f, it.fontSize.value, 0.0001f)
                assertEquals(viewModel.uiState.value.activeTheme.codeBackground, it.background)
            }
        }
        for (line in 0 until result.lineCount) {
            assertTrue(result.getLineTop(line) >= 0f)
            assertTrue(result.getLineBaseline(line) > result.getLineTop(line))
            assertTrue(result.getLineBottom(line) > result.getLineBaseline(line))
            assertTrue(result.getLineBottom(line) <= result.size.height + 1f)
            if (line > 0) assertTrue(result.getLineTop(line) >= result.getLineBottom(line - 1))
        }
    }

    @Test
    fun typographyChangesPreserveSelectionHistoryAndExactPlainTextSaveReload() {
        val source = "First line  \r\nSecond\tline\r\n\r\nUnicode café Ω  \n\nLast line\t \n"
        showDocument("")
        val editor = composeTestRule.onNodeWithContentDescription("Document editor")
        editor.performClick()
        composeTestRule.runOnIdle {
            viewModel.documentTextFieldState.edit { replace(0, length, source) }
            viewModel.documentTextFieldState.edit { replace(length, length, "temporary") }
            viewModel.undo()
            viewModel.documentTextFieldState.edit { selection = TextRange(3, 8) }
            assertTrue(viewModel.documentTextFieldState.undoState.canUndo)
            assertTrue(viewModel.documentTextFieldState.undoState.canRedo)
        }
        listOf("sans-serif" to FontFamily.SansSerif, "monospace" to FontFamily.Monospace,
            "serif" to FontFamily.Serif).forEachIndexed { index, (family, expectedFamily) ->
            composeTestRule.runOnIdle { viewModel.setEditorFontFamily(family) }
            setScale(listOf(1.11f, 1.4f, 0.8f)[index])
            composeTestRule.waitUntil(15_000) { viewModel.uiState.value.activeTheme.editorFontFamily == family }
            composeTestRule.waitForIdle()
            assertRun(layout(), "First line") { assertEquals(expectedFamily, it.fontFamily) }
            editor.assertIsFocused()
            composeTestRule.runOnIdle {
                assertEquals(source, viewModel.documentTextFieldState.text.toString())
                assertEquals(TextRange(3, 8), viewModel.documentTextFieldState.selection)
                assertTrue(viewModel.documentTextFieldState.undoState.canUndo)
                assertTrue(viewModel.documentTextFieldState.undoState.canRedo)
            }
        }
        composeTestRule.waitUntil(15_000) { runBlocking { storage.readFile(project, path) == source } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reloaded = EditorViewModel(db, storage, fileOperations, context)
        try {
            composeTestRule.runOnIdle { reloaded.loadFile(project.id, path) }
            composeTestRule.waitUntil(15_000) {
                reloaded.uiState.value.activeFilePath == path && reloaded.documentTextFieldState.text.toString() == source
            }
            assertEquals(source, runBlocking { storage.readFile(project, path) })
        } finally {
            runBlocking { reloaded.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        }
    }
}
