package com.attachdesign.kern

import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.theme.ModernAndroidMarkdownEditorTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    ).build()

    lifecycleScope.launch(Dispatchers.IO) {
      if (db.settingDao().getSetting("editor_font_family") == null) {
        db.settingDao().insertSetting(SettingEntity("editor_font_family", "serif"))
      }
      if (db.settingDao().getSetting("view_mode") == null) {
        db.settingDao().insertSetting(SettingEntity("view_mode", "RENDERED"))
      }
    }

    storageManager = StorageManager(applicationContext)
    externalOpenRequest = intent.toExternalOpenRequest()

    enableEdgeToEdge()
    setContent {
      ModernAndroidMarkdownEditorTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
          MainNavigation(
            db = db,
            storageManager = storageManager,
            externalOpenRequest = externalOpenRequest,
            onExternalOpenHandled = { handledRequest ->
              if (externalOpenRequest?.id == handledRequest.id) {
                externalOpenRequest = null
              }
            }
          )
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
    return ExternalOpenRequest(
      id = ++externalOpenRequestCounter,
      uriString = uri.toString(),
      mimeType = type
    )
  }
}
