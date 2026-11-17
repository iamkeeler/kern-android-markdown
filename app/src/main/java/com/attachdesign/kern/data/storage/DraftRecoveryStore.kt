package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.ProjectEntity
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecoveryDraft(
    val text: String,
    val baseText: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val fileId: Long? = null
)

/** Private recovery files are independent of destination folder access. Call from IO. */
class DraftRecoveryStore(private val directory: File) {
    fun read(project: ProjectEntity, path: String): RecoveryDraft? {
        val file = file(project, path)
        return if (file.exists()) Json.decodeFromString<RecoveryDraft>(file.readText()) else null
    }

    fun write(project: ProjectEntity, path: String, draft: RecoveryDraft) {
        file(project, path).writeTextAtomically(Json.encodeToString(draft))
    }

    fun remove(project: ProjectEntity, path: String) {
        val file = file(project, path)
        if (file.exists() && !file.delete()) throw java.io.IOException("Cannot remove recovery draft")
    }

    private fun file(project: ProjectEntity, path: String): File {
        val identity = "${project.id}\u0000${project.path}\u0000$path"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
}
