import java.io.File

fun processFile(file: File) {
    if (file.isDirectory) {
        file.listFiles()?.forEach { processFile(it) }
    } else if (file.extension == "kt") {
        var content = file.readText()
        if (content.contains("val theme = uiState.activeTheme")) {
            println("Found theme var in \${file.name}, this was a leftover from a previous edit.")
        }
    }
}

val dir = File("app/src/main/java/com/attachdesign/kern/ui")
processFile(dir)
