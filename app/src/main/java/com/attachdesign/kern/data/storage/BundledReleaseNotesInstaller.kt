package com.attachdesign.kern.data.storage

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.SettingEntity
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReleaseNotesVersion(val name: String, val code: Long) {
    val fileName: String
        get() = "Kern-${name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)}-build-$code.md"
    val heading: String get() = "# Kern $name (build $code)"
    val installedKey: String get() = "bundled_release_notes_$code"
}

/** Adds a normal Markdown document once per installed build, without replacing user files. */
class BundledReleaseNotesInstaller(
    private val db: AppDatabase,
    private val storage: StorageManager,
    private val writeMarkdown: (File, String) -> Unit = { file, text ->
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }
) {
    suspend fun install(version: ReleaseNotesVersion, markdown: String) = withContext(Dispatchers.IO) {
        require(version.code > 0 && markdown.startsWith(version.heading + "\n"))
        installMutex.withLock {
            if (db.settingDao().getSetting(version.installedKey) != null) return@withLock
            val projects = db.projectDao().getAllProjects()
            val project = projects.firstOrNull { !it.isExternal && it.path == "root" } ?: run {
                val seed = ProjectEntity(name = "Files", path = "root", isExternal = false,
                    isSelected = projects.isEmpty())
                seed.copy(id = db.projectDao().insertProject(seed))
            }
            ProjectMutationCoordinator.forDatabase(db).withMutation(project.id) {
                val folder = folder(project)
                val note = publish(project, folder, version.fileName, markdown)
                db.runInTransaction {
                    indexIfMissing(project, folder)
                    indexIfMissing(project, note)
                    db.settingDao().insertSetting(SettingEntity(version.installedKey, note.relativePath))
                }
            }
        }
    }

    private suspend fun children(project: ProjectEntity, path: String): List<VfsNode> =
        when (val result = storage.listDirectoryOutcome(project, path)) {
            is DirectoryOutcome.Success -> result.nodes
            else -> throw IOException("Release notes directory could not be read")
        }

    private suspend fun folder(project: ProjectEntity): VfsNode {
        val existing = children(project, "")
        for (number in 1..100) {
            val name = if (number == 1) "Release Notes" else "Release Notes ($number)"
            val node = existing.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (node != null) {
                if (node.isDirectory) return node
                continue
            }
            when (val result = storage.createNamedDirectory(project, "", name)) {
                is MutationOutcome.Success -> return result.actual.node
                is MutationOutcome.Failed -> if (result.reason != MutationFailure.Collision) throw MutationOutcomeException(result)
                is MutationOutcome.ReconciliationRequired -> throw MutationOutcomeException(result)
            }
        }
        throw IOException("No available release notes folder name")
    }

    private suspend fun publish(project: ProjectEntity, folder: VfsNode, fileName: String, markdown: String): VfsNode {
        val existing = children(project, folder.relativePath)
        val bytes = markdown.toByteArray(Charsets.UTF_8).size.toLong()
        var temporary: String? = null
        try {
            for (number in 1..100) {
                val leaf = if (number == 1) fileName else fileName.removeSuffix(".md") + " ($number).md"
                val node = existing.firstOrNull { it.name.equals(leaf, ignoreCase = true) }
                if (node != null) {
                    // Recover a completed write whose metadata was lost, without rewriting it.
                    if (node is VfsNode.File && node.size == bytes &&
                        storage.getAbsoluteFile(project, node.relativePath).readText(Charsets.UTF_8) == markdown) return node
                    continue
                }
                if (temporary == null) {
                    val result = storage.createNamedFile(project, folder.relativePath, ".kern-notes-${UUID.randomUUID()}.tmp")
                    if (result !is MutationOutcome.Success) throw MutationOutcomeException(result)
                    temporary = result.actual.node.relativePath
                    // Use the confirmed, exclusively created file; never replay a failed write
                    // into StorageManager's compatibility fallback directory.
                    val file = File(result.actual.absolutePath ?: throw IOException("Release note identity is missing"))
                    writeMarkdown(file, markdown)
                    if (file.readText(Charsets.UTF_8) != markdown) throw IOException("Release notes write was not confirmed")
                }
                when (val result = storage.renameNamedNode(project, temporary, leaf)) {
                    is MutationOutcome.Success -> {
                        temporary = null
                        return result.actual.node
                    }
                    is MutationOutcome.Failed -> if (result.reason != MutationFailure.Collision) throw MutationOutcomeException(result)
                    is MutationOutcome.ReconciliationRequired -> throw MutationOutcomeException(result)
                }
            }
            throw IOException("No available release note filename")
        } finally {
            temporary?.let { path ->
                withContext(NonCancellable) { storage.deletePermanentlyOutcome(project, path) }
            }
        }
    }

    private fun indexIfMissing(project: ProjectEntity, node: VfsNode) {
        if (db.fileDao().getFileByPath(project.id, node.relativePath) == null) {
            db.fileDao().insertFile(FileEntity(projectId = project.id, name = node.name,
                relativePath = node.relativePath, isDirectory = node.isDirectory,
                lastModified = (node as? VfsNode.File)?.lastModified ?: System.currentTimeMillis(),
                syncState = if (node.isDirectory) "SYNCED" else "PENDING"))
        }
    }

    companion object {
        const val ASSET_DIRECTORY = "release-notes"
        private val installMutex = Mutex()

        fun currentVersion(context: Context): ReleaseNotesVersion {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return ReleaseNotesVersion(info.versionName ?: "unknown", PackageInfoCompat.getLongVersionCode(info))
        }

        fun currentMarkdown(storage: StorageManager, version: ReleaseNotesVersion): String = try {
            storage.readAssetFile("$ASSET_DIRECTORY/${version.fileName}")
        } catch (_: IOException) {
            // Local builds can use the authored asset without reserving distribution numbers.
            val body = storage.readAssetFile("$ASSET_DIRECTORY/current.md").trim()
            if (body.isBlank()) throw IOException("Bundled release notes are empty")
            "${version.heading}\n\n$body\n"
        }
    }
}
