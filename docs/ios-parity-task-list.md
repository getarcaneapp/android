# Android iOS-parity task list

Last updated: 2026-07-17

This is the working backlog for bringing Arcane Android to product-outcome parity with iOS. It
turns the findings in [the pinned gap analysis](ios-android-gap-analysis.md) into issue-sized work;
it does not repeat that research.

The source comparison is pinned to:

- iOS `03f2f3d11e40f759ca62f0207bb3d59418a42933`
- libarcane-swift `c1016b2e0aaebffc112893179560c2462c1a013a`
- Android `c500c262e4e71b094e16ca8afc049f2286d22cfa`
- libarcane-kotlin `991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`

Revalidate conclusions against current source before starting an item. Record the Android, Kotlin
SDK, and Arcane server revisions in the resulting issue or pull request.

## Status legend

| Status | Meaning |
| --- | --- |
| **Ready** | Evidence and prerequisites are sufficient to begin. |
| **Needs revalidation** | The item is plausible or previously active, but current source, PR, CI, SDK, or runtime behavior must be checked before changing code. |
| **Blocked/Hold** | Do not implement until the named external dependency or product decision changes. |
| **Deferred** | Deliberately sequenced behind foundation work or not required for current parity. |
| **Done/verify** | Later notes suggest the work progressed or landed; confirm current behavior and close or reopen with new evidence. |

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

- [ ] **PAR-001 — Revalidate PR #29 authentication/session unlock**

- **Status:** Needs revalidation
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Inspect the current branch, PR #29 state, review feedback, and final CI results. Reproduce
  the original session-unlock failure before deciding whether any code remains.
- **Acceptance criteria:**
  - [ ] The PR's merge/close state, head revision, reviews, and CI conclusion are recorded.
  - [ ] Login restoration and unlock are exercised for fresh login, restored session, invalid token,
    logout, and process recreation.
  - [ ] The task is closed if current code already fixes the issue; otherwise a new issue describes the
    still-reproducible behavior and contains focused regression coverage.

- [ ] **PAR-002 — Make change-server state and credential scoping safe**

- **Status:** Ready
- **Priority:** P0
- **Dependencies:** PAR-001
- **Scope:** Ensure changing servers cannot reuse the prior server's client, current user,
  capabilities, cookies, active environment, token, cache, or operation state. Normalize server
  identity and scope credentials by that identity.
- **Acceptance criteria:**
  - [ ] Selecting change server immediately invalidates all in-memory state belonging to the old server.
  - [ ] Persisted tokens and other sensitive state cannot be loaded for a different normalized server.
  - [ ] Tests cover two servers, equivalent URL spellings, logout, invalid credentials, and process
    recreation.
  - [ ] Device testing confirms no prior-server data flashes or actions remain available.

- [ ] **PAR-003 — Fix complete-container loading before local filtering**

- **Status:** Ready
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Make the Containers tab filter a complete result set rather than the SDK's default
  first page of 20. Do not assume `limit = -1` is supported: inspect the Arcane handler and SDK
  semantics, then use explicit paging unless an unlimited query is documented and safely bounded.
- **Acceptance criteria:**
  - [ ] The server and SDK behavior for page size, start, limit, ordering, and terminal-page detection is
    documented in focused tests or issue evidence.
  - [ ] An environment with more than 20 containers displays and filters across the full set without
    duplicates, omissions, or infinite requests.
  - [ ] Search/status filters are proven to run after complete loading, or are moved server-side with
    equivalent semantics.
  - [ ] Loading, partial-page failure, refresh, cancellation, and empty states are covered.

- [ ] **PAR-004 — Audit all complete-list call sites for silent pagination truncation**

- **Status:** Ready
- **Priority:** P0
- **Dependencies:** PAR-003
- **Scope:** Inventory every list call whose UI or calculation claims fleet-wide or complete
  results. Prioritize environments, dashboard totals/cards, updates, all-environment image updates,
  and environment management.
- **Acceptance criteria:**
  - [ ] A checked inventory records each caller as intentionally paged, intentionally bounded, or fixed.
  - [ ] All complete-environment callers work with more than 20 environments.
  - [ ] Shared paging logic has duplicate/empty/short/final-page, cancellation, and error coverage.
  - [ ] UI copy does not claim complete totals when a view is intentionally bounded.

- [ ] **PAR-005 — Revalidate OIDC and admin-tab navigation**

- **Status:** Needs revalidation
- **Priority:** P0
- **Dependencies:** PAR-001
- **Scope:** Device-test current and legacy OIDC callbacks and verify Users, Notifications, System,
  and Roles retain drill-down actions both through Settings and when configured as primary tabs.
- **Acceptance criteria:**
  - [ ] OIDC success, cancel, invalid callback, provider error, and process-recreated callback flows are
    exercised on a device/emulator.
  - [ ] Each affected admin screen opens all supported details/actions from both navigation entry points.
  - [ ] Back behavior, tab switching, authorization loss, and environment changes do not strand a route.
  - [ ] Any remaining defect has a focused navigation regression test.

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

- [ ] **PAR-008 — Audit coroutine cancellation and stream ownership**

- **Status:** Ready
- **Priority:** P0
- **Dependencies:** None
- **Scope:** Find broad exception handling in stores and streams, rethrow `CancellationException`,
  and ensure environment/server/screen changes cancel the correct work.
- **Acceptance criteria:**
  - [ ] Broad catches no longer convert cancellation into user-visible failures or reconnect loops.
  - [ ] Tests cover cancellation during refresh, paging, reconnect, and environment/server changes.
  - [ ] At most one intended stream/job owner remains for each screen-level operation.
  - [ ] No stale result from a canceled prior environment can overwrite current state.

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
  Migrate one representative operation first, then the remaining approved operation types.
- **Acceptance criteria:**
  - [ ] Operations survive screen changes and expose progress, bounded logs, reconnect, cancel, success,
    failure, and indeterminate/unknown states from one owner.
  - [ ] Server, account, and environment changes cannot cross-contaminate operation state.
  - [ ] Process-death recovery follows the spec and never fabricates successful completion.
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

- **Status:** Blocked/Hold
- **Priority:** P1 after hold clears
- **Dependencies:** Kyle/upstream Arcane Updates changes
- **Scope:** Do not modify current counts, aggregation, or navigation based on prior conclusions.
  When upstream work lands, restart analysis from current Arcane handlers/types, SDKs, iOS, and
  Android source rather than applying an old patch or assumption.
- **Acceptance criteria:**
  - [ ] Upstream dependency and target revisions are explicitly recorded before work begins.
  - [ ] Counts are defined for pagination, permissions, unavailable environments, and server versions.
  - [ ] Navigation targets use authoritative resource/environment identity.
  - [ ] Multi-environment live-server tests prove counts and destination consistency.

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
- **Priority:** P3
- **Dependencies:** Stable operational foundation
- **Scope:** Treat iOS Foundation Models as a product concept, not a portable implementation. Define
  provider/device support, privacy, cost, context, tool permissions, and confirmation independently.
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

- [ ] **PAR-505 — Network topology visualization**

- **Status:** Deferred
- **Priority:** Not an Android-parity priority
- **Dependencies:** Shared product definition and accurate server data
- **Scope:** Both clients present topology primarily as a list. A graph is a shared enhancement and
  must not copy known iOS summary stubs.
- **Acceptance criteria:**
  - [ ] Node, edge, grouping, scale, interaction, and accessibility requirements are defined.
  - [ ] Counts and relationships come from authoritative server data.
  - [ ] Large and partially unavailable environments have a usable non-graph fallback.

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

- [ ] **PAR-V03 — Dashboard stream foundation and live-stats recovery**

- **Status:** Done/verify
- **Priority:** P1 if reopened
- **Dependencies:** PAR-008
- **Scope:** Verify reconnect, version fallback, cancellation, connection bounds, and recovery after
  server/environment changes.
- **Acceptance criteria:**
  - [ ] A current target server demonstrates recovery without duplicate streams or stale overwrites.
  - [ ] Unsupported/legacy behavior is explicit.
  - [ ] Close as verified or reopen with a focused reproduction.

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
