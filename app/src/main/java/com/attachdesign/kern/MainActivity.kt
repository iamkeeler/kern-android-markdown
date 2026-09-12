package com.attachdesign.kern

import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.MIGRATION_1_2
import com.attachdesign.kern.data.local.MIGRATION_2_3
import com.attachdesign.kern.data.local.MIGRATION_3_4
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.analytics.TelemetryCollection
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.IncomingFileImportPolicy
import com.attachdesign.kern.theme.ModernAndroidMarkdownEditorTheme
import com.attachdesign.kern.theme.Typography
import com.attachdesign.kern.ui.theme.ApplyKernSystemBars
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.ui.theme.toColorScheme

class MainActivity : ComponentActivity() {

  private lateinit var db: AppDatabase
  private lateinit var storageManager: StorageManager
  private var externalOpenRequestCounter = 0L
  private var externalOpenRequest by mutableStateOf<ExternalOpenRequest?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)


    db = Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java,
      "kern.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

    lifecycleScope.launch(Dispatchers.IO) {
      if (db.settingDao().getSetting("editor_font_family") == null) {
        db.settingDao().insertSetting(SettingEntity("editor_font_family", "serif"))
      }
      if (db.settingDao().getSetting("view_mode") == null) {
        db.settingDao().insertSetting(SettingEntity("view_mode", "RENDERED"))
      }
      if (db.settingDao().getSetting(TelemetryCollection.SETTING_KEY) == null) {
        db.settingDao().insertSetting(
          SettingEntity(TelemetryCollection.SETTING_KEY, TelemetryCollection.DEFAULT_ENABLED.toString())
        )
      }
    }

    lifecycleScope.launch(Dispatchers.IO) {
      db.settingDao().getSettingFlow(TelemetryCollection.SETTING_KEY).collect { setting ->
        val enabled = setting?.value?.toBooleanStrictOrNull() ?: TelemetryCollection.DEFAULT_ENABLED
        TelemetryCollection.setEnabled(enabled)
        runCatching { FirebaseAnalytics.getInstance(applicationContext).setAnalyticsCollectionEnabled(enabled) }
        runCatching { FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled) }
      }
    }

    storageManager = StorageManager(applicationContext)
    externalOpenRequest = intent.toExternalOpenRequest()

    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    enableEdgeToEdge()
    setContent {
      ModernAndroidMarkdownEditorTheme {
        // M14/M15: single app-root theme application — the active Kern theme drives
        // both the M3 ColorScheme and the system bars. Per-screen ApplyKernSystemBars
        // calls were removed to avoid races during transitions.
        val selectedThemeIdFlow = remember { db.settingDao().getSettingFlow("selected_theme_id") }
        val selectedThemeIdSetting by selectedThemeIdFlow.collectAsStateWithLifecycle(initialValue = null)
        var appTheme by remember { mutableStateOf(ThemeEngine.DefaultLight.toColorTheme()) }
        LaunchedEffect(selectedThemeIdSetting) {
          appTheme = withContext(Dispatchers.IO) {
            val themeId = selectedThemeIdSetting?.value?.toLongOrNull()
            var activeTheme = ThemeEngine.DefaultLight.toColorTheme()
            if (themeId != null) {
              val themeJson = db.themeDao().getThemeById(themeId)?.let { ThemeEngine.deserialize(it.jsonString) }
              if (themeJson != null) activeTheme = themeJson.toColorTheme()
            }
            activeTheme
          }
        }
        ApplyKernSystemBars(appTheme)
        MaterialTheme(
          colorScheme = appTheme.toColorScheme(),
          typography = Typography
        ) {
          Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            MainNavigation(
              db = db,
              storageManager = storageManager,
              externalOpenRequest = externalOpenRequest,
              onExternalOpenHandled = { handledRequest ->
                if (externalOpenRequest?.id == handledRequest.id) {
                  externalOpenRequest = null
                  clearExternalOpenIntent()
                }
              }
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    externalOpenRequest = intent.toExternalOpenRequest()
  }

  private fun Intent?.toExternalOpenRequest(): ExternalOpenRequest? {
    if (this?.action != Intent.ACTION_VIEW) return null
    val uri: Uri = data ?: return null
    if (!IncomingFileImportPolicy.isSupportedScheme(uri.scheme)) return null
    return ExternalOpenRequest(
      id = ++externalOpenRequestCounter,
      uriString = uri.toString(),
      mimeType = type
    )
  }

  private fun clearExternalOpenIntent() {
    if (intent?.action == Intent.ACTION_VIEW) {
      setIntent(Intent(this, MainActivity::class.java))
    }
  }
}
