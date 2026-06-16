import java.util.Properties

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

val androidSdkDirFromRootLocalProperties = file("local.properties")
    .takeIf { it.isFile }
    ?.let { localProperties ->
        Properties().apply {
            localProperties.inputStream().use(::load)
        }.getProperty("sdk.dir")
    }
    ?.takeIf { it.isNotBlank() }

if (
    androidSdkDirFromRootLocalProperties != null &&
    System.getenv("ANDROID_HOME").isNullOrBlank() &&
    System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() &&
    System.getProperty("android.home").isNullOrBlank()
) {
    System.setProperty("android.home", androidSdkDirFromRootLocalProperties)
}

val localArcaneSdk = file("../libarcane-kotlin")

fun agpVersionFromCatalog(catalog: File): String? = catalog
    .takeIf { it.isFile }
    ?.readText()
    ?.let { Regex("""(?m)^agp\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }

val appAgpVersion = agpVersionFromCatalog(file("gradle/libs.versions.toml"))
val localArcaneSdkAgpVersion = agpVersionFromCatalog(localArcaneSdk.resolve("gradle/libs.versions.toml"))
val forceRemoteArcaneSdk = providers.gradleProperty("arcane.remoteSdk").isPresent
val forceLocalArcaneSdk = providers.gradleProperty("arcane.localSdk").isPresent
val useLocalArcaneSdk = localArcaneSdk.isDirectory &&
    !forceRemoteArcaneSdk &&
    (forceLocalArcaneSdk || appAgpVersion == localArcaneSdkAgpVersion)

// Resolve the Arcane SDK (`app.getarcane:arcane-core` / `:arcane-android`) from a compatible
// sibling checkout when present, otherwise from its public Git repo built on demand as a Gradle
// source dependency (the `main` branch is pinned at the dependency use-site in app/build.gradle.kts).
// Android Gradle Plugin versions must match across composite/source builds, so an incompatible
// sibling checkout is skipped unless `-Parcane.localSdk` is passed explicitly.
//
// Pass `-Parcane.remoteSdk` to force the public Git source dependency even when a compatible sibling
// checkout exists.
if (useLocalArcaneSdk) {
    includeBuild(localArcaneSdk)
} else {
    sourceControl {
        gitRepository(uri("https://github.com/getarcaneapp/libarcane-kotlin.git")) {
            producesModule("app.getarcane:arcane-core")
            producesModule("app.getarcane:arcane-android")
        }
    }
}
