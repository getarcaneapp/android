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

rootProject.name = "arcane-android"
include(":app")
project(":app").projectDir = file("app")

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
val appAgpVersion = agpVersionFrom(file("gradle/libs.versions.toml"))
val localArcaneSdkAgpVersion = agpVersionFrom(localArcaneSdk.resolve("gradle/libs.versions.toml"))
val useLocalArcaneSdk =
    localArcaneSdk.isDirectory &&
        !providers.gradleProperty("arcane.remoteSdk").isPresent &&
        localArcaneSdkAgpVersion == appAgpVersion

// Resolve the Arcane SDK (`app.getarcane:arcane-core` / `:arcane-android`) from the sibling Git
// checkout only when its Android Gradle Plugin version matches this app. Gradle rejects composite
// builds that apply multiple AGP versions, so a stale sibling checkout must fall back to the public
// Git source dependency instead of breaking sync/builds.
//
// Pass `-Parcane.remoteSdk` to force the public Git source dependency even when the sibling
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

fun agpVersionFrom(versionCatalog: File): String? =
    versionCatalog
        .takeIf { it.isFile }
        ?.readLines()
        ?.firstNotNullOfOrNull { line ->
            Regex("""^agp\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
        }
