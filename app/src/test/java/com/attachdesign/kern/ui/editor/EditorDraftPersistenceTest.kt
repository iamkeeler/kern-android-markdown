package com.attachdesign.kern.ui.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.ProjectDao
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingDao
import com.attachdesign.kern.data.local.ThemeDao
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.sync.SyncProvider
import com.attachdesign.kern.test.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Focused regression tests for the review fixes:
 * - H7: SavedStateHandle draft persistence / restoration (process-death survival).
 * - L16: corrupt setting values must not crash the editor (safe enum parsing).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorDraftPersistenceTest {

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

    private val createdViewModels = mutableListOf<EditorViewModel>()

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        projectDao = mockk()
        fileDao = mockk()
        settingDao = mockk()
        val themeDao = mockk<ThemeDao>(relaxed = true)
        storageManager = mockk(relaxed = true)
        fileOpsManager = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { db.projectDao() } returns projectDao
        every { db.fileDao() } returns fileDao
        every { db.settingDao() } returns settingDao
        every { db.themeDao() } returns themeDao

        every { projectDao.getAllProjectsFlow() } returns MutableStateFlow(emptyList())
        every { settingDao.getSettingFlow(any()) } returns MutableStateFlow(null)
        every { settingDao.getSetting(any()) } returns null
        every { themeDao.getAllThemesFlow() } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        io.mockk.unmockkAll()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()): EditorViewModel {
        return EditorViewModel(db, storageManager, fileOpsManager, context, testDispatcher, handle)
            .also { createdViewModels.add(it) }
    }

    // ── H7: draft restoration ────────────────────────────────────────────────

    @Test
    fun `resolveInitialContent prefers SavedStateHandle draft over disk content`() {
        val handle = SavedStateHandle(
            mapOf(
                "editor_draft_file" to "1/note.md",
                "editor_draft_text" to "unsaved draft"
            )
        )
        val vm = createViewModel(handle)

        assertEquals("unsaved draft", vm.resolveInitialContent(1, "note.md", "disk content"))
    }

    @Test
    fun `resolveInitialContent falls back to disk content when no draft exists`() {
        val vm = createViewModel()

        assertEquals("disk content", vm.resolveInitialContent(1, "note.md", "disk content"))
    }

    @Test
    fun `resolveInitialContent ignores a draft saved for a different file`() {
        val handle = SavedStateHandle(
            mapOf(
                "editor_draft_file" to "1/other.md",
                "editor_draft_text" to "unsaved draft"
            )
        )
        val vm = createViewModel(handle)

        assertEquals("disk content", vm.resolveInitialContent(1, "note.md", "disk content"))
    }

    @Test
    fun `resolveInitialContent falls back to disk content when draft text is missing`() {
        val handle = SavedStateHandle(mapOf("editor_draft_file" to "1/note.md"))
        val vm = createViewModel(handle)

        assertEquals("disk content", vm.resolveInitialContent(1, "note.md", "disk content"))
    }

    // ── H7: draft persistence ────────────────────────────────────────────────

    @Test
    fun `onDocumentTextChanged persists draft to SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        val vm = createViewModel(handle)
        val project = ProjectEntity(id = 1, name = "Notes", path = "root", isExternal = false, isSelected = true)
        vm.setTestActiveFile(project, "note.md", "original")

        vm.onDocumentTextChanged("original plus edits")

        assertEquals("1/note.md", handle.get<String>("editor_draft_file"))
        assertEquals("original plus edits", handle.get<String>("editor_draft_text"))
    }

    @Test
    fun `onDocumentTextChanged with unchanged content writes no draft`() = runTest {
        val handle = SavedStateHandle()
        val vm = createViewModel(handle)
        val project = ProjectEntity(id = 1, name = "Notes", path = "root", isExternal = false, isSelected = true)
        vm.setTestActiveFile(project, "note.md", "original")

        vm.onDocumentTextChanged("original")

        assertNull(handle.get<String>("editor_draft_file"))
    }

    // ── L16: safe enum parsing ───────────────────────────────────────────────

    @Test
    fun `viewModeOrDefault falls back to RENDERED on corrupt value`() {
        val vm = createViewModel()

        assertEquals(ViewMode.RENDERED, vm.viewModeOrDefault("BOGUS"))
        assertEquals(ViewMode.RENDERED, vm.viewModeOrDefault(null))
        assertEquals(ViewMode.RENDERED, vm.viewModeOrDefault(""))
    }

    @Test
    fun `viewModeOrDefault parses valid values`() {
        val vm = createViewModel()

        assertEquals(ViewMode.RAW_PLAIN_TEXT, vm.viewModeOrDefault("RAW_PLAIN_TEXT"))
        assertEquals(ViewMode.SYNTAX_HIGHLIGHTED, vm.viewModeOrDefault("SYNTAX_HIGHLIGHTED"))
        assertEquals(ViewMode.RENDERED, vm.viewModeOrDefault("RENDERED"))
    }

    @Test
    fun `syncProviderOrDefault falls back to NONE on corrupt value`() {
        val vm = createViewModel()

        assertEquals(SyncProvider.NONE, vm.syncProviderOrDefault("BOGUS"))
        assertEquals(SyncProvider.NONE, vm.syncProviderOrDefault(null))
        assertEquals(SyncProvider.NONE, vm.syncProviderOrDefault(""))
    }

    @Test
    fun `syncProviderOrDefault parses valid values`() {
        val vm = createViewModel()

        assertEquals(SyncProvider.GOOGLE_DRIVE, vm.syncProviderOrDefault("GOOGLE_DRIVE"))
        assertEquals(SyncProvider.DROPBOX, vm.syncProviderOrDefault("DROPBOX"))
        assertEquals(SyncProvider.NONE, vm.syncProviderOrDefault("NONE"))
    }
}
