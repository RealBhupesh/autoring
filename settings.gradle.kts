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
    // Modules may not declare their own repositories; everything resolves from here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PriorityRing"

include(":engine") // pure Kotlin: models, block contracts, variables, executor, trace
include(":blocks") // Android implementations of triggers / conditions / actions
include(":data")   // Room, DataStore, repositories
include(":app")    // Compose UI, services, DI wiring
