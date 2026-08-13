# ProGuard and R8 rules for Modern Android Markdown Editor (Kern)

# ------------------------------------------------------------------------------
# Kotlin & Coroutines Rules
# ------------------------------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep Coroutines internal reflection APIs from being removed
-keepclassmembers class kotlinx.coroutines.** {
    *** *(*);
}

# ------------------------------------------------------------------------------
# Kotlinx Serialization Rules
# ------------------------------------------------------------------------------
# Keep serializers and serializable classes
-keepclassmembers class * {
    *** Companion;
}
-keep class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class **$$serializer { *; }

# ------------------------------------------------------------------------------
# Room Database Rules
# ------------------------------------------------------------------------------
# Room instantiates the generated database implementation reflectively. Keep
# both the implementation class and its no-argument constructor in minified
# builds (the constructor is otherwise eligible for removal by R8).
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep class com.attachdesign.kern.data.local.AppDatabase_Impl {
    public <init>();
}

# Keep Room entities and DAOs
-keep class * {
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.Entity *;
}
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
}

# ------------------------------------------------------------------------------
# Jetpack Compose Rules
# ------------------------------------------------------------------------------
# Keep Compose compiler metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
