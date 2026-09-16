# Android release channels and promotion policy

Arcane Mobile for Android is currently a public **alpha**. It is suitable for informed testing
against disposable or well-backed-up environments, but it is not yet represented as production-ready.
The app can perform destructive Docker operations; server-side backups and a recovery path remain the
operator's responsibility.

## Supported and tested baseline

- Android 7.0 / API 24 is the minimum install and core-flow target. API 35 is the compile/target SDK.
  Every release candidate covers API 24 minimum smoke, API 30 retained regression, and API 35 current
  permissions/background behavior. Compact portrait/landscape and at least one tablet or foldable
  expanded configuration are required.
- Arcane 2.10.2 is the minimum fully supported alpha server baseline. Features introduced after that
  version remain capability-gated and may show an explicit unsupported state. Each candidate tests
  2.10.2 plus the latest stable 2.11.x-or-newer release and current Arcane source when practical.
- Android consumes `libarcane-kotlin` as its only Arcane API client. A release records one exact SDK
  commit and validates both the matching sibling composite build and the public remote fallback. An
  F-Droid recipe must materialize the exact SDK commit as a sibling `srclib`; a moving `main` branch is
  never a release pin.
- The current preparation comparison pins are Android
  `90b67366638c21c30b2c748347a57bd8f184d491`, iOS
  `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
  `b29695d547b78389ed7230b35cd133f7046b4b52`, and Arcane current source
  `194e7ae87f0803bc2b85ed3a9a107fd432993ac4` (`v2.12.0-5-g194e7ae8`).

## Candidate checklist

Every alpha or beta candidate records exact revisions, test counts, device/server configurations,
failures/limitations, and cleanup. Required evidence is:

- green unit tests and debug assembly; Android lint with no unbaselined findings;
- deterministic API 30 instrumentation for auth routing, navigation/configurable tabs, adaptive UI,
  destructive confirmation, environment switching, authenticated routes, and representative durable
  operations;
- disposable live-server coverage of authentication/restoration, multi-environment/restricted-user
  behavior, operations/notifications, widgets/shortcuts/routes, cache/snapshot/offline recovery,
  update and Activity surfaces, destructive confirmation, force-stop/reopen, and process recreation;
- API 24 install/launch/core smoke, API 30 retained regression, API 35 permission/background behavior,
  compact plus expanded layouts, portrait/landscape, light/dark/automatic themes, keyboard/D-pad, and
  representative TalkBack;
- English, accented `en-XA`, and RTL `ar-XB` pseudolocales at 100% and 200% font scale for migrated
  flows, including animation scale 0/reduced-motion observations;
- backup/restore allowlist inspection and a disposable restore regression; previous-debug-to-candidate
  upgrade plus rollback/data-policy observations;
- clean-checkout sibling-SDK build, remote-SDK fallback build, unsigned release build, packaged
  manifest/permissions/exported components/shortcuts/widget/backup/signing inspection, dependency and
  secret/artifact scan, `git diff --check`, and an independent complete-diff/release review;
- accurate changelog/release notes, known limitations, F-Droid metadata state, source-tag/version
  agreement, and all disposable users/resources/instances/AVDs/APKs/widgets/screenshots/certificates/
  trust overrides/build directories removed.

Unit, instrumented, and live lanes are reported separately. A synthetic or mocked result never replaces
a required real-server/device observation. Automated reviews are not currently active; no Greptile
evidence is required or claimed.

## Alpha and beta gates

An **alpha** may be cut when all blocker checks above are green for its declared scope, no known
security/data-loss/crash blocker exists, destructive actions are correctly scoped and confirmed, the
minimum supported server/device flows work, and limitations are prominent in the release notes. Alpha
may retain bounded noncritical accessibility/localization gaps or explicit capability-gated feature
limits, but not misleading state, credential exposure, cross-server actions, or unrecoverable data
corruption.

Promotion to **beta** additionally requires two consecutive release candidates with the complete
matrix green, no unresolved P0/P1 parity verification candidate, representative physical-device
confirmation, stable upgrade/backup/offline behavior, completed English accessibility audit with no
critical WCAG/Android accessibility defect, pseudolocale readiness for the migrated high-traffic
slice, an explicit dependency-license/F-Droid eligibility decision, and no open crash/data-loss/
security issue. Beta still does not imply a production SLA or support for every Arcane feature.

Any reproducible credential disclosure, cross-account/server/environment mutation, backup inclusion of
sensitive state, destructive action without adequate confirmation, corrupt upgrade, unrecoverable
operation duplication, startup/auth crash on a supported baseline, or known exploitable dependency is
a release blocker. Stop publication, document impact, rotate exposed test credentials if relevant,
fix and rerun the affected complete matrix. Lower-severity defects require an owner, user-visible note
when relevant, and an explicit accept/defer decision.

## Version, upgrade, and rollback workflow

- `versionName` uses semantic versions. Pre-release labels are allowed while channel state requires
  them. `versionCode` is monotonically increasing; the current date-shaped convention must never be
  reused or decreased.
- Update `app/build.gradle.kts` and add the matching
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` in the candidate commit. The changelog
  describes user-visible behavior, compatibility, migrations, security notes, and known limitations.
- Create `v<versionName>` only after the exact candidate commit, clean tree, CI, release review, and
  explicit tag/publication authorization are recorded. The tag, source archive, Gradle version, and
  metadata must agree.
- Upgrade testing installs the prior published debug/release-equivalent artifact with sanitized
  disposable state, then installs the candidate without clearing data and repeats auth, navigation,
  cache, operation, widget, backup-policy, and server-scope checks.
- Android normally prevents a lower-version-code downgrade. Test rollback only on disposable data.
  A production rollback is a newly versioned forward release built from the last safe source plus any
  required data migration; never instruct users to bypass downgrade protection or assume newer stored
  state is readable by an older binary.

## Distribution and authority

Pull requests automatically produce an unsigned/debug GitHub artifact for review. Release signing is
optional and bounded to repository secrets on an explicitly authorized version tag. Maintainers with
the relevant repository/release credentials may perform publication only after the checklist is
approved. Merge, tag creation, release signing, GitHub Release publication, F-Droid submission,
external metadata PRs, and any eventual store upload are separate actions that each require explicit
authorization; preparing this repository does not authorize any of them.

F-Droid signs its own unsigned build from source. Upstream signing keys are never shared with F-Droid
or committed. Eventual Play/other store accounts, signing custody, staged rollout, rollback, privacy
listing, and reviewer responses remain maintainer responsibilities and are not delegated to an
automated test or PR author.

## PAR-401–406 validation record (2026-09-16)

The batch compared Android `90b67366638c21c30b2c748347a57bd8f184d491`, iOS
`8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
`b29695d547b78389ed7230b35cd133f7046b4b52`, and Arcane
`194e7ae87f0803bc2b85ed3a9a107fd432993ac4` (`v2.12.0-5-g194e7ae8`). The local SDK contract was
sufficient; no SDK source change or SDK PR was required.

- **Automated lanes:** `:app:testDebugUnitTest :app:assembleDebug` passed 380 tests in 66 suites with
  zero failures/errors/skips. `:app:lintDebug` passed with no new findings; the reviewed baseline has
  45 exact pre-existing errors and one advisory hint. The unsigned `:app:assembleRelease` build
  passed, and `apksigner` rejected the output as unsigned as intended. Instrumentation passed 9/9 on
  a fresh API 24 image, the retained API 30 regression image, and a fresh API 35 image. The API 24
  run installed/launched the app and exercised the suite; its API-29 system-bar assertion was
  assumption-skipped internally.
- **Server lane:** API 30 authenticated against disposable Arcane 2.10.2 image digest
  `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc` and a second isolated
  Arcane 2.11.1 image digest
  `sha256:527af49bc86e22d34de5c245b278852f347205c23016387931c6f4db1feb573b`.
  Current Arcane source was contract-inspected but not separately built live. Authentication restore,
  force-stop/reopen, multi-environment behavior, pins/routes, Update/Needs Attention/Activity entry
  points, environment sync, offline retained state/reconnect, shortcuts, notification channels,
  destructive-dialog cancellation, and the registered widget provider were exercised. The real
  destructive operation was dismissed; no live mutation was performed merely to satisfy a test.
- **Device/UI lane:** API 30 covered compact portrait/landscape, tablet-like and foldable-like display
  overrides, light/dark/automatic themes, gesture and three-button navigation, keyboard/D-pad, 100%
  and 200% font scale, English, accented `en-XA`, and RTL `ar-XB`. API 35 covered notification-denied
  launch/force-stop recovery, TalkBack service enablement and labeled keyboard focus traversal, widget
  and shortcut registration, and a credential-free backup attempt. The backup transport returned
  `Backup is not allowed`; the source/packaged allowlists were therefore verified, but no successful
  cloud restore round trip is claimed. No physical device or recorded spoken-output audit was
  available; those remain beta-promotion evidence, not hidden alpha claims.
- **Upgrade/rollback:** a disposable copy of the API 30 AVD installed prior version-code `260602`
  source (`358ab241bfc95e74234fc91a9968b9fcb73654ab`, paired with historical SDK
  `157131817ba96c0d8e0334f3615e7a45e6456216` and an AGP-only build compatibility adjustment), saved a
  sanitized server URL without credentials, then upgraded in place to `260901`. The configured server
  route survived force-stop/reopen. Reinstalling `260602` without downgrade authorization failed with
  `INSTALL_FAILED_VERSION_DOWNGRADE`, confirming that rollback must be a new forward version.
- **Cleanup:** the secondary server, disposable environment, API 24/API 35/upgrade AVD copies, APKs,
  user CA, debug trust override, screenshots, test data, and temporary source worktrees were removed.
  The original API 30 system image and the three pre-existing AVDs were restored/preserved. No store
  screenshot is retained. F-Droid remains blocked by the SDK license and proprietary credentials
  dependency documented in `fdroid-release-preparation.md`.

## Known limitations and exclusions

- Multi-server profiles (PAR-502), an Android AI assistant (PAR-503), and speculative Swarm workflow
  work (PAR-504) are intentionally deferred and are not beta promises.
- The app supports one configured Arcane server identity at a time, with multiple environments owned
  by that server. Older servers expose explicit unsupported states for newer capabilities.
- Localization is incremental; the first resource-backed slice is authentication, navigation,
  destructive confirmation, durable-operation state, and shared accessibility text. Unmigrated
  screens remain English in this alpha.
- F-Droid submission is blocked until the separate `libarcane-kotlin` source repository carries an
  explicit compatible FOSS license, the proprietary Google Play Services credentials dependency is
  removed/replaced for that build, and the exact release recipe passes official isolated validation.
- Screenshots/store assets are intentionally omitted unless they can be regenerated from sanitized
  disposable data and materially improve a future listing.
