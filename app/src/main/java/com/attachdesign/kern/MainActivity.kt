package com.attachdesign.kern

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.theme.ModernAndroidMarkdownEditorTheme

class MainActivity : ComponentActivity() {
  private lateinit var db: AppDatabase
  private lateinit var storageManager: StorageManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    db = Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java,
      "kern.db"
    ).fallbackToDestructiveMigration()
      .build()

    storageManager = StorageManager(applicationContext)

    enableEdgeToEdge()
    setContent {
      val view = androidx.compose.ui.platform.LocalView.current
      val settingDao = db.settingDao()
      val themeDao = db.themeDao()
      val selectedThemeIdSetting = settingDao.getSettingFlow("selected_theme_id").collectAsState(initial = null)
      var isDark by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

      androidx.compose.runtime.LaunchedEffect(selectedThemeIdSetting.value) {
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
              val themeId = selectedThemeIdSetting.value?.value?.toLongOrNull()
              if (themeId != null) {
                  val dbTheme = themeDao.getThemeById(themeId)
                  if (dbTheme != null) {
                      val themeJson = com.attachdesign.kern.ui.theme.ThemeEngine.deserialize(dbTheme.jsonString)
                      if (themeJson != null) {
                          val colorTheme = themeJson.toColorTheme()
                          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                              isDark = colorTheme.isDark
                          }
                      }
                  }
              }
          }
      }

      ModernAndroidMarkdownEditorTheme(darkTheme = isDark) { Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) { MainNavigation(db, storageManager) } }
    }
  }
}
