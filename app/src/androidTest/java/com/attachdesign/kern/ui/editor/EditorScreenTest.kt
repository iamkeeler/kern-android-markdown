package com.attachdesign.kern.ui.editor

import android.content.ClipboardManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    composeTestRule.onNodeWithText("Introduction").assertIsDisplayed()
    composeTestRule.onNodeWithText("This is a custom paragraph block with bold text.").assertIsDisplayed()
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

    // Focus on second paragraph block
    composeTestRule.onNodeWithText("This is a custom paragraph block with bold text.").performClick()

    // Verify format buttons appear in formatting toolbar
    composeTestRule.onNodeWithContentDescription("Format selection bold").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Format selection italic").assertIsDisplayed()

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
  fun testDirectChecklistToggling() {
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

    // Verify task lists are rendered with correct checkmarks
    composeTestRule.onNodeWithText("☐ Buy groceries").assertIsDisplayed()
    composeTestRule.onNodeWithText("☑ Read book").assertIsDisplayed()

    // Click on the toggle checkmark area
    composeTestRule.onAllNodesWithContentDescription("Toggle task list checkmark")[0]
        .performSemanticsAction(SemanticsActions.OnClick)

    // Verify that the first item visually transitions to checked state
    composeTestRule.waitForIdle()
    val checklistTexts = viewModel.uiState.value.paragraphs.items.map { it.block.rawText }
    org.junit.Assert.assertTrue(
        "The first checklist item was not toggled: $checklistTexts",
        checklistTexts.firstOrNull()?.startsWith("- [x]") == true
    )
    composeTestRule.onNodeWithText("☑ Buy groceries").assertIsDisplayed()
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

    composeTestRule.onNodeWithContentDescription("Diagram").assertIsDisplayed()
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

    val firstBounds = composeTestRule.onNodeWithText("First paragraph").fetchSemanticsNode().boundsInRoot
    val secondBounds = composeTestRule.onNodeWithText("Second paragraph").fetchSemanticsNode().boundsInRoot
    composeTestRule.onRoot().performTouchInput {
      down(Offset(firstBounds.left + 1f, firstBounds.center.y))
      advanceEventTime(800)
      moveTo(Offset(secondBounds.right - 1f, secondBounds.center.y))
      up()
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

    val targetText = "Paragraph 20 has enough text to remain easy to target in the editor."
    val target = composeTestRule.onNodeWithText(targetText)
    target.performScrollTo()
    composeTestRule.waitForIdle()
    val topBeforeFocus = target.fetchSemanticsNode().boundsInRoot.top
    val rootHeight = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.height

    target.performClick()
    composeTestRule.waitForIdle()

    val focusedTarget = composeTestRule.onNodeWithContentDescription("Block 20 of 30: paragraph")
    focusedTarget.assertIsFocused()
    val topAfterFocus = focusedTarget.fetchSemanticsNode().boundsInRoot.top
    org.junit.Assert.assertTrue(
        "Focusing a paragraph aligned it near the top: $topBeforeFocus to $topAfterFocus",
        topAfterFocus > rootHeight * 0.35f
    )
  }
}
