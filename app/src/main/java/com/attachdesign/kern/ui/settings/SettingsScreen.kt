package com.attachdesign.kern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
    val launchNewFileSetting by db.settingDao().getSettingFlow("launch_new_file").collectAsState(initial = null)
    val sentenceCapitalizationSetting by db.settingDao().getSettingFlow("sentence_capitalization").collectAsState(initial = null)

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
    val currentLaunchNewFile       = launchNewFileSetting?.value?.toBoolean() ?: true
    val currentSentenceCapitalization = sentenceCapitalizationSetting?.value?.toBoolean() ?: true

    val currentAutoHeader          = autoHeaderSetting?.value?.toBoolean() ?: true
    val currentAutoComplete        = autoCompleteSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteQuotes  = autoCompleteQuotesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteSingleQuotes = autoCompleteSingleQuotesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteBraces  = autoCompleteBracesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteParens  = autoCompleteParensSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteBrackets = autoCompleteBracketsSetting?.value?.toBoolean() ?: true

    val appFont = when (theme.editorFontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    var activeTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tabs Selector
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = theme.accent,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = theme.accent
                )
            },
            divider = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            val tabs = listOf("Visuals", "Behavior", "Sync", "About")
            tabs.forEachIndexed { idx, tabName ->
                val selected = activeTab == idx
                Tab(
                    selected = selected,
                    onClick = { activeTab = idx },
                    text = {
                        Text(
                            text = tabName,
                            fontSize = theme.typography.body,
                            fontFamily = appFont,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selectedContentColor = theme.accent,
                    unselectedContentColor = theme.textMuted
                )
            }
        }

        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (activeTab) {
                0 -> { // Visuals tab
                    Text(
                        text = "Presets",
                        color = theme.textMuted,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (theme.typography.tiny.value * 0.045f).sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))

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
                                .padding(vertical = theme.dimensions.spacingLarge),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Text(name, color = theme.textPrimary, fontSize = theme.typography.body, fontFamily = appFont)
                            val isCurrent = theme.name == presetJson.name
                            if (isCurrent) {
                                Text("Active", color = theme.accent, fontSize = theme.typography.tiny,
                                    fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(Modifier.height(theme.dimensions.spacingLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingLarge))

                    Text(
                        text = "Custom",
                        color = theme.textMuted,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (theme.typography.tiny.value * 0.045f).sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingMedium))

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
                    Spacer(Modifier.height(theme.dimensions.spacingLarge))

                    var inputThemeJson by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = inputThemeJson,
                        onValueChange = { inputThemeJson = it },
                        label = { Text("Paste Theme JSON", fontSize = theme.typography.small, color = theme.textMuted) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = theme.typography.small, color = theme.textPrimary),
                        modifier = Modifier.fillMaxWidth().height(theme.dimensions.themeInputHeight),
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
                    Spacer(Modifier.height(theme.dimensions.spacingMedium))

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

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text("View Mode", color = theme.textPrimary, fontSize = theme.typography.bodyLarge, fontFamily = appFont, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))
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
                                .padding(vertical = theme.dimensions.spacingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = theme.textPrimary, fontSize = theme.typography.body, fontFamily = appFont)
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

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text("Editor Font", color = theme.textPrimary, fontSize = theme.typography.bodyLarge, fontFamily = appFont, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))
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
                                .padding(vertical = theme.dimensions.spacingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = theme.textPrimary, fontSize = theme.typography.body, fontFamily = appFont)
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

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text("Editor Font Size", color = theme.textPrimary, fontSize = theme.typography.bodyLarge, fontFamily = appFont, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))
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
                                .padding(vertical = theme.dimensions.spacingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = theme.textPrimary, fontSize = theme.typography.body, fontFamily = appFont)
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
                            .padding(vertical = theme.dimensions.spacingMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                              Text(
                                  text = "Sticky Selection",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Keep selections active when applying toolbar formatting.",
                                  color = theme.textMuted,
                                  fontSize = theme.typography.tiny,
                                  lineHeight = theme.typography.bodyLarge
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

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .clickable {
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("launch_new_file", (!currentLaunchNewFile).toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Launch New File",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Automatically open the editor when a new file is created.",
                                  color = theme.textMuted,
                                  fontSize = theme.typography.tiny,
                                  lineHeight = theme.typography.bodyLarge
                              )
                          }
                          Switch(
                              checked = currentLaunchNewFile,
                              onCheckedChange = { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("launch_new_file", value.toString()))
                                  }
                              },
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.accent,
                                  checkedTrackColor = theme.accent.copy(alpha = 0.5f)
                              )
                          )
                      }

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .clickable {
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("auto_header_spacing", (!currentAutoHeader).toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Auto Header Spacing",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "#Text becomes # Text automatically.",
                                  color = theme.textMuted,
                                  fontSize = theme.typography.tiny,
                                  lineHeight = theme.typography.bodyLarge
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

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))


                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .clickable {
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("sentence_capitalization", (!currentSentenceCapitalization).toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Sentence Capitalization",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Automatically capitalize the first letter of sentences.",
                                  color = theme.textMuted,
                                  fontSize = theme.typography.tiny,
                                  lineHeight = theme.typography.bodyLarge
                              )
                          }
                          Switch(
                              checked = currentSentenceCapitalization,
                              onCheckedChange = { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("sentence_capitalization", value.toString()))
                                  }
                              },
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.accent,
                                  checkedTrackColor = theme.accent.copy(alpha = 0.5f)
                              )
                          )
                      }

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Auto Complete",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Auto-pair quotes, brackets, and braces.",
                                  color = theme.textMuted,
                                  fontSize = theme.typography.tiny,
                                  lineHeight = theme.typography.bodyLarge
                              )
                          }
                          Row(horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium)) {
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
                          Spacer(Modifier.height(theme.dimensions.spacingMedium))
                          Row(
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .padding(bottom = theme.dimensions.spacingLarge),
                              horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium)
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
                2 -> { // Sync tab
                     Text("Cloud Sync", color = theme.textPrimary, fontSize = theme.typography.bodyLarge, fontFamily = appFont, fontWeight = FontWeight.Bold)
                     Spacer(Modifier.height(theme.dimensions.spacingMedium))
                     Text(
                         "We are actively working on adding cloud synchronization features so you can access your documents anywhere. Stay tuned for updates!",
                         color = theme.textMuted,
                         fontSize = theme.typography.body,
                         fontFamily = appFont,
                         lineHeight = theme.typography.title
                     )
                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                }
                3 -> { // About tab
                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingMedium),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Kern", color = theme.textPrimary, fontSize = theme.typography.bodyLarge,
                             fontFamily = appFont, fontWeight = FontWeight.Medium)
                         Text("Version 1.0", color = theme.textMuted, fontSize = theme.typography.tiny,
                             fontFamily = FontFamily.Monospace)
                     }
                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingSmall),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Design & Development", color = theme.textMuted, fontSize = theme.typography.small)
                         Text("Attach.design", color = theme.textPrimary, fontSize = theme.typography.body,
                             fontFamily = appFont, fontWeight = FontWeight.Medium)
                     }

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text(
                        text = "Open Source Libraries",
                        color = theme.textMuted,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (theme.typography.tiny.value * 0.045f).sp
                    )
                    Spacer(Modifier.height(theme.dimensions.elevationMedium))

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
                             modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.elevationMedium),
                             horizontalArrangement = Arrangement.SpaceBetween,
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             Text(lib, color = theme.textPrimary, fontSize = theme.typography.body,
                                 fontFamily = appFont, modifier = Modifier.weight(1f))
                            Text(license, color = theme.textMuted, fontSize = theme.typography.tiny,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(Modifier.height(theme.dimensions.spacingMassive))
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

     val appFont = when (theme.editorFontFamily.lowercase()) {
         "serif" -> FontFamily.Serif
         "sans-serif", "sansserif" -> FontFamily.SansSerif
         "monospace" -> FontFamily.Monospace
         else -> FontFamily.Default
     }

     Column(
         modifier = modifier
             .fillMaxSize()
             .background(theme.background)
             .safeDrawingPadding()
             .padding(horizontal = theme.dimensions.spacingHuge, vertical = theme.dimensions.spacingExtraLarge)
     ) {
         // ── Page title row ────────────────────────────────────────────────────
         Row(
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(top = theme.dimensions.spacingMedium, bottom = theme.dimensions.spacingMedium),
             verticalAlignment = Alignment.CenterVertically,
             horizontalArrangement = Arrangement.SpaceBetween
         ) {
             Text(
                 text = "Settings",
                 fontSize = theme.typography.h1,
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

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        // Shared settings component
        SettingsTabsContent(
            db = db,
            theme = theme,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MinimalOutlinedButton(
    text: String,
    onClick: () -> Unit,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
     val appFont = when (theme.editorFontFamily.lowercase()) {
         "serif" -> FontFamily.Serif
         "sans-serif", "sansserif" -> FontFamily.SansSerif
         "monospace" -> FontFamily.Monospace
         else -> FontFamily.Default
     }
    val bgColor = if (isPrimary) theme.accent else Color.Transparent
    val textColor = if (isPrimary) theme.background else theme.textPrimary
    val borderColor = if (isPrimary) theme.accent else theme.textMuted.copy(alpha = 0.3f)

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(theme.dimensions.spacingSmall),
        border = BorderStroke(theme.dimensions.borderWidth, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = bgColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(horizontal = theme.dimensions.spacingExtraLarge, vertical = theme.dimensions.spacingMedium)
    ) {
        Text(
            text = text,
            fontSize = theme.typography.bodyLarge,
            fontFamily = appFont,
            fontWeight = if (isPrimary) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun MinimalOutlinedButtonSmall(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    modifier: Modifier = Modifier
) {
    val border    = if (selected) theme.accent else theme.textMuted.copy(alpha = 0.3f)
    val bg        = if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (selected) theme.accent else theme.textMuted

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        shape = RoundedCornerShape(theme.dimensions.spacingSmall),
        border = BorderStroke(theme.dimensions.borderWidth, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = bg,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(horizontal = theme.dimensions.spacingLarge, vertical = theme.dimensions.elevationMedium)
    ) {
        Text(
            text = text,
            fontSize = theme.typography.small,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
