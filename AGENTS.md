# Arcane Android Agent Guidance

Arcane Android is a Jetpack Compose companion for the Arcane Docker management server. It is an
operational client, so correctness, server compatibility, lifecycle behavior, and safe destructive
actions matter more than visual novelty.

## Collaboration Scope

Michael Kaltner is an authorized collaborator and an active Android feature contributor. Changes
may cover application code, tests, navigation, CI, Gradle, AGP, Kotlin, JDK, dependencies, and
coordinated SDK integration.

Use a focused branch by default. Do not push, merge to `main`, tag, sign, publish an APK, or create
a GitHub release unless the user explicitly requests it.

Always inspect `git status` first and preserve unrelated local changes and untracked files.

## Architecture

- `app/src/main/kotlin/app/getarcane/android/core/` — client ownership, authentication, preferences,
  environment state, streams, formatting, and shared application state.
- `app/src/main/kotlin/app/getarcane/android/nav/` — bottom-tab selection, persistence, swapping,
  and back-navigation behavior.
- `app/src/main/kotlin/app/getarcane/android/ui/screens/` — resource list/detail screens and
  operational flows.
- `app/src/main/kotlin/app/getarcane/android/ui/components/` — reusable Compose components.
- `app/src/test/` — JVM unit tests. Prefer extracting deterministic mapping/state logic so it can
  be tested without an emulator.

`ArcaneClientManager` is the central owner of server configuration, authentication state, current
user, capabilities, active environment, and the `ArcaneClient`. Do not create competing client
owners or independent authentication stores.

The app uses a single-activity Compose architecture. Follow the existing navigation and screen
patterns instead of introducing a second navigation framework or unrelated state-management
system.

## SDK Boundary

The app depends on the sibling `../libarcane-kotlin` checkout when it exists and its Android Gradle
Plugin version matches the app. Otherwise Gradle resolves the SDK from its Git source. Pass
`-Parcane.remoteSdk` only when intentionally testing the remote dependency.

Treat `libarcane-kotlin` as the sole application API client:

- use `ArcaneClient` services and SDK models directly;
- do not duplicate REST endpoints, DTOs, token refresh, serialization, WebSocket, or NDJSON logic;
- do not add pass-through wrappers around SDK methods;
- when the Arcane wire contract is unsupported, update and test the SDK first.

For cross-repository API work, inspect the matching types and handlers in `../arcane`, then update
the SDK, then update Android.

## Implementation Conventions

- Use Kotlin coroutines and structured cancellation. Rethrow `CancellationException`.
- Keep long-lived work in an explicitly owned scope and cancel or replace jobs when lifecycle or
  environment state changes.
- Keep environment-specific operations tied to the selected `EnvironmentId`; `"0"` represents the
  local Docker environment.
- Preserve authentication restoration without flashing the login UI.
- Treat unknown server enum values and optional fields defensively.
- Keep list/detail navigation mobile-native and preserve back and tab re-selection behavior.
- Put destructive operations behind clear resource- and environment-specific confirmation.
- Prefer existing shared components and formatting helpers before adding new ones.
- Add or update tests for nontrivial mapping, selection, status, retry, and navigation logic.
- Prefer Android string resources for new user-facing text when practical; do not mix a focused
  change with a broad localization rewrite.

## Toolchain and Verification

The checked-in build defines the authoritative versions. The expected baseline is JDK 21, JVM
target 17, Android API 24 minimum, and Android API 35 compile/target SDK.

Run the CI-equivalent checks:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

When the sibling SDK changed, run this first from `../libarcane-kotlin`:

```sh
./gradlew :arcane-core:test :arcane-android:assembleRelease
```

For lifecycle, navigation, streaming, authentication, or destructive-action changes, also test on
an emulator or device against an appropriate Arcane server when available. Clearly report when
manual/device testing was not possible.

Release signing is optional for development. Never request, expose, or commit signing secrets.
Do not commit generated Gradle output, local SDK paths, APKs, keystores, or machine-specific
configuration.
