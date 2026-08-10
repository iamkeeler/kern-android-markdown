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
        io.mockk.mockkStatic(android.graphics.Color::class)
        every { android.graphics.Color.parseColor(any()) } returns 0

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

        viewModel = EditorViewModel(db, storageManager, fileOpsManager, context)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        io.mockk.unmockkAll()
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
}
