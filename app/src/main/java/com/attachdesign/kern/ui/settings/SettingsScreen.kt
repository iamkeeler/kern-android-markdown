package com.attachdesign.kern.ui.settings

import com.attachdesign.kern.ui.theme.appFontFamily
import com.attachdesign.kern.ui.components.MinimalOutlinedButton
import com.attachdesign.kern.ui.components.MinimalOutlinedButtonSmall


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.ThemeEntity
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.ui.theme.AppThemeJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

@Composable
fun SettingsTabsContent(
    db: AppDatabase,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    modifier: Modifier = Modifier,

) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Observe global settings reactively
    val viewModeSetting     by db.settingDao().getSettingFlow("view_mode").collectAsState(initial = null)
    val editorFontSetting   by db.settingDao().getSettingFlow("editor_font_family").collectAsState(initial = null)
    val editorFontSizeScaleSetting by db.settingDao().getSettingFlow("editor_font_size_scale").collectAsState(initial = null)
    val stickySetting       by db.settingDao().getSettingFlow("sticky_selection").collectAsState(initial = null)

    val autoHeaderSetting   by db.settingDao().getSettingFlow("auto_header_spacing").collectAsState(initial = null)
    val autoCompleteSetting by db.settingDao().getSettingFlow("auto_complete_enabled").collectAsState(initial = null)
    val autoCompleteQuotesSetting       by db.settingDao().getSettingFlow("auto_complete_quotes").collectAsState(initial = null)
    val autoCompleteSingleQuotesSetting by db.settingDao().getSettingFlow("auto_complete_single_quotes").collectAsState(initial = null)
    val autoCompleteBracesSetting       by db.settingDao().getSettingFlow("auto_complete_braces").collectAsState(initial = null)
    val autoCompleteParensSetting       by db.settingDao().getSettingFlow("auto_complete_parens").collectAsState(initial = null)
    val autoCompleteBracketsSetting     by db.settingDao().getSettingFlow("auto_complete_brackets").collectAsState(initial = null)

    val currentViewMode            = viewModeSetting?.value ?: "RENDERED"
    val currentFontFamily          = editorFontSetting?.value ?: "serif"
    val currentFontSizeScale = editorFontSizeScaleSetting?.value?.toFloatOrNull() ?: 1.0f
    val currentSticky              = stickySetting?.value?.toBoolean() ?: true

    val currentAutoHeader          = autoHeaderSetting?.value?.toBoolean() ?: true
    val currentAutoComplete        = autoCompleteSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteQuotes  = autoCompleteQuotesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteSingleQuotes = autoCompleteSingleQuotesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteBraces  = autoCompleteBracesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteParens  = autoCompleteParensSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteBrackets = autoCompleteBracketsSetting?.value?.toBoolean() ?: true

    val appFont = theme.appFontFamily

    var activeTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tabs Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("Styles", "Behavior", "Themes", "Sync", "About")
            tabs.forEachIndexed { idx, tabName ->
                val selected = activeTab == idx
                Text(
                    text = tabName,
                    color = if (selected) theme.accent else theme.textMuted,
                    fontSize = 13.sp,
                    fontFamily = appFont,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { activeTab = idx }
                        .padding(vertical = 6.dp)
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = theme.textMuted.copy(alpha = 0.15f)
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (activeTab) {
                0 -> { // Styles tab
                    Text("View Mode", color = theme.textPrimary, fontSize = 14.sp, fontFamily = appFont, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val modes = listOf(
                        "RENDERED"           to "Live Preview",
                        "SYNTAX_HIGHLIGHTED" to "Syntax Highlighted",
                        "RAW_PLAIN_TEXT"     to "Raw Plain-Text"
                    )
                    modes.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        db.settingDao().insertSetting(SettingEntity("view_mode", mode))
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = theme.textPrimary, fontSize = 13.sp, fontFamily = appFont)
                            RadioButton(
                                selected = currentViewMode == mode,
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        db.settingDao().insertSetting(SettingEntity("view_mode", mode))
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = theme.accent)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(thickness = 1.dp, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))

                    Text("Editor Font", color = theme.textPrimary, fontSize = 14.sp, fontFamily = appFont, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val fonts = listOf(
                        "serif"      to "Serif (Book)",
                        "sans-serif" to "Sans-Serif (Modern)",
                        "monospace"  to "Monospace (Code)"
                    )
                    fonts.forEach { (font, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        db.settingDao().insertSetting(SettingEntity("editor_font_family", font))
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = theme.textPrimary, fontSize = 13.sp, fontFamily = appFont)
                            RadioButton(
                                selected = currentFontFamily == font,
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        db.settingDao().insertSetting(SettingEntity("editor_font_family", font))
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = theme.accent)
                            )
                        }
                    }

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
                1 -> { // Behavior tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.settingDao().insertSetting(SettingEntity("sticky_selection", (!currentSticky).toString()))
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                              Text(
                                  text = "Sticky Selection",
                                  color = theme.textPrimary,
                                  fontSize = 13.sp,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Keep selections active when applying toolbar formatting.",
                                  color = theme.textMuted,
                                  fontSize = 11.sp,
                                  lineHeight = 15.sp
                              )
                          }
                          Switch(
                              checked = currentSticky,
                              onCheckedChange = { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("sticky_selection", value.toString()))
                                  }
                              },
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.accent,
                                  checkedTrackColor = theme.accent.copy(alpha = 0.5f)
                              )
                          )
                      }

                      Spacer(Modifier.height(12.dp))
                      HorizontalDivider(thickness = 1.dp, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(12.dp))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .clickable {
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("auto_header_spacing", (!currentAutoHeader).toString()))
                                  }
                              }
                              .padding(vertical = 8.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                               Text(
                                  text = "Auto Header Spacing",
                                  color = theme.textPrimary,
                                  fontSize = 13.sp,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "#Text becomes # Text automatically.",
                                  color = theme.textMuted,
                                  fontSize = 11.sp,
                                  lineHeight = 15.sp
                              )
                          }
                          Switch(
                              checked = currentAutoHeader,
                              onCheckedChange = { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("auto_header_spacing", value.toString()))
                                  }
                              },
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.accent,
                                  checkedTrackColor = theme.accent.copy(alpha = 0.5f)
                              )
                          )
                      }

                      Spacer(Modifier.height(12.dp))
                      HorizontalDivider(thickness = 1.dp, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(12.dp))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .padding(vertical = 8.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                               Text(
                                  text = "Auto Complete",
                                  color = theme.textPrimary,
                                  fontSize = 13.sp,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Auto-pair quotes, brackets, and braces.",
                                  color = theme.textMuted,
                                  fontSize = 11.sp,
                                  lineHeight = 15.sp
                              )
                          }
                          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                              MinimalOutlinedButtonSmall(text = "Yes", selected = currentAutoComplete, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("auto_complete_enabled", "true"))
                                  }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "No", selected = !currentAutoComplete, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("auto_complete_enabled", "false"))
                                  }
                              }, theme = theme)
                          }
                      }

                      if (currentAutoComplete) {
                          Spacer(Modifier.height(8.dp))
                          Row(
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .padding(bottom = 10.dp),
                              horizontalArrangement = Arrangement.spacedBy(8.dp)
                          ) {
                              MinimalOutlinedButtonSmall(text = "\"\"", selected = currentAutoCompleteQuotes, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity("auto_complete_quotes", (!currentAutoCompleteQuotes).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "''", selected = currentAutoCompleteSingleQuotes, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity("auto_complete_single_quotes", (!currentAutoCompleteSingleQuotes).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "{}", selected = currentAutoCompleteBraces, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity("auto_complete_braces", (!currentAutoCompleteBraces).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "()", selected = currentAutoCompleteParens, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity("auto_complete_parens", (!currentAutoCompleteParens).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "[]", selected = currentAutoCompleteBrackets, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity("auto_complete_brackets", (!currentAutoCompleteBrackets).toString())) }
                              }, theme = theme)
                          }
                      }
                }
                2 -> { // Themes tab
                    Text(
                        text = "Presets",
                        color = theme.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(4.dp))

                    val presets = listOf(
                        "Cream & Charcoal (Default)" to ThemeEngine.DefaultLight,
                        "Inky Charcoal (Default)"   to ThemeEngine.DefaultDark
                    )
                    presets.forEach { (name, presetJson) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val jsonString = ThemeEngine.serialize(presetJson)
                                        val id = db.themeDao().insertTheme(ThemeEntity(name = presetJson.name, jsonString = jsonString))
                                        db.settingDao().insertSetting(SettingEntity("selected_theme_id", id.toString()))
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Text(name, color = theme.textPrimary, fontSize = 13.sp, fontFamily = appFont)
                            val isCurrent = theme.name == presetJson.name
                            if (isCurrent) {
                                Text("Active", color = theme.accent, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(thickness = 1.dp, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Custom",
                        color = theme.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    MinimalOutlinedButton(
                        text = "Export Current Theme",
                        onClick = {
                            val currentThemeJson = AppThemeJson(
                                name = theme.name,
                                isDark = theme.isDark,
                                backgroundHex   = String.format("#%06X", (0xFFFFFF and theme.background.value.toLong().toInt())),
                                surfaceHex      = String.format("#%06X", (0xFFFFFF and theme.surface.value.toLong().toInt())),
                                textPrimaryHex  = String.format("#%06X", (0xFFFFFF and theme.textPrimary.value.toLong().toInt())),
                                textMutedHex    = String.format("#%06X", (0xFFFFFF and theme.textMuted.value.toLong().toInt())),
                                accentHex       = String.format("#%06X", (0xFFFFFF and theme.accent.value.toLong().toInt())),
                                codeBackgroundHex = String.format("#%06X", (0xFFFFFF and theme.codeBackground.value.toLong().toInt())),
                                editorFontFamily = theme.editorFontFamily
                            )
                            val serialized = ThemeEngine.serialize(currentThemeJson)
                            clipboardManager.setText(AnnotatedString(serialized))
                            Toast.makeText(context, "Theme JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    var inputThemeJson by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = inputThemeJson,
                        onValueChange = { inputThemeJson = it },
                        label = { Text("Paste Theme JSON", fontSize = 12.sp, color = theme.textMuted) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = theme.textPrimary),
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor   = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedBorderColor   = theme.accent,
                            unfocusedBorderColor = theme.textMuted.copy(alpha = 0.5f),
                            focusedLabelColor   = theme.accent,
                            unfocusedLabelColor = theme.textMuted,
                            cursorColor = theme.accent
                        )
                    )
                    Spacer(Modifier.height(8.dp))

                    MinimalOutlinedButton(
                        text = "Import Custom Theme",
                        onClick = {
                            if (inputThemeJson.isNotBlank()) {
                                val themeJson = ThemeEngine.deserialize(inputThemeJson)
                                if (themeJson != null) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val id = db.themeDao().insertTheme(ThemeEntity(name = themeJson.name, jsonString = inputThemeJson))
                                        db.settingDao().insertSetting(SettingEntity("selected_theme_id", id.toString()))
                                    }
                                    Toast.makeText(context, "Custom theme applied successfully!", Toast.LENGTH_SHORT).show()
                                    inputThemeJson = ""
                                } else {
                                    Toast.makeText(context, "Invalid Theme JSON schema!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                3 -> { // Sync tab
                     Text("Cloud Sync", color = theme.textPrimary, fontSize = 14.sp, fontFamily = appFont, fontWeight = FontWeight.Bold)
                     Spacer(Modifier.height(8.dp))
                     Text(
                         "We are actively working on adding cloud synchronization features so you can access your documents anywhere. Stay tuned for updates!",
                         color = theme.textMuted,
                         fontSize = 13.sp,
                         fontFamily = appFont,
                         lineHeight = 18.sp
                     )
                     Spacer(Modifier.height(16.dp))
                }
                4 -> { // About tab
                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Kern", color = theme.textPrimary, fontSize = 14.sp,
                             fontFamily = appFont, fontWeight = FontWeight.Medium)
                         Text("Version 1.0", color = theme.textMuted, fontSize = 11.sp,
                             fontFamily = FontFamily.Monospace)
                     }
                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Design & Development", color = theme.textMuted, fontSize = 12.sp)
                         Text("Attach.design", color = theme.textPrimary, fontSize = 13.sp,
                             fontFamily = appFont, fontWeight = FontWeight.Medium)
                     }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(thickness = 1.dp, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Open Source Libraries",
                        color = theme.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(6.dp))

                    val libraries = listOf(
                        "Jetpack Compose"                to "Apache 2.0 · Google",
                        "Compose Material 3"             to "Apache 2.0 · Google",
                        "AndroidX Navigation 3"          to "Apache 2.0 · Google",
                        "Room Persistence Library"       to "Apache 2.0 · Google",
                        "AndroidX Lifecycle & ViewModel" to "Apache 2.0 · Google",
                        "KotlinX Serialization"          to "Apache 2.0 · JetBrains",
                        "KotlinX Collections Immutable"  to "Apache 2.0 · JetBrains",
                        "Kotlin"                         to "Apache 2.0 · JetBrains"
                    )
                    libraries.forEach { (lib, license) ->
                         Row(
                             modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                             horizontalArrangement = Arrangement.SpaceBetween,
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             Text(lib, color = theme.textPrimary, fontSize = 13.sp,
                                 fontFamily = appFont, modifier = Modifier.weight(1f))
                            Text(license, color = theme.textMuted, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    db: AppDatabase,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Observe global settings reactively (only theme is needed for status bar and background)
    val selectedThemeIdSetting by db.settingDao().getSettingFlow("selected_theme_id").collectAsState(initial = null)
    val editorFontSetting by db.settingDao().getSettingFlow("editor_font_family").collectAsState(initial = null)
    var theme by remember { mutableStateOf(ThemeEngine.DefaultLight.toColorTheme()) }

    LaunchedEffect(selectedThemeIdSetting, editorFontSetting) {
        withContext(Dispatchers.IO) {
            val themeId = selectedThemeIdSetting?.value?.toLongOrNull()
            val savedFont = editorFontSetting?.value ?: "serif"
            var activeTheme = ThemeEngine.DefaultLight.toColorTheme()
            if (themeId != null) {
                val dbTheme = db.themeDao().getThemeById(themeId)
                if (dbTheme != null) {
                    val themeJson = ThemeEngine.deserialize(dbTheme.jsonString)
                    if (themeJson != null) {
                        activeTheme = themeJson.toColorTheme()
                    }
                }
            }
            activeTheme = activeTheme.copy(editorFontFamily = savedFont)
            withContext(Dispatchers.Main) {
                theme = activeTheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !theme.isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !theme.isDark
            }
        }
    }

     val appFont = theme.appFontFamily

     Column(
         modifier = modifier
             .fillMaxSize()
             .background(theme.background)
             .safeDrawingPadding()
             .padding(horizontal = 24.dp, vertical = 16.dp)
     ) {
         // ── Page title row ────────────────────────────────────────────────────
         Row(
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(top = 8.dp, bottom = 8.dp),
             verticalAlignment = Alignment.CenterVertically,
             horizontalArrangement = Arrangement.SpaceBetween
         ) {
             Text(
                 text = "Settings",
                 fontSize = 28.sp,
                 fontFamily = appFont,
                 fontWeight = FontWeight.Light,
                 color = theme.textPrimary,
                 letterSpacing = (-0.5).sp
             )
            MinimalOutlinedButton(
                text = "Back",
                onClick = onBackClick,
                theme = theme
            )
        }

        Spacer(Modifier.height(16.dp))

        // Shared settings component
        SettingsTabsContent(
            db = db,
            theme = theme,
            modifier = Modifier.weight(1f)
        )
    }
}

