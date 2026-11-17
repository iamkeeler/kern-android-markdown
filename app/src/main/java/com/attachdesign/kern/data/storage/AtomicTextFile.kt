package com.attachdesign.kern.data.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Commits complete UTF-8 content without truncating the previous file on failure. */
internal fun File.writeTextAtomically(text: String) {
    val parent = parentFile ?: throw java.io.IOException("Missing parent directory")
    if (!parent.isDirectory && !parent.mkdirs()) throw java.io.IOException("Cannot create parent directory")
    val temporary = File.createTempFile(".kern-", ".tmp", parent)
    try {
        FileOutputStream(temporary).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(temporary.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temporary.delete()
    }
}
