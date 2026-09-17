# F-Droid release preparation

This is preparation evidence, not a submission or a reproducibility claim. The authoritative inputs
were rechecked against the current [F-Droid inclusion policy](https://f-droid.org/docs/Inclusion_Policy/),
[app metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/),
[submission quick start](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
[reproducible-build guidance](https://f-droid.org/docs/Reproducible_Builds/), and
[anti-feature definitions](https://f-droid.org/docs/Anti-Features/) on 2026-09-16.

## Identity and release inputs

| Field | Prepared value |
| --- | --- |
| Application ID | `app.getarcane.android` |
| Current version | `0.1.0` / version code `260901` |
| Android support | min API 24; compile/target API 35 |
| License | BSD-3-Clause; root `LICENSE`, copyright Kyle Mendell |
| Source | `https://github.com/getarcaneapp/android` |
| Issues | `https://github.com/getarcaneapp/android/issues` |
| Category | System |
| Build tools | JDK 21, Gradle wrapper 9.4.1, AGP 9.1.1, Kotlin 2.2.10, Android platform/build-tools 35 |
| Native toolchain | No NDK/CMake/native library build required |
| Metadata | `fastlane/metadata/android/en-US/` title, short/full description, and version-code changelogs |
| Icon | Adaptive launcher icon is built entirely from repository resources; F-Droid may extract it from the APK. No store screenshot is required for initial metadata. |

The app is an operational client for a user-configured Arcane server. It makes network requests only
to that configured server and to resource endpoints the server/user asks it to use. The optional
hosted demo is user-selected. Browser surfaces may be opened for server-configured OIDC/passkey
flows. There is no ad, analytics, telemetry, crash-reporting, Firebase, billing, or proprietary push
dependency, and no tracker was found. However, the packaged dependency graph does contain Google Play
Services auth/FIDO libraries through AndroidX Credentials; that is a proprietary build/runtime
dependency and is not represented as F-Droid eligible.
An Arcane server is required; the app does not silently provision or subscribe to a hosted service.

Any future metadata must declare the applicable `NonFreeDep` anti-feature while the Google Play
Services credentials provider remains packaged. Merely declaring an anti-feature does not cure the
inclusion-policy problem: the dependency must be removed/replaced, or current F-Droid policy and
tooling must explicitly accept the exact build. Network-service anti-features must also be
re-evaluated against the exact release behavior and disclosures at submission time.

## Source and dependency audit

All normal Android libraries resolve from Google Maven or Maven Central and use established FOSS
licenses (AndroidX/Compose/Gradle/Kotlin/Ktor/coroutines/Coil). The only application-specific source
dependency is `libarcane-kotlin`. Local builds prefer a sibling checkout with a matching AGP version;
otherwise Gradle source control fetches its public Git repository. No `.aar`, `.jar` library, `.so`,
APK, keystore, certificate, generated source archive, or dynamically downloaded executable is
committed; `gradle-wrapper.jar` is the expected bootstrap binary.

There are two release-blocking eligibility issues. First, the pinned `libarcane-kotlin` repository at
`b29695d547b78389ed7230b35cd133f7046b4b52` has no `LICENSE`, `COPYING`, or equivalent license
declaration. F-Droid requires all dependencies to be verifiably free software. The Android project's
BSD-3-Clause license cannot be presumed to license a separate repository. A future submission must
remain blocked until upstream adds an explicit compatible license and the exact pinned SDK revision
contains it. This batch does not rewrite or infer upstream licensing.

Second, release dependency and packaged-manifest inspection found
`androidx.credentials:credentials-play-services-auth:1.6.0` and its transitive
`com.google.android.gms` auth, FIDO, block-store, identity-credentials, base, basement, and tasks
artifacts. The merged manifest confirms their credential-provider and revocation components. These
are used for the current passkey integration but are proprietary dependencies. A future F-Droid build
must use an audited FOSS-only credential-provider strategy or otherwise resolve this policy blocker;
the candidate recipe below is therefore structural documentation, not a buildable eligible recipe.

The remote Gradle fallback follows the SDK `main` branch and is useful for ordinary clean-checkout CI,
but is not a reproducible release input. An F-Droid recipe must use a separately declared `srclib`
for `https://github.com/getarcaneapp/libarcane-kotlin.git` at an exact full commit, place/symlink it at
`../libarcane-kotlin`, and let the existing composite build consume it. Network fetching from Gradle
source control during the isolated build must not be relied upon.

Ordinary fallback buildability was verified from a fresh GitHub clone of Android
`94e972ae03afd3a9fe133ad78a2861fadc4fc36d` with no sibling SDK, `local.properties`, or signing
variables. `-Parcane.remoteSdk` resolved public SDK `main` to
`b29695d547b78389ed7230b35cd133f7046b4b52`; 380 unit tests and debug/unsigned-release assembly
passed. This validates the documented fallback, not the candidate F-Droid recipe or reproducibility.

Candidate external metadata structure after the SDK license blocker is resolved:

```yaml
Categories:
  - System
License: BSD-3-Clause
SourceCode: https://github.com/getarcaneapp/android
IssueTracker: https://github.com/getarcaneapp/android/issues
RepoType: git
Repo: https://github.com/getarcaneapp/android.git

Builds:
  - versionName: 0.1.0
    versionCode: 260901
    commit: <full release tag commit>
    subdir: app
    srclibs:
      - libarcane-kotlin@<full licensed SDK commit>
    prebuild: ln -s $$libarcane-kotlin$$ ../../libarcane-kotlin
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 260901
```

This snippet intentionally is not installed as executable repository metadata: the release commit and
licensed SDK commit do not exist yet, and knowingly committing placeholders as a claimed valid recipe
would be misleading. F-Droid's external `fdroiddata` change remains a separately authorized future
submission.

## Build and signing boundary

A future release candidate is built from a clean tag checkout with a clean exact SDK sibling:

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug
./gradlew --no-daemon :app:assembleRelease
```

With no `ARCANE_RELEASE_*` variables, the second command creates an unsigned release APK. This is the
artifact boundary suitable for F-Droid to sign with its own infrastructure. Upstream GitHub signing,
if explicitly authorized, uses repository secrets and a separate keystore; neither key material nor a
signed artifact is a source input. A successful clean build proves buildability, not bit-for-bit
reproducibility. Reproducibility may be claimed only after at least two clean isolated builds with
identical declared inputs produce matching normalized/unsigned artifacts and the official F-Droid
tooling confirms the result.

Release tags use `v<versionName>` (for example `v0.1.0`) and must point at the commit whose Gradle
version name/code and `fastlane` changelog agree. The source archive is the forge-generated archive of
that tag. The publisher verifies the tag, changelog, metadata, dependency pin, unsigned artifact,
manifest/permissions, and update-check result before any signing or submission action.

## Validation and remaining submission work

Before a future F-Droid metadata PR:

1. Land an explicit FOSS license in `libarcane-kotlin`, remove or replace the proprietary Google Play
   Services credential dependency for the F-Droid build, select the resulting full commit, and
   define/validate the `srclib` entry.
2. Cut no tag until the release checklist, upgrade/rollback matrix, and publication authorization are
   complete.
3. Repeat the isolated Android+SDK build without local properties, signing variables, or caches;
   validate the candidate YAML with the current `fdroidserver` lint/build tools.
4. Re-run dependency/APK/manifest/tracker/anti-feature scans and verify the adaptive icon plus
   English metadata. Add screenshots only if sanitized disposable data materially improves the
   listing; screenshots must contain no real host, user, credential, resource identifier, or
   notification content.
5. Submit the external metadata PR separately. Do not upload upstream signing keys or signed APKs to
   the F-Droid build recipe.

No F-Droid submission, external metadata PR, signing key, tag, release, or store artifact is created
by PAR-405.
