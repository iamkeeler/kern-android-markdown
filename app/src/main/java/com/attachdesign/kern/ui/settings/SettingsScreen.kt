package com.attachdesign.kern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.ThemeEntity
import com.attachdesign.kern.data.stats.StatsRepository
import com.attachdesign.kern.domain.stats.UserStats
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.ui.theme.AppThemeJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
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
    val uriHandler = LocalUriHandler.current

    // Observe global settings reactively
    val viewModeSetting     by db.settingDao().getSettingFlow("view_mode").collectAsState(initial = null)
    val editorFontSetting   by db.settingDao().getSettingFlow("editor_font_family").collectAsState(initial = null)
    val editorFontSizeScaleSetting by db.settingDao().getSettingFlow("editor_font_size_scale").collectAsState(initial = null)
    val stickySetting       by db.settingDao().getSettingFlow("sticky_selection").collectAsState(initial = null)
    val launchNewFileSetting by db.settingDao().getSettingFlow("launch_new_file").collectAsState(initial = null)
    val sentenceCapitalizationSetting by db.settingDao().getSettingFlow("sentence_capitalization").collectAsState(initial = null)
    val showWorkspaceIntroSetting by db.settingDao().getSettingFlow("show_workspace_intro").collectAsState(initial = null)

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
    val currentShowWorkspaceIntro  = showWorkspaceIntroSetting?.value?.toBoolean() ?: true

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
            val tabs = listOf("Appearance", "Behavior", "Stats", "Sync", "About")
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
                0 -> { // Appearance tab
                    Text(
                        text = "THEMES",
                        color = theme.textMuted,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
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

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text("VIEW MODE", color = theme.textMuted, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
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

                    Text("EDITOR FONT", color = theme.textMuted, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
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

                    var sliderScale by remember(currentFontSizeScale) { mutableStateOf(currentFontSizeScale) }
                    
                    val sizeLabel = when {
                        sliderScale <= 0.8f -> "Small"
                        sliderScale <= 1.0f -> "Medium"
                        sliderScale <= 1.2f -> "Large"
                        else -> "Extra Large"
                    }
                    
                    Text(
                        text = "EDITOR FONT SIZE: ${sizeLabel.uppercase()}",
                        color = theme.textMuted,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))
                    Slider(
                        value = sliderScale,
                        onValueChange = { sliderScale = it },
                        onValueChangeFinished = {
                            coroutineScope.launch(Dispatchers.IO) {
                                db.settingDao().insertSetting(SettingEntity("editor_font_size_scale", sliderScale.toString()))
                            }
                        },
                        valueRange = 0.8f..1.4f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.accent,
                            activeTrackColor = theme.accent,
                            inactiveTrackColor = theme.textMuted.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = theme.dimensions.spacingSmall),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Small", color = theme.textMuted, fontSize = theme.typography.tiny)
                        Text("Medium", color = theme.textMuted, fontSize = theme.typography.tiny)
                        Text("Large", color = theme.textMuted, fontSize = theme.typography.tiny)
                        Text("Extra Large", color = theme.textMuted, fontSize = theme.typography.tiny)
                    }

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text(
                        text = "CUSTOM THEME",
                        color = theme.textMuted,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
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
                            Toast.makeText(context, "Theme copied", Toast.LENGTH_SHORT).show()
                        },
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingLarge))

                    var inputThemeJson by remember { mutableStateOf("") }
                    val isJsonValid = remember(inputThemeJson) {
                        if (inputThemeJson.isBlank()) true else ThemeEngine.deserialize(inputThemeJson) != null
                    }
                    
                    OutlinedTextField(
                        value = inputThemeJson,
                        onValueChange = { inputThemeJson = it },
                        label = { Text("Paste Theme JSON", fontSize = theme.typography.small, color = theme.textMuted) },
                        isError = !isJsonValid,
                        supportingText = {
                            if (!isJsonValid) {
                                Text("Invalid Theme JSON format", color = Color(0xFFE53935))
                            }
                        },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = theme.typography.small, color = theme.textPrimary),
                        modifier = Modifier.fillMaxWidth().height(theme.dimensions.themeInputHeight),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor   = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            errorTextColor = theme.textPrimary,
                            focusedBorderColor   = theme.accent,
                            unfocusedBorderColor = theme.textMuted.copy(alpha = 0.5f),
                            errorBorderColor = Color(0xFFE53935),
                            focusedLabelColor   = theme.accent,
                            unfocusedLabelColor = theme.textMuted,
                            errorLabelColor = Color(0xFFE53935),
                            errorSupportingTextColor = Color(0xFFE53935),
                            cursorColor = theme.accent
                        )
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingMedium))

                    MinimalOutlinedButton(
                        text = "Import Custom Theme",
                        onClick = {
                            if (inputThemeJson.isNotBlank() && isJsonValid) {
                                val themeJson = ThemeEngine.deserialize(inputThemeJson)
                                if (themeJson != null) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val id = db.themeDao().insertTheme(ThemeEntity(name = themeJson.name, jsonString = inputThemeJson))
                                        db.settingDao().insertSetting(SettingEntity("selected_theme_id", id.toString()))
                                    }
                                    Toast.makeText(context, "Theme applied", Toast.LENGTH_SHORT).show()
                                    inputThemeJson = ""
                                }
                            } else if (inputThemeJson.isNotBlank() && !isJsonValid) {
                                Toast.makeText(context, "Invalid theme JSON", Toast.LENGTH_SHORT).show()
                            }
                        },
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> { // Behavior tab
                    Text(
                        text = "EDITOR BEHAVIOR",
                        color = theme.textMuted,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))

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
                                  text = "Maintain text selection after applying formatting.",
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
                                  text = "Open the editor automatically when creating a new file.",
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

                      Text(
                          text = "TYPING & AUTOMATION",
                          color = theme.textMuted,
                          fontSize = theme.typography.tiny,
                          fontFamily = FontFamily.Monospace,
                          fontWeight = FontWeight.Bold,
                          letterSpacing = 1.2.sp
                      )
                      Spacer(Modifier.height(theme.dimensions.spacingSmall))

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
                                  text = "Insert a space after header symbols automatically (e.g. # Header).",
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
                              .clickable {
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("show_workspace_intro", (!currentShowWorkspaceIntro).toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Workspace Intro Dialog",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Show confirmation prompt before adding watched folders.",
                                  color = theme.textMuted,
                                  fontSize = theme.typography.tiny,
                                  lineHeight = theme.typography.bodyLarge
                              )
                          }
                          Switch(
                              checked = currentShowWorkspaceIntro,
                              onCheckedChange = { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("show_workspace_intro", value.toString()))
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
                                      db.settingDao().insertSetting(SettingEntity("auto_complete_enabled", (!currentAutoComplete).toString()))
                                  }
                              }
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
                          Switch(
                              checked = currentAutoComplete,
                              onCheckedChange = { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity("auto_complete_enabled", value.toString()))
                                  }
                              },
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.accent,
                                  checkedTrackColor = theme.accent.copy(alpha = 0.5f)
                              )
                          )
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
                2 -> { // Stats tab
                    StatsTabContent(db = db, theme = theme, appFont = appFont)
                }
                3 -> { // Sync tab
                     Text("CLOUD SYNC", color = theme.textMuted, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
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
                4 -> { // About tab
                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingMedium),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Kern", color = theme.textPrimary, fontSize = theme.typography.bodyLarge,
                             fontFamily = appFont, fontWeight = FontWeight.Medium)
                          val appVersion = remember {
                              try {
                                  val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                  "Version ${packageInfo.versionName ?: "1.0"}"
                              } catch (e: Exception) {
                                  "Version 1.0"
                              }
                          }
                          Text(appVersion, color = theme.textMuted, fontSize = theme.typography.tiny,
                              fontFamily = FontFamily.Monospace)
                     }
                      Row(
                          modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingSmall),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                      ) {
                          Text("Design & Development", color = theme.textMuted, fontSize = theme.typography.small)
                          Text("Attach.design", color = theme.accent, fontSize = theme.typography.body,
                              fontFamily = appFont, fontWeight = FontWeight.Medium,
                              modifier = Modifier.clickable {
                                  try {
                                      uriHandler.openUri("https://kern.attach.design")
                                  } catch (e: Exception) {
                                      e.printStackTrace()
                                  }
                              })
                      }
                      Row(
                          modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingSmall),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                      ) {
                          Text("Privacy Policy", color = theme.textMuted, fontSize = theme.typography.small)
                          Text("Read Policy", color = theme.accent, fontSize = theme.typography.body,
                              fontFamily = appFont, fontWeight = FontWeight.Medium,
                              modifier = Modifier.clickable {
                                  try {
                                      uriHandler.openUri("https://kern.attach.design/privacy.html")
                                  } catch (e: Exception) {
                                      e.printStackTrace()
                                  }
                              })
                      }

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                     
                     Text(
                         text = "A modern, typography-focused Markdown editor built for distraction-free writing, local file privacy, and rich document analytics.",
                         color = theme.textMuted,
                         fontSize = theme.typography.body,
                         fontFamily = appFont,
                         lineHeight = theme.typography.title,
                         modifier = Modifier.padding(vertical = theme.dimensions.spacingSmall)
                     )

                     Spacer(Modifier.height(theme.dimensions.spacingSmall))

                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingSmall),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Design & Development", color = theme.textMuted, fontSize = theme.typography.small)
                         Text("Attach.design", color = theme.accent, fontSize = theme.typography.body,
                             fontFamily = appFont, fontWeight = FontWeight.Medium,
                             modifier = Modifier.clickable {
                                 try {
                                     uriHandler.openUri("https://kern.attach.design")
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                 }
                             })
                     }

                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingSmall),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Source Code", color = theme.textMuted, fontSize = theme.typography.small)
                         Text("GitHub Repository", color = theme.accent, fontSize = theme.typography.body,
                             fontFamily = appFont, fontWeight = FontWeight.Medium,
                             modifier = Modifier.clickable {
                                 try {
                                     uriHandler.openUri("https://github.com/iamkeeler/kern-android-markdown")
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                 }
                             })
                     }

                     Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingSmall),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text("Privacy Policy", color = theme.textMuted, fontSize = theme.typography.small)
                         Text("Read Policy", color = theme.accent, fontSize = theme.typography.body,
                             fontFamily = appFont, fontWeight = FontWeight.Medium,
                             modifier = Modifier.clickable {
                                 try {
                                     uriHandler.openUri("https://kern.attach.design/privacy.html")
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                 }
                             })
                     }

                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                     HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                     Text(
                         text = "OPEN SOURCE LIBRARIES",
                         color = theme.textMuted,
                         fontSize = theme.typography.tiny,
                         fontFamily = FontFamily.Monospace,
                         fontWeight = FontWeight.Bold,
                         letterSpacing = 1.2.sp
                     )
                     Spacer(Modifier.height(theme.dimensions.elevationMedium))

                     val libraries = listOf(
                         "Jetpack Compose"                to "Apache 2.0 · Google",
                         "Compose Material 3"             to "Apache 2.0 · Google",
                         "AndroidX Navigation 3"          to "Apache 2.0 · Google",
                         "Room Persistence Library"       to "Apache 2.0 · Google",
                         "AndroidX Lifecycle & ViewModel" to "Apache 2.0 · Google",
                         "Firebase Analytics"             to "Apache 2.0 · Google",
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

     var isVisible by remember { mutableStateOf(false) }
     LaunchedEffect(Unit) { isVisible = true }

     androidx.compose.animation.AnimatedVisibility(
         visible = isVisible,
         enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(50)),
         exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(50)),
         modifier = modifier.fillMaxSize().background(theme.background)
     ) {
         Column(
             modifier = Modifier
                 .fillMaxSize()
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

@Composable
fun StatsTabContent(
    db: AppDatabase,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    appFont: FontFamily
) {
    val coroutineScope = rememberCoroutineScope()
    val statsRepository = remember(db) { StatsRepository(db) }
    val userStats by statsRepository.getStatsFlow().collectAsState(initial = UserStats())
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = theme.dimensions.spacingSmall)
    ) {
        // WRITING METRICS
        Text(
            text = "WRITING METRICS",
            color = theme.textMuted,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        Row(modifier = Modifier.fillMaxWidth()) {
            MetricStatTile(
                label = "Words Written",
                value = UserStats.formatNumber(userStats.wordsWritten),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(theme.dimensions.spacingLarge))
            MetricStatTile(
                label = "Characters Written",
                value = UserStats.formatNumber(userStats.charactersWritten),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        // READING METRICS
        Text(
            text = "READING METRICS",
            color = theme.textMuted,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        Row(modifier = Modifier.fillMaxWidth()) {
            MetricStatTile(
                label = "Words Read",
                value = UserStats.formatNumber(userStats.wordsRead),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(theme.dimensions.spacingLarge))
            MetricStatTile(
                label = "Est. Reading Time",
                value = UserStats.formatReadingTime(userStats.estimatedReadingTimeMinutes),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        // ACTIVITY METRICS
        Text(
            text = "ACTIVITY METRICS",
            color = theme.textMuted,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        Row(modifier = Modifier.fillMaxWidth()) {
            MetricStatTile(
                label = "Documents Opened",
                value = UserStats.formatNumber(userStats.documentsOpened),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(theme.dimensions.spacingLarge))
            MetricStatTile(
                label = "Times Shared",
                value = UserStats.formatNumber(userStats.timesShared),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        // WORKSPACE TOTALS
        Text(
            text = "WORKSPACE TOTALS",
            color = theme.textMuted,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        Row(modifier = Modifier.fillMaxWidth()) {
            MetricStatTile(
                label = "Indexed Files",
                value = UserStats.formatNumber(userStats.totalIndexedFiles),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(theme.dimensions.spacingLarge))
            MetricStatTile(
                label = "Workspace Words",
                value = UserStats.formatNumber(userStats.totalIndexedWords),
                theme = theme,
                appFont = appFont,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge * 2))

        // Reset Button
        OutlinedButton(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(theme.dimensions.borderWidth, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Text("Reset Statistics", fontFamily = appFont, fontSize = theme.typography.body)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Statistics", fontFamily = appFont) },
            text = { Text("Are you sure you want to reset all tracked writing, reading, and document activity statistics?", fontFamily = appFont) },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            statsRepository.resetAllStats()
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error, fontFamily = appFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", fontFamily = appFont)
                }
            }
        )
    }
}

@Composable
private fun MetricStatTile(
    label: String,
    value: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    appFont: FontFamily,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(theme.textMuted.copy(alpha = 0.05f), RoundedCornerShape(theme.dimensions.spacingSmall))
            .padding(theme.dimensions.spacingLarge)
    ) {
        Text(
            text = value,
            fontSize = 26.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = theme.accent
        )
        Spacer(Modifier.height(theme.dimensions.spacingTiny))
        Text(
            text = label,
            fontSize = theme.typography.tiny,
            fontFamily = appFont,
            color = theme.textMuted
        )
    }
}

