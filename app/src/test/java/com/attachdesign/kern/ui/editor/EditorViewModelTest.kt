@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.attachdesign.kern.ui.editor

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.ProjectDao
import com.attachdesign.kern.data.local.SettingDao
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var db: AppDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var fileDao: FileDao
    private lateinit var settingDao: SettingDao
    private lateinit var storageManager: StorageManager
    private lateinit var fileOpsManager: FileOperationsManager
    private lateinit var context: Context

    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        projectDao = mockk()
        fileDao = mockk()
        settingDao = mockk()
        val themeDao = mockk<com.attachdesign.kern.data.local.ThemeDao>(relaxed = true)
        storageManager = mockk(relaxed = true)
        fileOpsManager = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { db.projectDao() } returns projectDao
        every { db.fileDao() } returns fileDao
        every { db.settingDao() } returns settingDao
        every { db.themeDao() } returns themeDao

        val flow = MutableStateFlow(emptyList<com.attachdesign.kern.data.local.ProjectEntity>())
        every { projectDao.getAllProjectsFlow() } returns flow
        every { settingDao.getSettingFlow(any()) } returns MutableStateFlow(null)
        every { settingDao.getSetting(any()) } returns null
        every { themeDao.getAllThemesFlow() } returns MutableStateFlow(emptyList())

        viewModel = EditorViewModel(db, storageManager, fileOpsManager, context, testDispatcher)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        io.mockk.unmockkAll()
    }

    @Test
    fun `reentering same file preserves live text selection and undo history`() = runTest {
        val project = com.attachdesign.kern.data.local.ProjectEntity(id = 3, name = "Drafts", path = "drafts", isExternal = false, isSelected = true)
        every { projectDao.getProjectById(3) } returns project
        every { fileDao.getFilesForProject(3) } returns emptyList()
        every { settingDao.insertSetting(any()) } returns Unit
        // Empty saved source also avoids invoking Android Bundle-backed word telemetry
        // in this plain JVM test; the invariant is that live text survives re-entry.
        coEvery { storageManager.readFile(project, "draft.md") } returns ""
        coEvery { storageManager.listDirectory(project, "") } returns emptyList()
        viewModel.loadFile(3, "draft.md")?.join()
        viewModel.documentTextFieldState.edit {
            replace(0, length, "Live unsaved draft")
            selection = androidx.compose.ui.text.TextRange(2, 6)
        }
        val canUndo = viewModel.documentTextFieldState.undoState.canUndo
        viewModel.loadFile(3, "draft.md")?.join()
        advanceUntilIdle()
        assertEquals("Live unsaved draft", viewModel.documentTextFieldState.text.toString())
        assertEquals(androidx.compose.ui.text.TextRange(2, 6), viewModel.documentTextFieldState.selection)
        assertEquals(canUndo, viewModel.documentTextFieldState.undoState.canUndo)
        io.mockk.coVerify(exactly = 1) { storageManager.readFile(project, "draft.md") }
    }

    @Test
    fun `reentering while the same document loads shares the pending read`() = runTest {
        val project = com.attachdesign.kern.data.local.ProjectEntity(id = 3, name = "Drafts", path = "drafts", isExternal = false, isSelected = true)
        every { projectDao.getProjectById(3) } returns project
        every { fileDao.getFilesForProject(3) } returns emptyList()
        every { settingDao.insertSetting(any()) } returns Unit
        val content = kotlinx.coroutines.CompletableDeferred<String>()
        coEvery { storageManager.readFile(project, "draft.md") } coAnswers { content.await() }
        coEvery { storageManager.listDirectory(project, "") } returns emptyList()
        val original = viewModel.loadFile(3, "draft.md")
        val repeated = viewModel.loadFile(3, "draft.md")
        org.junit.Assert.assertSame(original, repeated)
        content.complete("")
        original?.join()
        io.mockk.coVerify(exactly = 1) { storageManager.readFile(project, "draft.md") }
    }

    @Test
    fun `split restores prior sidebar without changing document state`() = runTest {
        viewModel.documentTextFieldState.edit { replace(0, length, "Unsaved **draft**") }
        viewModel.toggleSidebar(SidebarMode.SETTINGS)
        viewModel.toggleCompanion()
        assertEquals(SidebarMode.CLOSED, viewModel.uiState.value.sidebarMode)
        assertTrue(viewModel.companion.state.value.isOpen)
        viewModel.toggleCompanion()
        assertEquals(SidebarMode.SETTINGS, viewModel.uiState.value.sidebarMode)
        assertEquals("Unsaved **draft**", viewModel.documentTextFieldState.text.toString())
    }

    @Test
    fun `toggleBulletList adds hyphen prefix when bullet list is not present`() = runTest {
        // Setup initial paragraphs state
        val paragraphBlock = com.attachdesign.kern.parser.MarkdownParser.parseParagraph("Hello world", "1")
        val immutableBlock = com.attachdesign.kern.ui.editor.ImmutableParagraphBlock(paragraphBlock)
        
        viewModel.setTestParagraphs(listOf(immutableBlock))
        viewModel.setTestTextFieldValue(0, TextFieldValue("Hello world"))

        // Act
        viewModel.toggleBulletList(0)
        advanceUntilIdle()

        // Assert
        val textValue = viewModel.paragraphTextFieldValues.value[0]?.text
        assertEquals("- Hello world", textValue)
    }

    @Test
    fun `toggleBulletList removes hyphen prefix when bullet list is already present`() = runTest {
        // Setup initial paragraphs state with bullet list
        val paragraphBlock = com.attachdesign.kern.parser.MarkdownParser.parseParagraph("- Hello world", "1")
        val immutableBlock = com.attachdesign.kern.ui.editor.ImmutableParagraphBlock(paragraphBlock)
        
        viewModel.setTestParagraphs(listOf(immutableBlock))
        viewModel.setTestTextFieldValue(0, TextFieldValue("- Hello world"))

        // Act
        viewModel.toggleBulletList(0)
        advanceUntilIdle()

        // Assert
        val textValue = viewModel.paragraphTextFieldValues.value[0]?.text
        assertEquals("Hello world", textValue)
    }

    @Test
    fun `toggleSidebar in METRICS mode calculates readability metrics from document text`() = runTest {
        // Set document content
        viewModel.documentTextFieldState.edit {
            replace(0, length, "The quick brown fox jumps over the lazy dog easily.")
        }

        // Toggle metrics sidebar
        viewModel.toggleSidebar(SidebarMode.METRICS)
        advanceUntilIdle()

        // Verify metrics
        val metrics = viewModel.uiState.value.hemingwayMetrics
        org.junit.Assert.assertNotNull(metrics)
        assertEquals(10, metrics?.wordCount)
        assertTrue((metrics?.charCount ?: 0) > 0)
        assertEquals(1, metrics?.sentenceCount)
        assertEquals(1, metrics?.adverbCount) // "easily"
    }

    @Test
    fun `toggleReadabilityPopup triggers analysis on full document content`() = runTest {
        viewModel.documentTextFieldState.edit {
            replace(0, length, "First sentence here. Second sentence was written by him.")
        }

        viewModel.toggleReadabilityPopup()
        advanceUntilIdle()

        val metrics = viewModel.uiState.value.hemingwayMetrics
        org.junit.Assert.assertNotNull(metrics)
        assertEquals(9, metrics?.wordCount)
        assertEquals(2, metrics?.sentenceCount)
        assertEquals(1, metrics?.passiveVoiceCount) // "was written"
    }

    @Test
    fun `undo and redo modify documentTextFieldState correctly`() = runTest {
        viewModel.documentTextFieldState.edit {
            replace(0, length, "Hello")
        }
        viewModel.documentTextFieldState.edit {
            replace(length, length, " World")
        }
        assertEquals("Hello World", viewModel.documentTextFieldState.text.toString())
        assertTrue(viewModel.documentTextFieldState.undoState.canUndo)

        viewModel.undo()
        assertEquals("Hello", viewModel.documentTextFieldState.text.toString())
        assertTrue(viewModel.documentTextFieldState.undoState.canRedo)

        viewModel.redo()
        assertEquals("Hello World", viewModel.documentTextFieldState.text.toString())
    }
}
