<div align="center">

  <img src=".github/assets/img/PNG-3.png" alt="Arcane Logo" width="500" />
  <p>Arcane Mobile — Manage your Docker hosts from Android.</p>

<a href="https://github.com/getarcaneapp/android/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-BSD--3--Clause-blue.svg" alt="License"></a>
<a href="https://discord.gg/WyXYpdyV3Z"><img src="https://img.shields.io/badge/discord-join-5865F2.svg?logo=discord&logoColor=white" alt="Discord"></a>

<br />

</div>

## About

Arcane Mobile is the official Android companion for [Arcane](https://github.com/getarcaneapp/arcane). It connects to any Arcane manager or agent and lets you browse and operate your Docker environments — containers, images, volumes, networks, and Compose projects — from your phone.

## Documentation

For setup instructions, configuration details, and development guides, visit the **[official documentation site](https://getarcane.app)**.

## Requirements

- Android 7.0 (API 24) or later
- An Arcane server reachable over HTTPS

## Building

This is a [Jetpack Compose](https://developer.android.com/compose) app written in Kotlin, using Material 3 and a single-Activity, multi-tab navigation shell.

| Toolchain | Version |
| --- | --- |
| Android Gradle Plugin | 9.1.1 (aligned with libarcane-kotlin source dependency) |
| Gradle | 9.4.1 (wrapper) |
| Kotlin | 2.2.10 |
| JDK | 21 recommended; 17+ supported |

Open the project in **Android Studio** (Otter Feature Drop or later) and let it sync, or build from the command line. For a local CLI checkout, point Gradle at the Android SDK and use JDK 21:

```sh
printf 'sdk.dir=/home/nameless1/Android/Sdk\n' > local.properties
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/nameless1/Android/Sdk
export ANDROID_SDK_ROOT=/home/nameless1/Android/Sdk
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Install the debug build on a connected emulator or device:

```sh
./gradlew :app:installDebug --no-daemon
```

The app depends on [`libarcane-kotlin`](https://github.com/getarcaneapp/libarcane-kotlin) — the Kotlin SDK that talks to the Arcane API. By default, Gradle resolves the SDK from a compatible sibling `../libarcane-kotlin` Git checkout when it exists; otherwise it resolves the SDK from the public Git repository on the `main` branch and builds it on demand. No separate publish step is needed. A sibling checkout is considered compatible only when its Android Gradle Plugin version matches this app, because Gradle/AGP do not allow multiple AGP versions in the same composite/source build.

To force the public Git source dependency even when a compatible sibling checkout exists, pass `-Parcane.remoteSdk`:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug -Parcane.remoteSdk --no-daemon
```

To force a sibling checkout for local SDK development, pass `-Parcane.localSdk`; if that checkout uses a different AGP version, align one side first.

### Running on an emulator

A helper script boots the `arcane` AVD with public DNS servers (needed for the hosted demo to resolve):

```sh
./run-emulator.sh
```

Once an emulator or device is available, install and launch the app from Android Studio, or use:

```sh
./gradlew :app:installDebug --no-daemon
adb shell monkey -p app.getarcane.android 1
```

## Reporting Issues

Found a bug or have a feature request? [Open an issue on GitHub](https://github.com/getarcaneapp/android/issues).

## Translating

Help translate Arcane on Crowdin: https://crowdin.com/project/arcane-docker-management

Thank you for checking out Arcane Mobile! Your feedback and contributions are always welcome.
