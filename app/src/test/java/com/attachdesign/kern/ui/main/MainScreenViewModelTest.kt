package com.attachdesign.kern.ui.main

import app.cash.turbine.test
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectDao
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.QuoteDao
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var db: AppDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var fileDao: FileDao
    private lateinit var quoteDao: QuoteDao
    private lateinit var storageManager: StorageManager

    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        db = mockk()
        projectDao = mockk()
        fileDao = mockk()
        quoteDao = mockk()
        storageManager = mockk(relaxed = true)

        every { db.projectDao() } returns projectDao
        every { db.fileDao() } returns fileDao
        every { db.quoteDao() } returns quoteDao

        coEvery { projectDao.getAllProjects() } returns emptyList()
        coEvery { projectDao.getAllProjectsFlow() } returns emptyFlow()
        coEvery { projectDao.getSelectedProjectFlow() } returns emptyFlow()
        coEvery { quoteDao.getCount() } returns 1
        coEvery { quoteDao.getAllQuotes() } returns emptyList()
        coEvery { fileDao.getFilesForProjects(any()) } returns emptyList()
        coEvery { projectDao.getSelectedProject() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createFile appends md extension if missing`() = runTest {
        // Setup
        val proj = ProjectEntity(id = 1L, name = "Test", path = "test", isExternal = false, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { fileDao.insertFile(any()) } returns 1L

        viewModel = MainScreenViewModel(db, storageManager)

        // Act
        viewModel.createFile("newfile", proj)
        advanceUntilIdle()

        // Assert
        coVerify {
            storageManager.createFile(proj, "newfile.md")
            fileDao.insertFile(match {
                it.name == "newfile.md" &&
                it.relativePath == "newfile.md" &&
                it.projectId == 1L &&
                it.syncState == "PENDING"
            })
        }
    }

    @Test
    fun `createFile does not append md extension if present`() = runTest {
        // Setup
        val proj = ProjectEntity(id = 1L, name = "Test", path = "test", isExternal = false, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { fileDao.insertFile(any()) } returns 1L

        viewModel = MainScreenViewModel(db, storageManager)

        // Act
        viewModel.createFile("existing.md", proj)
        advanceUntilIdle()

        // Assert
        coVerify {
            storageManager.createFile(proj, "existing.md")
            fileDao.insertFile(match {
                it.name == "existing.md" &&
                it.relativePath == "existing.md"
            })
        }
    }

    @Test
    fun `createFile constructs correct relative path when current path is set`() = runTest {
        // Setup
        val proj = ProjectEntity(id = 1L, name = "Test", path = "test", isExternal = false, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { fileDao.insertFile(any()) } returns 1L
        coEvery { storageManager.listDirectory(any(), any()) } returns emptyList()
        coEvery { fileDao.getFilesForProject(any()) } returns emptyList()

        viewModel = MainScreenViewModel(db, storageManager)

        viewModel.navigateToSegment(proj, "subfolder")

        // Act
        // Use implicit target project (i.e. use activeProject)
        viewModel.createFile("nested")
        advanceUntilIdle()

        // Assert
        coVerify {
            storageManager.createFile(proj, "subfolder/nested.md")
            fileDao.insertFile(match {
                it.relativePath == "subfolder/nested.md" &&
                it.name == "nested.md"
            })
        }
    }

    @Test
    fun `createFile sets sync state to SYNCED for external projects`() = runTest {
        // Setup
        val proj = ProjectEntity(id = 1L, name = "External", path = "ext", isExternal = true, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { fileDao.insertFile(any()) } returns 1L

        viewModel = MainScreenViewModel(db, storageManager)

        // Act
        viewModel.createFile("test", proj)
        advanceUntilIdle()

        // Assert
        coVerify {
            fileDao.insertFile(match {
                it.syncState == "SYNCED"
            })
        }
    }

    @Test
    fun `createFile falls back to selected project from db if neither target nor active is set`() = runTest {
        // Setup
        val proj = ProjectEntity(id = 2L, name = "DB Selected", path = "db", isExternal = false, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { fileDao.insertFile(any()) } returns 1L
        coEvery { projectDao.getAllProjects() } returns listOf(proj)

        viewModel = MainScreenViewModel(db, storageManager)

        // Act
        viewModel.createFile("fallback")
        advanceUntilIdle()

        // Assert
        coVerify {
            storageManager.createFile(proj, "fallback.md")
            fileDao.insertFile(match {
                it.projectId == 2L
            })
        }
    }
}
