package com.attachdesign.kern.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.attachdesign.kern.EditorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

/** UI tests for [com.attachdesign.kern.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var db: AppDatabase
  private lateinit var storageManager: StorageManager
  private lateinit var fileOpsManager: FileOperationsManager

  private val project = ProjectEntity(
      id = 1L,
      name = "Main Project",
      path = "main_project_${System.nanoTime()}",
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
    fileOpsManager = FileOperationsManager(db, storageManager, context)

    // Seed default settings required for theme loading in MainScreen
    db.settingDao().insertSetting(SettingEntity("selected_theme_id", "0"))
    db.settingDao().insertSetting(SettingEntity("editor_font_family", "serif"))
    db.settingDao().insertSetting(SettingEntity("launch_new_file", "true"))
  }

  @After
  fun tearDown() {
    val viewModel = composeTestRule.runOnIdle {
      ViewModelProvider(composeTestRule.activity)[MainScreenViewModel::class.java]
    }
    composeTestRule.activityRule.scenario.close()
    runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    db.close()
  }

  private fun waitForText(text: String) {
    composeTestRule.waitUntil(15_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitForIdle()
  }

  private fun waitForStartup() {
    val viewModel = composeTestRule.runOnIdle {
      ViewModelProvider(composeTestRule.activity)[MainScreenViewModel::class.java]
    }
    composeTestRule.waitUntil(30_000) { !viewModel.explorerState.value.isLoading }
    waitForText("files")
  }

  private fun isImeVisible(): Boolean {
    var visible = false
    composeTestRule.runOnUiThread {
      visible = ViewCompat.getRootWindowInsets(composeTestRule.activity.window.decorView)
          ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }
    return visible
  }

  @Test
  fun testBundledReleaseNotesAppearInFilesAndOpenAsMarkdown() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val version = com.attachdesign.kern.data.storage.BundledReleaseNotesInstaller.currentVersion(context)
    var opened: EditorKey? = null
    composeTestRule.setContent {
      MainScreen({ opened = it as? EditorKey }, db, storageManager, fileOpsManager, Modifier.fillMaxSize())
    }
    waitForStartup()
    val root = db.projectDao().getAllProjects().single { !it.isExternal && it.path == "root" }
    val path = db.settingDao().getSetting(version.installedKey)!!.value
    val filename = path.substringAfterLast('/')
    composeTestRule.onNodeWithText(path.substringBeforeLast('/')).assertIsDisplayed().performClick()
    waitForText(filename)
    composeTestRule.onNodeWithText(filename).assertIsDisplayed()
    val contents = runBlocking { storageManager.readFile(root, path) }
    assertTrue(contents.startsWith(version.heading + "\n"))
    assertTrue(contents.contains("## What's new"))
    assertEquals(com.attachdesign.kern.data.storage.BundledReleaseNotesInstaller.currentMarkdown(storageManager, version), contents)
    android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/kern-bundled-release-notes.png"
        )
    ).use { it.readBytes() }
    composeTestRule.onNodeWithText(filename).performClick()
    composeTestRule.waitUntil(5_000) { opened != null }
    composeTestRule.runOnIdle {
      assertEquals(root.id, opened?.projectId)
      assertEquals(path, opened?.filePath)
    }
  }

  @Test
  fun testSearchOpensReadyToTypeAndClearKeepsSearchOpen() {
    composeTestRule.setContent {
      MainScreen({}, db, storageManager, fileOpsManager, Modifier.fillMaxSize())
    }
    waitForStartup()

    composeTestRule.onNodeWithContentDescription("Search").performClick()
    val input = composeTestRule.onNode(hasSetTextAction())
    input.assertIsFocused().performTextInput("draft")
    composeTestRule.waitUntil(10_000) { isImeVisible() }
    composeTestRule.onNodeWithContentDescription("Clear search").performClick()
    input.assertIsFocused().assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
    )
    composeTestRule.onNodeWithContentDescription("Close search").assertIsDisplayed().performClick()
    composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()
  }

  @Test
  fun testSearchBackDismissesKeyboardThenSearchAndRestoresQuery() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      MainScreen({}, db, storageManager, fileOpsManager, Modifier.fillMaxSize())
    }
    waitForStartup()
    composeTestRule.onNodeWithContentDescription("Search").performClick()
    composeTestRule.onNode(hasSetTextAction()).performTextInput("retained query")
    composeTestRule.waitUntil(10_000) { isImeVisible() }

    Espresso.pressBack()
    composeTestRule.waitUntil(10_000) { !isImeVisible() }
    composeTestRule.onNode(hasSetTextAction()).assertTextEquals("retained query")
    restoration.emulateSavedInstanceStateRestore()
    composeTestRule.onNode(hasSetTextAction()).assertTextEquals("retained query")
    org.junit.Assert.assertFalse(isImeVisible())

    Espresso.pressBack()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()
    org.junit.Assert.assertFalse(composeTestRule.activity.isFinishing)
  }

  @Test
  fun testEmptyStateDisplaysWorkspacesHint() {
    composeTestRule.setContent {
      MainScreen(
          onItemClick = {},
          db = db,
          storageManager = storageManager,
          fileOpsManager = fileOpsManager,
          modifier = Modifier.fillMaxSize()
      )
    }

    // Startup now seeds a default workspace. Construct a genuinely empty state
    // in this test's in-memory database after startup has completed.
    waitForStartup()
    composeTestRule.runOnIdle {
      db.projectDao().getAllProjects().forEach {
        db.fileDao().deleteFilesForProject(it.id)
        db.projectDao().deleteProjectById(it.id)
      }
    }
    composeTestRule.onNodeWithText("files").performClick()
    waitForText("No workspaces")
    composeTestRule.onNodeWithText("No workspaces").assertIsDisplayed()
    composeTestRule.onNodeWithText("Use Add workspace to open a folder on your device.").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Sort").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Recently added").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Add workspace").performClick()
    composeTestRule.onNodeWithText("Open Local Folder").assertIsDisplayed()
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
          fileOpsManager = fileOpsManager,
          modifier = Modifier.fillMaxSize()
      )
    }

    // Click on 'files' to navigate up/back to workspaces list
    waitForStartup()
    composeTestRule.onNodeWithText("files").performClick()

    // Click on the project item to navigate into the workspace directory
    waitForText("Release Notes")
    waitForText("Main Project")
    composeTestRule.onNodeWithText("Main Project").performClick()

    // Assert that the drill down view list is displaying files/subfolders
    waitForText("readme.md")
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
          fileOpsManager = fileOpsManager,
          modifier = Modifier.fillMaxSize()
      )
    }

    // The current toolbar exposes semantic icon controls rather than text FABs.
    waitForStartup()
    composeTestRule.onNodeWithContentDescription("New Document").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("New File in Main Project").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.onNodeWithContentDescription("New Folder").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("New Folder in Main Project").assertIsDisplayed()
  }

  private fun showCompactLargeFont(onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit = {}) {
    composeTestRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
        Box(Modifier.width(320.dp).fillMaxSize()) {
          MainScreen(onItemClick, db, storageManager, fileOpsManager, Modifier.fillMaxSize())
        }
      }
    }
    waitForStartup()
  }

  private val longUnicodeName = "草稿Δ🙂".repeat(12) + "_final_document.md"

  private fun assertTwoLineEllipsis(text: String) {
    val layouts = mutableListOf<TextLayoutResult>()
    composeTestRule.onNodeWithText(text, useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    assertEquals(2, layout.layoutInput.maxLines)
    assertTrue(layout.lineCount <= 2)
    assertTrue("Long content should ellipsize within its own column", layout.isLineEllipsized(layout.lineCount - 1))
  }

  @Test
  fun deepBreadcrumbAndLongUnicodeRowKeepSortUpAndOverflowReachableAtLargeFonts() {
    val mixedCaseProject = project.copy(name = "MyCase Ω 文档 Workspace")
    val path = listOf("第一层Alpha", "第二层Beta", "第三层Gamma", "第四层Delta").joinToString("/")
    db.projectDao().insertProject(mixedCaseProject)
    runBlocking {
      storageManager.createDirectory(mixedCaseProject, path)
      storageManager.writeFile(mixedCaseProject, "$path/$longUnicodeName", "Unicode document")
    }
    val opened = mutableListOf<androidx.navigation3.runtime.NavKey>()
    showCompactLargeFont { opened += it }
    val vm = composeTestRule.runOnIdle {
      ViewModelProvider(composeTestRule.activity)[MainScreenViewModel::class.java]
    }
    composeTestRule.runOnIdle { vm.navigateToSegment(mixedCaseProject, path) }
    waitForText(longUnicodeName)
    composeTestRule.onNodeWithText(mixedCaseProject.name).assertExists()
    val up = composeTestRule.onNodeWithContentDescription("Up")
    val sort = composeTestRule.onNodeWithContentDescription("Sort")
    up.assertIsDisplayed()
    sort.assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Name (A–Z)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Name (Z–A)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Recently modified").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("explorer-breadcrumbs").performTouchInput { swipeLeft() }
    up.assertIsDisplayed()
    sort.assertIsDisplayed()
    assertTwoLineEllipsis(longUnicodeName)
    val overflow = composeTestRule.onNodeWithContentDescription("More actions for $longUnicodeName ($path/$longUnicodeName)")
    overflow.assertIsDisplayed().performClick()
    assertTrue("Opening overflow must not open the document", opened.isEmpty())
    composeTestRule.onNodeWithText("Rename").performClick()
    composeTestRule.onNodeWithText("Rename Document").assertIsDisplayed()
    assertTrue(opened.isEmpty())
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.onNodeWithText(longUnicodeName).performClick()
    assertEquals(1, opened.size)
    assertTrue(opened.single() is EditorKey)
    up.performClick()
    composeTestRule.waitUntil(5_000) { vm.explorerState.value.currentPath == path.substringBeforeLast('/') }
  }

  @Test
  fun searchResultConstrainsUnicodeNameAndWorkspacePathWithNamedOverflow() {
    val longProject = project.copy(name = "MyCase Ω 文档 Workspace ".repeat(8))
    db.projectDao().insertProject(longProject)
    runBlocking { storageManager.writeFile(longProject, longUnicodeName, "Searchable document") }
    val opened = mutableListOf<androidx.navigation3.runtime.NavKey>()
    showCompactLargeFont { opened += it }
    composeTestRule.onNodeWithContentDescription("Search").performClick()
    composeTestRule.onNode(hasSetTextAction()).performTextInput("final_document")
    Espresso.closeSoftKeyboard()
    waitForText(longUnicodeName)
    assertTwoLineEllipsis(longUnicodeName)
    assertTwoLineEllipsis("${longProject.name}/")
    composeTestRule.onNodeWithContentDescription("More actions for $longUnicodeName in ${longProject.name}/$longUnicodeName")
        .assertIsDisplayed().performClick()
    assertTrue(opened.isEmpty())
    composeTestRule.onNodeWithText("Delete").performClick()
    composeTestRule.onNodeWithText("Delete document?").assertIsDisplayed()
    assertTrue(opened.isEmpty())
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.onNodeWithText(longUnicodeName).performClick()
    assertEquals(1, opened.size)
  }

  @Test
  fun compactLargeFontRootKeepsWorkspaceAndSortActionsReachable() {
    showCompactLargeFont()
    composeTestRule.onNodeWithContentDescription("Sort").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Recently modified").performClick()
    composeTestRule.onNodeWithText("Add workspace").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Open Local Folder").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cancel").performClick()
  }

  @Test
  fun emptyFolderAndSearchCopyNameTheExistingControls() {
    db.projectDao().insertProject(project)
    composeTestRule.setContent {
      MainScreen({}, db, storageManager, fileOpsManager, Modifier.fillMaxSize())
    }
    waitForStartup()
    composeTestRule.onNodeWithText("Use New Document or New Folder to add content.").assertIsDisplayed()
    composeTestRule.onNodeWithText("New Document").performClick()
    composeTestRule.onNodeWithText("New File in Main Project").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.onNodeWithContentDescription("Search").performClick()
    composeTestRule.onNode(hasSetTextAction()).performTextInput("no_matching_filename_92841")
    waitForText("No matches found")
    composeTestRule.onNodeWithText("Try another filename in Search.").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
  }

  @Test
  fun failedCreateKeepsDraftThroughRestorationAndImeRetryCreatesOneActualFile() {
    db.projectDao().insertProject(project)
    runBlocking { storageManager.writeFile(project, "existing.md", "Keep existing exact\r\n") }
    val opened = mutableListOf<androidx.navigation3.runtime.NavKey>()
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent { MainScreen({ opened += it }, db, storageManager, fileOpsManager, Modifier.fillMaxSize()) }
    waitForStartup()
    composeTestRule.onNodeWithContentDescription("New Document").performClick()
    composeTestRule.onNode(hasSetTextAction()).performTextInput("existing")
    composeTestRule.onNodeWithText("Create").performClick()
    waitForText("An item with that name already exists. Choose another name.")
    assertTrue(opened.isEmpty())
    restoration.emulateSavedInstanceStateRestore()
    composeTestRule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("existing")))
    composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Fresh.MD")
    composeTestRule.onNode(hasSetTextAction()).performImeAction()
    composeTestRule.waitUntil(15_000) { opened.size == 1 }
    assertEquals("Fresh.MD", (opened.single() as EditorKey).filePath)
    assertEquals("Keep existing exact\r\n", runBlocking { storageManager.readFile(project, "existing.md") })
    assertTrue(runBlocking { storageManager.fileExists(project, "Fresh.MD") })
    assertTrue(!runBlocking { storageManager.fileExists(project, "Fresh.MD.md") })
  }

}
