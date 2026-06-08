package com.example.modernandroidmarkdowneditor.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val isExternal: Boolean,
    val isSelected: Boolean
)

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val syncState: String, // "SYNCED", "PENDING", "FAILED"
    val wordCount: Int = 0,
    val characterCount: Int = 0,
    val readabilityGrade: String = "N/A"
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jsonString: String
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects")
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    fun getAllProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE isSelected = 1 LIMIT 1")
    fun getSelectedProject(): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProjectById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE isSelected = 1 LIMIT 1")
    fun getSelectedProjectFlow(): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProject(project: ProjectEntity): Long

    @Update
    fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET isSelected = 0")
    fun deselectAllProjects()

    @Query("DELETE FROM projects WHERE id = :id")
    fun deleteProjectById(id: Long)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE projectId = :projectId")
    fun getFilesForProjectFlow(projectId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE projectId = :projectId")
    fun getFilesForProject(projectId: Long): List<FileEntity>

    @Query("SELECT * FROM files WHERE projectId IN (:projectIds)")
    fun getFilesForProjects(projectIds: List<Long>): List<FileEntity>

    @Query("SELECT * FROM files WHERE projectId = :projectId AND relativePath = :relativePath LIMIT 1")
    fun getFileByPath(projectId: Long, relativePath: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFile(file: FileEntity): Long

    @Update
    fun updateFile(file: FileEntity)

    @Query("DELETE FROM files WHERE projectId = :projectId AND relativePath = :relativePath")
    fun deleteFile(projectId: Long, relativePath: String)

    @Query("DELETE FROM files WHERE projectId = :projectId")
    fun deleteFilesForProject(projectId: Long)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    fun getSetting(key: String): SettingEntity?

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<SettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSetting(setting: SettingEntity)
}

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes")
    fun getAllThemesFlow(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1")
    fun getThemeById(id: Long): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTheme(theme: ThemeEntity): Long

    @Query("DELETE FROM themes WHERE id = :id")
    fun deleteTheme(id: Long)
}

@Database(entities = [ProjectEntity::class, FileEntity::class, SettingEntity::class, ThemeEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun fileDao(): FileDao
    abstract fun settingDao(): SettingDao
    abstract fun themeDao(): ThemeDao
}
