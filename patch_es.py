es_path = 'app/src/main/java/com/attachdesign/kern/ui/editor/EditorScreen.kt'
with open(es_path, 'r') as f:
    content = f.read()

# Add import
if "import com.attachdesign.kern.ui.theme.appFontFamily" not in content:
    content = content.replace("import com.attachdesign.kern.ui.theme.AppColorTheme", "import com.attachdesign.kern.ui.theme.AppColorTheme\nimport com.attachdesign.kern.ui.theme.appFontFamily")

block1 = """    val editorFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }"""

content = content.replace(block1, "    val editorFont = theme.appFontFamily")

with open(es_path, 'w') as f:
    f.write(content)
