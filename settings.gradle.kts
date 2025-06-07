// ---- Plugin resolution ----------------------------------------------------
pluginManagement {
    repositories {
        google {
            // keep your fine-grained filters
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    // explicit plugin coordinates + versions
    plugins {
        id("com.android.application") version "8.4.0"
        id("org.jetbrains.kotlin.android") version "2.0.0"
        id("com.google.dagger.hilt.android") version "2.51"
    }
}

// ---- Library resolution for project dependencies --------------------------
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// ---- Root & modules -------------------------------------------------------
rootProject.name = "Bead wrapper"

// app module (wrapper)
// add more includes later, e.g. ":demo" for the caller test app
include(":app")
