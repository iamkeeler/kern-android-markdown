package com.attachdesign.kern.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

@OptIn(ExperimentalTestApi::class)
class EditorCanvasFocusTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var storage: StorageManager
    private lateinit var viewModel: EditorViewModel
    private lateinit var density: Density
    private val project = ProjectEntity(
        id = 1L, name = "Canvas", path = "canvas_${System.nanoTime()}",
        isExternal = false, isSelected = true
    )
    private val path = "document.md"

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        storage = StorageManager(context)
        db.projectDao().insertProject(project)
        db.fileDao().insertFile(FileEntity(
            id = 1L, projectId = project.id, name = path, relativePath = path,
            isDirectory = false, lastModified = 0L, syncState = "SYNCED"
        ))
        db.settingDao().insertSetting(SettingEntity(SettingKeys.EDITOR_FONT_SIZE_SCALE, "1.0"))
        viewModel = EditorViewModel(db, storage, FileOperationsManager(db, storage, context), context)
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.close()
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        db.close()
    }

    private fun showDocument(source: String, wide: Boolean = false) {
        runBlocking { storage.writeFile(project, path, source) }
        composeTestRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                if (wide) DpSize(1100.dp, 1400.dp) else DpSize(400.dp, 1000.dp)
            )) {
                val localDensity = LocalDensity.current
                SideEffect { density = localDensity }
                EditorScreen(project.id, path, viewModel, onBackClick = {})
            }
        }
        composeTestRule.waitUntil(30_000) {
            viewModel.uiState.value.activeFilePath == path &&
                viewModel.documentTextFieldState.text.toString() == source
        }
        composeTestRule.waitForIdle()
        waitForIme(visible = false)
    }

    private fun pixels(dp: Int): Float = with(density) { dp.dp.toPx() }

    private fun editor() = composeTestRule.onNodeWithContentDescription("Document editor")

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        editor().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun textPosition() = composeTestRule.onNodeWithTag("Document text", useUnmergedTree = true)
        .fetchSemanticsNode().positionInRoot

    private fun scrollBy(pixels: Float) {
        composeTestRule.onNodeWithTag("Document scroll", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
        composeTestRule.waitForIdle()
    }

    private fun longDocument() = (1..100).joinToString("\n") {
        "Line $it keeps a visible writing anchor."
    }

    private fun waitForIme(visible: Boolean) {
        composeTestRule.waitUntil(15_000) {
            composeTestRule.runOnIdle {
                val insets = ViewCompat.getRootWindowInsets(composeTestRule.activity.window.decorView)
                insets != null && insets.isVisible(WindowInsetsCompat.Type.ime()) == visible &&
                    (if (visible) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
                     else insets.getInsets(WindowInsetsCompat.Type.ime()).bottom == 0)
            }
        }
        composeTestRule.waitForIdle()
    }

    /** Root coordinates are measured before each gesture; no field semantics click is substituted. */
    private fun tapRoot(point: Offset) {
        val root = composeTestRule.onNodeWithTag("Editor surface").fetchSemanticsNode().boundsInRoot
        composeTestRule.onNodeWithTag("Editor surface").performTouchInput { click(point - root.topLeft) }
        composeTestRule.waitForIdle()
    }

    private fun assertFocusedImeAndType(typed: String) {
        editor().assertIsFocused()
        waitForIme(visible = true)
        val before = composeTestRule.runOnIdle { viewModel.documentTextFieldState.text.toString() }
        editor().performTextInput(typed)
        composeTestRule.waitUntil(15_000) {
            viewModel.documentTextFieldState.text.toString().let { after ->
                after.length == before.length + typed.length && typed in after
            }
        }
    }

    @Test
    fun emptyCanvasCenterTapOpensRealImeAndAcceptsTyping() {
        showDocument("")
        val root = composeTestRule.onNodeWithTag("Editor surface").fetchSemanticsNode().boundsInRoot
        val header = composeTestRule.onNodeWithTag("Editor header").fetchSemanticsNode().boundsInRoot
        val toolbar = composeTestRule.onNodeWithContentDescription("Minimize formatting toolbar")
            .fetchSemanticsNode().boundsInRoot
        val point = Offset(root.center.x, (header.bottom + toolbar.top) / 2f)
        assertTrue(point.y > editor().fetchSemanticsNode().boundsInRoot.top + pixels(48))
        tapRoot(point)
        assertFocusedImeAndType("Canvas")

        // A dismissed IME must reopen when the focused writer's whitespace is tapped again.
        composeTestRule.runOnIdle {
            val window = composeTestRule.activity.window
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        waitForIme(visible = false)
        val field = editor().fetchSemanticsNode().boundsInRoot
        tapRoot(Offset(root.center.x, field.top + layout().size.height + pixels(64)))
        assertFocusedImeAndType(" again")
    }

    @Test
    fun shortDocumentTapBelowTextOpensRealImeAndAcceptsTyping() {
        showDocument("One short line")
        val field = editor().fetchSemanticsNode().boundsInRoot
        tapRoot(Offset(field.center.x, field.top + layout().size.height + pixels(64)))
        assertFocusedImeAndType(" below")
    }

    @Test
    fun shortDocumentSideGutterTapOpensRealImeAndAcceptsTyping() {
        showDocument("One short line")
        val root = composeTestRule.onNodeWithTag("Editor surface").fetchSemanticsNode().boundsInRoot
        val field = editor().fetchSemanticsNode().boundsInRoot
        tapRoot(Offset(root.left + pixels(8), field.top + pixels(24)))
        assertFocusedImeAndType(" gutter")
    }

    @Test
    fun shortDocumentTopPaddingTapOpensRealImeAndAcceptsTyping() {
        showDocument("One short line")
        val field = editor().fetchSemanticsNode().boundsInRoot
        val header = composeTestRule.onNodeWithTag("Editor header").fetchSemanticsNode().boundsInRoot
        // Native field decoration owns the 16dp inset below the header/divider.
        val point = Offset(field.center.x, header.bottom + pixels(8))
        tapRoot(point)
        assertFocusedImeAndType(" top")
    }

    @Test
    fun wideCanvasLimitsMeasuredTextLayoutTo680Dp() {
        showDocument((1..100).joinToString(" ") { "word$it" }, wide = true)
        val result = layout()
        val textWidthDp = result.size.width / density.density
        assertTrue("Measured text width was ${textWidthDp}dp", textWidthDp <= 680f + 1f / density.density)
        assertTrue("Wide reading canvas unexpectedly collapsed", textWidthDp >= 600f)
        val textCenter = textPosition().x + result.size.width / 2f
        assertEquals(editor().fetchSemanticsNode().boundsInRoot.center.x, textCenter, pixels(1))
        composeTestRule.runOnIdle {
            assertEquals((1..100).joinToString(" ") { "word$it" }, viewModel.documentTextFieldState.text.toString())
        }
    }

    @Test
    fun focusingAScrolledVisibleLinePreservesItsViewportAnchor() {
        showDocument(longDocument())
        scrollBy(pixels(600))
        val result = layout()
        val viewportTop = composeTestRule.onNodeWithTag("Editor header").fetchSemanticsNode().boundsInRoot.bottom
        val line = result.getLineForVerticalPosition(viewportTop + pixels(96) - textPosition().y)
        val before = textPosition().y + result.getLineTop(line)
        val headerBefore = composeTestRule.onNodeWithTag("Editor header").fetchSemanticsNode().boundsInRoot
        tapRoot(Offset(textPosition().x + pixels(24), before + pixels(8)))
        editor().assertIsFocused()
        waitForIme(visible = true)
        val after = textPosition().y + layout().getLineTop(line)
        assertEquals("Focusing a line already clear of the IME moved the document", before, after, pixels(1))
        val headerAfter = composeTestRule.onNodeWithTag("Editor header").fetchSemanticsNode().boundsInRoot
        assertEquals(headerBefore.top, headerAfter.top, pixels(1))

        // With the IME settled, another native tap must also leave the same anchor in place.
        tapRoot(Offset(textPosition().x + pixels(48), after + pixels(8)))
        assertEquals(after, textPosition().y + layout().getLineTop(line), pixels(1))
    }

    @Test
    fun finalLineScrollsAboveExpandedAndMinimizedToolbarWithRealIme() {
        showDocument(longDocument())
        scrollBy(100_000f)
        fun lastLineBottom(): Float {
            val result = layout()
            return textPosition().y + result.getLineBottom(result.lineCount - 1)
        }
        fun assertClear(description: String) {
            scrollBy(100_000f)
            val toolbar = composeTestRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            assertTrue("Last line ${lastLineBottom()} was obscured by $description at ${toolbar.top}",
                lastLineBottom() < toolbar.top)
        }

        assertClear("Minimize formatting toolbar")
        val result = layout()
        tapRoot(Offset(textPosition().x + pixels(24),
            textPosition().y + result.getLineTop(result.lineCount - 1) + pixels(8)))
        waitForIme(visible = true)
        assertClear("Minimize formatting toolbar")
        composeTestRule.onNodeWithContentDescription("Minimize formatting toolbar").performClick()
        assertClear("Expand formatting toolbar")
        waitForIme(visible = true)
        composeTestRule.onNodeWithContentDescription("Expand formatting toolbar").performClick()
        assertClear("Minimize formatting toolbar")
        composeTestRule.onNodeWithContentDescription("More formatting actions").performClick()
        waitForIme(visible = true)
    }

    @Test
    fun legacyParagraphEditingAndBackgroundFocusBehaviorRemainAvailable() {
        showDocument("Legacy paragraph")
        composeTestRule.runOnIdle { viewModel.setDocumentEditorEnabledForTest(false) }
        val paragraph = composeTestRule.onNodeWithContentDescription("Block 1 of 1: paragraph")
        paragraph.performClick()
        paragraph.performSemanticsAction(SemanticsActions.SetSelection) {
            it("Legacy paragraph".length, "Legacy paragraph".length, false)
        }
        paragraph.assertIsFocused().performTextInput(" extra")
        paragraph.performSemanticsAction(SemanticsActions.SetSelection) { it(0, 6, false) }
        composeTestRule.onNodeWithContentDescription("Format selection bold").performClick()
        composeTestRule.runOnIdle {
            assertTrue(viewModel.uiState.value.paragraphs.items.single().block.rawText.contains("**Legacy**"))
        }
        val root = composeTestRule.onNodeWithTag("Editor surface").fetchSemanticsNode().boundsInRoot
        tapRoot(Offset(root.center.x, root.center.y))
        composeTestRule.runOnIdle { assertEquals(-1, viewModel.uiState.value.focusedParagraphIndex) }
    }
}
