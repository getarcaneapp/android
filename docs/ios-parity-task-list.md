# Android iOS-parity task list

Last updated: 2026-09-11

This is the working backlog for bringing Arcane Android to product-outcome parity with iOS. It
turns the findings in [the pinned gap analysis](ios-android-gap-analysis.md) into issue-sized work;
it does not repeat that research.

Use the [Android parity delivery workflow](parity-delivery-workflow.md) when taking an item from
this canonical backlog through local validation and a review-ready pull request.

The source comparison is pinned to:

- iOS `6088fcc0ef04dc906ce74e9129dffa96894a6da5`
- libarcane-swift `facc40e20e32b7d6600b004fd744a214bbd2a166`
- Android `75fde394f61ee8838f201f25b42a3e83af146513` (Image Insights branch base)
- libarcane-kotlin `275e7a533bd5f68063d3e275012041d0f846e251` (Image History PR #9)
- Arcane `5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105` (live compatibility target:
  2.10.2 tag `670ee2b34ea7b0fb2917643229b6ce9070ee9742`)

Revalidate conclusions against current source before starting an item. Record the Android, Kotlin
SDK, and Arcane server revisions in the resulting issue or pull request.

The 2026-08-21 refresh advances the iOS comparison from 0.5.4 to 0.7.0. It adds explicit backlog
coverage for passkeys/MFA, current scoped variables, image layer history, deploy options, template
discovery, container-registry names, and appearance persistence. It also promotes topology from a
shared enhancement to an Android gap and removes AI from the active parity roadmap because iOS 0.7.0
removed the Arcane Assistant.

## Recommended starting queue

The P0 correctness foundation through **PAR-101** and PAR-501's multi-environment validation are
complete. Continue with the remaining P1 workflow slices.

The Projects Workspace, Accounts and Administration, Container and Activity Reliability, and Image
Insights batches are complete; the last is on its review branches. Continue with the remaining
Ready items according to priority and dependencies.

## Status legend

| Status | Meaning |
| --- | --- |
| **Ready** | Evidence and prerequisites are sufficient to begin. |
| **Needs revalidation** | The item is plausible or previously active, but current source, PR, CI, SDK, or runtime behavior must be checked before changing code. |
| **Blocked/Hold** | Do not implement until the named external dependency or product decision changes. |
| **Deferred** | Deliberately sequenced behind foundation work or not required for current parity. |
| **Done/verify** | Later notes suggest the work progressed or landed; confirm current behavior and close or reopen with new evidence. |
| **Complete** | Required implementation, acceptance criteria, and validation evidence are complete. |

Priorities are **P0** correctness/security, **P1** high-frequency workflow parity, **P2**
resilience/native continuity, and **P3** maturity or optional expansion. Dependencies name task IDs;
`None` means the task can be started independently.

## Checkbox policy

- The checkbox on each task title records completion; **Status** records workflow state.
- Mark a task `[x]` only when every required acceptance criterion is checked and validation evidence
  is recorded.
- Tasks with **Blocked/Hold**, **Deferred**, **Needs revalidation**, or **Done/verify** status remain
  unchecked until their required implementation and verification are complete.
- Reopened tasks return to `[ ]`, along with any acceptance criteria that are no longer satisfied.
- Code inspection alone cannot satisfy acceptance criteria that require device, emulator, or live
  Arcane server validation.
- Existing progress, including the dashboard items under **Done/verify candidates**, identifies
  verification candidates and does not by itself establish completion.

## Definition of parity and done

Parity means Android provides the same useful outcome as iOS through Android-native conventions.
It does not mean copying Apple APIs or presentation. Examples include an ongoing notification
instead of a Live Activity, Glance instead of WidgetKit, and Android shortcuts/deep links instead
of App Intents.

An implementation task is done only when:

- its acceptance criteria are met and user-visible states cover loading, empty, error, success,
  authorization, and unsupported-server behavior as applicable;
- API work follows `Arcane contract -> libarcane-kotlin -> Android`, without app-local endpoint,
  DTO, auth, or stream duplication;
- focused tests are added and the Android CI-equivalent checks pass;
- SDK checks pass first when the SDK changes;
- device/emulator and live-server results are reported separately where required;
- sensitive state is scoped by normalized server and user identity; and
- the gap analysis and this backlog are updated when the work lands.

### 2026-09-09 consolidated live evidence

The Projects Workspace batch (PAR-103, PAR-113, PAR-114, and PAR-115) passed on an API 30 emulator
against disposable Arcane 2.10.2, covering workspace mutations and conflicts, variables/resolved
YAML, archived/GitOps restrictions, deploy options, templates, registry identity, and encrypted
credential preservation. SDK PR #6 merged as `b3d80a2`, followed by Android PR #49 as `26efa46`.

The Accounts and Administration batch used fingerprint
`google/sdk_gphone_x86_64/generic_x86_64_arm64:11/RSR1.240422.006/12134477:userdebug/dev-keys`
against a separate disposable Arcane 2.10.2 server. Account/profile propagation, password errors
and successful reauthentication, permission isolation, variable create/edit/delete/scope/sync across
26 environments, secret preservation/protection, server/account/environment isolation, current-
server upgrade gating, passkey/MFA availability, password step-up, and safe ceremony cancellation
passed. The provider page did not complete an actual WebAuthn ceremony; deterministic SDK and
Android tests cover Credential Manager mapping and all ceremony transitions without treating that
external-provider boundary as a successful credential.

The typed contract change is libarcane-kotlin commit `bf6df2f` in PR #7, merged as `7787bff` on
2026-09-10. Android integration was initially based on `26efa46` and refreshed onto `3da9770` after
the SDK merge. SDK and Android publication remain separate; the SDK contract has landed and Android
PR #51 is the remaining integration change.

Both live matrices used image digest
`sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`. No production data or
accounts were targeted. The standard SDK and Android gates below both passed after the live-test
harness was removed; `:arcane-android:testDebugUnitTest` also passed for the Credential Manager
adapter and browser bridge.

The standard checks are:

```text
# libarcane-kotlin, when changed
./gradlew :arcane-core:test :arcane-android:assembleRelease

# android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Phase 0: Revalidate active history and stop correctness leaks

- [x] **PAR-001 — Revalidate PR #29 authentication/session unlock**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Inspect the current branch, PR #29 state, review feedback, and final CI results. Reproduce
  the original session-unlock failure before deciding whether any code remains.
- **Acceptance criteria:**
  - [x] The PR's merge/close state, head revision, reviews, and CI conclusion are recorded.
  - [x] Login restoration and unlock are exercised for fresh login, restored session, invalid token,
    logout, and process recreation.
  - [x] The task is closed if current code already fixes the issue; otherwise a new issue describes the
    still-reproducible behavior and contains focused regression coverage.
- **Evidence:** PR #29 merged as `7a99c89` from final head
  `6a04b3bc514702aef10726f8aeb2793328bef2c2`; its Android workflow run 29106832332 succeeded. The
  sole P1 review thread was fixed, replied to, and resolved. Current-source revalidation found no
  later changes to the restore path. On 2026-08-21,
  `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug` passed all 104 tests, including
  six focused restore tests, and assembled the debug APK; `git diff --check` passed. A follow-up
  focused run passed after changing the invalid-session case to the SDK's exact
  `ArcaneError.Unauthorized`. Michael confirmed password login on 2026-08-21; OIDC is not configured
  on the test server and is not required for this task. A force-stop/relaunch restored the valid
  session without flashing login; logout/relaunch did not flash authenticated content; and process
  recreation restored without a login flash. The current source fixes the reported behavior, so no
  new issue is required.

- [x] **PAR-002 — Make change-server state and credential scoping safe**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** PAR-001
- **Scope:** Ensure changing servers cannot reuse the prior server's client, current user,
  capabilities, cookies, active environment, token, cache, or operation state. Normalize server
  identity and scope credentials by that identity.
- **Acceptance criteria:**
  - [x] Selecting change server immediately invalidates all in-memory state belonging to the old server.
  - [x] Persisted tokens and other sensitive state cannot be loaded for a different normalized server.
  - [x] Tests cover two servers, equivalent URL spellings, logout, invalid credentials, and process
    recreation.
  - [x] Device testing confirms no prior-server data flashes or actions remain available.
- **Evidence:** Canonical HTTP(S) origins and SHA-256 token namespaces follow the current iOS model.
  SDK `AndroidSecureTokenStore` accounts are origin-bound with guarded one-time legacy migration.
  Change server rotates the session scope/client generation and immediately resets client, user,
  capabilities, cookies, environment, loading/demo state, and visible navigation state. The saved
  URL, environment, and credential-origin binding are durably cleared before setup is shown; token,
  remote-session, and old-client cleanup then continue independently. The process port cache is
  origin-scoped. The focused auth/server/cache matrix passed 28 tests. On 2026-08-21,
  `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug` passed all 120 tests and assembled
  the debug APK; `git diff --check` passed. On a physical device, Michael confirmed that sign-out
  retained only the intended server selection, Change Server exposed blank setup without prior
  credentials/content, and an immediate force-stop/relaunch still restored blank setup. That test
  exposed and then verified the persistence-ordering fix in `bc0368d`. A live switch to a second
  server origin and manual equivalent-URL check were not performed; those cases are covered by the
  focused JVM matrix rather than claimed as device evidence.

- [x] **PAR-003 — Fix complete-container loading before local filtering**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Make the Containers tab filter a complete result set rather than the SDK's default
  first page of 20. Do not assume `limit = -1` is supported: inspect the Arcane handler and SDK
  semantics, then use explicit paging unless an unlimited query is documented and safely bounded.
- **Acceptance criteria:**
  - [x] The server and SDK behavior for page size, start, limit, ordering, and terminal-page detection is
    documented in focused tests or issue evidence.
  - [x] An environment with more than 20 containers displays and filters across the full set without
    duplicates, omissions, or infinite requests.
  - [x] Search/status filters are proven to run after complete loading, or are moved server-side with
    equivalent semantics.
  - [x] Loading, partial-page failure, refresh, cancellation, and empty states are covered.
  - [x] A device/emulator against a live server with more than 20 containers confirms display,
    filtering, refresh, and environment-change behavior without duplicates or omissions.
- **Validation evidence (updated 2026-08-21):**
  - Review: PR [#41](https://github.com/getarcaneapp/android/pull/41) merged as `fdfabe3`; its focused
    Greptile finding was fixed and verified in `2d97dad`.
  - Source pins: Android base `ca211804fcb3223b7b65abb0d13a97afad81799e`,
    libarcane-kotlin `89c8dd58886a099cdbea9cb9362c9262ba5851d9`, and Arcane
    `b501c49cc9f3d3433494f8334178ac65a59a013d`.
  - The SDK forwards `start` and `limit`; Arcane defaults them to `0` and `20`, documents
    `limit = -1` as one-page "show all", bypasses offset slicing for that value, and reports the complete
    `totalItems`. The app therefore uses one finite show-all request, de-duplicates by container ID,
    validates the unique count before publishing, and only then applies local search and filters.
    This avoids offset traversal across a changing collection whose supported sort keys have no
    unique secondary ordering.
  - `.\gradlew.bat :app:testDebugUnitTest --tests
    "app.getarcane.android.ui.screens.containers.ContainerPaginationTest" --rerun-tasks` passed all
    14 focused tests (0 failures, 0 errors, 0 skipped).
  - `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` passed all 98 unit tests and produced
    the debug APK; `git diff --check` passed.
  - On 2026-08-21, Michael verified the merged implementation on a physical device against a live
    environment with approximately 90 containers. Complete display, search, running/stopped filters,
    refresh, and environment switching all passed without duplicates or omissions.

- [x] **PAR-004 — Audit all complete-list call sites for silent pagination truncation**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** PAR-003
- **Scope:** Inventory every list call whose UI or calculation claims fleet-wide or complete
  results. Prioritize environments, dashboard totals/cards, updates, all-environment image updates,
  and environment management.
- **PAR-003 follow-up:** Evaluate a reusable complete-list pattern that selects an endpoint-supported
  show-all request or stable offset traversal and consistently handles count validation,
  de-duplication, cancellation, and atomic failure. Keep call-site inventory and changes within
  PAR-004.
- **Audit record:** [Complete-list loading and checked caller inventory](complete-list-loading.md)
- **Acceptance criteria:**
  - [x] A checked inventory records each caller as intentionally paged, intentionally bounded, or fixed.
  - [x] All complete-environment callers work with more than 20 environments.
  - [x] Shared paging logic has duplicate/empty/short/final-page, cancellation, and error coverage.
  - [x] UI copy does not claim complete totals when a view is intentionally bounded.
- **Validation evidence (2026-08-21):**
  - Source pins: Android base `fb0ac8f91b0acee6f0771a4f880208b74513beb8`,
    libarcane-kotlin `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`, and Arcane
    `0fd8820822f49e2da25739306bc9bc401253fa9e`.
  - Arcane's shared pagination contract documents `limit = -1` as a finite show-all request. The
    shared Android loader uses that request, de-duplicates stable identities, validates success and
    raw/unique totals, rejects malformed responses atomically, and propagates cancellation.
  - Focused runs passed 26 tests across `CompleteListLoaderTest`, `ContainerPaginationTest`, and
    `DashboardNeedsAttentionMapperTest` (0 failures, 0 errors, 0 skipped). The environment fixture
    contains 125 rows and verifies the exact show-all query.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed all 131 unit tests and assembled the
    debug APK; `git diff --check` passed.
  - A 2026-08-21 physical-device comparison found Android reporting 11 dashboard updates while the
    Arcane dashboard reported 4. Android was counting raw images with updates instead of Arcane's
    impacted-resource action items. The mapping now uses initial and streamed dashboard action-item
    totals and has focused regression coverage. The first retest remained at the loading placeholder
    because the SDK's action-items-only and aggregate-overview endpoints are not exposed by the
    pinned Arcane server revision; Android now reads action items from the supported per-environment
    dashboard snapshot instead. Michael's second physical-device retest showed the expected update
    count and verified the remaining PAR-004 items.

- [x] **PAR-005 — Revalidate Settings admin drill-down navigation**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** PAR-001
- **Scope:** Verify that Users, Notifications, System, and Roles retain their supported detail and
  action flows when opened through Settings. Admin/configuration destinations are intentionally not
  eligible as bottom-tab replacements under merged Android PR #5, so primary-admin-tab behavior is
  not part of this task.
- **Acceptance criteria:**
  - [x] Users, Notifications, System, and Roles open their supported list/detail routes through Settings.
  - [x] Nested Back behavior and tab switching do not strand a route; focused coverage resets protected
    routes after authorization loss and environment-bound routes after environment changes.
  - [x] Notification provider forms map current Arcane configuration keys and nested event settings
    correctly, preserve existing credentials safely, and have focused round-trip coverage.
  - [x] The final focused checks and Android CI-equivalent baseline pass after the notification form
    correction.
- **Implementation and validation evidence (updated 2026-09-01):**
  - Compared Android main `c52269f80a0c4e65c6bd09447ac0813495f7c6b0`, the pre-reconciliation
    PAR-005 branch `6a64eed19603450a1df417c00c8b1bc7d6e93f5a`, iOS
    `a3440b05238d2620b91d984557c87994ab15fb28`, libarcane-swift
    `38b5c32dde5b17eb0bc22b1c13fb4204699c8faf`, Arcane v2.10.0
    `963af121da1b7114bc155b640db4af4a0a80158a`, and the notification SDK changes.
  - Android PR #5 established that Users, Notifications, System, Roles, and other
    administration/configuration destinations are not pinnable bottom tabs. The former
    primary-admin-tab scope, implementation, tests, and acceptance requirement are therefore obsolete.
  - Michael's physical-device/live-server retest confirmed password login, Settings administration
    drill-down and Back behavior, and successful Settings > Notifications loading.
  - OIDC is not configured on the available Arcane server. Its device-flow matrix is deferred until a
    suitable provider is available; no OIDC pass/fail conclusion is recorded and it does not block this
    respecified navigation task.
  - libarcane-kotlin PR #3 merged as `b0e2576f008d1e0ca023e1e5f46a686a82f64df6`,
    adding Google Chat and tolerant future-provider decoding. PR #4 merged as
    `7a192f3ebc1a7c623eea6a4919085fc23180add2`, correcting raw notification settings response decoding;
    its tested head was `6df91357907fb75963d8e784a6e055387961e6b2`.
  - Notification loading is device-verified. Provider forms now use the Arcane v2.10.0 configuration
    keys and JSON value shapes, write event flags under `config.events` with server snake_case keys,
    preserve redacted credentials through the server's blank-value contract, retain unknown config,
    and validate Signal's mutually exclusive authentication modes. Focused deterministic mapping and
    round-trip tests pass.
  - Focused Settings route-safety and notification-provider tests passed. The final
    `./gradlew :app:testDebugUnitTest :app:assembleDebug` baseline passed and `git diff --check` is clean.
    Michael's device validation covered the reachable navigation and loading behavior; no destructive
    live notification-provider save was performed against production credentials.

- [x] **PAR-006 — Correct Android links, release notes, and version hygiene**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Replace iOS repository/issue destinations and copied iOS release claims with deliberate
  Android links and Android-verified notes. Align displayed notes with the app version.
- **Acceptance criteria:**
  - [x] Source, issue, documentation, privacy, and support links resolve to intentional destinations.
  - [x] Release notes contain only shipped Android behavior and have consistent version ordering.
  - [x] The current app version maps to an appropriate note, and future automatic presentation cannot
    show notes for an unshipped version.
  - [x] Link and release-note mapping logic has focused coverage.
- **Validation evidence (2026-09-01):**
  - Source pins: Android base `1aed66bdd32d9427776d5da69720325cccd37dfe`, current iOS
    `cdd05d89a1169bea50b53a12dcd00ca479233d26`, build-resolved libarcane-kotlin
    `6df91357907fb75963d8e784a6e055387961e6b2` (`origin/main`
    `7a192f3ebc1a7c623eea6a4919085fc23180add2`), and Arcane
    `0c7174f1089079d79535563ac2d54b032ea6914a`. No SDK or server contract changes were required.
  - Current iOS keeps its platform-specific issue destination and drives automatic What's New from
    the installed marketing version. Android now centralizes deliberate Android source/issues,
    Arcane documentation/privacy, project sharing, and Discord support destinations. Direct network
    checks returned HTTP 200 for each destination (Discord resolves to its canonical invite URL).
  - The copied iOS/TestFlight/VoiceOver/Liquid Glass/Swift SDK changelog was replaced by conservative
    Android `0.1.0` notes. Semantic version mapping sorts visible notes, hides entries newer than the
    installed APK, and exposes an exact match for badges or future automatic presentation. Unknown
    versions expose no notes.
  - `versionName` remains `0.1.0`; `versionCode` advances from the `260602` embedded in both public
    alpha artifacts to `260901`. The assembled APK manifest confirms both values.
  - Focused `AppLinksTest` and `ReleaseNotesTest` runs passed all 5 tests. The CI-equivalent
    `./gradlew :app:testDebugUnitTest :app:assembleDebug` baseline passed all 172 tests (0 failures,
    0 errors, 0 skipped) and assembled the debug APK; `git diff --check` passed.
  - On 2026-09-01, Michael's physical-device screenshots confirmed App Settings displays version
    `0.1.0` and build `260901`, and What's New displays only the Android `0.1.0` entry with the exact
    version marked **Installed**. External destination taps were not separately reported; their
    destinations are covered by the live HTTP audit and focused mapping test.

- [x] **PAR-007 — Define Android backup and data-extraction policy**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** PAR-002
- **Scope:** Replace template backup rules with explicit policy for tokens, server/account data,
  future caches, snapshots, and operation state across supported Android versions.
- **Audit record:** [Android backup and data-extraction policy](backup-and-data-extraction-policy.md)
- **Acceptance criteria:**
  - [x] Sensitive credentials, cookies, cached server responses, and operation payloads are excluded.
  - [x] Legacy backup rules and current data-extraction rules express the same intended boundary.
  - [x] Backup/restore behavior is checked on a supported emulator or documented platform test.
  - [x] No machine-specific paths, secrets, or backup artifacts are committed.
- **Validation evidence (2026-09-10):**
  - Source pins: Android base `27aa01b77f10f421c7ebea5d6648b66001847cd2`, current iOS
    `6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
    `7787bff82973302062d1d0c8db4c12f09547c5b0`, and Arcane
    `6a9ff7aa64fbec74e379b5dc9622699189093d73`. No SDK or server changes are required.
  - The persistence audit covers both app DataStores; pinned-resource and project-deploy
    SharedPreferences; SDK token ciphertext and Android Keystore ownership; cookies; Projects
    workspace edits; variables; account, passkey, MFA, and registry forms; response/image caches;
    databases; files and public downloads; saved-instance state; server/environment/resource
    identity; and operation payloads.
  - Android 11-and-lower cloud backup, Android 12+ cloud backup, and Android 12+ device transfer use
    the same deny-by-default allowlist. Only `file/datastore/arcane_tabs.preferences_pb` is portable;
    it contains local bottom-tab customization and last-tab selection. All credentials,
    server/account and environment/resource identity, caches, snapshots, operation state, other
    private files/preferences, databases, and app-specific external files are excluded by omission.
  - All 3 focused `BackupPolicyTest` tests passed. The CI-equivalent
    `./gradlew :app:testDebugUnitTest :app:assembleDebug` baseline passed all 261 tests (0 failures,
    0 errors, 0 skipped) and assembled the debug APK. Packaged `aapt2 dump xmltree` inspection
    confirmed manifest wiring and found only the approved DataStore path in the compiled legacy,
    cloud, and device-transfer sections; `git diff --check` passed.
  - Runtime validation used the debug APK (`0.1.0`/`260901`, target SDK 35) on the disposable
    Android 15/API 35 Google APIs AVD `par007_backup_api35`, fingerprint
    `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys`, against
    disposable Arcane 2.10.2 image
    `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`. A temporary,
    emulator-trusted HTTPS proxy connected the unchanged tested APK to the local HTTP server.
  - Pre-backup state covered a customized `Networks` bottom tab and last-selected `Projects` tab;
    server URL, session and SDK tokens; dark/custom appearance; non-local environment; project pin;
    non-default project deploy options; loaded server data; and an unsent project form. Both the
    encrypted local cloud transport and the separately initialized Google D2D test transport
    returned package `Success`.
  - After uninstall/reinstall in each cycle, the sole restored app-private file was
    `files/datastore/arcane_tabs.preferences_pb`. Setup had an empty server URL and no preference or
    token DataStore, SharedPreferences, cache, database, other file, or operation form. After
    reauthentication, the customized tab layout and selected `Projects` tab returned; appearance,
    environment, pins, and deploy options were defaults, while the disposable project appeared only
    when current server data reloaded.
  - The documented transport/test settings were restored and the disposable project, proxy, AVD,
    APK copy, screenshots, and inspection files were removed. No credentials, backup data, or
    machine-specific artifacts are present in the change.

- [x] **PAR-008 — Audit coroutine cancellation and stream ownership**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Find broad exception handling in stores and streams, rethrow `CancellationException`,
  and ensure environment/server/screen changes cancel the correct work.
- **Audit record:** [Coroutine and stream ownership](coroutine-stream-ownership.md)
- **Acceptance criteria:**
  - [x] Broad catches no longer convert cancellation into user-visible failures or reconnect loops.
  - [x] Tests cover cancellation during refresh, paging, reconnect, and environment/server changes.
  - [x] At most one intended stream/job owner remains for each screen-level operation.
  - [x] No stale result from a canceled prior environment can overwrite current state.
- **Validation evidence (2026-08-21):**
  - Source pins: Android base `b49f4d3c36b224b865d423a0febe93a26ca42689`,
    libarcane-kotlin `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`, and Arcane
    `0fd8820822f49e2da25739306bc9bc401253fa9e`.
  - Focused runs passed 33 tests across `CoroutineFailuresTest`, `DashboardStreamStoreTest`,
    `CompleteListLoaderTest`, and `UpdaterRunScreenTest` (0 failures, 0 errors, 0 skipped), including
    refresh, paging, reconnect, environment-removal, and client-replacement cancellation.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed all 143 unit tests and assembled the
    debug APK; `git diff --check` passed.
  - Michael's physical-device/live-server smoke test passed dashboard reconnect, environment
    switching without stale stream content, Activity Center refresh and screen departure, container
    Logs/Stats/Terminal departure and reopen, and recovery after force-stop.

- [x] **PAR-009 — Persist and apply Light/Dark/Auto appearance**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Replace the screen-local theme selection with one persisted preference owned at the app
  level and applied at the `ArcaneTheme` root. Preserve the existing accent-color behavior and
  system-theme default.
- **Acceptance criteria:**
  - [x] Light, Dark, and Auto update the whole application immediately and survive process recreation.
  - [x] Auto follows system night-mode changes without reopening Settings.
  - [x] Invalid or missing persisted values fall back to Auto, and migration does not disturb accent.
  - [x] State mapping and persistence have focused tests; representative screens are device-checked in
    light/dark mode.
- **Validation evidence (2026-08-22):**
  - Source pins: Android base `519adca53d5e08d83133ef8ea6a77ccd94b9db71`,
    libarcane-kotlin `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`, and Arcane
    `0fd8820822f49e2da25739306bc9bc401253fa9e` (no SDK or server changes were required).
  - `PrefsAppearanceTest` passed 5 focused tests covering stable Light/Dark/Auto values, system-mode
    resolution, missing and invalid fallback, app-owned asynchronous persistence, and isolation from
    the existing accent preference.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed all 153 unit tests and assembled the
    debug APK; `git diff --check` passed.
  - Michael's physical-device check passed immediate whole-app Light/Dark switching, readable system
    bars, Back/re-entry and rapid-departure persistence, force-stop restoration of theme and accent,
    and Auto tracking Android system light/dark changes without reopening Appearance.
  - Follow-up (2026-09-01): Android PR #47's fresh CI run reproduced an initialization race where a
    stale first DataStore emission could overwrite a newer app-owned theme/accent selection. Pending
    selections now win until persistence observes them. Five forced focused reruns and a fresh
    remote-SDK `:app:testDebugUnitTest :app:assembleDebug` baseline passed.

## Phase 1: Validate destructive behavior and complete daily workflows

- [x] **PAR-101 — Validate System Prune end to end**

- **Status:** Complete
- **Priority:** P0
- **Dependencies:** PAR-002
- **Scope:** Audit stale PR #12 without assuming its result handling is current, then exercise prune
  against disposable environments with known resources; do not infer server effects from UI or unit
  tests.
- **Acceptance criteria:**
  - [x] A real-device/emulator plus live-server matrix covers success, nothing-to-prune, partial/error,
    authorization failure, disconnect, cancellation, and repeated invocation.
  - [x] Before/after server state proves exactly which eligible resources were removed and retained.
  - [x] The UI reports server results accurately and cannot imply success after a failed mutation.
  - [x] Results identify tested Android, SDK, server, and API versions.
- **Validation evidence (2026-09-10):**
  - Source pins: Android base `9b16902dde5c96fac0c55fd5360288d6c3ac544f`, current iOS
    `6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
    `7787bff82973302062d1d0c8db4c12f09547c5b0`, and Arcane
    `a50ab984528cf443c07e1a58052a09af2632dd63`. Current iOS loads the server's
    per-environment prune defaults, gates the action with `system:prune`, and treats an accepted
    background activity as pending Activity Center work rather than a completed prune.
  - Stale PR #12 (`97209135b01d2cbc379be287b38d625b40e8811f`) was closed without merge,
    had no review, and had two failed Build APK checks. Its useful defaults, build-cache/result, and
    dismissal ideas were re-evaluated against the current activity contract rather than reused. The
    resulting fix loads safe server defaults, checks `system:prune` for the target environment,
    prevents duplicate submission, preserves structured cancellation, reports synchronous errors
    and partial results as errors, and directs accepted asynchronous work to Activity Center.
  - Exact current main reproduced the defect: invalid container age
    `definitely-invalid-duration` created failed activity
    `c3422f42-781f-49a8-9c83-e967afaf951f` while Docker state remained byte-for-byte unchanged
    (`docker system df` hash `9468160ad60d59907aaaa852855c23043f976879246dff7050c639bdf30afb3c`),
    but Android displayed the green message `No resources pruned.` The fixed build created failed
    activity `067b9856-c546-4b0c-a44d-2d6d18414d23` for the equivalent invalid request, retained
    the identical Docker-state hash, and did not show completed-success feedback.
  - Live API 30 testing used AVD `arcane_test_api30` against disposable LXD `arcane-e2e`, Arcane
    2.10.2 image digest
    `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`,
    Docker 29.1.3 / API 1.52, and environment `0` (`Local Docker`). Successful activity
    `a4e43c9f-37dc-4481-9bed-85583fc9bb4e` removed the known stopped container, dangling image,
    unused volume, and unused network while retaining the running container, tagged image, mounted
    volume, and attached network. Docker counts changed from 5 to 4 images, 3 to 2 containers, and
    2 to 1 volumes exactly as expected.
  - The same measured matrix covered a no-op (`8675de56-5831-4eae-a15c-005c15c0150d`, identical
    before/after hash `51b67ea642f7d3a229a3c3ad7fbc9f0a45ac08c231a30f188ae9e79a40fb868a`),
    partial failure (`b4b1c4c0-d5f6-4ca5-9f2b-0a01ac9bb4fe`, unused network removed while the
    invalid-age container prune failed and its stopped container remained), and two simultaneous
    taps producing exactly one activity (`a3755975-13fb-49c4-ac04-6742ed11ef97`).
  - Back navigation from the configured sheet produced no activity and left Docker state unchanged
    (activity count 56 to 56; hash
    `5be0da1ae58f7b37523e5d7badadc92d69e7734c934b695178c9271553a46af0`). With Arcane stopped,
    Android showed `Couldn't reach the server` rather than success; after restart, retry created
    successful activity `1c753672-888a-498c-8260-028e1f31f77c`, and Activity Center showed
    `System prune completed` for `Local Docker`.
  - A disposable environment-scoped viewer received HTTP 403 `permission denied: system:prune`
    with identical before/after Docker hash
    `fbb44140423ee7c650b0945e1353c51a639069d77f70b12a0635b6d1ea84125a`; the current Android build
    exposed neither the dashboard toolbar prune control nor the environment-card action. All
    disposable users, containers, images, volumes, and networks were removed afterward; the AVD
    app data and temporary trust configuration were also removed.
  - Focused prune/default/result/permission tests passed 10 tests. The CI-equivalent
    `./gradlew :app:testDebugUnitTest :app:assembleDebug` baseline and `git diff --check` passed;
    the complete diff received an independent self-review because automated reviews were inactive.

- [x] **PAR-102 — Match per-environment Upgrade Arcane capability gating**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-004
- **Scope:** Recheck current server/SDK support for the iOS `checkUpgrade.canUpgrade` outcome. Show
  the environment action only from authoritative capability/version data, not duplicated UI
  heuristics.
- **Acceptance criteria:**
  - [x] Current Arcane handler/type and both SDK contracts are compared before implementation.
  - [x] The action is visible and enabled only when the selected environment can upgrade.
  - [x] Unsupported, unauthorized, loading, error, and older-server states are explicit.
  - [x] Multi-environment tests prove gating is calculated per environment.

  **Validation evidence (2026-09-09):** The SDK models Arcane's authoritative upgrade check and
  Android resolves it for the selected environment without version heuristics. Focused tests cover
  eligible/current, unsupported, unauthorized, loading, error, older-server, cancellation, and
  independent multi-environment states. On the API 30 live matrix, current Arcane 2.10.2 correctly
  hid the upgrade action while 25 unreachable disposable remotes retained independent error state.

- [x] **PAR-103 — Build the existing-project file workspace**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-002
- **Scope:** Use the typed Kotlin SDK project-file operations to add a file tree; Compose, `.env`,
  and text editing; save/create; rename/move/delete; variable-resolution preview; and resolved YAML.
  Keep archived and GitOps projects read-only where required.
- **Acceptance criteria:**
  - [x] Users can inspect and perform every supported file mutation with clear dirty/conflict state.
  - [x] Destructive actions identify the project/environment and require confirmation.
  - [x] Reload, save failure, concurrent server change, unsupported/binary file, and archived/GitOps
    states preserve data and explain why an action is unavailable.
  - [x] Typed SDK calls are used directly and mapping/state logic has focused tests.

  **Validation evidence (updated 2026-09-09):** SDK PR #6 merged as `b3d80a2`, followed by Android
  PR #49 as `26efa46`. Focused mapping, protected-path/GitOps structure, conflict rebase,
  partial-save, path-safety, permission, and read-only tests passed in the full SDK and Android
  gates. Arcane's present Compose/`.env` update contract is explicitly surfaced as last-write-wins.
  Live testing passed on an API 30 emulator against disposable Arcane 2.10.2 image digest
  `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`, covering workspace
  mutations, conflicts, variables/resolved YAML, and archived/GitOps restrictions.

- [x] **PAR-104 — Add signed-in account/profile management**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-002
- **Scope:** Add a non-admin account destination for viewing/updating display name and email,
  changing password, displaying avatar/Gravatar behavior, signing out, and changing server using
  existing SDK support.
- **Acceptance criteria:**
  - [x] Profile editing and password change validate inputs and report server errors without losing data.
  - [x] The route is clearly distinct from administrator user management.
  - [x] Updated identity propagates to current-user state and authorization-dependent UI.
  - [x] Sign-out and change-server paths satisfy PAR-002's invalidation rules.

  **Validation evidence (2026-09-09):** Focused tests cover profile/password validation, avatar
  mapping, diagnostic redaction, mutation state, cancellation, and scoped session invalidation.
  Live API 30 testing verified immediate display-name propagation, email/avatar behavior, password
  policy errors, a successful password change and re-login, the non-admin account boundary, sign
  out, and sign out plus change server against disposable Arcane 2.10.2.

- [x] **PAR-105 — Add image attestation workflows**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** None
- **Scope:** Use existing Kotlin SDK support to provide attestation list, filter, detail, and safe
  statement copy for an image.
- **Acceptance criteria:**
  - [x] Empty, malformed/unknown, loading, error, unauthorized, and unsupported-server states are clear.
  - [x] Selection remains tied to the correct image digest and environment.
  - [x] Copied/exported data is complete and intentionally labeled.
  - [x] DTO/serialization coverage remains in the SDK; Android adds state and presentation tests.

  **Validation evidence (2026-09-11):** Android's image detail now opens an environment-, session-,
  and immutable-image-scoped Attestations destination using the existing typed SDK service. It
  provides predicate filtering, exact digest/predicate/platform selection, defensive unknown and
  optional-field rendering, a no-trust warning, bounded raw preview, complete explicitly labeled
  clipboard copy, and complete Storage Access Framework export. The shared nine-test Image Insights
  suite covers identity/route scoping, filtering and filtered-empty state, exact selection, stale
  success/failure rejection, cancellation/error mapping, formatting, and bounded preview versus
  complete export. Live API 30 validation against Arcane 2.10.2 exercised two predicate types,
  unknown predicate/no-subject data, a 110,330-byte statement, unattested, malformed, registry-error
  recovery, unauthorized, v2.1.0 unsupported, refresh, environment switching, force-stop, and
  repeated navigation. Presence is presented as metadata, never as cryptographic verification.
  Full source, contract, fixture, limitation, and cleanup evidence is recorded in
  [Image Insights parity evidence](image-insights-parity.md).

- [x] **PAR-106 — Complete container lifecycle and detail actions**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-003
- **Scope:** Compare current Android actions with supported server/SDK operations and add high-value
  omissions such as pause and kill where appropriate, without copying unsupported iOS behavior.
- **Acceptance criteria:**
  - [x] An action inventory records parity, intentional omission, permission gate, and server gate.
  - [x] Added actions use resource/environment-specific confirmation and accurate result/error feedback.
  - [x] State refreshes after success without losing selection or showing stale controls.
  - [x] Device/live-server validation covers each destructive lifecycle action added.

  **Validation evidence (2026-09-11):** The inventory and contract decisions are recorded in
  [container-activity-reliability.md](container-activity-reliability.md). API 30 AVD testing against
  disposable Arcane 2.10.2/Docker 29.1.3 proved pause, unpause, and SIGKILL with Docker state before
  and after, resource/environment-specific confirmations, updated controls, and retained detail
  selection. Permission, version, state, feedback, and forced-delete confirmation rules have focused
  tests. Rename is intentionally absent because pinned Arcane has no matching handler.

- [x] **PAR-107 — Add log and terminal copy/share/export continuity**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-008
- **Scope:** Provide Android Sharesheet/Storage Access Framework or MediaStore outcomes for container
  and project logs, plus consistent search, copy, pause/resume, timestamp, ANSI, retention, and
  terminal copy/clear behavior.
- **Acceptance criteria:**
  - [x] Users can copy selected content and share/export a clearly scoped log without truncation surprises.
  - [x] Large streams use bounded memory and cancellation; secrets receive an explicit product review.
  - [x] Export failure, permission/canceled picker, reconnect, and environment changes are safe.
  - [x] Shared formatting/state logic has focused tests and device sharing is exercised.

  **Validation evidence (2026-09-11):** Focused tests cover bounded line/character retention,
  reconnect deduplication, ANSI/plain export formatting, timestamps, search inputs, manual versus
  scroll pause, cancellation, safe filenames, terminal retention, and clearing. On the AVD, ANSI and
  noisy fixtures exercised the container/project viewers; the Android Sharesheet received stripped
  plain text, the Storage Access Framework showed a scoped filename and preserved logs on
  cancellation, manual pause retained and counted new lines until explicit resume, and an interactive
  terminal proved command output, copy-all, and clear. Export confirmation names the source,
  environment, exact retained window, discarded/shortened scope, and secret risk.

- [x] **PAR-108 — Add lifecycle-aware live event refresh**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-008
- **Scope:** Provide live-enough Events behavior via a server-supported stream or bounded,
  lifecycle-aware polling. Do not invent an app-local protocol.
- **Acceptance criteria:**
  - [x] The chosen mechanism and interval/backoff are based on the current Arcane/SDK contract.
  - [x] Events update while visible, stop when no longer owned, and do not duplicate or reorder entries.
  - [x] Refresh, reconnect, partial failure, environment change, and stale-state UI are covered.
  - [x] Battery/network impact is bounded and documented.

  **Validation evidence (2026-09-11):** Current Arcane and SDK expose paginated reads but no Events
  stream, so the visible route uses five-second polling with 10/20/40/60-second failure backoff and a
  200-row cap. Deterministic tests prove ID deduplication, stable timestamp ordering, and bounded
  backoff. On the AVD, a newly emitted `user.login` event was fetched on the next visible poll (server
  creation 16:38:11 UTC, app request 16:38:12 UTC); server request logs proved the bounded cadence.
  Manual/live pause, route ownership, client/session generation guards, and refresh error retention
  use structured cancellation so hidden/background routes perform no polling.

- [x] **PAR-109 — Add Activity Center terminal-failure retry**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-008
- **Scope:** Add an explicit recovery path when live or paginated activity loading terminates, while
  preserving healthy data from other environments.
- **Acceptance criteria:**
  - [x] Users can identify which environment/source failed and retry it without discarding healthy results.
  - [x] Repeated retry is bounded, cancellation-aware, and does not duplicate activities.
  - [x] Tests cover terminal stream error, heartbeat timeout, one-environment failure, full failure, and
    successful recovery.

  **Validation evidence (2026-09-11):** libarcane-kotlin PR #8 corrected the SDK to Arcane's
  multiplexed activity stream and merged as `b21faefd091de53fa30e6b5b910c66f49ec8076c` after its
  102-test/release-assembly baseline passed. Android tests cover the finite 1/2/4-second reconnect
  budget, heartbeat timeout and healthy heartbeat, owner cancellation, one-source failure, full
  failure, source identity, and individual recovery. On the AVD, a deliberately unreachable second
  environment showed its own Retry while Local Docker activities remained healthy; retry preserved
  them. A later terminal stream timeout exposed the separate stream Retry, whose recovery cleared
  only that failure while the unreachable environment remained identifiable.

- [x] **PAR-110 — Add passkey sign-in and MFA management**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-001, PAR-002
- **Scope:** Inspect the current Arcane passkey/WebAuthn handlers and Swift SDK, add typed passkey,
  step-up, MFA-policy, and recovery support to `libarcane-kotlin`, then integrate Android Credential
  Manager for login and signed-in account management. Do not duplicate ceremony JSON or endpoints in
  the app.
- **Acceptance criteria:**
  - [x] Server capabilities gate passkey login, enrollment, rename/delete, step-up, MFA policy, and
    recovery; older/unsupported servers retain password/OIDC paths.
  - [x] Credential creation/assertion maps origin, RP ID, challenge, cancellation, and provider errors
    through typed SDK models without logging sensitive ceremony data.
  - [x] Login, pending MFA, account management, last-passkey restrictions, recovery, process recreation,
    and server/account changes fail safely.
  - [x] SDK contract tests, Android state tests, and device/live-server passkey validation are recorded
    separately with Android, SDK, and Arcane revisions.

  **Validation evidence (2026-09-09):** Kotlin contract tests cover every typed passkey, MFA,
  step-up, recovery, and mobile-login route plus browser-bridge state, bounded manifest retrieval,
  sensitive-model redaction, and Android Credential Manager mapping. Android tests cover capability
  and permission gates, enrollment/delete policy, recovery-material clearing/normalization, grant
  expiry, provider cancellation, browser return/dismissal, retry, and stale-callback rejection. On
  API 30, Arcane 2.10.2 reported no enrolled passkeys and MFA
  disabled; password step-up failure/success and browser-return cancellation were verified. The
  disposable server served the mobile manifest/bridge, but its WebAuthn provider page did not
  complete a ceremony, so no credential was fabricated and that provider-dependent boundary is
  recorded separately from the deterministic Credential Manager coverage.

- [x] **PAR-111 — Add scoped global-variable management**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-002, PAR-004
- **Scope:** Model the current v2 variables API in `libarcane-kotlin`, then add permission-gated Android
  list/search/create/edit/delete/sync flows for secret and non-secret values scoped to all or selected
  environments. The older template-variable endpoints are not the same contract.
- **Acceptance criteria:**
  - [x] Variable models, permission constants, mutations, sync requests, and per-environment sync status
    are typed and tested in the SDK first.
  - [x] Secret values never appear in logs, clipboard actions, accessibility text, or stale UI; copying
    non-secret keys/values is explicit.
  - [x] Unsupported, unauthorized, empty, partial-sync, failed-sync, concurrent edit, and server change
    states preserve scope and report accurate outcomes.
  - [x] More than 20 environments can be selected and reported without omissions or duplicate sync work.

  **Validation evidence (2026-09-09):** SDK serialization/route tests and Android reducer/model tests
  cover permission mapping, search, create/edit/delete, optimistic conflicts, secret handling,
  all/selected scope, partial failure, retry, cancellation, deduplication, and stale-result rejection.
  Live API 30 testing created, searched, edited, synced, and deleted secret and non-secret variables;
  preserved an omitted secret value; blocked the route for an unauthorized account; protected the
  secret screen with `FLAG_SECURE`; and selected 24 named environments. Sync reported one local
  success and 25 unreachable remote failures once each without omissions or duplicate work.

- [x] **PAR-112 — Add image layer history**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** None
- **Scope:** Add the typed per-image Docker layer-history contract to `libarcane-kotlin`, then expose a
  History destination in image detail. Keep this distinct from the existing image-build history API.
- **Acceptance criteria:**
  - [x] Layer ID/missing-layer, command, size, created time, and tags decode unknown/optional fields
    defensively in SDK tests.
  - [x] Loading, empty, error, unauthorized, and unsupported-server states identify the image and
    environment without leaking a prior selection.
  - [x] Refresh and environment/server changes cannot publish history for the wrong image digest.
  - [x] Focused Android tests and live-server validation cover a multi-layer image and a history-less
    image.

  **Validation evidence (2026-09-11):** libarcane-kotlin PR #9 adds the typed per-image history
  route and defensive model, with two focused contract tests covering route encoding, server order,
  null/missing fields, and ignored unknown fields. Its full gate passed 104 tests with zero failures
  or errors and one skipped (103 passed), plus Android release assembly; GitHub CI is green. Android
  presents Docker layer history—not image-build or updater history—with metadata-only layers,
  commands, sizes, timestamps, tags, and comments. The full Android gate passed 303 tests and debug
  assembly. Live API 30 testing proved newest-first rendering of seven deterministic layers, a
  genuine zero-history scratch image, pull refresh, exact image/environment labeling, cancellation
  on navigation, environment switching, unauthorized and v2.1.0 unsupported states, and no stale
  data after force-stop/reopen or repeated navigation. See
  [Image Insights parity evidence](image-insights-parity.md).

- [x] **PAR-113 — Add scoped project deploy options**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-002
- **Scope:** Use the Kotlin SDK's existing `DeployOptions` to let users choose pull policy and force
  recreation before deploy. Store defaults by normalized server, user, environment, and project.
  PAR-202 will later adopt the same options when it becomes the operation owner.
- **Acceptance criteria:**
  - [x] Default, always-pull, never-pull, force-recreate, cancel, unsupported, and server-error behavior
    are explicit and map to typed SDK values.
  - [x] Preferences cannot cross servers, accounts, environments, or projects and are cleared or
    migrated according to PAR-002.
  - [x] The launched stream receives exactly the selected options and reports the server result without
    fabricating success after failure or cancellation.
  - [x] Mapping/persistence tests and device/live-server deploy evidence are recorded.

  **Validation evidence (updated 2026-09-09):** Typed missing/always/never and force-recreation
  values flow unchanged to deploy and redeploy streams; scoped persistence and terminal failure
  behavior have focused tests. SDK PR #6 merged as `b3d80a2`, followed by Android PR #49 as
  `26efa46`; both automated gates passed. Deploy options passed live testing on an API 30 emulator
  against disposable Arcane 2.10.2 image digest
  `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`.

- [x] **PAR-114 — Complete template discovery, import, and deployment**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-004
- **Scope:** Extend the existing Android registry CRUD, grouped browser, preview, and deploy flow with
  current iOS outcomes: search, local/remote source filtering, metadata, remote download, and complete
  result loading through the typed Kotlin template service.
- **Acceptance criteria:**
  - [x] Search and source filters cover all loaded templates and clearly distinguish local, configured-
    registry, and remote entries.
  - [x] Metadata/preview and remote download handle unsupported, malformed, duplicate, unauthorized,
    offline, and partial-page states without losing the current selection.
  - [x] Deploying a selected template preserves its identity and content through project creation and
    hands long-running work to PAR-202 when applicable.
  - [x] Pagination/filter/download state has focused tests and a live-server import/deploy check.

  **Validation evidence (updated 2026-09-09):** Complete typed pagination, search/source filtering,
  composite identity, metadata/preview retry, remote import, and exact-content project-creation
  handoff are implemented with focused state and permission tests. SDK PR #6 merged as `b3d80a2`,
  followed by Android PR #49 as `26efa46`; both automated gates passed. Template discovery, import,
  and deployment passed live testing on an API 30 emulator against disposable Arcane 2.10.2 image
  digest `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`.

- [x] **PAR-115 — Add container-registry display names**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** None
- **Scope:** Current Arcane and both SDKs have no registry `name` field. Derive a stable Android
  display name from description/provider/URL with URL/ID disambiguation, preserve credentials and
  unrelated fields on edit, and add the current `repositoryNames` field to Kotlin SDK
  read/create/update/sync models.
- **Acceptance criteria:**
  - [x] Missing, blank, duplicate, and unknown-server values decode safely and display a stable URL/ID
    fallback.
  - [x] Create/edit preserves credentials and unrelated registry fields and never logs token/secret
    values.
  - [x] List, preview, pull-usage, and destructive confirmations identify the same registry clearly.
  - [x] SDK serialization plus Android mapping/form tests pass against old and current payload fixtures.

  **Validation evidence (updated 2026-09-09):** Exact current-server and both-SDK audits disproved
  the stale `name`-field premise. Derived display identities, duplicate fallback, unknown provider
  types, credential-preserving requests, and repository-name compatibility have focused tests. SDK
  PR #6 merged as `b3d80a2`, followed by Android PR #49 as `26efa46`; both automated gates passed.
  Registry identity and credential preservation passed live testing on an API 30 emulator against
  disposable Arcane 2.10.2 image digest
  `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`.

## Phase 2: Own long-running operations before adding system surfaces

- [x] **PAR-201 — Specify the app-level operation store**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-002, PAR-008
- **Scope:** Write a reviewed design before implementation for deploy, pull, build, update, and other
  user-initiated long-running work. Define identity, ownership, persistence, reconnect/reattach,
  cancellation, bounded logs, concurrency, process death, server/user/environment scoping, and
  v1/v2 behavior.
- **Acceptance criteria:**
  - [x] The specification includes state transitions, persistence schema, invalidation, recovery,
    retention, concurrency policy, and security boundaries.
  - [x] It identifies which operations can reattach server-side and which can only preserve a terminal
    or unknown state after process death.
  - [x] It assigns one source of truth and explicitly prevents screens, services, and notifications from
    becoming competing operation owners.
  - [x] Test strategy and migration/rollout plan are included before implementation began.

  **Validation evidence (2026-09-11):** `docs/durable-operation-store.md` records the reviewed
  permanent owner, bindings, complete state machine, recovery/cancellation contracts, schema and
  migrations, bounded retention, cleanup, backup/security policy, Activity Center relationship,
  exact routes, and the decision not to use WorkManager or a foreground service. An independent
  design review completed without blocker or high-risk findings before PAR-202 implementation was
  accepted. Source pins: Android `0e65cbebbf924e7553952f751c32debdbf5b2b57`, iOS
  `6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
  `70086644c63624340f9827a056c19edf8ca7e05f`, and Arcane
  `5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105`.

- [x] **PAR-202 — Implement the app-level operation store and in-app surface**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** PAR-201
- **Scope:** Implement the approved store and an in-app operation center/floating progress surface.
  Migrate one representative operation first, then the remaining approved operation types. Treat
  configurable activity-start feedback as a bounded projection of this store, not a second owner.
- **Acceptance criteria:**
  - [x] Operations survive screen changes and expose progress, bounded logs, reconnect, cancel, success,
    failure, and indeterminate/unknown states from one owner.
  - [x] Server, account, and environment changes cannot cross-contaminate operation state.
  - [x] Process-death recovery follows the spec and never fabricates successful completion.
  - [x] Optional activity-start feedback distinguishes user/system work, keeps environment context, and
    opens the authoritative operation/activity destination without notification spam.
  - [x] State-machine, persistence, concurrent-operation, cancellation, and migration tests pass.

  **Validation evidence (2026-09-11):** libarcane-kotlin PR #10 merged as
  `af6fa681d1c4c1e1af8a26a774f69a193df02680`, adding the typed Activity lookup, cancellation,
  stream correlation, and fleet-update contracts used exclusively by Android. The app-level store
  owns project deploy/redeploy, pull/build, image pull, container update/redeploy, updater, and fleet
  work. The 322-test Android JVM suite passed with no failures or skips, including focused state,
  schema/migration, bounded-output, duplicate, stale-binding, cancellation, retry, fleet, image-target,
  route-recreation, restored-cancellation, row-cap, unsupported-server, actual DataStore round-trip,
  and backup-policy coverage;
  `:app:assembleDebug` passed with both the sibling SDK and the merged
  remote `main` fallback. Live API 35 tests against disposable
  Arcane 2.10.2 (`sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`)
  proved success, explicit server failure, repeated cancellation, duplicate suppression, concurrent
  records, tab/navigation/rotation/background survival, terminal relaunch, process-kill reattachment,
  server restart truthfulness, exact Activity correlation, and a 2,500-line bounded-output fixture.
  Capability-denied recovery is covered deterministically and resolves to interrupted/unknown rather
  than fabricated success.

- [x] **PAR-203 — Add Android ongoing operation notifications**

- **Status:** Complete
- **Priority:** P2
- **Dependencies:** PAR-202
- **Scope:** Project operation-store state into Android notifications. Use foreground execution only
  for eligible user-initiated work that Android policy requires to continue beyond the screen.
- **Acceptance criteria:**
  - [x] Notifications are projections of PAR-202 state and never own or duplicate the operation.
  - [x] Progress, cancel/open actions, completion, failure, permission denial, and notification-disabled
    behavior are correct.
  - [x] Foreground-service types, lifecycle, disclosure, and recent Android background restrictions are
    satisfied.
  - [x] Device tests cover backgrounding, rotation, process pressure/recreation, multiple operations,
    server change, and notification taps.

  **Validation evidence (2026-09-11):** API 35 live tests covered permission allow, deny, and revoke;
  grouped collision-free updates; running, success, failure, and cancelled projections; cancellation;
  private bounded content; and exact operation plus Activity Center tap routes. Revoking permission
  during active work killed the process, while the server Activity continued; regrant and relaunch
  reattached accurately. API 30 (`arcane_test_api30`) launched the actual APK without a runtime
  notification prompt, confirming pre-Android-13 behavior. Packaged-manifest inspection found only
  `POST_NOTIFICATIONS`, the non-exported action receiver, no app foreground service, and no foreground-
  service permission or type. API 35 restrictions therefore do not justify WorkManager or a foreground
  service for the chosen server-owned, reattachable execution model. Cleanup verification found zero
  fixture projects, users, environments, containers, images, AVDs, APKs, CA files, redirects, LXD
  devices, screenshots, exported logs, or other durable-test temporary files; the existing API 30 AVD
  was retained and the app was uninstalled from it. The independent complete-diff final review passed
  with no remaining blocker or high-risk findings.

## Phase 3: Resilient reads and Android-native continuity

- [x] **PAR-301 — Design and implement a scoped API response cache**

- **Status:** Complete
- **Priority:** P2
- **Dependencies:** PAR-002, PAR-004
- **Scope:** Add bounded stale-read caching for dashboard and selected high-value resource lists.
  Scope entries by normalized server, user, environment, request identity, and relevant permissions.
  Do not queue mutations.
- **Acceptance criteria:**
  - [x] The design defines expiry, LRU/size bounds, request coalescing, invalidation, schema migration,
    stale markers, and sensitive-data treatment.
  - [x] Initial screens render last-known data offline and visibly distinguish stale from current state.
  - [x] Auth/server/environment changes and successful destructive mutations invalidate affected entries.
  - [x] Corrupt cache, permission change, refresh race, no-network, and storage-bound tests pass.

  **Design and source evidence (2026-09-15):** The pre-implementation fetch/revalidation used Android
  `ed03850f3c378cefd2824aacc64dba1f896bd03f`, iOS
  `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
  `af6fa681d1c4c1e1af8a26a774f69a193df02680`, and Arcane
  `9e5bfea2f213a63f11c83f63f77e3c8499f23aba`; PR #57 was present on fetched Android `origin/main`.
  The permanent [resilient-read design](resilient-reads-foundation.md) records the version-1 key and
  envelope schemas, seven per-resource TTL/stale policies, 4 MiB/64-entry memory LRU, 24 MiB/256-entry
  disk LRU, 2 MiB entry cap, atomic replacement, fail-closed schema handling, request coalescing,
  generation fencing, targeted invalidation, manual clearing, and the sanitizer boundary. The cache
  lives in app-private `cacheDir`; backup and device-transfer allowlists admit none of it. SDK models
  and services remain the only API boundary and `ArcaneClientManager` remains the only authenticated
  client owner. Detail reads, mutations, streams, credentials, raw logs, operation payloads, and
  secret-bearing fields are intentional exclusions.

  **Automated and live evidence (2026-09-15):** `./gradlew :app:testDebugUnitTest
  :app:assembleDebug` passed with 354 JVM tests, zero failures/errors/skips. Focused coverage passed for
  TTL/expiry, explicit stale/fresh/error state, force-refresh fallback, corruption and old schema,
  disk/memory bounds, coalescing, late-result/invalidation races, account/permission isolation,
  authorization eviction, sanitization, process restoration, and backup placement; `git diff
  --check` and packaged manifest/resource inspection also passed. The real debug APK 0.1.0 (260901)
  ran on the existing Android 11/API 30 `arcane_test_api30` AVD against disposable local Arcane 2.10.2
  (`sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`). Fresh Dashboard and all
  selected list families were loaded, then force-stop plus network/server disconnection produced
  visibly marked cached data after cold process start. Reconnection replaced it with a single
  current row and stable ordering. Live checks also covered expired/corrupt/old-schema files, bounds,
  manual Clear Cache (9.1 KiB to zero), permission grant/revoke without administrator-data exposure,
  logout, Change Server, account change, process death, and environment disable/delete.

  A confirmed container deletion rewrote only the current-scope container entry, removed the current
  Dashboard aggregate, and left Projects plus another permission scope untouched. A completed durable
  image-pull operation rewrote Images and invalidated Dashboard while leaving Containers, Projects,
  and the other permission scope untouched. Server/Docker inspection confirmed both outcomes. All
  disposable users, environment, container, image, APK installation, shortcut state, cache evidence,
  trust material, screenshots, and the temporary LXD AVD clone were removed; `arcane-e2e`, the original
  API 30 AVD, and existing environments were preserved. No API 35 AVD was created. Remaining scope is
  deliberate: detail responses and arbitrary endpoints are not cached, and Android may reclaim
  `cacheDir`; neither is an unmet PAR-301 criterion.

- [x] **PAR-302 — Establish a durable sanitized snapshot pipeline**

- **Status:** Complete
- **Priority:** P2
- **Dependencies:** PAR-301
- **Scope:** Derive small, versioned, credential-free snapshots for external surfaces from the
  authoritative app/cache state. Keep the snapshot writer separate from widget presentation.
- **Acceptance criteria:**
  - [x] The schema contains only reviewed at-a-glance fields and no tokens, cookies, secrets, raw logs,
    or mutation capability.
  - [x] Snapshots are atomically written, size-bounded, versioned, scoped, and invalidated on logout or
    server/account change.
  - [x] Freshness and error metadata let consumers avoid implying live status.
  - [x] Process-death, corrupt/old schema, account switch, and offline update tests pass.

  **Design and source evidence (2026-09-15):** The compared source pins are Android
  `ed03850f3c378cefd2824aacc64dba1f896bd03f`, iOS
  `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
  `af6fa681d1c4c1e1af8a26a774f69a193df02680`, and Arcane
  `9e5bfea2f213a63f11c83f63f77e3c8499f23aba`. The strict version-1 schema and security boundary are
  documented in [Resilient Reads foundation](resilient-reads-foundation.md). Its 64 KiB app-private
  `noBackupFilesDir` file contains source/generation timestamps, fresh/stale/error/signed-out state,
  a closed error code, opaque one-way scope/environment keys, bounded aggregate counts, and at most
  ten bounded environment summaries. It contains no URL, username, account/environment ID,
  credential, cookie, token, secret, log, resource detail, operation data, or mutation capability.
  The storage-only writer has no SDK/client dependency; an explicit active-scope fence plus monotonic
  generation checks reject delayed writers across logout or account/server transitions. Glance and
  widget presentation remain intentionally outside this batch.

  **Automated and live evidence (2026-09-15):** The 354-test green Android baseline includes focused
  snapshot tests for atomic round-trip/process restart, strict corruption/unknown-schema/oversize
  rejection, count/name/row bounds, scope mismatch, active-writer fencing, delayed/concurrent writers,
  same-tick session activation, and immediate signed-out replacement. The live API 30/Arcane 2.10.2
  run inspected the app-private file without printing its contents: it was 645 bytes, used only the
  strict reviewed keys, and a forbidden-term scan for URL/username/password/token/cookie/API
  key/secret/credential/mutation material returned zero. Fresh online Dashboard state produced
  `FRESH`; disconnected cached state produced `STALE`; refused live refresh produced `ERROR` with
  `NETWORK_UNAVAILABLE`; reconnect returned to `FRESH`; logout and Change Server synchronously wrote
  `SIGNED_OUT`. Process death, corrupt/old cache input, offline refresh, permission/account change,
  and environment change were exercised in the same run. The snapshot remained a separate file from
  the read cache and durable-operation DataStore, and only one `ArcaneClient` construction site exists
  in the app. Cleanup removed the snapshot evidence and APK installation with the rest of the
  disposable fixtures. Remaining limitation is intentional: there is no widget consumer in this
  batch, so future PAR-303 work must preserve this schema and authenticated-route boundary.

- [x] **PAR-303 — Add privacy-reviewed Glance widgets**

- **Status:** Complete
- **Priority:** P2
- **Dependencies:** PAR-302, PAR-305
- **Scope:** Add one or two focused Android widgets for outcomes such as fleet status or environments,
  backed only by the snapshot pipeline.
- **Acceptance criteria:**
  - [x] Widgets never instantiate a second authenticated API client or expose secrets.
  - [x] Stale, signed-out, unavailable, and loading states are explicit.
  - [x] Taps use authenticated internal routes and cannot open the wrong server/environment.
  - [x] Widget resize, refresh limits, reboot, logout, and process-death behavior is device-tested.

  **Design and source evidence (2026-09-16):** Compared pins are Android
  `0f353eaaa53eff7d9c7720ec2e131db96ba59253`, iOS
  `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
  `b29695d547b78389ed7230b35cd133f7046b4b52`, and Arcane
  `9fa57c867b1085a7142d7e755f87ddd9318b1a1d`. One responsive Fleet Status Glance widget reads only
  strict schema-2 data from `noBackupFilesDir`; the schema adds a one-way server binding solely for
  the existing authenticated Dashboard route. The widget has no manager/client/token/network/work
  scheduler/mutation dependency, no periodic provider refresh, and no app-defined configuration or
  Glance state. Fresh, stale, unavailable, signed-out, and unconfigured states are textual as well as
  colored; 120 x 100, 240 x 120, and 300 x 200 dp presentations progressively disclose only bounded
  privacy-reviewed aggregates and display names. The receiver is non-exported. Glance's internal
  widget-manager bookkeeping and the snapshot remain excluded by the unchanged backup allowlist.

  **Automated and live evidence (2026-09-16):** The green 377-test baseline and 63-test focused
  presentation run cover snapshot privacy/schema/bounds/session fencing, material refresh decisions,
  safe route encoding, responsive model states, provider manifest, and backup policy. On the API 35
  compact phone the widget was added, resized, removed, reinstalled/re-added, and observed fresh
  (9/9), 35-minute stale, server-unavailable, signed-out, and unconfigured across app process death,
  app-data clear, cold/warm taps, Change Server, logout, and device/launcher restart. Private-file
  and APK/source inspection proved it consumed only the sanitized projection and introduced no
  second authenticated client. The aggregate widget intentionally has no per-environment action, so
  deleted/disabled environment resource routes are not representable; wrong-server, permission,
  stale, and signed-out taps remain fenced by the existing route resolver. Full AVD matrix, Arcane
  image, cleanup, privacy fields, and intentional exclusions are recorded in
  [Android-native presentation batch](native-presentation.md).

- [x] **PAR-304 — Add adaptive navigation and list-detail layouts**

- **Status:** Complete
- **Priority:** P2
- **Dependencies:** PAR-005
- **Scope:** Map iOS sidebar outcomes to Android's adaptive rail/drawer/list-detail patterns while
  preserving configurable tabs, back behavior, and compact-phone usability.
- **Acceptance criteria:**
  - [x] Compact, medium, and expanded widths have deliberate navigation behavior.
  - [x] Tab selection and independent route state survive resizing, rotation, and process recreation.
  - [x] Large screens do not merely stretch phone layouts where list-detail presentation is appropriate.
  - [x] Foldable/tablet emulator tests and accessibility navigation checks are recorded.

  **Design and source evidence (2026-09-16):** Compared pins are Android
  `0f353eaaa53eff7d9c7720ec2e131db96ba59253`, iOS
  `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
  `b29695d547b78389ed7230b35cd133f7046b4b52`, and Arcane
  `9fa57c867b1085a7142d7e755f87ddd9318b1a1d`. The existing configurable four-slot bottom bar remains
  below 600 dp; 600–839 dp uses a rail plus a permission-filtered More sheet; 840 dp and wider uses a
  grouped permanent drawer with every authorized destination. Existing selection/DataStore/route
  owners and one hoisted nested `NavController` per stack remain authoritative; non-pinnable
  administrative destinations launch through their existing Settings host so their deeper routes
  remain functional. Containers and
  Projects use a 360 dp list beside the same detail host whenever at least 600 dp content width
  remains; `launchSingleTop` plus root normalization prevents duplicate detail destinations while
  preserving compact Back. No second navigation framework, adaptive store, or broad screen redesign
  was introduced.

  **Automated and live evidence (2026-09-16):** The green 377-test baseline and focused 63-test run
  cover breakpoints, authorization, configurable selection, restoration, Back, route construction,
  shortcuts, and session/environment fences. Disposable API 35 validation covered a 1080 x 2400
  compact phone, a 2208 x 1840 foldable across expanded/half-open/1080 x 2092 compact/1600 x 1840
  medium states, and a 2560 x 1600 tablet rotated to 1600 x 2560 medium portrait. Tab customization,
  independent nested stacks, list selection/detail/Back, rotation, resize/fold, process recreation,
  valid cold/warm authenticated routes, static shortcuts, and 200% font scale passed. TalkBack and
  keyboard/D-pad navigation were exercised on representative expanded resource/topology flows.
  Operation notifications retain the already-tested authenticated-route coordinator and were
  regression-covered rather than given a second adaptive route path. The complete device and cleanup
  record is in [Android-native presentation batch](native-presentation.md). Follow-up API 30
  authenticated validation reproduced a stale first-composition Dashboard callback, then verified
  the corrected current-selection handler from Containers, Images, Projects, Settings, reinstall,
  and process restart. Two Compose device tests passed the Dashboard-to-Volumes-to-Dashboard touch
  sequence and long-press-without-selection behavior.

- [x] **PAR-305 — Define authenticated resource routes and Android shortcuts**

- **Status:** Complete
- **Priority:** P2
- **Dependencies:** PAR-002, PAR-005
- **Scope:** Define stable internal routes for tabs, environments, containers, and projects, then add
  a small set of static/dynamic app shortcuts. This is the Android-native counterpart to iOS deep
  links, quick actions, and selected App Intents—not a promise of Siri-equivalent behavior.
- **Acceptance criteria:**
  - [x] Route identity includes server/environment/resource context and validates authentication,
    authorization, existence, and unsupported destinations.
  - [x] Cold start, warm start, login-required, stale shortcut, and wrong-server paths fail safely.
  - [x] Shortcut publication removes stale or unauthorized entities.
  - [x] Navigation and device tests cover external intents and back-stack construction.

  **Design and source evidence (2026-09-15):** The compared source pins are Android
  `ed03850f3c378cefd2824aacc64dba1f896bd03f`, iOS
  `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
  `af6fa681d1c4c1e1af8a26a774f69a193df02680`, and Arcane
  `9e5bfea2f213a63f11c83f63f77e3c8499f23aba`. One versioned `AuthenticatedRoute` codec now covers
  Dashboard/tabs, environments, containers, projects, Activities, durable operations, shortcuts,
  and future snapshot/widget consumers. Its stable payload binds resource routes to the canonical
  server hash and hex-encodes bounded UTF-8 environment/resource arguments. Parsing rejects payloads
  over 4 KiB, arguments over 512 bytes, bad shape/version/encoding, queries/fragments/user info/ports,
  and unsupported destinations. Resolution occurs after login and checks server, user permission,
  capability, enabled/existing environment, existing resource/operation, and destination support
  through the existing manager and typed SDK. Existing operation notifications use the same model;
  no mutation route or shortcut was added.

  **Automated and live evidence (2026-09-15):** The 354-test green Android baseline includes focused
  codec round-trip, server/environment/resource identity, malformed/oversized/untrusted payload,
  current-binding restriction, login-continuation coordinator, shortcut XML, manifest, and backup
  tests. Final AAPT2/APK inspection found exactly the Dashboard, Containers, and Projects static
  shortcuts plus the typed VIEW intent filter. On the real API 30 debug APK, `adb shell am start`
  covered cold Containers, warm Projects, authenticated environment/resource routes, login-required
  continuation through logout/login, wrong server, deleted resource, disabled then deleted
  environment, unauthorized restricted user, malformed 5 KiB payload, and unsupported/stale targets.
  Every rejection showed a recovery explanation rather than opening a same-ID target. An external
  detail stack returned to its intended root and the task contained one `MainActivity`.

  Fresh authorized data published one reviewed dynamic environment shortcut alongside the three
  static read-only shortcuts; invocation opened the typed environment route. Offline/stale state,
  permission restriction, logout, and Change Server removed every dynamic shortcut immediately, and
  fresh reconnect/authorization republished it. Static shortcuts remained and no mutation shortcut
  appeared. Packaged and live shortcut state were both inspected. Cleanup removed the disposable
  dynamic shortcut and APK by uninstalling the app, then deleted the temporary AVD clone while
  preserving the existing API 30 AVD. No API 35 AVD was created. Remaining scope is intentional:
  dynamic publication is limited to three enabled, permission-reviewed environments; resource-level
  dynamic shortcuts and widget presentation await a separately reviewed use case.

- [x] **PAR-505 — Add interactive network topology visualization**

- **Status:** Complete
- **Priority:** P2
- **Dependencies:** None
- **Scope:** iOS 0.7.0 now renders an interactive network-to-container diagram while Android presents
  the same typed graph as grouped rows. Add a bounded, zoomable/pannable Android visualization while
  retaining the current list as an accessible and large-graph fallback. Do not copy known iOS summary
  stubs.
- **Acceptance criteria:**
  - [x] Node, edge, grouping, scale, interaction, and accessibility requirements are defined.
  - [x] Counts and relationships come from authoritative server data.
  - [x] Large, cyclic, malformed, and partially unavailable graphs remain bounded and have a usable
    non-graph fallback.
  - [x] Selection, environment changes, rotation, font scaling, and TalkBack are device-tested.

  **Design and source evidence (2026-09-16):** Compared pins are Android
  `0f353eaaa53eff7d9c7720ec2e131db96ba59253`, iOS
  `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
  `b29695d547b78389ed7230b35cd133f7046b4b52`, and Arcane
  `9fa57c867b1085a7142d7e755f87ddd9318b1a1d`. Merged SDK PR #11 supplies defensive unknown-node
  decoding; Android uses only that typed response. Hard bounds are 500 nodes/2,000 edges, with an
  80-node/240-edge interactive threshold, 512-byte IDs, 256-byte labels/metadata, 0.35–1.75 zoom,
  bounded pan, and explicit Fit/Reset. Deterministic bipartite grouping drops duplicate, missing,
  reversed, cyclic/self, unknown-type, and malformed relationships without inventing edges. The
  grouped list remains the direct accessible and forced over-limit fallback; selection is fenced by
  environment and canonical graph identity, and refresh races cannot publish an old graph.

  **Automated and live evidence (2026-09-16):** The green 377-test baseline includes deterministic
  maximum hard-bound and over-limit graphs plus cyclic, duplicate-node/edge, missing-endpoint,
  malformed-label/ID, isolated-node, layout, viewport, and selection tests. Against Arcane 2.10.2
  image `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`, Docker inspection
  proved a six-network/eight-container shared and isolated fixture before the typed response rendered
  18 nodes/13 edges. Phone/tablet runs covered empty/moderate topology, pan, real pinch zoom, Fit,
  Reset, node detail, list fallback, rotation, environment change, offline retained-data labeling,
  and 200% font scale. TalkBack exported one labeled focusable button per node; keyboard/D-pad focus
  and Enter opened details, selected borders supplied a non-color cue, and decorative edges were
  silent. Fixtures, AVDs, APK, screenshots, mounts, packages, and trust artifacts were removed while
  preserving existing environments and the API 30 AVD. Full evidence and exclusions are in
  [Android-native presentation batch](native-presentation.md).

## Phase 4: Quality, accessibility, localization, and distribution

- [ ] **PAR-401 — Establish an incremental localization path**

- **Status:** Ready
- **Priority:** P3
- **Dependencies:** None
- **Scope:** Define string-resource conventions and migrate text as touched, then address the highest
  traffic and accessibility-critical screens. iOS is also English-only, so this is product maturity
  rather than a literal missing iOS parity item.
- **Acceptance criteria:**
  - [ ] New user-visible text is resource-backed with plural, formatting, and accessibility conventions.
  - [ ] A scoped first migration covers authentication, navigation, destructive confirmations, and
    operation status without combining all app text into one risky change.
  - [ ] Pseudolocale checks find no clipping in the migrated flows.
  - [ ] Formatting does not concatenate grammar-sensitive fragments.

- [ ] **PAR-402 — Run a cross-cutting accessibility and interaction audit**

- **Status:** Ready
- **Priority:** P2
- **Dependencies:** PAR-304
- **Scope:** Audit TalkBack semantics, focus order, touch targets, contrast, font scaling, reduced
  motion, progress announcements, destructive confirmations, and useful haptic feedback.
- **Acceptance criteria:**
  - [ ] Core auth, navigation, dashboard, container action, project operation, and Settings flows pass a
    documented manual accessibility checklist.
  - [ ] Automated Compose accessibility checks cover representative screens.
  - [ ] At 200% font scale, critical actions and status remain reachable and understandable.
  - [ ] Motion/haptics convey state without becoming the only signal.

- [ ] **PAR-403 — Add focused UI and live-server test foundations**

- **Status:** Ready
- **Priority:** P2
- **Dependencies:** PAR-001, PAR-005
- **Scope:** Replace the template instrumentation test with a small reliable suite for authentication
  routing, configurable admin tabs, destructive confirmation, and environment switching. Define a
  disposable live-server harness for destructive/streaming validation.
- **Acceptance criteria:**
  - [ ] Tests are deterministic, use controlled fixtures/fakes where appropriate, and run on a documented
    emulator API level.
  - [ ] CI runs the selected instrumentation suite or clearly separates a scheduled/manual device lane.
  - [ ] Live-server tests cannot target an unapproved production server and clean up disposable state.
  - [ ] Unit, instrumented, and live-server claims remain separately reported.

- [ ] **PAR-404 — Add incremental static-quality and security gates**

- **Status:** Ready
- **Priority:** P3
- **Dependencies:** None
- **Scope:** Add Android lint first, then evaluate focused formatting/static analysis and dependency
  or secret scanning without introducing a noisy all-at-once migration.
- **Acceptance criteria:**
  - [ ] Each enabled gate has a documented baseline and fails only on actionable new violations.
  - [ ] Security-sensitive manifest, backup, exported-component, cleartext, and dependency findings are
    triaged rather than blanket-suppressed.
  - [ ] CI runtime and local commands are documented.
  - [ ] No mass reformat or unrelated cleanup is bundled with gate enablement.

- [ ] **PAR-405 — Prepare F-Droid packaging and metadata**

- **Status:** Ready
- **Priority:** P2
- **Dependencies:** PAR-006, PAR-007
- **Scope:** Research and prepare reproducible F-Droid-compatible release packaging and metadata,
  including application identity, licensing, source/build recipe, changelog, icons/screenshots,
  network-service disclosure, and dependency eligibility.
- **Acceptance criteria:**
  - [ ] The build recipe succeeds from a clean checkout without proprietary build-time dependencies or
    uncommitted machine configuration.
  - [ ] Version code/name, supported SDKs, signing boundary, update metadata, and release-source tag
    workflow are documented.
  - [ ] Metadata, fastlane assets if chosen, license, privacy/network disclosures, and anti-feature
    declarations pass applicable F-Droid validation.
  - [ ] No signing key or release is created/published as part of this preparation task without separate
    authorization.

- [ ] **PAR-406 — Define Android alpha/beta release criteria**

- **Status:** Ready
- **Priority:** P3
- **Dependencies:** PAR-006, PAR-403, PAR-405
- **Scope:** Replace ambiguous “not intended for devices” messaging with explicit support,
  compatibility, verification, known-limitations, and release-channel criteria.
- **Acceptance criteria:**
  - [ ] Minimum supported Arcane/server, Android, and Kotlin SDK compatibility expectations are stated.
  - [ ] Alpha/beta promotion gates cover builds, tests, device/live-server matrix, privacy, backup,
    upgrade, rollback, and release-note integrity.
  - [ ] Distribution channels and signing/publishing responsibilities are documented without embedding
    credentials.
  - [ ] User-facing repository messaging matches the actual release state.

## Hold and deferred product tracks

- [x] **PAR-501 — Arcane Updates counts and navigation**

- **Status:** Complete
- **Priority:** P1
- **Dependencies:** Explicit product decision to follow current iOS Updates semantics (cleared
  2026-08-27)
- **Scope:** Keep the Dashboard Updates tile, Needs Attention row, and opened Updates screen aligned
  to the current iOS image-oriented model. Count outdated images across enabled environments; treat
  projects and containers as consumer context. Arcane web's updateable-resource grouping is an
  intentional product difference and must not replace the mobile image total.
- **Acceptance criteria:**
  - [x] Product decision and target revisions are explicitly recorded before work begins.
  - [x] Counts are defined for permissions, unavailable environments, and server versions.
  - [x] Dashboard entry points open the image-oriented Updates list without losing environment identity.
  - [x] Multi-environment live-server tests prove counts and destination consistency.
- **Implementation and validation evidence (updated 2026-09-11):**
  - Source pins: Android base `982d8cc844c604a029371ebda0ae12e80d5764bd`, iOS
    `a3440b05238d2620b91d984557c87994ab15fb28`, libarcane-kotlin
    `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`, and Arcane
    `8d10b7db2d34aefa44f0f9a684f3b84b2ae355d7`.
  - At the pinned iOS revision, iOS loaded `images.updateSummary(...).imagesWithUpdates` for every
    enabled environment and documented the image total as the intended mobile value, although its
    streamed-count precedence could still expose the resource count. The explicit product decision
    and the image-oriented Updates screen resolved that internal inconsistency for Android.
  - Michael reproduced the Android mismatch on PAR-005: Dashboard displayed one updateable project
    while the opened image-oriented Updates screen displayed four outdated images.
  - On 2026-09-01 Michael's physical-device/live-server retest confirmed that the Dashboard and the
    opened image-oriented Updates screen both reported the expected four outdated images. This closes
    the previously reproduced one-project-versus-four-images inconsistency.
  - Android now derives the fleet total from the same per-environment image summaries and leaves the
    total unavailable if any enabled environment summary fails. Streamed resource action items still
    support environment-card context but cannot override the image count.
  - Focused dashboard mapping/count tests passed (10 tests). The CI-equivalent
    `./gradlew :app:testDebugUnitTest :app:assembleDebug` baseline passed. Multi-environment device
    confirmation remains pending.
  - Publication revalidation on 2026-09-09 compared Android `26efa46809a24074417d5d42c06639e4287a4b8d`,
    iOS `6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
    `b3d80a2ffd39ea8c87b4699e9798268fc0ae7a4b`, and Arcane
    `16db5a33747b49350407073aa0040baa5e151947`. Current iOS now explicitly prioritizes summary-based
    image totals over streamed resource counts for both Dashboard entry points. Michael reproduced
    the still-unmerged Android behavior with a Dashboard total of four and an Updates-screen total
    of eleven; branch validation against that multi-environment server remains pending.
  - After refreshing the branch onto Android `26efa46809a24074417d5d42c06639e4287a4b8d`,
    the focused dashboard count/mapping tests and the CI-equivalent
    `./gradlew :app:testDebugUnitTest :app:assembleDebug` baseline passed on 2026-09-09.
  - Single-environment live regression preflight on 2026-09-10 used Android base
    `9b16902dde5c96fac0c55fd5360288d6c3ac544f`, current iOS
    `6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
    `7787bff82973302062d1d0c8db4c12f09547c5b0`, and Arcane
    `a50ab984528cf443c07e1a58052a09af2632dd63`. A disposable `alpine:latest` image deliberately
    pinned to the older 3.18 digest produced one server-reported outdated image. Dashboard Updates,
    Needs Attention, and the opened Updates screen each displayed `1`; a subsequent streamed image
    check (`5450cdfc-f27b-4c01-893c-390624b1a461`) did not revert any count. The fixture was removed.
    At that point multi-environment live confirmation remained pending, so status and its final
    criterion stayed open.
  - Completion revalidation on 2026-09-11 compared Android
    `5525278dc6694984ecacdae92a35e58f646ca151`, iOS
    `6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
    `7787bff82973302062d1d0c8db4c12f09547c5b0`, and Arcane source
    `5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105`. Live validation used two Arcane v2.10.2
    instances from image revision `670ee2b34ea7b0fb2917643229b6ce9070ee9742` and digest
    `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`: the existing
    `arcane-e2e` manager at `https://10.0.2.2:43553` and a disposable Ubuntu 24.04 LXD
    instance registered as `PAR-501 Isolated Remote`. Their Docker 29.1.3 daemons had distinct IDs
    (`67215421-d4f4-477b-94ad-29d3d71ff651` and
    `de81b8e0-7a31-403f-a67d-7c299e5c1712`) and separate `/var/lib/docker` roots.
  - The deterministic fixture put old `alpine:latest` and `busybox:latest` image IDs in one local
    Compose-labelled project, `par501-multi-image`, and an old, unused `nginx:latest` image in the
    isolated environment. Arcane reported image summaries of two plus one, while the resource-oriented
    Dashboard action items reported one plus zero. Image-list state confirmed the three distinct old
    digests and their newer registry digests, proving the expected mobile fleet total was three images,
    not one updateable resource.
  - The actual Android app (`versionCode=260901`, `versionName=0.1.0`) ran on AVD
    `arcane_test_api30`, API 30/Android 11 build
    `RSR1.210722.013.A2/10067904`, with emulator 37.1.11.0. Dashboard Updates, Needs Attention, and
    Updates details each showed `3`; both Dashboard entry points opened the same image-oriented Updates
    screen, whose environment cards showed local `2` and remote `1`. Refresh retained `3`. Adding a
    temporary standalone consumer changed Arcane's streamed resource action count from one to two while
    the image total and both Dashboard surfaces correctly remained `3`.
  - Stopping the remote Arcane instance left the local image subtotal at two and made its summary proxy
    return HTTP 502. Updates details displayed `Update total unavailable`, and a Dashboard reload showed
    an em dash rather than the misleading partial total `2`. After the instance restarted, refreshing
    Updates and returning to Dashboard recovered `3` without restarting the app; its process remained
    PID 2584. Disabling the remote registration while it still directly reported one outdated image
    produced `2 updates available` across `1 environment`, Dashboard Updates `2`, and Needs Attention
    `2`, consistently excluding the disabled environment.
  - Focused `DashboardTotalsTest`, `DashboardNeedsAttentionMapperTest`, and
    `DashboardOverviewSourceTest` runs passed all 10 tests. The CI-equivalent
    `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed all 269 unit tests and assembled the
    debug APK. The remote registration, disposable LXD instance, all Docker fixtures, the debug-only
    CA trust override, the installed test APK, and temporary evidence files were removed; the manager
    returned to its single local environment and zero outdated images. Automated reviews were inactive,
    so no Greptile evidence is claimed.

- [ ] **PAR-502 — Multi-server profiles**

- **Status:** Deferred
- **Priority:** P3
- **Dependencies:** PAR-002, PAR-202, PAR-301, PAR-305
- **Scope:** Specify profiles only after single-server credential, cache, operation, and route scoping
  are correct.
- **Acceptance criteria:**
  - [ ] The design covers credentials, cookies, caches, snapshots, operations, routes, active selection,
    deletion, migration, and concurrent server behavior.
  - [ ] Switching cannot leak data or actions between servers/users.
  - [ ] Product scope distinguishes saved profiles from simultaneous fleet aggregation.
  - [ ] Implementation is split into reviewable persistence, client ownership, and UI tasks.

- [ ] **PAR-503 — Evaluate an Android AI assistant**

- **Status:** Deferred
- **Priority:** Not an Android-parity priority
- **Dependencies:** Stable operational foundation
- **Scope:** iOS 0.7.0 removed the Arcane Assistant, so there is no current parity gap. Retain this only
  as a possible independent product/security investigation; define provider/device support, privacy,
  cost, context, tool permissions, and confirmation before any implementation.
- **Acceptance criteria:**
  - [ ] A product/security design establishes data boundaries and starts with read-only tools.
  - [ ] Every mutation is staged, explained, scoped, and explicitly confirmed.
  - [ ] Unsupported devices and offline/provider failure have a complete non-AI app path.
  - [ ] No provider SDK or server dependency is added before the design is approved.

- [ ] **PAR-504 — Swarm workflow**

- **Status:** Deferred
- **Priority:** Not an Android-parity priority
- **Dependencies:** Shared Arcane product/API definition
- **Scope:** Both clients currently expose placeholders. Do not count Swarm as an Android deficit or
  implement speculative client behavior.
- **Acceptance criteria:**
  - [ ] Shared user workflows, Arcane API contract, authorization, and server compatibility are defined.
  - [ ] SDK work precedes Android UI where required.
  - [ ] The gap analysis is updated from **Shared gap** only after a real product target exists.

## Done/verify candidates

These items appear to have progressed or landed in later workspace notes. They are not active
implementation work unless current-source or runtime verification finds a regression.

- [ ] **PAR-V01 — Pinned dashboard resources and context actions**

- **Status:** Done/verify
- **Priority:** P1 if reopened
- **Dependencies:** PAR-004
- **Scope:** Verify pins, context actions, persistence, permission changes, and correct environment
  targeting on current source.
- **Acceptance criteria:**
  - [ ] More than one environment and process recreation preserve the intended pins.
  - [ ] Unauthorized/stale resources disappear or become safely unavailable.
  - [ ] Close as verified or reopen with a focused reproduction.

- [ ] **PAR-V02 — Needs Attention action items**

- **Status:** Done/verify
- **Priority:** P1 if reopened
- **Dependencies:** PAR-004
- **Scope:** Verify counts, actions, navigation, partial failures, and authorization on current source.
- **Acceptance criteria:**
  - [ ] Items navigate to the correct server/environment/resource.
  - [ ] Fleet pagination and partial environment failure do not create false totals.
  - [ ] Close as verified or reopen with a focused reproduction.

- [x] **PAR-V03 — Dashboard stream foundation and live-stats recovery**

- **Status:** Complete
- **Priority:** P1 if reopened
- **Dependencies:** PAR-008
- **Scope:** Verify reconnect, version fallback, cancellation, connection bounds, and recovery after
  server/environment changes.
- **Acceptance criteria:**
  - [x] A current target server demonstrates recovery without duplicate streams or stale overwrites.
  - [x] Unsupported/legacy behavior is explicit.
  - [x] Close as verified or reopen with a focused reproduction.
- **Validation evidence (2026-08-21):**
  - Source pins: Android base `84f822b393e2b02e8dcaf200081105104d3eb151`,
    libarcane-kotlin `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`, and Arcane
    `0fd8820822f49e2da25739306bc9bc401253fa9e`.
  - Michael's physical-device/live-server smoke test on the current target passed dashboard network
    loss and reconnect, environment switching without stale values, stream-screen departure and
    reopen, and recovery after force-stop. The PAR-008 regression matrix separately proves one live
    owner and rejects stale snapshots across refresh, environment removal, and client replacement.
  - A typed `ArcaneError.NotFound` from `dashboard/stream` is explicitly treated as a legacy server:
    reconnect stops without a failure banner, REST totals remain authoritative, and a replacement
    client resets stream support. Repeated transport failures enter bounded idle retry, and live
    system stats select at most six unique environments.
  - Focused runs passed 21 tests across `DashboardStreamStoreTest`, `DashboardStatsHistoryTest`, and
    `DashboardTotalsTest` (0 failures, 0 errors, 0 skipped).
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed all 148 unit tests and assembled the
    debug APK; `git diff --check` passed.

- [ ] **PAR-V04 — Update All environments**

- **Status:** Done/verify
- **Priority:** P1 if reopened
- **Dependencies:** PAR-004, PAR-501
- **Scope:** Verify complete environment coverage and result reporting while preserving PAR-501's
  completed Updates counts/navigation decision.
- **Acceptance criteria:**
  - [ ] More than 20 environments are included exactly once where eligible.
  - [ ] Partial, unsupported, unauthorized, cancel, and error results are accurately attributed.
  - [ ] Close as verified or reopen outside PAR-501 only with independent evidence.

- [ ] **PAR-V05 — Environment card actions**

- **Status:** Done/verify
- **Priority:** P1 if reopened
- **Dependencies:** PAR-102
- **Scope:** Verify current sync, system, upgrade, prune, detail, and active-environment actions
  against permissions and server capabilities.
- **Acceptance criteria:**
  - [ ] Every visible action targets the card's environment and has an accurate enabled state.
  - [ ] Upgrade behavior is tracked by PAR-102 and prune runtime behavior by PAR-101.
  - [ ] Close remaining actions as verified or reopen individually.

- [ ] **PAR-V06 — Failed Activity badge**

- **Status:** Done/verify
- **Priority:** P1 if reopened
- **Dependencies:** PAR-004, PAR-109
- **Scope:** Verify count, environment attribution, clearing/retry behavior, and navigation.
- **Acceptance criteria:**
  - [ ] Pagination, live updates, clearing, and partial failures cannot leave a misleading badge.
  - [ ] Tapping opens the relevant Activity Center context.
  - [ ] Close as verified or reopen with a focused reproduction.

- [ ] **PAR-V07 — Black bottom inset**

- **Status:** Done/verify
- **Priority:** P2 if reopened
- **Dependencies:** None
- **Scope:** Check representative light/dark themes, gesture/three-button navigation, keyboard,
  rotation, and edge-to-edge screens.
- **Acceptance criteria:**
  - [ ] No unintended black inset appears across the checked configurations.
  - [ ] Close as verified or reopen with screenshots, device/API details, and a focused reproduction.

## Backlog maintenance

- Keep one primary task per issue; split implementation subtasks when they cross repository
  boundaries or cannot be reviewed independently.
- Treat this central list as canonical; do not pre-create a file for every task.
- Create `docs/tasks/<task-id>-<slug>.md` only when a substantial task enters active work and needs
  design or validation notes, and link that work packet from its central task entry while active.
- After completion, move durable decisions and evidence to the appropriate permanent location, then
  remove the temporary task file and link when they no longer add value.
- Retain a task file only when it contains lasting architectural rationale; prefer moving that
  rationale into an ADR or another permanent document.
- Change **Needs revalidation** to **Ready**, **Blocked/Hold**, or **Done/verify** only with current
  evidence.
- Move landed work to **Done/verify** until required device/live-server checks pass, then record the
  verified outcome in the gap analysis.
- Never reopen a closed candidate merely because it appeared in an older note.
- Refresh the pinned gap-analysis baseline before a broad reprioritization.
