package com.attachdesign.kern.ui.editor

import android.content.Context
import android.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import com.attachdesign.kern.data.local.*
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.parser.MarkdownParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: EditorViewModel
    private lateinit var db: AppDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)



        mockkStatic(Color::class)
        every { Color.parseColor(any()) } returns 0xFFFFFF

        mockkStatic(android.os.Looper::class)
        val looper = mockk<android.os.Looper>(relaxed = true)
        every { android.os.Looper.getMainLooper() } returns looper

        mockkStatic(android.os.Handler::class)
        val handler = mockk<android.os.Handler>(relaxed = true)
        every { handler.post(any()) } returns true



        db = mockk(relaxed = true)
        storageManager = mockk(relaxed = true)
        context = mockk(relaxed = true)

        val projectDao = mockk<ProjectDao>(relaxed = true)
        val fileDao = mockk<FileDao>(relaxed = true)
        val settingDao = mockk<SettingDao>(relaxed = true)
        val themeDao = mockk<ThemeDao>(relaxed = true)

        every { db.projectDao() } returns projectDao
        every { db.fileDao() } returns fileDao
        every { db.settingDao() } returns settingDao
        every { db.themeDao() } returns themeDao

        coEvery { settingDao.getSettingFlow(any()) } returns MutableStateFlow(null)

        viewModel = EditorViewModel(db, storageManager, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setViewModelState(paragraphs: List<String>) {
        val parsedBlocks = paragraphs.map { MarkdownParser.parseParagraph(it) }
        val immutableBlocks = parsedBlocks.map { ImmutableParagraphBlock(it) }.toImmutableList()
        val immutableList = ImmutableParagraphList(immutableBlocks)

        val uiStateField: Field = EditorViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val uiStateFlow = uiStateField.get(viewModel) as MutableStateFlow<EditorUiState>

        uiStateFlow.value = uiStateFlow.value.copy(paragraphs = immutableList)

        val valuesField: Field = EditorViewModel::class.java.getDeclaredField("_paragraphTextFieldValues")
        valuesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val valuesFlow = valuesField.get(viewModel) as MutableStateFlow<Map<Int, TextFieldValue>>

        val valuesMap = paragraphs.mapIndexed { index, text -> index to TextFieldValue(text) }.toMap()
        valuesFlow.value = valuesMap
    }

    @Test
    fun testMergeParagraphWithPrevious_indexZero_doesNothing() {
        setViewModelState(listOf("First", "Second"))
        viewModel.mergeParagraphWithPrevious(0)

        assertEquals(2, viewModel.uiState.value.paragraphs.items.size)
        assertEquals("First", viewModel.uiState.value.paragraphs.items[0].block.rawText)
        assertEquals("Second", viewModel.uiState.value.paragraphs.items[1].block.rawText)
    }

    @Test
    fun testMergeParagraphWithPrevious_validIndex_mergesCorrectly() {
        setViewModelState(listOf("First paragraph.", "Second paragraph."))

        viewModel.mergeParagraphWithPrevious(1)

        // Assert paragraphs list
        val paragraphs = viewModel.uiState.value.paragraphs.items
        assertEquals(1, paragraphs.size)
        assertEquals("First paragraph.Second paragraph.", paragraphs[0].block.rawText)

        // Assert text field values
        val values = viewModel.paragraphTextFieldValues.value
        assertEquals(1, values.size)
        assertEquals("First paragraph.Second paragraph.", values[0]?.text)

        // Assert cursor is placed correctly (at the junction)
        assertEquals("First paragraph.".length, values[0]?.selection?.start)
    }

    @Test
    fun testMergeParagraphWithPrevious_multipleParagraphs_mergesMiddle() {
        setViewModelState(listOf("P1", "P2", "P3", "P4"))

        viewModel.mergeParagraphWithPrevious(2)

        // Assert paragraphs list
        val paragraphs = viewModel.uiState.value.paragraphs.items
        assertEquals(3, paragraphs.size)
        assertEquals("P1", paragraphs[0].block.rawText)
        assertEquals("P2P3", paragraphs[1].block.rawText)
        assertEquals("P4", paragraphs[2].block.rawText)

        // Assert text field values
        val values = viewModel.paragraphTextFieldValues.value
        assertEquals(3, values.size)
        assertEquals("P1", values[0]?.text)
        assertEquals("P2P3", values[1]?.text)
        assertEquals("P4", values[2]?.text)

        // Assert cursor
        assertEquals("P2".length, values[1]?.selection?.start)
    }

}
