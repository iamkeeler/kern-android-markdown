package com.attachdesign.kern.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.ProjectEntity
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BundledReleaseNotesInstallerTest {
    private lateinit var db: AppDatabase
    private lateinit var storage: StorageManager
    private lateinit var installer: BundledReleaseNotesInstaller
    private val version = ReleaseNotesVersion("0.1.44", 45)
    private val markdown = "${version.heading}\n\n## What's new\n\n- Bundled notes.\n"

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        storage = StorageManager(context)
        installer = BundledReleaseNotesInstaller(db, storage)
    }

    @After fun cleanup() {
        db.projectDao().getAllProjects().filter { !it.isExternal }.forEach {
            storage.getAbsoluteFile(it, "").deleteRecursively()
        }
        db.close()
    }

    private fun root() = db.projectDao().getAllProjects().single { !it.isExternal && it.path == "root" }
    private fun path(build: ReleaseNotesVersion = version) = db.settingDao().getSetting(build.installedKey)!!.value

    @Test fun firstInstallCreatesReadableMarkdownAndItsFolderIndex() = runBlocking {
        installer.install(version, markdown)
        assertEquals("Release Notes/${version.fileName}", path())
        assertEquals(markdown, storage.readFile(root(), path()))
        assertTrue(db.fileDao().getFileByPath(root().id, "Release Notes")!!.isDirectory)
        assertFalse(db.fileDao().getFileByPath(root().id, path())!!.isDirectory)
    }

    @Test fun repeatedLaunchAndUpgradePreserveUserEditsAndHistory() = runBlocking {
        installer.install(version, markdown)
        val original = path()
        storage.writeFile(root(), original, "My annotations\n\nKeep this exact text.")
        installer.install(version, markdown)
        val newer = ReleaseNotesVersion("0.1.45", 46)
        val newerBody = "${newer.heading}\n\nNew release.\n"
        installer.install(newer, newerBody)
        assertEquals("My annotations\n\nKeep this exact text.", storage.readFile(root(), original))
        assertEquals(newerBody, storage.readFile(root(), path(newer)))
        assertEquals(2, db.fileDao().getFilesForProject(root().id).count { !it.isDirectory })
    }

    @Test fun deletingTheNotesDoesNotRecreateThemOnEveryLaunch() = runBlocking {
        installer.install(version, markdown)
        storage.deletePermanentlyOutcome(root(), path())
        installer.install(version, markdown)
        assertFalse(storage.fileExists(root(), path()))
    }

    @Test fun folderAndFileNameCollisionsNeverReplaceExistingUserContent() = runBlocking {
        val project = ProjectEntity(name = "Files", path = "root", isExternal = false, isSelected = true)
        val actual = project.copy(id = db.projectDao().insertProject(project))
        storage.writeFile(actual, "Release Notes", "User file occupying the folder name")
        storage.createDirectory(actual, "Release Notes (2)")
        storage.writeFile(actual, "Release Notes (2)/${version.fileName}", "User document occupying the note name")
        installer.install(version, markdown)
        assertEquals("User file occupying the folder name", storage.readFile(actual, "Release Notes"))
        assertEquals("User document occupying the note name", storage.readFile(actual, "Release Notes (2)/${version.fileName}"))
        assertEquals("Release Notes (2)/Kern-0.1.44-build-45 (2).md", path())
        assertEquals(markdown, storage.readFile(actual, path()))
    }

    @Test fun existingCompletedNoteIsReindexedWithoutDuplicatingIt() = runBlocking {
        val seed = ProjectEntity(name = "Files", path = "root", isExternal = false, isSelected = true)
        val actual = seed.copy(id = db.projectDao().insertProject(seed))
        storage.writeFile(actual, "Release Notes/${version.fileName}", markdown)
        installer.install(version, markdown)
        assertEquals("Release Notes/${version.fileName}", path())
        assertEquals(1, storage.listDirectory(actual, "Release Notes").size)
    }

    @Test fun failedWriteCannotMarkTheBuildInstalledAndCanRetry() = runBlocking {
        val failing = BundledReleaseNotesInstaller(db, storage) { _, _ -> throw IOException("Write failed") }
        try {
            failing.install(version, markdown)
            fail("The failed write must be reported")
        } catch (_: IOException) { }
        assertNull(db.settingDao().getSetting(version.installedKey))
        assertTrue(db.fileDao().getFilesForProject(root().id).isEmpty())
        assertTrue(storage.listDirectory(root(), "Release Notes").isEmpty())
        installer.install(version, markdown)
        assertEquals(markdown, storage.readFile(root(), path()))
    }

    @Test fun externalWorkspaceSelectionAndContentsAreUntouched() = runBlocking {
        val external = ProjectEntity(name = "Work", path = "content://documents/tree/work", isExternal = true, isSelected = true)
        val id = db.projectDao().insertProject(external)
        installer.install(version, markdown)
        assertEquals(id, db.projectDao().getSelectedProject()!!.id)
        assertFalse(root().isSelected)
        assertEquals(2, db.projectDao().getAllProjects().size)
    }

    @Test fun concurrentInstallersCreateOnlyOneNote() = runBlocking {
        listOf(async(Dispatchers.IO) { installer.install(version, markdown) },
            async(Dispatchers.IO) { installer.install(version, markdown) }).awaitAll()
        assertEquals(1, storage.listDirectory(root(), "Release Notes").size)
        assertEquals(2, db.fileDao().getFilesForProject(root().id).size)
    }
}
