/*
 * build.gradle.kts (App-level) — THE most important build file
 *
 * This controls:
 * 1. SDK versions (which Android versions we support)
 * 2. App identity (package name, version)
 * 3. ALL libraries we use
 *
 * Libraries explained:
 * - Jetpack Compose: UI toolkit (replaces old XML layouts)
 * - Retrofit: HTTP client (calls our FastAPI backend)
 * - Room: Local SQLite database (offline cache)
 * - WorkManager: Background tasks (sync when online)
 * - Navigation: Screen-to-screen navigation
 * - DataStore: Key-value storage (for device_id, preferences)
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")  // Needed by Room for code generation
}

android {
    namespace = "com.krishi.app"
    compileSdk = 35  // Latest SDK to compile against

    defaultConfig {
        applicationId = "com.krishi.app"
        minSdk = 26        // Android 8.0+ (covers 95%+ devices in India)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Backend URL — deployed on HuggingFace Spaces
        buildConfigField("String", "BASE_URL", "\"https://sisaram-krish.hf.space\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true   // Shrink APK size (removes unused code)
            isShrinkResources = true // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://sisaram-krish.hf.space\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true     // Enable Jetpack Compose
        buildConfig = true // Enable BuildConfig for BASE_URL
    }
}

dependencies {
    // --- Jetpack Compose (UI) ---
    // BOM = Bill of Materials — locks all Compose libraries to compatible versions
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")                  // Core Compose
    implementation("androidx.compose.material3:material3")    // Material 3 design
    implementation("androidx.compose.ui:ui-tooling-preview")  // Preview in Android Studio
    implementation("androidx.activity:activity-compose:1.9.3") // Compose + Activity bridge
    debugImplementation("androidx.compose.ui:ui-tooling")     // Debug tools

    // --- Navigation (screen-to-screen) ---
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // --- Retrofit (HTTP client → calls our FastAPI backend) ---
    // Retrofit turns our API endpoints into simple Kotlin function calls:
    //   @GET("api/digest") suspend fun getDigest(): DigestResponse
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")  // JSON parsing
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // Debug HTTP logs

    // --- Room (local SQLite database for offline cache) ---
    // Room lets us save digests/prices locally so the app works offline.
    // You define Kotlin data classes and Room generates all the SQL.
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")  // Kotlin coroutines support
    ksp("androidx.room:room-compiler:$roomVersion")         // Code generation

    // --- WorkManager (background sync) ---
    // Syncs news/prices in background even when app is closed.
    // Android's recommended way to do reliable background work.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // --- DataStore (key-value preferences) ---
    // Stores device_id, language preference, onboarding state.
    // Modern replacement for SharedPreferences.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Lifecycle (ViewModel) ---
    // ViewModel holds screen state and survives screen rotation.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // --- Core ---
    implementation("androidx.core:core-ktx:1.15.0")
}
