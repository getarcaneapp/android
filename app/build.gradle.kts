import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 provides built-in Kotlin; the standalone kotlin-android plugin is dropped (it would
    // clash registering the `kotlin` extension). The Compose + serialization compiler plugins stay.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseStoreFile = providers.environmentVariable("ARCANE_RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("ARCANE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ARCANE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ARCANE_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    releaseStoreFile.isPresent &&
        releaseStorePassword.isPresent &&
        releaseKeyAlias.isPresent &&
        releaseKeyPassword.isPresent

android {
    namespace = "app.getarcane.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.getarcane.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 260602
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Arcane SDK source: the sibling checkout when present, or the public Git repo's `main` branch
// when built with -Parcane.remoteSdk or without the sibling checkout (kept in sync with
// settings.gradle.kts). The composite build ignores the version constraint and substitutes the
// local modules, so the branch is only used for git.
val localArcaneSdk = rootProject.layout.projectDirectory.dir("../libarcane-kotlin").asFile
val appAgpVersion = agpVersionFrom(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml").asFile)
val localArcaneSdkAgpVersion = agpVersionFrom(localArcaneSdk.resolve("gradle/libs.versions.toml"))
val arcaneFromGit =
    providers.gradleProperty("arcane.remoteSdk").isPresent ||
        !localArcaneSdk.isDirectory ||
        localArcaneSdkAgpVersion != appAgpVersion

dependencies {
    // Arcane SDK — resolved from the sibling checkout when present, otherwise from Git.
    if (arcaneFromGit) {
        implementation(libs.arcane.core) { version { branch = "main" } }
        implementation(libs.arcane.android) { version { branch = "main" } }
    } else {
        implementation(libs.arcane.core)
        implementation(libs.arcane.android)
    }

    // OkHttp HTTP engine for Ktor: robust TLS/HTTP-2 on Android (the pure-Kotlin CIO engine
    // fails the TLS handshake against some Cloudflare-fronted hosts, e.g. demo.getarcane.app).
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}

fun agpVersionFrom(versionCatalog: File): String? =
    versionCatalog
        .takeIf { it.isFile }
        ?.readLines()
        ?.firstNotNullOfOrNull { line ->
            Regex("""^agp\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
        }
