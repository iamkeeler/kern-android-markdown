package com.attachdesign.kern.ui.editor

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.FileOperationsManager
import kotlinx.coroutines.runBlocking
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
      path = "test_project",
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
    db.close()
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

    // Toggle sidebar to show Settings options
    composeTestRule.onNodeWithContentDescription("Toggle consolidated settings sidebar").performClick()

    // Assert Settings sidebar pane is displayed
    composeTestRule.onNodeWithText("View Configurations").assertIsDisplayed()
    
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

    // Focus on second paragraph block
    composeTestRule.onNodeWithText("This is a custom paragraph block with bold text.").performClick()

    // Verify format buttons appear in formatting toolbar
    composeTestRule.onNodeWithContentDescription("Format selection bold").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Format selection italic").assertIsDisplayed()

    // Toggle minimize/expand toolbar
    composeTestRule.onNodeWithContentDescription("Minimize toolbar").performClick()
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

    // Toggle readability metrics popup/sidebar
    composeTestRule.onNodeWithContentDescription("Toggle readability metrics popup").performClick()

    // Verify word counts and readability levels are presented on screen
    composeTestRule.onNodeWithText("Readability Analytics").assertIsDisplayed()
    composeTestRule.onNodeWithText("Word Count").assertIsDisplayed()
    composeTestRule.onNodeWithText("Character Count").assertIsDisplayed()
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

    // Verify task lists are rendered with correct checkmarks
    composeTestRule.onNodeWithText("☐ Buy groceries").assertIsDisplayed()
    composeTestRule.onNodeWithText("☑ Read book").assertIsDisplayed()

    // Click on the toggle checkmark area
    composeTestRule.onNodeWithContentDescription("Toggle task list checkmark").performClick()

    // Verify that the first item visually transitions to checked state
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

    val firstBounds = composeTestRule.onNodeWithText("First paragraph").fetchSemanticsNode().boundsInRoot
    val secondBounds = composeTestRule.onNodeWithText("Second paragraph").fetchSemanticsNode().boundsInRoot
    composeTestRule.onRoot().performTouchInput {
      down(Offset(firstBounds.left + 1f, firstBounds.center.y))
      advanceEventTime(800)
      moveTo(Offset(secondBounds.right - 1f, secondBounds.center.y))
      up()
    }
    composeTestRule.onRoot().performKeyInput {
      keyDown(Key.CtrlLeft)
      keyDown(Key.C)
      keyUp(Key.C)
      keyUp(Key.CtrlLeft)
    }

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    org.junit.Assert.assertEquals("First paragraph\n\nSecond paragraph", copied)
  }
}
