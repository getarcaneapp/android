# Android iOS-parity task list

Last updated: 2026-09-01

This is the working backlog for bringing Arcane Android to product-outcome parity with iOS. It
turns the findings in [the pinned gap analysis](ios-android-gap-analysis.md) into issue-sized work;
it does not repeat that research.

Use the [Android parity delivery workflow](parity-delivery-workflow.md) when taking an item from
this canonical backlog through local validation and a review-ready pull request.

The source comparison is pinned to:

- iOS `2d7f277fe322d67c88d62b826f068fa92785e3fe`
- libarcane-swift `38b5c32dde5b17eb0bc22b1c13fb4204699c8faf`
- Android `10b26b2275fb8b9772ff69f2e1f6418225be532a`
- libarcane-kotlin `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`

Revalidate conclusions against current source before starting an item. Record the Android, Kotlin
SDK, and Arcane server revisions in the resulting issue or pull request.

The 2026-08-21 refresh advances the iOS comparison from 0.5.4 to 0.7.0. It adds explicit backlog
coverage for passkeys/MFA, current scoped variables, image layer history, deploy options, template
discovery, container-registry names, and appearance persistence. It also promotes topology from a
shared enhancement to an Android gap and removes AI from the active parity roadmap because iOS 0.7.0
removed the Arcane Assistant.

## Recommended starting queue

Work the correctness foundation before selecting a large feature:

1. **PAR-003 verification**, then **PAR-004** — finish the pending device check and remove silent
   complete-list truncation outside Containers.
2. **PAR-005**, **PAR-008**, and **PAR-009** — repair reachable navigation, cancellation ownership,
   and app-wide theme state.
3. **PAR-006** and **PAR-007** — correct release/support metadata and establish the sensitive-data
   backup boundary.
4. **PAR-101** — validate System Prune now that PAR-002's server scoping is proven.

After that foundation, the highest-value feature slice is **PAR-103** (project files), followed by
**PAR-104** (account/profile), **PAR-110** (passkeys/MFA), and **PAR-111** (global variables). Tasks
without dependencies can move sooner when they do not distract from the P0 queue.

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

- [ ] **PAR-006 — Correct Android links, release notes, and version hygiene**

- **Status:** Ready
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Replace iOS repository/issue destinations and copied iOS release claims with deliberate
  Android links and Android-verified notes. Align displayed notes with the app version.
- **Acceptance criteria:**
  - [ ] Source, issue, documentation, privacy, and support links resolve to intentional destinations.
  - [ ] Release notes contain only shipped Android behavior and have consistent version ordering.
  - [ ] The current app version maps to an appropriate note, and future automatic presentation cannot
    show notes for an unshipped version.
  - [ ] Link and release-note mapping logic has focused coverage.

- [ ] **PAR-007 — Define Android backup and data-extraction policy**

- **Status:** Ready
- **Priority:** P0
- **Dependencies:** PAR-002
- **Scope:** Replace template backup rules with explicit policy for tokens, server/account data,
  future caches, snapshots, and operation state across supported Android versions.
- **Acceptance criteria:**
  - [ ] Sensitive credentials, cookies, cached server responses, and operation payloads are excluded.
  - [ ] Legacy backup rules and current data-extraction rules express the same intended boundary.
  - [ ] Backup/restore behavior is checked on a supported emulator or documented platform test.
  - [ ] No machine-specific paths, secrets, or backup artifacts are committed.

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

- [ ] **PAR-101 — Validate System Prune end to end**

- **Status:** Needs revalidation
- **Priority:** P0
- **Dependencies:** PAR-002
- **Scope:** Treat PR #12's UI/result handling as implemented but not operationally proven. Exercise
  prune against disposable environments with known resources; do not infer server effects from UI
  or unit tests.
- **Acceptance criteria:**
  - [ ] A real-device/emulator plus live-server matrix covers success, nothing-to-prune, partial/error,
    authorization failure, disconnect, cancellation, and repeated invocation.
  - [ ] Before/after server state proves exactly which eligible resources were removed and retained.
  - [ ] The UI reports server results accurately and cannot imply success after a failed mutation.
  - [ ] Results identify tested Android, SDK, server, and API versions.

- [ ] **PAR-102 — Match per-environment Upgrade Arcane capability gating**

- **Status:** Needs revalidation
- **Priority:** P1
- **Dependencies:** PAR-004
- **Scope:** Recheck current server/SDK support for the iOS `checkUpgrade.canUpgrade` outcome. Show
  the environment action only from authoritative capability/version data, not duplicated UI
  heuristics.
- **Acceptance criteria:**
  - [ ] Current Arcane handler/type and both SDK contracts are compared before implementation.
  - [ ] The action is visible and enabled only when the selected environment can upgrade.
  - [ ] Unsupported, unauthorized, loading, error, and older-server states are explicit.
  - [ ] Multi-environment tests prove gating is calculated per environment.

- [ ] **PAR-103 — Build the existing-project file workspace**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-002
- **Scope:** Use the typed Kotlin SDK project-file operations to add a file tree; Compose, `.env`,
  and text editing; save/create; rename/move/delete; variable-resolution preview; and resolved YAML.
  Keep archived and GitOps projects read-only where required.
- **Acceptance criteria:**
  - [ ] Users can inspect and perform every supported file mutation with clear dirty/conflict state.
  - [ ] Destructive actions identify the project/environment and require confirmation.
  - [ ] Reload, save failure, concurrent server change, unsupported/binary file, and archived/GitOps
    states preserve data and explain why an action is unavailable.
  - [ ] Typed SDK calls are used directly and mapping/state logic has focused tests.

- [ ] **PAR-104 — Add signed-in account/profile management**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-002
- **Scope:** Add a non-admin account destination for viewing/updating display name and email,
  changing password, displaying avatar/Gravatar behavior, signing out, and changing server using
  existing SDK support.
- **Acceptance criteria:**
  - [ ] Profile editing and password change validate inputs and report server errors without losing data.
  - [ ] The route is clearly distinct from administrator user management.
  - [ ] Updated identity propagates to current-user state and authorization-dependent UI.
  - [ ] Sign-out and change-server paths satisfy PAR-002's invalidation rules.

- [ ] **PAR-105 — Add image attestation workflows**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** None
- **Scope:** Use existing Kotlin SDK support to provide attestation list, filter, detail, and safe
  statement copy for an image.
- **Acceptance criteria:**
  - [ ] Empty, malformed/unknown, loading, error, unauthorized, and unsupported-server states are clear.
  - [ ] Selection remains tied to the correct image digest and environment.
  - [ ] Copied/exported data is complete and intentionally labeled.
  - [ ] DTO/serialization coverage remains in the SDK; Android adds state and presentation tests.

- [ ] **PAR-106 — Complete container lifecycle and detail actions**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-003
- **Scope:** Compare current Android actions with supported server/SDK operations and add high-value
  omissions such as pause and kill where appropriate, without copying unsupported iOS behavior.
- **Acceptance criteria:**
  - [ ] An action inventory records parity, intentional omission, permission gate, and server gate.
  - [ ] Added actions use resource/environment-specific confirmation and accurate result/error feedback.
  - [ ] State refreshes after success without losing selection or showing stale controls.
  - [ ] Device/live-server validation covers each destructive lifecycle action added.

- [ ] **PAR-107 — Add log and terminal copy/share/export continuity**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-008
- **Scope:** Provide Android Sharesheet/Storage Access Framework or MediaStore outcomes for container
  and project logs, plus consistent search, copy, pause/resume, timestamp, ANSI, retention, and
  terminal copy/clear behavior.
- **Acceptance criteria:**
  - [ ] Users can copy selected content and share/export a clearly scoped log without truncation surprises.
  - [ ] Large streams use bounded memory and cancellation; secrets receive an explicit product review.
  - [ ] Export failure, permission/canceled picker, reconnect, and environment changes are safe.
  - [ ] Shared formatting/state logic has focused tests and device sharing is exercised.

- [ ] **PAR-108 — Add lifecycle-aware live event refresh**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-008
- **Scope:** Provide live-enough Events behavior via a server-supported stream or bounded,
  lifecycle-aware polling. Do not invent an app-local protocol.
- **Acceptance criteria:**
  - [ ] The chosen mechanism and interval/backoff are based on the current Arcane/SDK contract.
  - [ ] Events update while visible, stop when no longer owned, and do not duplicate or reorder entries.
  - [ ] Refresh, reconnect, partial failure, environment change, and stale-state UI are covered.
  - [ ] Battery/network impact is bounded and documented.

- [ ] **PAR-109 — Add Activity Center terminal-failure retry**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-008
- **Scope:** Add an explicit recovery path when live or paginated activity loading terminates, while
  preserving healthy data from other environments.
- **Acceptance criteria:**
  - [ ] Users can identify which environment/source failed and retry it without discarding healthy results.
  - [ ] Repeated retry is bounded, cancellation-aware, and does not duplicate activities.
  - [ ] Tests cover terminal stream error, heartbeat timeout, one-environment failure, full failure, and
    successful recovery.

- [ ] **PAR-110 — Add passkey sign-in and MFA management**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-001, PAR-002
- **Scope:** Inspect the current Arcane passkey/WebAuthn handlers and Swift SDK, add typed passkey,
  step-up, MFA-policy, and recovery support to `libarcane-kotlin`, then integrate Android Credential
  Manager for login and signed-in account management. Do not duplicate ceremony JSON or endpoints in
  the app.
- **Acceptance criteria:**
  - [ ] Server capabilities gate passkey login, enrollment, rename/delete, step-up, MFA policy, and
    recovery; older/unsupported servers retain password/OIDC paths.
  - [ ] Credential creation/assertion maps origin, RP ID, challenge, cancellation, and provider errors
    through typed SDK models without logging sensitive ceremony data.
  - [ ] Login, pending MFA, account management, last-passkey restrictions, recovery, process recreation,
    and server/account changes fail safely.
  - [ ] SDK contract tests, Android state tests, and device/live-server passkey validation are recorded
    separately with Android, SDK, and Arcane revisions.

- [ ] **PAR-111 — Add scoped global-variable management**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-002, PAR-004
- **Scope:** Model the current v2 variables API in `libarcane-kotlin`, then add permission-gated Android
  list/search/create/edit/delete/sync flows for secret and non-secret values scoped to all or selected
  environments. The older template-variable endpoints are not the same contract.
- **Acceptance criteria:**
  - [ ] Variable models, permission constants, mutations, sync requests, and per-environment sync status
    are typed and tested in the SDK first.
  - [ ] Secret values never appear in logs, clipboard actions, accessibility text, or stale UI; copying
    non-secret keys/values is explicit.
  - [ ] Unsupported, unauthorized, empty, partial-sync, failed-sync, concurrent edit, and server change
    states preserve scope and report accurate outcomes.
  - [ ] More than 20 environments can be selected and reported without omissions or duplicate sync work.

- [ ] **PAR-112 — Add image layer history**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** None
- **Scope:** Add the typed per-image Docker layer-history contract to `libarcane-kotlin`, then expose a
  History destination in image detail. Keep this distinct from the existing image-build history API.
- **Acceptance criteria:**
  - [ ] Layer ID/missing-layer, command, size, created time, and tags decode unknown/optional fields
    defensively in SDK tests.
  - [ ] Loading, empty, error, unauthorized, and unsupported-server states identify the image and
    environment without leaking a prior selection.
  - [ ] Refresh and environment/server changes cannot publish history for the wrong image digest.
  - [ ] Focused Android tests and live-server validation cover a multi-layer image and a history-less
    image.

- [ ] **PAR-113 — Add scoped project deploy options**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-002
- **Scope:** Use the Kotlin SDK's existing `DeployOptions` to let users choose pull policy and force
  recreation before deploy. Store defaults by normalized server, user, environment, and project.
  PAR-202 will later adopt the same options when it becomes the operation owner.
- **Acceptance criteria:**
  - [ ] Default, always-pull, never-pull, force-recreate, cancel, unsupported, and server-error behavior
    are explicit and map to typed SDK values.
  - [ ] Preferences cannot cross servers, accounts, environments, or projects and are cleared or
    migrated according to PAR-002.
  - [ ] The launched stream receives exactly the selected options and reports the server result without
    fabricating success after failure or cancellation.
  - [ ] Mapping/persistence tests and device/live-server deploy evidence are recorded.

- [ ] **PAR-114 — Complete template discovery, import, and deployment**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-004
- **Scope:** Extend the existing Android registry CRUD, grouped browser, preview, and deploy flow with
  current iOS outcomes: search, local/remote source filtering, metadata, remote download, and complete
  result loading through the typed Kotlin template service.
- **Acceptance criteria:**
  - [ ] Search and source filters cover all loaded templates and clearly distinguish local, configured-
    registry, and remote entries.
  - [ ] Metadata/preview and remote download handle unsupported, malformed, duplicate, unauthorized,
    offline, and partial-page states without losing the current selection.
  - [ ] Deploying a selected template preserves its identity and content through project creation and
    hands long-running work to PAR-202 when applicable.
  - [ ] Pagination/filter/download state has focused tests and a live-server import/deploy check.

- [ ] **PAR-115 — Add container-registry display names**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** None
- **Scope:** Add the current optional registry `name` field to Kotlin SDK read/create/update/sync models,
  then expose it in Android list and form UI while retaining URL fallback for older records.
- **Acceptance criteria:**
  - [ ] Missing, blank, duplicate, and unknown-server values decode safely and display a stable URL/ID
    fallback.
  - [ ] Create/edit preserves credentials and unrelated registry fields and never logs token/secret
    values.
  - [ ] List, preview, pull-usage, and destructive confirmations identify the same registry clearly.
  - [ ] SDK serialization plus Android mapping/form tests pass against old and current payload fixtures.

## Phase 2: Own long-running operations before adding system surfaces

- [ ] **PAR-201 — Specify the app-level operation store**

- **Status:** Ready
- **Priority:** P1
- **Dependencies:** PAR-002, PAR-008
- **Scope:** Write a reviewed design before implementation for deploy, pull, build, update, and other
  user-initiated long-running work. Define identity, ownership, persistence, reconnect/reattach,
  cancellation, bounded logs, concurrency, process death, server/user/environment scoping, and
  v1/v2 behavior.
- **Acceptance criteria:**
  - [ ] The specification includes state transitions, persistence schema, invalidation, recovery,
    retention, concurrency policy, and security boundaries.
  - [ ] It identifies which operations can reattach server-side and which can only preserve a terminal
    or unknown state after process death.
  - [ ] It assigns one source of truth and explicitly prevents screens, services, and notifications from
    becoming competing operation owners.
  - [ ] Test strategy and migration/rollout plan are included before implementation begins.

- [ ] **PAR-202 — Implement the app-level operation store and in-app surface**

- **Status:** Deferred
- **Priority:** P1
- **Dependencies:** PAR-201
- **Scope:** Implement the approved store and an in-app operation center/floating progress surface.
  Migrate one representative operation first, then the remaining approved operation types. Treat
  configurable activity-start feedback as a bounded projection of this store, not a second owner.
- **Acceptance criteria:**
  - [ ] Operations survive screen changes and expose progress, bounded logs, reconnect, cancel, success,
    failure, and indeterminate/unknown states from one owner.
  - [ ] Server, account, and environment changes cannot cross-contaminate operation state.
  - [ ] Process-death recovery follows the spec and never fabricates successful completion.
  - [ ] Optional activity-start feedback distinguishes user/system work, keeps environment context, and
    opens the authoritative operation/activity destination without notification spam.
  - [ ] State-machine, persistence, concurrent-operation, cancellation, and migration tests pass.

- [ ] **PAR-203 — Add Android ongoing operation notifications**

- **Status:** Deferred
- **Priority:** P2
- **Dependencies:** PAR-202
- **Scope:** Project operation-store state into Android notifications. Use foreground execution only
  for eligible user-initiated work that Android policy requires to continue beyond the screen.
- **Acceptance criteria:**
  - [ ] Notifications are projections of PAR-202 state and never own or duplicate the operation.
  - [ ] Progress, cancel/open actions, completion, failure, permission denial, and notification-disabled
    behavior are correct.
  - [ ] Foreground-service types, lifecycle, disclosure, and recent Android background restrictions are
    satisfied.
  - [ ] Device tests cover backgrounding, rotation, process pressure/recreation, multiple operations,
    server change, and notification taps.

## Phase 3: Resilient reads and Android-native continuity

- [ ] **PAR-301 — Design and implement a scoped API response cache**

- **Status:** Ready
- **Priority:** P2
- **Dependencies:** PAR-002, PAR-004
- **Scope:** Add bounded stale-read caching for dashboard and selected high-value resource lists.
  Scope entries by normalized server, user, environment, request identity, and relevant permissions.
  Do not queue mutations.
- **Acceptance criteria:**
  - [ ] The design defines expiry, LRU/size bounds, request coalescing, invalidation, schema migration,
    stale markers, and sensitive-data treatment.
  - [ ] Initial screens render last-known data offline and visibly distinguish stale from current state.
  - [ ] Auth/server/environment changes and successful destructive mutations invalidate affected entries.
  - [ ] Corrupt cache, permission change, refresh race, no-network, and storage-bound tests pass.

- [ ] **PAR-302 — Establish a durable sanitized snapshot pipeline**

- **Status:** Deferred
- **Priority:** P2
- **Dependencies:** PAR-301
- **Scope:** Derive small, versioned, credential-free snapshots for external surfaces from the
  authoritative app/cache state. Keep the snapshot writer separate from widget presentation.
- **Acceptance criteria:**
  - [ ] The schema contains only reviewed at-a-glance fields and no tokens, cookies, secrets, raw logs,
    or mutation capability.
  - [ ] Snapshots are atomically written, size-bounded, versioned, scoped, and invalidated on logout or
    server/account change.
  - [ ] Freshness and error metadata let consumers avoid implying live status.
  - [ ] Process-death, corrupt/old schema, account switch, and offline update tests pass.

- [ ] **PAR-303 — Add privacy-reviewed Glance widgets**

- **Status:** Deferred
- **Priority:** P2
- **Dependencies:** PAR-302, PAR-305
- **Scope:** Add one or two focused Android widgets for outcomes such as fleet status or environments,
  backed only by the snapshot pipeline.
- **Acceptance criteria:**
  - [ ] Widgets never instantiate a second authenticated API client or expose secrets.
  - [ ] Stale, signed-out, unavailable, and loading states are explicit.
  - [ ] Taps use authenticated internal routes and cannot open the wrong server/environment.
  - [ ] Widget resize, refresh limits, reboot, logout, and process-death behavior is device-tested.

- [ ] **PAR-304 — Add adaptive navigation and list-detail layouts**

- **Status:** Ready
- **Priority:** P2
- **Dependencies:** PAR-005
- **Scope:** Map iOS sidebar outcomes to Android's adaptive rail/drawer/list-detail patterns while
  preserving configurable tabs, back behavior, and compact-phone usability.
- **Acceptance criteria:**
  - [ ] Compact, medium, and expanded widths have deliberate navigation behavior.
  - [ ] Tab selection and independent route state survive resizing, rotation, and process recreation.
  - [ ] Large screens do not merely stretch phone layouts where list-detail presentation is appropriate.
  - [ ] Foldable/tablet emulator tests and accessibility navigation checks are recorded.

- [ ] **PAR-305 — Define authenticated resource routes and Android shortcuts**

- **Status:** Ready
- **Priority:** P2
- **Dependencies:** PAR-002, PAR-005
- **Scope:** Define stable internal routes for tabs, environments, containers, and projects, then add
  a small set of static/dynamic app shortcuts. This is the Android-native counterpart to iOS deep
  links, quick actions, and selected App Intents—not a promise of Siri-equivalent behavior.
- **Acceptance criteria:**
  - [ ] Route identity includes server/environment/resource context and validates authentication,
    authorization, existence, and unsupported destinations.
  - [ ] Cold start, warm start, login-required, stale shortcut, and wrong-server paths fail safely.
  - [ ] Shortcut publication removes stale or unauthorized entities.
  - [ ] Navigation and device tests cover external intents and back-stack construction.

- [ ] **PAR-505 — Add interactive network topology visualization**

- **Status:** Ready
- **Priority:** P2
- **Dependencies:** None
- **Scope:** iOS 0.7.0 now renders an interactive network-to-container diagram while Android presents
  the same typed graph as grouped rows. Add a bounded, zoomable/pannable Android visualization while
  retaining the current list as an accessible and large-graph fallback. Do not copy known iOS summary
  stubs.
- **Acceptance criteria:**
  - [ ] Node, edge, grouping, scale, interaction, and accessibility requirements are defined.
  - [ ] Counts and relationships come from authoritative server data.
  - [ ] Large, cyclic, malformed, and partially unavailable graphs remain bounded and have a usable
    non-graph fallback.
  - [ ] Selection, environment changes, rotation, font scaling, and TalkBack are device-tested.

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

- [ ] **PAR-501 — Arcane Updates counts and navigation**

- **Status:** Needs validation
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
  - [ ] Multi-environment live-server tests prove counts and destination consistency.
- **Implementation and validation evidence (2026-08-27):**
  - Source pins: Android base `982d8cc844c604a029371ebda0ae12e80d5764bd`, iOS
    `a3440b05238d2620b91d984557c87994ab15fb28`, libarcane-kotlin
    `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`, and Arcane
    `8d10b7db2d34aefa44f0f9a684f3b84b2ae355d7`.
  - Current iOS loads `images.updateSummary(...).imagesWithUpdates` for every enabled environment,
    and its Dashboard source explicitly documents the image total as the intended mobile value. Its
    current streamed-count precedence can still expose the resource count, so the explicit product
    decision and the image-oriented Updates screen resolve that internal inconsistency for Android.
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
- **Scope:** Verify complete environment coverage and result reporting, but do not change Arcane
  Updates counts/navigation while PAR-501 is on hold.
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
