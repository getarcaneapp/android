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
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.3.1 |
| Kotlin | 2.2.10 |
| JDK | 17+ |

Open the project in **Android Studio** (Otter Feature Drop or later) and let it sync, or build from the command line:

```sh
./gradlew :app:installDebug
```

The app depends on [`libarcane-kotlin`](https://github.com/getarcaneapp/libarcane-kotlin) — the Kotlin SDK that talks to the Arcane API — consumed as a [Gradle composite build](https://docs.gradle.org/current/userguide/composite_builds.html) from the sibling `../libarcane-kotlin` directory. It resolves automatically on first build; no separate publish step is needed.

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
