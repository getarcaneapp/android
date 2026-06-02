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

rootProject.name = "Arcane Mobile"
include(":app")

// Consume the SDK from the sibling repo as a composite build: `app.getarcane:arcane-core`
// and `app.getarcane:arcane-android` are substituted with the local modules.
includeBuild("../libarcane-kotlin")
