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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.swipeDown
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
import org.junit.rules.TestName

@OptIn(ExperimentalTestApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
class EditorScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val testName = TestName()

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
    if (testName.methodName.startsWith("testCompanion") || testName.methodName.startsWith("testInjected") || testName.methodName.startsWith("testReference")) {
      captureScreenshot(testName.methodName)
    }
    // Dispose direct Room observers in the UI before the fixture's database closes.
    composeTestRule.activityRule.scenario.close()
    if (::viewModel.isInitialized) {
      runBlocking {
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
      }
    }
    if (::db.isInitialized) db.close()
  }

  private fun captureScreenshot(name: String) {
    runCatching {
      val command = "screencap -p /sdcard/Download/kern-$name.png"
      android.os.ParcelFileDescriptor.AutoCloseInputStream(
          InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
      ).use { it.readBytes() }
    }
  }

  private fun waitForDocument(text: String) {
    composeTestRule.waitUntil(timeoutMillis = 30_000) {
      viewModel.uiState.value.paragraphs.items.any { text in it.block.rawText }
    }
    composeTestRule.waitForIdle()
  }

  private fun wideEditor(feedback: FoldFeedback? = null) {
    composeTestRule.setContent {
      // ForcedSize rescales the phone density but leaves the real keyboard's pixel inset
      // intact. Reserve enough virtual height to test editing with that real IME open.
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1400.dp))) {
        val editorState by viewModel.uiState.collectAsStateWithLifecycle()
        com.attachdesign.kern.theme.ModernAndroidMarkdownEditorTheme(activeTheme = editorState.activeTheme) {
          CompositionLocalProvider(LocalFoldFeedback provides feedback) {
            EditorScreen(project.id, file.relativePath, viewModel, onBackClick = {})
          }
        }
      }
    }
    waitForDocument("Introduction")
  }

  @Test fun testInlineImageResolutionFollowsDocumentSwitch() {
    val raw = "See ![diagram](diagram.png) here"
    val block = com.attachdesign.kern.parser.MarkdownParser.parseParagraph(raw)
    val projection = com.attachdesign.kern.parser.MarkdownRenderer.render(block)
    val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
    var document by mutableStateOf("reference")
    composeTestRule.setContent {
      val current = document
      RenderedMarkdownText(raw, androidx.compose.ui.text.AnnotatedString(projection.text),
          androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
          resolveImageModel = { calls.add(current); null }, imageResolutionKey = current)
    }
    composeTestRule.waitUntil(10_000) { calls.contains("reference") }
    composeTestRule.runOnIdle { document = "preview" }
    composeTestRule.waitUntil(10_000) { calls.contains("preview") }
    composeTestRule.runOnIdle { document = "reference" }
    composeTestRule.waitUntil(10_000) { synchronized(calls) { calls.count { it == "reference" } >= 2 } }
  }

  @Test fun testCompanionTogglePreservesDraftSelectionUndoAndFocus() {
    wideEditor()
    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    editor.performClick().performTextInput(" An unsaved addition.")
    val imeWasVisible = composeTestRule.runOnIdle {
      androidx.core.view.ViewCompat.getRootWindowInsets(composeTestRule.activity.window.decorView)
          ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
    }
    val text = viewModel.documentTextFieldState.text.toString()
    composeTestRule.runOnIdle { viewModel.documentTextFieldState.edit { selection = TextRange(3, 7) } }
    val canUndo = viewModel.documentTextFieldState.undoState.canUndo
    composeTestRule.onNodeWithContentDescription("Open split view").performClick()
    composeTestRule.onNodeWithContentDescription("Second editor pane").assertIsDisplayed()
    composeTestRule.onNode(hasContentDescription("Close split view") and
        hasAnyAncestor(hasContentDescription("Second editor pane"))).performClick()
    editor.assertIsFocused()
    composeTestRule.runOnIdle {
      assertEquals(text, viewModel.documentTextFieldState.text.toString())
      assertEquals(TextRange(3, 7), viewModel.documentTextFieldState.selection)
      assertEquals(canUndo, viewModel.documentTextFieldState.undoState.canUndo)
      if (imeWasVisible == true) assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(composeTestRule.activity.window.decorView)
          ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
      viewModel.undo()
      assertTrue(viewModel.documentTextFieldState.text.toString() != text)
    }
  }

  @Test fun testSecondEditorSavesIndependentlyAndRetainsDraft() {
    runBlocking { storageManager.writeFile(project, "outline.md", "# Outline for the draft\n\nKeep these notes beside the writing.") }
    wideEditor()
    val original = viewModel.documentTextFieldState.text.toString()
    composeTestRule.onNodeWithContentDescription("Open split view").performClick()
    composeTestRule.onNodeWithText("Choose file").performClick()
    composeTestRule.onNode(hasText(file.name) and !isEnabled()).assertIsNotEnabled()
    composeTestRule.onNodeWithText("outline.md").performClick()
    composeTestRule.waitUntil(15_000) {
      composeTestRule.onAllNodes(hasContentDescription("Document editor") and
          hasAnyAncestor(hasContentDescription("Second editor pane"))).fetchSemanticsNodes().isNotEmpty()
    }
    val second = composeTestRule.onNode(hasContentDescription("Document editor") and
        hasAnyAncestor(hasContentDescription("Second editor pane")))
    second.performClick().performTextInput(" New notes.")
    captureScreenshot("second-editor")
    assertEquals(original, viewModel.documentTextFieldState.text.toString())
    assertEquals(file.relativePath, viewModel.uiState.value.activeFilePath)
    composeTestRule.waitUntil(15_000) {
      runBlocking { storageManager.readFile(project, "outline.md") }.contains("New notes.")
    }
    second.performTextInput(" Closing edit.")
    composeTestRule.onNode(hasContentDescription("Back") and
        hasAnyAncestor(hasContentDescription("Second editor pane"))).performClick()
    composeTestRule.waitUntil(15_000) {
      runBlocking { storageManager.readFile(project, "outline.md") }.contains("Closing edit.")
    }
    composeTestRule.onNodeWithContentDescription("Open split view").performClick()
    composeTestRule.onNode(hasContentDescription("Document editor") and
        hasAnyAncestor(hasContentDescription("Second editor pane"))).assertIsDisplayed()
  }

  @Test fun testCompanionSuspendsOnNarrowWidthAndRestoresPreviousSidebar() {
    var wide by mutableStateOf(true)
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(if (wide) 900.dp else 400.dp, 700.dp))) {
        EditorScreen(project.id, file.relativePath, viewModel, onBackClick = {})
      }
    }
    waitForDocument("Introduction")
    composeTestRule.runOnIdle { viewModel.toggleSidebar(SidebarMode.METRICS) }
    composeTestRule.onNodeWithContentDescription("Open split view").performClick()
    composeTestRule.onNodeWithContentDescription("Second editor pane").assertIsDisplayed()
    composeTestRule.runOnIdle { wide = false }
    composeTestRule.onNodeWithContentDescription("Second editor pane").assertDoesNotExist()
    assertTrue(viewModel.companion.state.value.isOpen)
    composeTestRule.runOnIdle { wide = true }
    composeTestRule.onNodeWithContentDescription("Second editor pane").assertIsDisplayed()
    composeTestRule.onNode(hasContentDescription("Close split view") and
        hasAnyAncestor(hasContentDescription("Second editor pane"))).performClick()
    composeTestRule.runOnIdle { assertEquals(SidebarMode.METRICS, viewModel.uiState.value.sidebarMode) }
  }

  @Test fun testInjectedHingeGestureTogglesCompanionExactlyOnceEachWay() {
    db.settingDao().insertSetting(SettingEntity("fold_to_split", "true"))
    val angles = MutableStateFlow(180f)
    val geometry = MutableStateFlow<FoldGeometry?>(null)
    val gestureClock = java.util.concurrent.atomic.AtomicLong(0)
    val feedbackCalls = mutableListOf<Pair<Boolean, Boolean>>()
    val feedback = object : FoldFeedback {
      override fun triggerFoldFeedback(hapticsEnabled: Boolean, soundEnabled: Boolean) {
        feedbackCalls += hapticsEnabled to soundEnabled
      }
    }
    val environment = object : FoldEnvironment {
      override val sensorAvailable = true
      override val angles = angles
      override val geometry = geometry
      override fun monotonicTimeMillis() = gestureClock.get()
    }
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 700.dp))) {
        val density = LocalDensity.current
        LaunchedEffect(density) {
          geometry.value = FoldGeometry(with(density) { 440.dp.roundToPx() }, 0,
              with(density) { 460.dp.roundToPx() }, with(density) { 700.dp.roundToPx() }, true, true)
        }
        CompositionLocalProvider(LocalFoldEnvironment provides environment, LocalFoldFeedback provides feedback) {
          EditorScreen(project.id, file.relativePath, viewModel, onBackClick = {})
        }
      }
    }
    waitForDocument("Introduction")
    composeTestRule.waitUntil(10_000) { angles.subscriptionCount.value > 0 }
    fun holdFor(duration: Long) {
      repeat((duration / 50).toInt()) {
        gestureClock.addAndGet(50)
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.waitForIdle()
      }
    }
    holdFor(450)
    val hinge = geometry.value!!
    angles.value = 145f
    geometry.value = hinge.copy(isSeparating = false, left = hinge.left + 2, right = hinge.right + 2)
    holdFor(250)
    assertTrue("A bend alone must not constrain the writer to one side",
        composeTestRule.onNodeWithContentDescription("Document editor").fetchSemanticsNode().boundsInRoot.right > hinge.left)
    angles.value = 180f
    geometry.value = hinge
    holdFor(200)
    composeTestRule.waitUntil(10_000) { viewModel.companion.state.value.isOpen }
    composeTestRule.onNodeWithContentDescription("Second editor pane").assertIsDisplayed()
    val writerBounds = composeTestRule.onNodeWithContentDescription("Document editor").fetchSemanticsNode().boundsInRoot
    val readerBounds = composeTestRule.onNodeWithContentDescription("Second editor pane").fetchSemanticsNode().boundsInRoot
    assertTrue("Writer must stay before the hinge", writerBounds.right <= geometry.value!!.left)
    assertTrue("Reader must stay after the hinge", readerBounds.left >= geometry.value!!.right)
    holdFor(1300)
    angles.value = 145f
    holdFor(250)
    angles.value = 180f
    holdFor(200)
    composeTestRule.waitUntil(10_000) { !viewModel.companion.state.value.isOpen }
    composeTestRule.onNodeWithContentDescription("Open split view").assertIsDisplayed()
    holdFor(1200)
    angles.value = 145f
    holdFor(200)
    composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
    composeTestRule.waitUntil(5_000) { angles.subscriptionCount.value == 0 }
    angles.value = 180f
    composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    holdFor(600)
    assertTrue("Resuming must not complete an old gesture", !viewModel.companion.state.value.isOpen)
    composeTestRule.runOnIdle { assertEquals(listOf(true to false, true to false), feedbackCalls) }
  }

  @Test fun testFoldFeedbackPreferencesPersistAndPreviewDoesNotChangeLayout() {
    val calls = mutableListOf<Pair<Boolean, Boolean>>()
    val feedback = object : FoldFeedback {
      override fun triggerFoldFeedback(hapticsEnabled: Boolean, soundEnabled: Boolean) {
        calls += hapticsEnabled to soundEnabled
      }
    }
    wideEditor(feedback)
    val draft = viewModel.documentTextFieldState.text.toString()
    composeTestRule.onNodeWithContentDescription("More Options").performClick()
    composeTestRule.onNodeWithText("Fold shortcut").performClick()
    composeTestRule.onNodeWithContentDescription("Fold haptics").assertIsOn()
    composeTestRule.onNodeWithContentDescription("Paper crease sound").assertIsOff()
    composeTestRule.onNodeWithText("Try feedback").performClick()
    composeTestRule.runOnIdle { assertEquals(listOf(true to false), calls) }
    composeTestRule.onNodeWithContentDescription("Fold haptics").performClick()
    composeTestRule.onNodeWithContentDescription("Paper crease sound").performClick()
    composeTestRule.waitUntil(10_000) {
      viewModel.foldHaptics.value?.value == "false" && viewModel.foldSound.value?.value == "true"
    }
    composeTestRule.onNodeWithText("Try feedback").performClick()
    composeTestRule.runOnIdle {
      assertEquals(listOf(true to false, false to true), calls)
      assertEquals(draft, viewModel.documentTextFieldState.text.toString())
      assertTrue(!viewModel.companion.state.value.isOpen)
    }
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.onNodeWithContentDescription("More Options").performClick()
    composeTestRule.onNodeWithText("Fold shortcut").performClick()
    composeTestRule.onNodeWithContentDescription("Fold haptics").assertIsOff()
    composeTestRule.onNodeWithContentDescription("Paper crease sound").assertIsOn()
    assertEquals("false", db.settingDao().getSetting("fold_haptics")?.value)
    assertEquals("true", db.settingDao().getSetting("fold_sound")?.value)
    captureScreenshot("fold-feedback-dialog")
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.onNodeWithContentDescription("Open split view").performClick()
    composeTestRule.runOnIdle { assertEquals(2, calls.size) }
  }

  @Test fun testFoldShortcutUnavailableKeepsManualSplitControl() {
    wideEditor()
    composeTestRule.onNodeWithContentDescription("Open split view").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("More Options").performClick()
    composeTestRule.onNodeWithText("Fold shortcut").performClick()
    composeTestRule.onNodeWithContentDescription("Fold shortcut").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.onNodeWithContentDescription("Open split view").performClick()
    composeTestRule.onNodeWithContentDescription("Second editor pane").assertIsDisplayed()
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
    composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
    composeTestRule.onNodeWithText("Redo").assertIsDisplayed()
    composeTestRule.onNodeWithText("Indent").assertIsDisplayed()
    // Complete the menu interaction before tapping a control behind its popup.
    composeTestRule.onNodeWithText("Indent").performClick()

    // Toggle minimize/expand toolbar
    composeTestRule.onNodeWithContentDescription("Minimize formatting toolbar").assertIsDisplayed().performClick()
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

  @Test
  fun testElasticOverscrollBouncePreservesEditorState() {
    runBlocking {
      storageManager.writeFile(project, "test_file.md", "# Top Header\n\nDocument body text.")
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
    waitForDocument("Top Header")

    val editor = composeTestRule.onNodeWithContentDescription("Document editor")
    editor.assertIsDisplayed()

    // Perform elastic downward drag gesture at top of document
    editor.performTouchInput {
      swipeDown(startY = centerY, endY = centerY + 300f)
    }
    composeTestRule.waitForIdle()

    // Verify document content and editor remain stable and displayed after bounce
    editor.assertIsDisplayed()
    editor.assertTextContains("# Top Header", substring = true)
    editor.assertTextContains("Document body text.", substring = true)
  }
}
