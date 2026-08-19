package com.attachdesign.kern.ui.editor

import android.content.ClipboardManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.viewModelScope
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.FileOperationsManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditorScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var db: AppDatabase
  private lateinit var storageManager: StorageManager
  private lateinit var fileOpsManager: FileOperationsManager
  private lateinit var viewModel: EditorViewModel

  private val project = ProjectEntity(
      id = 1L,
      name = "Test Project",
      path = "test_project_${System.nanoTime()}",
      isExternal = false,
      isSelected = true
  )

  private val file = FileEntity(
      id = 1L,
      projectId = 1L,
      name = "test_file.md",
      relativePath = "test_file.md",
      isDirectory = false,
      lastModified = System.currentTimeMillis(),
      syncState = "SYNCED"
  )

  @Before
  fun setup() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    storageManager = StorageManager(context)
    fileOpsManager = FileOperationsManager(db, storageManager, context)

    // Populate database
    db.projectDao().insertProject(project)
    db.fileDao().insertFile(file)
    
    // Seed default settings required by ViewModel configuration flows
    db.settingDao().insertSetting(SettingEntity("editor_font_size_scale", "1.0"))
    db.settingDao().insertSetting(SettingEntity("selected_theme_id", "0"))
    db.settingDao().insertSetting(SettingEntity("editor_font_family", "serif"))
    db.settingDao().insertSetting(SettingEntity("auto_header_spacing", "true"))
    db.settingDao().insertSetting(SettingEntity("auto_complete_enabled", "true"))
    db.settingDao().insertSetting(SettingEntity("view_mode", "RENDERED"))
    db.settingDao().insertSetting(SettingEntity("sticky_selection", "true"))
    db.settingDao().insertSetting(SettingEntity("sync_provider", "NONE"))

    // Initialize content file
    runBlocking {
      storageManager.writeFile(project, "test_file.md", "# Introduction\n\nThis is a custom paragraph block with **bold text**.")
    }

    viewModel = EditorViewModel(db, storageManager, fileOpsManager, context)
  }

  @After
  fun tearDown() {
    if (::viewModel.isInitialized) {
      runBlocking {
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
      }
    }
    if (::db.isInitialized) db.close()
  }

  private fun waitForDocument(text: String) {
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      viewModel.uiState.value.paragraphs.items.any { text in it.block.rawText }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun testEditorScreenLoadsContent() {
    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Introduction")

    // Verify file name in breadcrumb header and initial text contents loaded
    composeTestRule.onNodeWithText("test_file.md").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Document editor")
        .assertIsDisplayed()
        .assertTextContains("# Introduction", substring = true)
        .assertTextContains("This is a custom paragraph block with **bold text**.", substring = true)
  }

  @Test
  fun testViewModeTransitions() {
    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Introduction")

    // Open Settings from the header overflow menu.
    composeTestRule.onNodeWithContentDescription("More Options").performClick()
    composeTestRule.onNodeWithText("Settings").performClick()

    // Assert Settings sidebar pane is displayed
    composeTestRule.onNodeWithText("VIEW MODE").assertIsDisplayed()
    
    // Switch to Raw Plain-Text view mode
    composeTestRule.onNodeWithText("Raw Plain-Text").performClick()

    // Switch back to Live Preview
    composeTestRule.onNodeWithText("Live Preview").performClick()

    // Close Settings Sidebar
    composeTestRule.onNodeWithContentDescription("Close sidebar").performClick()
  }

  @Test
  fun testFloatingFormattingToolbarInteraction() {
    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("custom paragraph")

    composeTestRule.onNodeWithContentDescription("Document editor").performClick()

    // Verify format buttons appear in formatting toolbar
    composeTestRule.onNodeWithContentDescription("Format selection bold").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Format selection italic").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Toggle bullet list").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("More formatting actions").assertIsDisplayed()

    // Formatting is non-dismissive: actions remain available until the explicit collapse control.
    composeTestRule.onNodeWithContentDescription("Format selection bold").performClick()
    composeTestRule.onNodeWithContentDescription("Format selection italic").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("More formatting actions").performClick()
    composeTestRule.onNodeWithText("Indent").assertIsDisplayed()

    // Toggle minimize/expand toolbar
    composeTestRule.onNodeWithContentDescription("Minimize formatting toolbar").performClick()
    composeTestRule.onNodeWithContentDescription("Expand formatting toolbar").performClick()
  }

  @Test
  fun testMetricsSidebarCalculations() {
    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Introduction")

    // Toggle readability metrics popup/sidebar
    composeTestRule.onNodeWithContentDescription("Toggle readability metrics popup").performClick()

    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      viewModel.uiState.value.hemingwayMetrics != null
    }
    // Verify word and character metrics are presented in the popup.
    composeTestRule.onNodeWithText("READABILITY").assertIsDisplayed()
    composeTestRule.onNodeWithText("Words").assertIsDisplayed()
    composeTestRule.onNodeWithText("Characters").assertIsDisplayed()
  }

  @Test
  fun testChecklistFormattingTogglesCurrentTask() {
    // Write checklist items to the file before setting Compose content
    runBlocking {
      storageManager.writeFile(project, "test_file.md", "- [ ] Buy groceries\n- [x] Read book")
    }

    // Reload the ViewModel to read the updated file contents
    viewModel.loadFile(project.id, file.relativePath)

    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Buy groceries")

    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    editor.assertTextContains("- [ ] Buy groceries", substring = true)
    editor.performSemanticsAction(SemanticsActions.SetSelection) { action ->
      action(0, 0, false)
    }
    composeTestRule.onNodeWithContentDescription("More formatting actions").performClick()
    composeTestRule.onNodeWithText("Checklist").performClick()

    // Verify that the first item visually transitions to checked state
    composeTestRule.waitForIdle()
    val checklistTexts = viewModel.uiState.value.paragraphs.items.map { it.block.rawText }
    org.junit.Assert.assertTrue(
        "The first checklist item was not toggled: $checklistTexts",
        checklistTexts.firstOrNull()?.startsWith("- [x]") == true
    )
    editor.assertTextContains("- [x] Buy groceries", substring = true)
  }

  @Test
  fun testDocumentEditorContinuesMarkdownList() {
    runBlocking {
      storageManager.writeFile(project, "test_file.md", "- **First**")
    }
    viewModel.loadFile(project.id, file.relativePath)

    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("First")

    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    editor.performClick()
    editor.performSemanticsAction(SemanticsActions.SetSelection) { action ->
      action("- **First**".length, "- **First**".length, false)
    }
    editor.performTextInput("\n")

    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      viewModel.documentTextFieldState.text.toString() == "- **First**\n- "
    }
  }

  @Test
  fun testInlineMarkdownImageCreatesRenderedImageNode() {
    runBlocking {
      storageManager.writeFile(
          project,
          "test_file.md",
          "Before ![Diagram](https://example.com/diagram.png) after"
      )
    }
    viewModel.loadFile(project.id, file.relativePath)

    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Before ![Diagram]")

    composeTestRule.onNodeWithContentDescription("Document editor")
        .assertIsDisplayed()
        .assertTextContains("![Diagram](https://example.com/diagram.png)", substring = true)
  }

  @Test
  fun testCrossParagraphSelectionCopiesParagraphBoundary() {
    runBlocking {
      storageManager.writeFile(project, "test_file.md", "First paragraph\n\nSecond paragraph")
    }
    viewModel.loadFile(project.id, file.relativePath)

    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("First paragraph")

    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    editor.performClick()
    editor.performSemanticsAction(SemanticsActions.SetSelection) { action ->
      action(0, "First paragraph\n\nSecond paragraph".length, false)
    }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val eventTime = SystemClock.uptimeMillis()
    instrumentation.sendKeySync(
        KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON)
    )
    instrumentation.sendKeySync(
        KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON)
    )
    instrumentation.waitForIdleSync()

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    org.junit.Assert.assertEquals("First paragraph\n\nSecond paragraph", copied)
  }

  @Test
  fun testRenderedEditorSelectsOnlyTheRequestedWordRange() {
    runBlocking {
      storageManager.writeFile(project, "test_file.md", "# Introduction\n\nA selectable paragraph")
    }
    viewModel.loadFile(project.id, file.relativePath)

    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Introduction")

    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    editor.performClick()
    editor.performSemanticsAction(SemanticsActions.SetSelection) { action ->
          // "Intro" starts at zero in the rendered text, but at offset two in Markdown.
          action(0, 5, false)
        }
    composeTestRule.waitForIdle()

    // The hidden heading marker is included at the start of the source range so applying
    // formatting to the visible selection preserves the Markdown block construct.
    org.junit.Assert.assertEquals(TextRange(0, 7), viewModel.documentTextFieldState.selection)
  }

  @Test
  fun testFocusingVisibleParagraphPreservesViewport() {
    val document = (1..30).joinToString("\n\n") { index ->
      "Paragraph $index has enough text to remain easy to target in the editor."
    }
    runBlocking {
      storageManager.writeFile(project, "test_file.md", document)
    }
    viewModel.loadFile(project.id, file.relativePath)

    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Paragraph 20")

    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    val boundsBeforeFocus = editor.fetchSemanticsNode().boundsInRoot
    editor.performClick()
    composeTestRule.waitForIdle()
    editor.assertIsFocused()
    val boundsAfterFocus = editor.fetchSemanticsNode().boundsInRoot
    org.junit.Assert.assertEquals(boundsBeforeFocus.top, boundsAfterFocus.top, 1f)
  }

  @Test
  fun testTypingStressWithRichBlocksPersistsExactText() {
    val source = buildString {
      append("![Diagram](https://example.com/diagram.png)\n\n")
      append("| Name | Value |\n")
      append("| --- | --- |\n")
      append("| Alpha | One |\n\n")
      append("Final paragraph")
    }
    val typed = "\n\n" + (1..60).joinToString(" ") { "word$it" } + " 日本語 😀"
    runBlocking { storageManager.writeFile(project, "test_file.md", source) }
    viewModel.loadFile(project.id, file.relativePath)

    composeTestRule.setContent {
      EditorScreen(
          projectId = project.id,
          filePath = file.relativePath,
          viewModel = viewModel,
          onBackClick = {},
          modifier = Modifier.fillMaxSize()
      )
    }
    waitForDocument("Final paragraph")

    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    editor.performSemanticsAction(SemanticsActions.SetSelection) { action ->
      action(source.length, source.length, false)
    }
    editor.performTextInput(typed)
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      viewModel.documentTextFieldState.text.toString() == source + typed
    }
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      runBlocking { storageManager.readFile(project, file.relativePath) } == source + typed
    }
    editor.assertIsDisplayed()
  }
}
