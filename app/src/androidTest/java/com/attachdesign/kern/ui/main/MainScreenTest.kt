package com.attachdesign.kern.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.storage.StorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.attachdesign.kern.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var db: AppDatabase
  private lateinit var storageManager: StorageManager

  private val project = ProjectEntity(
      id = 1L,
      name = "Main Project",
      path = "main_project",
      isExternal = false,
      isSelected = true
  )

  @Before
  fun setup() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    storageManager = StorageManager(context)

    // Seed default settings required for theme loading in MainScreen
    db.settingDao().insertSetting(SettingEntity("selected_theme_id", "0"))
    db.settingDao().insertSetting(SettingEntity("editor_font_family", "serif"))
    db.settingDao().insertSetting(SettingEntity("launch_new_file", "true"))
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun testEmptyStateDisplaysWorkspacesHint() {
    composeTestRule.setContent {
      MainScreen(
          onItemClick = {},
          db = db,
          storageManager = storageManager,
          modifier = Modifier.fillMaxSize()
      )
    }

    // Verify empty state warning is displayed
    composeTestRule.onNodeWithText("No workspaces").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tap [+ workspace] above to add a local folder.").assertIsDisplayed()
  }

  @Test
  fun testProjectNavigationAndDrillDown() {
    // Insert a project and a file
    db.projectDao().insertProject(project)
    val file = FileEntity(
        id = 1L,
        projectId = 1L,
        name = "readme.md",
        relativePath = "readme.md",
        isDirectory = false,
        lastModified = System.currentTimeMillis(),
        syncState = "SYNCED"
    )
    db.fileDao().insertFile(file)

    runBlocking {
      storageManager.writeFile(project, "readme.md", "Read Me contents.")
    }

    composeTestRule.setContent {
      MainScreen(
          onItemClick = {},
          db = db,
          storageManager = storageManager,
          modifier = Modifier.fillMaxSize()
      )
    }

    // Click on the project item to navigate into the workspace directory
    composeTestRule.onNodeWithText("Main Project").performClick()

    // Assert that the drill down view list is displaying files/subfolders
    composeTestRule.onNodeWithText("readme.md").assertIsDisplayed()
  }

  @Test
  fun testAddFileAndFolderFloatingButtons() {
    // Insert project to activate editor sidebar operations
    db.projectDao().insertProject(project)

    composeTestRule.setContent {
      MainScreen(
          onItemClick = {},
          db = db,
          storageManager = storageManager,
          modifier = Modifier.fillMaxSize()
      )
    }

    // Verify that directory floating control options are displayed
    composeTestRule.onNodeWithText("+ File").assertIsDisplayed()
    composeTestRule.onNodeWithText("+ Folder").assertIsDisplayed()
  }
}
