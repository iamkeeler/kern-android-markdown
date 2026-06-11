package com.attachdesign.kern

import android.os.Bundle
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
      ModernAndroidMarkdownEditorTheme { Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) { MainNavigation(db, storageManager) } }
    }
  }
}
