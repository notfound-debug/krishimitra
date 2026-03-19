/*
 * settings.gradle.kts — Project-level Gradle settings
 *
 * What this does:
 * - Tells Gradle WHERE to download plugins (google, mavenCentral)
 * - Tells Gradle WHERE to download libraries (same repos)
 * - Names the project and includes the :app module
 *
 * You almost never need to edit this file.
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KrishiMitra"
include(":app")
