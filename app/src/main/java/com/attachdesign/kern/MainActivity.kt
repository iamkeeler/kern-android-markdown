package com.attachdesign.kern

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
  companion object {
    private const val REQUEST_CODE_STORAGE_PERMISSION = 1001
  }

  private fun checkAndRequestPermissions() {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
      val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
      val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
      if (readPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
          REQUEST_CODE_STORAGE_PERMISSION
        )
      }
    }
  }

  private lateinit var db: AppDatabase
  private lateinit var storageManager: StorageManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    checkAndRequestPermissions()

    db = Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java,
      "kern.db"
    ).fallbackToDestructiveMigration()
      .build()

    lifecycleScope.launch(Dispatchers.IO) {
      if (db.settingDao().getSetting("editor_font_family") == null) {
        db.settingDao().insertSetting(SettingEntity("editor_font_family", "serif"))
      }
      if (db.settingDao().getSetting("view_mode") == null) {
        db.settingDao().insertSetting(SettingEntity("view_mode", "RENDERED"))
      }
    }

    storageManager = StorageManager(applicationContext)

    enableEdgeToEdge()
    setContent {
      ModernAndroidMarkdownEditorTheme { Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) { MainNavigation(db, storageManager) } }
    }
  }
}
