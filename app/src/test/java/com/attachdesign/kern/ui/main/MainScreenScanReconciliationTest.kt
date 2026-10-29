package com.attachdesign.kern.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.QuoteEntity
import com.attachdesign.kern.data.storage.DirectoryOutcome
import com.attachdesign.kern.data.storage.NamingTestDocumentsProvider
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.ProjectMutationCoordinator
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenScanReconciliationTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @get:Rule val mainDispatcher = MainDispatcherRule(dispatcher)
    private lateinit var db: AppDatabase
    private lateinit var storage: StorageManager
    private lateinit var vm: MainScreenViewModel
    private lateinit var local: ProjectEntity
    private lateinit var linked: ProjectEntity

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // This fixture controls coroutine races explicitly; make Room flow delivery deterministic
        // instead of mixing real executor threads with virtual test-scheduler assertions.
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor(directExecutor).setTransactionExecutor(directExecutor)
            .allowMainThreadQueries().build()
        local = insertProject("Files", "root", false, true)
        linked = insertProject("Linked", "content://test/tree/work", true, false)
        db.quoteDao().insertQuotes(listOf(QuoteEntity(text = "Quote", author = "Author", year = 2026)))
        storage = mockk(relaxed = true)
        coEvery { storage.listDirectory(any(), any()) } returns emptyList()
        coEvery { storage.listDirectoryOutcome(any(), any()) } returns DirectoryOutcome.Success(emptyList())
        coEvery { storage.fileExists(any(), any()) } returns true
        // Prevent startup's existing sample repair from changing the test's index assertions.
        listOf("Welcome.md", "Formatting Examples.md", "Notes").forEach {
            insertRow(local, it, directory = it == "Notes")
        }
        newViewModel()
    }

    @After
    fun teardown() {
        clearViewModel()
        db.close()
    }

    private fun insertProject(name: String, path: String, external: Boolean, selected: Boolean): ProjectEntity {
        val row = ProjectEntity(name = name, path = path, isExternal = external, isSelected = selected)
        return row.copy(id = db.projectDao().insertProject(row))
    }

    private fun insertRow(project: ProjectEntity, path: String, directory: Boolean = false): FileEntity {
        val row = FileEntity(projectId = project.id, name = path.substringAfterLast('/'),
            relativePath = path, isDirectory = directory, lastModified = 123, syncState = "PENDING",
            wordCount = 42, characterCount = 123, readabilityGrade = "7")
        return row.copy(id = db.fileDao().insertFile(row))
    }

    private fun node(path: String) = VfsNode.File(path.substringAfterLast('/'), path, 13, 234)
    private fun paths(project: ProjectEntity) = db.fileDao().getFilesForProject(project.id).map { it.relativePath }.toSet()
    private fun newViewModel() {
        vm = MainScreenViewModel(db, storage, mockk<FileOperationsManager>(relaxed = true), dispatcher)
    }
    private fun clearViewModel() {
        if (::vm.isInitialized) {
            ViewModel::class.java.getDeclaredMethod("clear\$lifecycle_viewmodel").apply {
                isAccessible = true
            }.invoke(vm)
        }
    }

    @Test
    fun nestedFailurePreservesEveryExistingRowAndSuccessfulRetryCommitsWholeSnapshot() = runTest {
        val existing = insertRow(linked, "old.md")
        coEvery { storage.listDirectoryOutcome(linked, "") } returns DirectoryOutcome.Success(
            listOf(node("new.md"), VfsNode.Directory("folder", "folder")))
        coEvery { storage.listDirectoryOutcome(linked, "folder") } returns DirectoryOutcome.AccessDenied
        vm.scanProject(linked)
        advanceUntilIdle()
        assertEquals(setOf("old.md"), paths(linked))
        assertEquals(existing, db.fileDao().getFileByPath(linked.id, "old.md"))

        coEvery { storage.listDirectoryOutcome(linked, "folder") } returns DirectoryOutcome.Success(listOf(node("folder/nested.md")))
        vm.scanProject(linked)
        advanceUntilIdle()
        assertEquals(setOf("new.md", "folder", "folder/nested.md"), paths(linked))
    }

    @Test
    fun missingFailureAndNestedCancellationNeverPruneOrInsertPartialSnapshot() = runTest {
        insertRow(linked, "old.md")
        for (outcome in listOf(DirectoryOutcome.Missing, DirectoryOutcome.Failure(IOException("offline")))) {
            coEvery { storage.listDirectoryOutcome(linked, "") } returns outcome
            vm.scanProject(linked)
            advanceUntilIdle()
            assertEquals(setOf("old.md"), paths(linked))
        }
        coEvery { storage.listDirectoryOutcome(linked, "") } returns DirectoryOutcome.Success(
            listOf(node("new.md"), VfsNode.Directory("folder", "folder")))
        coEvery { storage.listDirectoryOutcome(linked, "folder") } throws CancellationException("Canceled provider")
        vm.scanProject(linked)
        advanceUntilIdle()
        assertEquals(setOf("old.md"), paths(linked))
    }

    @Test
    fun newerScanFinishingFirstPreventsOlderScanFromResurrectingOrPruning() = runTest {
        val delayed = CompletableDeferred<DirectoryOutcome>()
        var request = 0
        coEvery { storage.listDirectoryOutcome(linked, "") } coAnswers {
            if (++request == 1) delayed.await() else DirectoryOutcome.Success(listOf(node("newer.md")))
        }
        vm.scanProject(linked)
        vm.scanProject(linked)
        advanceUntilIdle()
        assertEquals(setOf("newer.md"), paths(linked))
        delayed.complete(DirectoryOutcome.Success(listOf(node("older.md"))))
        advanceUntilIdle()
        assertEquals(setOf("newer.md"), paths(linked))
    }

    @Test
    fun concurrentCreateRenameAndMoveInvalidateSnapshotBeforeCommit() = runTest {
        for (mutation in listOf("create", "rename", "move")) {
            db.fileDao().deleteFilesForProject(linked.id)
            val delayed = CompletableDeferred<DirectoryOutcome>()
            coEvery { storage.listDirectoryOutcome(linked, "") } coAnswers { delayed.await() }
            vm.scanProject(linked)
            ProjectMutationCoordinator.forDatabase(db).withMutation(linked.id) {
                insertRow(linked, "$mutation.md")
            }
            delayed.complete(DirectoryOutcome.Success(listOf(node("stale.md"))))
            advanceUntilIdle()
            assertEquals(setOf("$mutation.md"), paths(linked))
        }
    }

    @Test
    fun completeScanRetainsExistingMetricsAndGenuineEmptyExternalScanPrunes() = runTest {
        val existing = insertRow(linked, "keep.md")
        coEvery { storage.listDirectoryOutcome(linked, "") } returns DirectoryOutcome.Success(listOf(node("keep.md")))
        vm.scanProject(linked)
        advanceUntilIdle()
        assertEquals(existing, db.fileDao().getFileByPath(linked.id, "keep.md"))
        coEvery { storage.listDirectoryOutcome(linked, "") } returns DirectoryOutcome.Success(emptyList())
        vm.scanProject(linked)
        advanceUntilIdle()
        assertTrue(paths(linked).isEmpty())
    }

    @Test
    fun ambiguousInternalRootNeverPrunesExistingRowsButCanIndexObservedFiles() = runTest {
        insertRow(local, "previous-root.md")
        coEvery { storage.listDirectoryOutcome(local, "") } returns DirectoryOutcome.Success(listOf(node("observed.md")))
        vm.scanProject(local)
        advanceUntilIdle()
        assertTrue(paths(local).containsAll(listOf("previous-root.md", "observed.md")))
        coEvery { storage.listDirectoryOutcome(local, "") } returns DirectoryOutcome.Success(emptyList())
        vm.scanProject(local)
        advanceUntilIdle()
        assertTrue(paths(local).containsAll(listOf("previous-root.md", "observed.md")))
    }

    @Test
    fun removedWorkspaceCannotBeResurrectedByLateScanEvenWithoutGenerationInvalidation() = runTest {
        val delayed = CompletableDeferred<DirectoryOutcome>()
        coEvery { storage.listDirectoryOutcome(linked, "") } coAnswers { delayed.await() }
        vm.scanProject(linked)
        // Exercise the transaction's existence guard independently of the normal removal gate.
        db.projectDao().deleteProjectById(linked.id)
        delayed.complete(DirectoryOutcome.Success(listOf(node("late.md"))))
        advanceUntilIdle()
        assertTrue(paths(linked).isEmpty())
    }

    @Test
    fun activeLinkedRemovalRepairsSelectionHistoryAndSurvivesRecreation() = runTest {
        val collection = backgroundScope.launch(dispatcher) { vm.explorerState.collect {} }
        vm.selectProject(linked)
        vm.navigateToSegment(linked, "folder")
        advanceUntilIdle()
        insertRow(linked, "folder/keep.md")
        vm.deleteProject(linked)
        advanceUntilIdle()
        assertNull(db.projectDao().getProjectById(linked.id))
        assertEquals(local.id, db.projectDao().getSelectedProject()?.id)
        assertEquals(local.id, vm.explorerState.value.activeProject?.id)
        assertEquals("", vm.explorerState.value.currentPath)
        vm.navigateBack()
        vm.navigateForward()
        advanceUntilIdle()
        assertNotEquals(linked.id, vm.explorerState.value.activeProject?.id)
        assertTrue(paths(linked).isEmpty())
        collection.cancel()
        clearViewModel()
        newViewModel()
        val recreatedCollection = backgroundScope.launch(dispatcher) { vm.explorerState.collect {} }
        advanceUntilIdle()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(10_000) { vm.explorerState.first { !it.isLoading && it.activeProject?.id == local.id } }
        }
        assertEquals(local.id, vm.explorerState.value.activeProject?.id)
        recreatedCollection.cancel()
        coVerify(exactly = 0) { storage.deleteFile(any(), any()) }
    }

    @Test
    fun inactiveRemovalPreservesCurrentDestinationDuplicateAssociationAndRelinking() = runTest {
        val other = insertProject("Same folder", linked.path, true, false)
        insertRow(other, "still-linked.md")
        val collection = backgroundScope.launch(dispatcher) { vm.explorerState.collect {} }
        vm.navigateToSegment(local, "subfolder")
        advanceUntilIdle()
        vm.deleteProject(linked)
        advanceUntilIdle()
        assertEquals(local.id, vm.explorerState.value.activeProject?.id)
        assertEquals("subfolder", vm.explorerState.value.currentPath)
        assertNotNull(db.projectDao().getProjectById(other.id))
        assertEquals(setOf("still-linked.md"), paths(other))
        val relinked = insertProject("Relinked", linked.path, true, false)
        coEvery { storage.listDirectoryOutcome(relinked, "") } returns DirectoryOutcome.Success(listOf(node("restored.md")))
        vm.scanProject(relinked)
        advanceUntilIdle()
        assertEquals(setOf("restored.md"), paths(relinked))
        collection.cancel()
    }

    @Test
    fun removalLeavesProviderFolderBinaryBytesAndDuplicateUriAssociationUntouched() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = NamingTestDocumentsProvider.register(context)
        val folder = provider.add("資料", directory = true)
        val binary = provider.add("asset.bin", parent = folder.id, bytes = byteArrayOf(0, 1, -1, 4))
        val beforeIds = provider.entries.keys.toSet()
        val originalBytes = binary.bytes.copyOf()
        val removable = insertProject("Provider", NamingTestDocumentsProvider.TREE_URI, true, false)
        val duplicate = insertProject("Provider duplicate", removable.path, true, false)
        val grantsBefore = context.contentResolver.persistedUriPermissions.toList()
        vm.deleteProject(removable)
        advanceUntilIdle()
        assertEquals(beforeIds, provider.entries.keys)
        assertArrayEquals(originalBytes, provider.entries[binary.id]!!.bytes)
        assertEquals(folder.id, provider.entries[binary.id]!!.parent)
        assertNotNull(db.projectDao().getProjectById(duplicate.id))
        assertEquals(grantsBefore, context.contentResolver.persistedUriPermissions)
        assertEquals(0, provider.renameCalls)
        assertEquals(0, provider.moveCalls)
    }

    @Test
    fun defaultSandboxCannotBeRemoved() = runTest {
        vm.deleteProject(local)
        advanceUntilIdle()
        assertNotNull(db.projectDao().getProjectById(local.id))
        assertEquals(local.id, db.projectDao().getSelectedProject()?.id)
    }
    @Test
    fun deniedDirectoryHasRecoveryStateAndSuccessfulRefreshKeepsItsIndex() = runTest {
        insertRow(linked, "locked/keep.md")
        val collection = backgroundScope.launch(dispatcher) { vm.explorerState.collect {} }
        coEvery { storage.listDirectoryOutcome(linked, "locked") } returns DirectoryOutcome.AccessDenied
        vm.navigateToSegment(linked, "locked")
        advanceUntilIdle()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(10_000) { vm.explorerState.first { it.directoryError != null } }
        }
        assertNotNull(vm.explorerState.value.directoryError)
        assertTrue(paths(linked).contains("locked/keep.md"))
        coEvery { storage.listDirectoryOutcome(linked, "") } returns DirectoryOutcome.Success(listOf(VfsNode.Directory("locked", "locked")))
        coEvery { storage.listDirectoryOutcome(linked, "locked") } returns DirectoryOutcome.Success(listOf(node("locked/keep.md")))
        vm.refreshCurrentDirectory()
        advanceUntilIdle()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(10_000) { vm.explorerState.first { it.directoryError == null && it.drillFiles.any { node -> node.relativePath == "locked/keep.md" } } }
        }
        assertNull(vm.explorerState.value.directoryError)
        assertEquals("locked/keep.md", vm.explorerState.value.drillFiles.single().relativePath)
        assertTrue(paths(linked).contains("locked/keep.md"))
        collection.cancel()
    }

}
