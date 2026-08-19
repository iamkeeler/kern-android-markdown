package com.attachdesign.kern.ui.main

import androidx.lifecycle.ViewModel
import app.cash.turbine.test
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.ProjectDao
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.QuoteDao
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private fun ViewModel.clearForTest() {
    val clearMethod = ViewModel::class.java.getDeclaredMethod("clear\$lifecycle_viewmodel")
    clearMethod.isAccessible = true
    clearMethod.invoke(this)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var db: AppDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var fileDao: FileDao
    private lateinit var quoteDao: QuoteDao
    private lateinit var storageManager: StorageManager
    private lateinit var fileOpsManager: FileOperationsManager

    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setup() {
        db = mockk()
        projectDao = mockk()
        fileDao = mockk()
        quoteDao = mockk()
        storageManager = mockk(relaxed = true)
        fileOpsManager = mockk(relaxed = true)

        every { db.projectDao() } returns projectDao
        every { db.fileDao() } returns fileDao
        every { db.quoteDao() } returns quoteDao

        coEvery { projectDao.getAllProjects() } returns emptyList()
        coEvery { projectDao.getAllProjectsFlow() } returns emptyFlow()
        coEvery { projectDao.getSelectedProjectFlow() } returns emptyFlow()
        coEvery { quoteDao.getCount() } returns 1
        coEvery { quoteDao.getAllQuotes() } returns emptyList()
        coEvery { quoteDao.insertQuotes(any()) } returns Unit
        coEvery { fileDao.getFilesForProjects(any()) } returns emptyList()
        coEvery { fileDao.getFilesForProject(any()) } returns emptyList()
        coEvery { fileDao.insertFile(any()) } returns 1L
        coEvery { projectDao.getSelectedProject() } returns null
        coEvery { projectDao.insertProject(any()) } returns 1L
        coEvery { projectDao.deleteProjectById(any()) } returns Unit
        coEvery { projectDao.updateProject(any()) } returns Unit
        coEvery { projectDao.getProjectById(any()) } returns null
        coEvery { fileDao.deleteFilesForProject(any()) } returns Unit
        coEvery { fileDao.deleteFile(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.clearForTest()
        }
    }

    @Test
    fun `createFile appends md extension if missing`() = runTest {
        // Setup
        val proj = ProjectEntity(id = 1L, name = "Test", path = "test", isExternal = false, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { fileDao.insertFile(any()) } returns 1L

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)

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

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)

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

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)

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
    fun `createFile preserves current path when target project is supplied`() = runTest {
        val proj = ProjectEntity(id = 1L, name = "Test", path = "test", isExternal = false, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { fileDao.insertFile(any()) } returns 1L
        coEvery { storageManager.listDirectory(any(), any()) } returns emptyList()
        coEvery { fileDao.getFilesForProject(any()) } returns emptyList()

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)
        viewModel.navigateToSegment(proj, "subfolder")

        viewModel.createFile("nested", proj)
        advanceUntilIdle()

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

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)

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

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)

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

    @Test
    fun `isLoading starts true and becomes false after initialization`() = runTest {
        val proj = ProjectEntity(id = 1L, name = "Test", path = "test", isExternal = false, isSelected = true)
        coEvery { quoteDao.getAllQuotesFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { projectDao.getAllProjects() } returns listOf(proj)
        coEvery { projectDao.getAllProjectsFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { projectDao.getSelectedProjectFlow() } returns kotlinx.coroutines.flow.flowOf(null)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { storageManager.listDirectory(any(), any()) } returns emptyList()
        coEvery { fileDao.getFilesForProject(any()) } returns emptyList()

        val localDispatcher = StandardTestDispatcher(testScheduler)
        val testVm = MainScreenViewModel(db, storageManager, fileOpsManager, localDispatcher)
        val job = backgroundScope.launch(localDispatcher) {
            testVm.explorerState.collect {}
        }
        testScheduler.advanceUntilIdle()
        assertEquals(false, testVm.explorerState.value.isLoading)
        testVm.clearForTest()
        job.cancel()
    }

    @Test
    fun `navigation history tracks navigation back and forward states correctly`() = runTest {
        val proj = ProjectEntity(id = 1L, name = "Test", path = "test", isExternal = false, isSelected = true)
        coEvery { projectDao.getSelectedProject() } returns proj
        coEvery { projectDao.getAllProjectsFlow() } returns kotlinx.coroutines.flow.flowOf(listOf(proj))
        coEvery { storageManager.listDirectory(any(), any()) } returns emptyList()
        coEvery { fileDao.getFilesForProject(any()) } returns emptyList()

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)
        val job = backgroundScope.launch {
            viewModel.explorerState.collect {}
        }
        advanceUntilIdle()

        // Initially at root/selected project root (or wherever setup initializes, let's look at initial state)
        assertEquals(false, viewModel.explorerState.value.canNavigateBack)
        assertEquals(false, viewModel.explorerState.value.canNavigateForward)

        // Navigate to folder
        val folder = VfsNode.Directory("subfolder", "subfolder")
        viewModel.navigateToFolder(folder, proj)
        advanceUntilIdle()

        // Should be able to go back, but not forward
        assertEquals(true, viewModel.explorerState.value.canNavigateBack)
        assertEquals(false, viewModel.explorerState.value.canNavigateForward)
        assertEquals("subfolder", viewModel.explorerState.value.currentPath)

        // Navigate back
        viewModel.navigateBack()
        advanceUntilIdle()

        // Should not be able to go back, but should be able to go forward
        assertEquals(false, viewModel.explorerState.value.canNavigateBack)
        assertEquals(true, viewModel.explorerState.value.canNavigateForward)
        assertEquals("", viewModel.explorerState.value.currentPath)

        // Navigate forward
        viewModel.navigateForward()
        advanceUntilIdle()

        // Should be back to subfolder
        assertEquals(true, viewModel.explorerState.value.canNavigateBack)
        assertEquals(false, viewModel.explorerState.value.canNavigateForward)
        assertEquals("subfolder", viewModel.explorerState.value.currentPath)

        job.cancel()
    }

    @Test
    fun `migration renames legacy project from notes to root`() = runTest {
        val legacyProj = ProjectEntity(id = 3L, name = "Notes", path = "notes", isExternal = false, isSelected = true)
        
        coEvery { projectDao.getAllProjects() } returns listOf(legacyProj)
        coEvery { projectDao.getSelectedProject() } returns legacyProj
        
        // Mock storageManager.getAbsoluteFile to return a temporary folder
        val tempDir = java.nio.file.Files.createTempDirectory("kern_test_migration").toFile()
        val oldDir = java.io.File(tempDir, "notes")
        oldDir.mkdirs()
        java.io.File(oldDir, "test_file.txt").writeText("hello")
        
        every { storageManager.getAbsoluteFile(legacyProj, "") } returns oldDir
        
        // Act
        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)
        advanceUntilIdle()
        
        // Assert projectDao.updateProject was called to rename path to root and name to Files
        coVerify {
            projectDao.updateProject(match {
                it.name == "Files" && it.path == "root" && it.id == 3L
            })
        }
        
        // Assert directory on disk was renamed to root
        val newDir = java.io.File(tempDir, "root")
        assertEquals(true, newDir.exists())
        assertEquals(false, oldDir.exists())
        assertEquals("hello", java.io.File(newDir, "test_file.txt").readText())
        
        // Clean up
        tempDir.deleteRecursively()
    }

    @Test
    fun `recovery indexes existing workspace when database is empty`() = runTest {
        val restoredFile = VfsNode.File(
            name = "My Note.md",
            relativePath = "My Note.md",
            size = 42L,
            lastModified = 1234L
        )
        coEvery { projectDao.insertProject(any()) } returns 7L
        coEvery { storageManager.listDirectory(any(), "") } returns listOf(restoredFile)
        coEvery { storageManager.fileExists(any(), any()) } returns true

        viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)
        advanceUntilIdle()

        coVerify {
            fileDao.insertFile(match {
                it.projectId == 7L &&
                    it.name == "My Note.md" &&
                    it.relativePath == "My Note.md" &&
                    !it.isDirectory &&
                    it.syncState == "SYNCED"
            })
        }
        coVerify(exactly = 0) { storageManager.writeFile(any(), "Welcome.md", any()) }
        coVerify(exactly = 0) { storageManager.writeFile(any(), "Formatting Examples.md", any()) }
    }
}
