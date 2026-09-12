import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

// Room schema export location; required for exportSchema = true so schema
// JSON is emitted and checked in under app/schemas for migration review.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val isSigningConfigured = if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    true
} else {
    false
}

android {
    namespace = "com.attachdesign.kern"
    compileSdk = 36
    
    val vMajor = project.property("version.major").toString().toInt()
    val vMinor = project.property("version.minor").toString().toInt()
    val vPatch = project.property("version.patch").toString().toInt()
    val vCode = project.property("version.code").toString().toInt()

    defaultConfig {
        applicationId = "com.attachdesign.kern"
        minSdk = 26
        targetSdk = 36
        versionCode = vCode
        versionName = "$vMajor.$vMinor.$vPatch"
    }

    signingConfigs {
        create("release") {
            if (isSigningConfigured) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

// M20: never silently fall back to the debug key for release artifacts. Fail the
// build when a release package is requested without release signing configured.
// Checked against the task graph (not at configuration time) so non-packaging
// tasks such as testDebugUnitTest and lint keep working without a keystore.
gradle.taskGraph.whenReady {
    val packagingRelease = allTasks.any { task ->
        val name = task.name
        name.contains("Release") &&
            (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package"))
    }
    if (packagingRelease && !isSigningConfigured) {
        throw GradleException(
            "Release signing is not configured: add keystore.properties at the repository root " +
                "(storeFile, storePassword, keyAlias, keyPassword) or supply the CI signing " +
                "secrets. Refusing to produce a release artifact signed with the debug key."
        )
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-core")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("io.mockk:mockk:1.13.10")
  testImplementation("app.cash.turbine:turbine:1.0.0")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Immutable Collections
  implementation(libs.kotlinx.collections.immutable)

  // Serialization JSON
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.documentfile)
}
dependencies {
  implementation("androidx.compose.material:material-icons-extended:1.5.0")
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
}

tasks.register("incrementVersionPatch") {
    group = "versioning"
    description = "Increments the patch version and versionCode in gradle.properties"
    doLast {
        val propertiesFile = project.rootProject.file("gradle.properties")
        if (propertiesFile.exists()) {
            val properties = Properties()
            propertiesFile.inputStream().use { inputStream ->
                properties.load(inputStream)
            }

            val major = properties.getProperty("version.major")?.toInt() ?: 0
            val minor = properties.getProperty("version.minor")?.toInt() ?: 0
            val patch = properties.getProperty("version.patch")?.toInt() ?: 0
            val code = properties.getProperty("version.code")?.toInt() ?: 1

            val newPatch = patch + 1
            val newCode = code + 1

            val lines = propertiesFile.readLines().map { line ->
                when {
                    line.startsWith("version.patch=") -> "version.patch=$newPatch"
                    line.startsWith("version.code=") -> "version.code=$newCode"
                    else -> line
                }
            }
            propertiesFile.writeText(lines.joinToString("\n") + "\n")
            println("Version updated to $major.$minor.$newPatch (Version Code: $newCode)")
        } else {
            throw GradleException("gradle.properties file not found")
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("started", "passed", "skipped", "failed")
        showStandardStreams = true
    }
}
