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

For continuation context from the currently running Android app, see [`docs/current-app-state.md`](docs/current-app-state.md).

## Requirements

- Android 7.0 (API 24) or later
- An Arcane server reachable over HTTPS

## Building

This is a [Jetpack Compose](https://developer.android.com/compose) app written in Kotlin, using Material 3 and a single-Activity, multi-tab navigation shell.

| Toolchain | Version |
| --- | --- |
| Android Gradle Plugin | 9.0.0 |
| Gradle | 9.4.1 |
| Kotlin | 2.2.10 |
| JDK | 21 |

Open the project in **Android Studio** (Quail Feature Drop or later) and let it sync, or build from the command line:

```sh
./gradlew :app:installDebug
```

### Android Studio run configuration

Open the repository root (the directory containing `settings.gradle.kts`) in
Android Studio. After Gradle sync, the project should expose a single Android
application module/source set named `arcane-android.app.main` (shown in some
Android Studio dialogs as `app` or `app.main`).

A shared **Android App** run configuration named `Arcane Android` is checked in
under `.run/`. If Android Studio does not pick it up automatically, create one
manually with:

- Module: `arcane-android.app.main` (or the `app`/`app.main` entry Android Studio shows)
- Launch: Default Activity (`app.getarcane.android/.MainActivity`)
- Deploy target: any API 24+ emulator or device

The checked-in manifest already marks `MainActivity` as the launcher activity,
so no custom activity arguments are required.

The app depends on [`libarcane-kotlin`](https://github.com/getarcaneapp/libarcane-kotlin) — the Kotlin SDK that talks to the Arcane API. By default, Gradle resolves the SDK from the sibling `../libarcane-kotlin` Git checkout when it exists; otherwise it resolves the SDK from the public Git repository on the `main` branch and builds it on demand. No separate publish step is needed.

To force the public Git source dependency even when the sibling checkout exists, pass `-Parcane.remoteSdk`:

```sh
./gradlew :app:installDebug -Parcane.remoteSdk
```

### Running on an emulator

A helper script boots the `arcane` AVD with public DNS servers (needed for the hosted demo to resolve):

```sh
./run-emulator.sh
```

## Reporting Issues

Found a bug or have a feature request? [Open an issue on GitHub](https://github.com/getarcaneapp/android/issues).

## Translating

Help translate Arcane on Crowdin: https://crowdin.com/project/arcane-docker-management

Thank you for checking out Arcane Mobile! Your feedback and contributions are always welcome.
