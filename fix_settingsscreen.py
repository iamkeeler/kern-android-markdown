import re

with open("app/src/main/java/com/attachdesign/kern/ui/settings/SettingsScreen.kt", "r") as f:
    content = f.read()

# Add editorFontSizeScaleSetting correctly
content = content.replace(
    "    val editorFontSetting   by db.settingDao().getSettingFlow(\"editor_font_family\").collectAsState(initial = null)\n",
    "    val editorFontSetting   by db.settingDao().getSettingFlow(\"editor_font_family\").collectAsState(initial = null)\n    val editorFontSizeScaleSetting by db.settingDao().getSettingFlow(\"editor_font_size_scale\").collectAsState(initial = null)\n"
)

# Add currentFontSizeScale correctly
content = content.replace(
    "    val currentFontFamily          = editorFontSetting?.value ?: \"serif\"\n",
    "    val currentFontFamily          = editorFontSetting?.value ?: \"serif\"\n    val currentFontSizeScale = editorFontSizeScaleSetting?.value?.toFloatOrNull() ?: 1.0f\n"
)

with open("app/src/main/java/com/attachdesign/kern/ui/settings/SettingsScreen.kt", "w") as f:
    f.write(content)
