/*
 * build.gradle.kts (Project-level)
 *
 * Declares Gradle plugins used across the project.
 * apply false = "download this plugin but don't apply it here,
 * the :app module will apply it"
 *
 * KSP (Kotlin Symbol Processing) is needed by Room DB —
 * it generates database code at compile time.
 */

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
