# Testing and quality gates

This document defines the repeatable Android validation lanes and the security review boundary for
release-readiness changes. Unit, instrumented, and disposable-server results are always reported
separately; success in one lane is not evidence for another.

## Local and CI lanes

Use JDK 21 with the repository's Gradle wrapper. The required pull-request baseline is:

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
./gradlew --no-daemon :app:lintDebug
```

The first command normally completes in about two minutes on the reference development host after
dependencies are cached. Lint normally completes in under two minutes. CI runs those gates in the
`Build APK` job and runs the deterministic Compose/route suite separately on an API 30 x86_64
KVM-accelerated emulator with animations disabled. The emulator lane runs for pull requests, `main`
and tag pushes, and explicit workflow dispatches; feature-branch push builds do not duplicate an open
pull request's emulator job:

```sh
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

The instrumented suite uses controlled UI state and invalid/example addresses; it does not depend on
an Arcane server. It covers authentication routing while restoration is pending, configurable and
adaptive navigation, environment selection, destructive confirmation, authenticated-route parsing,
and a representative durable operation. Tests wait for Compose/Espresso idleness instead of using
fixed sleeps.

Live behavior is a distinct manual or scheduled lane. Export credentials only in the invoking shell,
never in Gradle properties, scripts, screenshots, or source. `scripts/run-disposable-live-tests.sh`
accepts only the checked disposable host/emulator URLs, requires the literal disposable opt-in, gives
every resource a unique `par-live-` prefix, and always invokes an idempotent cleanup callback. The
callback must enumerate the test prefix, delete users/resources/secondary instances created by the
run, and fail if anything remains. Commands are bounded to 900 seconds by default; set
`ARCANE_E2E_TIMEOUT_SECONDS` between 30 and 3600 only when a documented lane requires it. A private
CA path may be supplied through
`ARCANE_E2E_CA_CERT`; disabling TLS verification is not part of the durable harness.

## Lint policy

Android lint is an error-on-new-warning gate for debug and release builds. The checked-in
`app/lint-baseline.xml` contains only findings confirmed to predate PAR-404. A baseline entry is not a
waiver: remediation should remove the finding and its entry in the same focused change. New entries
require a written triage explaining why the code cannot reasonably be fixed now. Blanket issue
suppression and directory-wide `ignore` rules are not accepted.

Run `./gradlew :app:updateLintBaseline` only after reviewing the full report, then inspect the XML and
use `./gradlew :app:lintDebug` to prove the resulting gate. Dependency-version suggestions, legacy
launcher-icon guidance, and deliberate minimum-SDK resource attributes are tracked as existing debt;
locale-sensitive formatting findings were fixed rather than baselined.

Formatting and broad static-analysis plugins were evaluated but are not enabled in this batch. The
current tree has no agreed formatting baseline, so ktlint/detekt would create a large unrelated
rewrite and a noisy suppressions file. `git diff --check` remains mandatory. Revisit a formatter or
detekt in a dedicated change with an approved baseline and bounded rule set.

## Security triage

| Area | Current decision |
| --- | --- |
| Permissions | App source declares `INTERNET` and runtime-gated `POST_NOTIFICATIONS`. The merged release manifest also contains credentials-library `USE_BIOMETRIC`/`USE_FINGERPRINT` and WorkManager `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, and `FOREGROUND_SERVICE`, plus AndroidX's generated non-exported-receiver permission. No location, storage, camera, microphone, contacts, or package-query permission is present. |
| Exported components | `MainActivity` is exported only for launcher and narrowly parsed route/OIDC/passkey intents. The app-owned operation and widget receivers are non-exported. Merged AndroidX/Google services that must be exported are protected by `BIND_JOB_SERVICE`, `BIND_REMOTEVIEWS`, Google revocation, or `DUMP` permissions; all merged providers are non-exported. |
| Intents and shortcuts | External route payloads pass the authenticated route resolver and server/account/environment fences. Static shortcuts contain no resource or credential data. |
| Backup and transfer | Explicit allowlists include only local tab customization. Credentials, server identity, cached/server-derived state, operations, pins, snapshots, and widget data remain excluded. |
| Network security | The supported configuration requires HTTPS. No cleartext opt-in or network-security override is shipped. Test certificates and trust changes are disposable device state and are removed after testing. |
| Web/file exposure | The app has no WebView or FileProvider. Browser-based authentication uses typed SDK contracts and platform browser surfaces; no app-private file is exposed. |
| Cryptography | Application code does not invent cryptographic primitives. Credential protection, PKCE/passkey protocol work, and transport behavior remain owned by Android APIs and `libarcane-kotlin`. |
| Logging | Production code does not enable Ktor body logging and does not intentionally log credentials, tokens, passkey payloads, recovery codes, or variable secrets. Evidence and screenshots are scanned before retention. |
| Dependencies | Build inputs are Google Maven, Maven Central, Gradle plugins, and public source for `libarcane-kotlin`; no telemetry, ad, analytics, Firebase, or tracker dependency is present. The SDK's `credentials-play-services-auth` dependency brings proprietary Google Play Services auth/FIDO artifacts into the packaged app, and the SDK repository lacks an explicit license file. Both findings block an F-Droid eligibility claim until resolved and re-audited. |
| Binary inputs | The Gradle wrapper JAR is the only committed build binary. No AAR/JAR dependency, native `.so`, APK, keystore, certificate, generated backup, or downloaded executable is stored in the repository. |
| CI supply chain | Third-party actions are pinned to immutable commit SHAs. Workflows receive read-only contents permission except the already-scoped tag release job. Signing material is optional GitHub secret input and is never available to pull-request builds. |

Dependency inventory is reviewed with `./gradlew :app:dependencies` plus APK inspection. A standalone
dependency-vulnerability or secret-scanning product is not made a blocking gate here: no stable,
complete, non-proprietary Android advisory gate is already configured, and adding an unreviewed
scanner would create unactionable failures. Repository history and the complete diff are still
searched for credential formats, keystores, certificates, APKs, local paths, and generated data
before publication. Dependabot or an OSV-based gate can be added separately once ownership and
remediation SLAs are defined.

## Remediation workflow

1. Reproduce the gate locally from a clean checkout and record the exact tool/JDK versions.
2. Classify the result as new actionable code, verified pre-existing debt, false positive, or tool
   instability. Security-sensitive findings are never dismissed solely because they are old.
3. Fix the smallest safe scope and add a regression test when behavior changed.
4. If a baseline is unavoidable, add only the exact finding with a durable explanation here or in
   the relevant policy document.
5. Rerun unit, lint, applicable instrumentation/live lanes, `git diff --check`, and the packaged APK
   inspection. Report each lane separately in the PR.
