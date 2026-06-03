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

// Resolve the Arcane SDK (`app.getarcane:arcane-core` / `:arcane-android`) from its public Git
// repo, built on demand as a Gradle source dependency (the `main` branch is pinned at the
// dependency use-site in app/build.gradle.kts).
//
// For local SDK development, build with `-Parcane.localSdk` to use the sibling `../libarcane-kotlin`
// checkout as a composite build instead — uncommitted SDK changes are picked up immediately.
if (providers.gradleProperty("arcane.localSdk").isPresent) {
    includeBuild("../libarcane-kotlin")
} else {
    sourceControl {
        gitRepository(uri("https://github.com/getarcaneapp/libarcane-kotlin.git")) {
            producesModule("app.getarcane:arcane-core")
            producesModule("app.getarcane:arcane-android")
        }
    }
}
