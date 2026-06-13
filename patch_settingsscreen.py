import re

with open("app/src/main/java/com/attachdesign/kern/ui/settings/SettingsScreen.kt", "r") as f:
    content = f.read()

# Add collectAsState for editor_font_size_scale
content = content.replace(
    "    val editorFontSetting   by db.settingDao().getSettingFlow(\"editor_font_family\").collectAsState(initial = null)\n",
    "    val editorFontSetting   by db.settingDao().getSettingFlow(\"editor_font_family\").collectAsState(initial = null)\n    val editorFontSizeScaleSetting by db.settingDao().getSettingFlow(\"editor_font_size_scale\").collectAsState(initial = null)\n"
)

content = content.replace(
    "    val currentFontFamily   = editorFontSetting?.value ?: \"serif\"\n",
    "    val currentFontFamily   = editorFontSetting?.value ?: \"serif\"\n    val currentFontSizeScale = editorFontSizeScaleSetting?.value?.toFloatOrNull() ?: 1.0f\n"
)


# Add Font Size option under Editor Font
old_fonts_block = """                    }
                }
                1 -> { // Behavior tab"""

new_fonts_block = """                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(thickness = 1.dp, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))

                    Text("Editor Font Size", color = theme.textPrimary, fontSize = 14.sp, fontFamily = appFont, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val fontSizes = listOf(
                        0.8f to "Small",
                        1.0f to "Medium",
                        1.2f to "Large",
                        1.4f to "Extra Large"
                    )
                    fontSizes.forEach { (scale, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        db.settingDao().insertSetting(SettingEntity("editor_font_size_scale", scale.toString()))
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = theme.textPrimary, fontSize = 13.sp, fontFamily = appFont)
                            RadioButton(
                                selected = currentFontSizeScale == scale,
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        db.settingDao().insertSetting(SettingEntity("editor_font_size_scale", scale.toString()))
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = theme.accent)
                            )
                        }
                    }
                }
                1 -> { // Behavior tab"""

content = content.replace(old_fonts_block, new_fonts_block)


with open("app/src/main/java/com/attachdesign/kern/ui/settings/SettingsScreen.kt", "w") as f:
    f.write(content)
