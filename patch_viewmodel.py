import re

with open("app/src/main/java/com/attachdesign/kern/ui/editor/EditorViewModel.kt", "r") as f:
    content = f.read()

# Add to EditorUiState
content = content.replace(
    "    val autoCompleteBrackets: Boolean = true,\n",
    "    val autoCompleteBrackets: Boolean = true,\n    val editorFontSizeScale: Float = 1.0f,\n"
)

# Add collection to init block
new_collect = """        viewModelScope.launch(Dispatchers.IO) {
            db.settingDao().getSettingFlow("editor_font_size_scale").collect { setting ->
                val scale = setting?.value?.toFloatOrNull() ?: 1.0f
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(editorFontSizeScale = scale)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {"""

content = content.replace("        viewModelScope.launch(Dispatchers.IO) {", new_collect, 1)

# Add to loadSettings
content = content.replace(
    "val autoCompleteBracketsSetting = db.settingDao().getSetting(\"auto_complete_brackets\")?.value ?: \"true\"\n",
    "val autoCompleteBracketsSetting = db.settingDao().getSetting(\"auto_complete_brackets\")?.value ?: \"true\"\n        val editorFontSizeScaleSetting = db.settingDao().getSetting(\"editor_font_size_scale\")?.value ?: \"1.0\"\n"
)

content = content.replace(
    "autoCompleteBrackets = autoCompleteBracketsSetting.toBoolean(),\n",
    "autoCompleteBrackets = autoCompleteBracketsSetting.toBoolean(),\n                editorFontSizeScale = editorFontSizeScaleSetting.toFloatOrNull() ?: 1.0f,\n"
)

with open("app/src/main/java/com/attachdesign/kern/ui/editor/EditorViewModel.kt", "w") as f:
    f.write(content)
