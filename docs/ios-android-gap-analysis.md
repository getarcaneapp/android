# iOS-to-Android gap analysis

Last reviewed: 2026-07-17

This document compares Arcane's iOS application with the Android application to guide Android
product planning. It is a source-analysis snapshot, not a promise that Android will reproduce every
iOS implementation detail.

## Comparison baseline

The analysis is pinned to these product revisions:

| Component | Revision | Notes |
| --- | --- | --- |
| iOS | [`03f2f3d11e40f759ca62f0207bb3d59418a42933`](https://github.com/getarcaneapp/iOS/tree/03f2f3d11e40f759ca62f0207bb3d59418a42933) | `main`, app version 0.5.4, dated 2026-07-10 |
| iOS resolved Swift SDK | [`c1016b2e0aaebffc112893179560c2462c1a013a`](https://github.com/getarcaneapp/libarcane-swift/tree/c1016b2e0aaebffc112893179560c2462c1a013a) | `libarcane-swift` revision resolved by the iOS project |
| Android | [`c500c262e4e71b094e16ca8afc049f2286d22cfa`](https://github.com/getarcaneapp/android/tree/c500c262e4e71b094e16ca8afc049f2286d22cfa) | `origin/main` product baseline |
| Kotlin SDK | [`991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8`](https://github.com/getarcaneapp/libarcane-kotlin/tree/991dfdc1ee747c171ebf1b5953fe5fb61ceadfb8) | `origin/main` product baseline |

The Android and Kotlin SDK checkouts also had `agent/add-agent-guidance` commits at review time.
Those commits add only repository guidance and are not treated as product functionality.

### Method and limitations

This is a static source comparison of application structure, visible routes, stores, service calls,
models, persistence, tests, and release automation. No iOS build was run because the review was
performed on Windows. No emulator/device or live Arcane server was used, and neither application's
complete runtime behavior was manually exercised. Items that depend on server version, runtime
permissions, signing, background execution limits, or App Store/Play distribution should therefore
be validated before implementation decisions are made.

The current Android/Kotlin composite checkout did pass
`./gradlew :app:testDebugUnitTest :app:assembleDebug` during this analysis. That result confirms the
reviewed source compiles and its JVM unit tests pass; it does not validate device or live-server
behavior.

The comparison distinguishes product capabilities from platform-specific mechanisms. For example,
an iOS Live Activity does not imply that Android needs a literal copy; the Android question is
whether a persistent notification or another Android-native surface should provide the same
operational continuity.

## Status legend

| Status | Meaning |
| --- | --- |
| **Parity** | Android provides the same core user outcome, even if the UI or platform API differs. |
| **Partial** | Android implements a useful subset, but an important workflow, state, or polish layer is absent. |
| **Android gap** | The iOS user outcome has no meaningful Android implementation at this baseline. |
| **Shared gap** | Both applications lack the capability or expose only a placeholder. |
| **Android strength** | Android is ahead or has a materially stronger implementation in this area. |
| **Validate** | Static inspection is insufficient or the SDK/server prerequisite needs confirmation. |

## Executive summary

Android is already a substantial operational client rather than a shell. Its resource coverage is
broad: containers, images, projects, volumes, networks, ports, updates, activities, events, jobs,
Git repositories, GitOps, registries, templates, user administration, RBAC, notifications,
authentication settings, system settings, builds, and upgrades are all represented. The application
also has live streams for important operational views and a larger JVM unit-test body plus working
CI than the iOS repository.

The largest difference is depth and continuity, not the count of resource screens. iOS has a more
mature application shell and operational layer: disk-backed stale-while-revalidate caching,
adaptive tablet navigation, profile management, a complete project-file workspace, richer log
workflows, image attestations, persistent deployment progress, and several native entry points.
iOS also contains an optional on-device AI assistant, although that implementation is tied to
Apple's Foundation Models and should be treated as a product concept rather than a direct Android
port.

The most urgent Android work is smaller than those strategic gaps. Change-server can leave
prior-server state or credentials alive; some admin destinations lose their drill-down callbacks
when selected as main tabs; the appearance selector is non-persistent and does not drive the app
theme; and Settings links point to the iOS repository. Those should be fixed before broad parity
work. Separately, decide whether to expose the currently unreachable environment
list/detail/test surface.

The recommended sequence is:

1. Fix reachable-navigation and settings defects.
2. Complete high-value daily workflows: project files, profile/account management, logs, image
   attestations, and missing container actions.
3. Add resilient cached reads and persistent long-running operation state.
4. Add Android-native equivalents for adaptive navigation, widgets, shortcuts, deep links, and
   ongoing-operation notifications.
5. Consider optional strategic features such as AI assistance and multi-server profiles only after
   the operational foundation is reliable.

## Detailed capability matrix

### Application shell, navigation, and presentation

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Application architecture | SwiftUI state machine with setup, authentication, login, and authenticated states; service/store ownership around the SDK. | Single `ComponentActivity`, Compose auth router, central `ArcaneClientManager`, and screen-local stores. See `app/src/main/kotlin/app/getarcane/android/MainActivity.kt`, `ui/ArcaneApp.kt`, and `core/ArcaneClientManager.kt`. | **Parity** for the core application lifecycle. Preserve one client/auth owner. |
| Configurable primary tabs | Four user-swappable resource tabs plus Settings, with tab state and independent navigation. | Four user-swappable resource tabs plus fixed Settings; selected tabs persist through `nav/NavTabsStore.kt`. | **Parity** in basic configuration. |
| Adaptive large-screen navigation | Compact navigation and an optional regular-width sidebar/drawer. | Bottom navigation only; no tablet-adaptive rail/sidebar strategy was found. | **Android gap.** Add a `NavigationSuiteScaffold`-style adaptive shell or equivalent after navigation defects are fixed. |
| Per-tab navigation continuity | Independent navigation stacks, environment-aware rebuild, deep-link restoration. | Android rebuilds the selected tab's content and loses that tab's nested stack when switching tabs. | **Android gap.** Preserve independent stacks across tab switches; separately validate configuration-change and process-recreation restoration. |
| Deep links and external entry points | Deep links can select tab/environment/container/project; quick actions and widgets use them. | OIDC callback handling exists, but no comparable authenticated resource deep-link system was found. | **Android gap.** Define stable internal routes before widgets and shortcuts. |
| Release notes | Version-aware release notes display automatically when appropriate. | A manually reachable What's New surface exists, but Android does not automatically present new-version notes. | **Android gap.** Add version-gated automatic presentation after correcting the Android release-note data. |
| Appearance | Accent, sidebar preference, alternate icons, material compatibility, motion-aware polish. | Accent preference exists. Light/Dark/Auto is screen-local state, resets, and does not drive the application theme. | **Partial/defect.** Wire theme mode into persistent app-wide state before adding further appearance options. |
| Localization | English-only; future-language intent is visible. | Most user-visible text is hard-coded; only minimal string resources exist. | **Shared gap**, with higher Android remediation cost. New work should use resources without coupling a feature to a full rewrite. |
| Accessibility and interaction polish | Haptics, toasts, custom confirmations, reduce-motion handling, skeletons, tips, and review prompts. | Standard Compose semantics and confirmations exist, but no comparable coordinated polish layer was identified. | **Partial.** Audit accessibility, motion, haptics, and destructive confirmations as cross-cutting work. |

### Server setup, authentication, and account

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Server setup | Server URL setup with DNS/bootstrap retry and local-server allowances. | URL normalization and server setup exist. | **Parity** for the primary outcome; compare error recovery during live testing. |
| Password authentication | Password login, secure persisted credentials/tokens, session restore, and logout. | Password login, encrypted token storage, restoration, and logout via `ArcaneClientManager`. | **Parity.** |
| OIDC | Uses `ASWebAuthenticationSession` and public provider information. | Current and legacy OIDC callback/deep-link handling. | **Parity** at the product level; device-test provider variants. |
| Demo mode | Demo provisioning and session behavior. | Demo provisioning, heartbeat, and countdown. | **Parity**, with Android exposing explicit heartbeat/countdown behavior. |
| User profile | View/update display name and email, change password, avatar/Gravatar handling, sign out, and change server. | Current-user data is held for authorization, but no comparable end-user profile/account workflow was found. | **Android gap.** Add a profile route distinct from admin user management. |
| Multiple server profiles | No complete multi-profile manager was identified; change-server flow exists. | One active server is persisted. `changeServer` only transitions to setup; stale client, user, capabilities, cookies, or tokens may survive, and stored tokens are not scoped by server. | **Shared profile gap plus Android security/correctness defect.** Immediately invalidate all prior-server state and scope credentials by server before considering profiles. |
| Biometric application lock | No core capability identified. | No core capability identified. | **Shared gap**, not required for iOS parity. Consider separately if threat modeling supports it. |

### Dashboard and environment management

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Fleet dashboard | Fleet totals, server cards, stats/sparklines, needs-attention groups, failed activities, pinned resources/actions, update-all, and card actions for environment sync/system/upgrade/prune. | Fleet totals/cards, stats/sparklines, needs attention, failed activities, pins/actions, update-all, and per-environment prune/detail/active behavior; sync/system/upgrade card actions are absent. | **Partial.** Add the missing high-value card actions with permission and server-capability gating. |
| Live dashboard updates | v2 stream with legacy fallback and bounded concurrent stats streams. | Dashboard streaming with reconnect behavior and resource statistics streams. | **Partial.** Validate fallback/version behavior and connection limits under many environments. |
| Environment selection | Active environment selection and environment-aware navigation. | Active environment selection, detail/test, persistence, and client rebuild. | **Parity** for selection. |
| Environment management | Neither pinned client exposes a confirmed full create/edit/delete workflow. | `ui/screens/environments/EnvironmentsScreen.kt` contains an unreachable list/detail/set-active/metadata/version/test surface; it is not full CRUD. | **Shared/undefined capability.** Decide the supported server workflow before adding full management. Android may separately expose its existing read/detail/test surface if that has product value. |
| Fleet pagination | Environment-backed views load the complete relevant fleet. | The SDK environment list defaults to 20. `DashboardScreen`, `UpdatesScreen`, `AllEnvironmentsImageUpdatesScreen`, and `EnvironmentListScreen` call it without pagination, silently omitting environments above 20. | **Android correctness defect.** Implement explicit paging or a deliberate complete-fleet query and test fleets of 0, 20, 21, and multiple pages. |
| Offline dashboard snapshot | Disk cache and last-known server snapshots support stale display. | No disk-backed response cache/database was found. | **Android gap.** See the resilience section. |

### Containers

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Inventory and filtering | List/search/filter, selection, bulk deletion, and prune. | List/search/filter, pin, resource actions, and prune. | **Partial.** Confirm bulk selection/delete parity. |
| Lifecycle actions | Start, stop, restart, pause, unpause, redeploy, rename, and delete as applicable. | Start, stop, restart, unpause, redeploy, rename, and delete are visible across list/detail flows. Pause and kill are absent from the UI even though the pinned Kotlin SDK exposes them. | **Android UI gap.** Add pause and kill with current-state gating and explicit confirmation appropriate to their impact. |
| Detail depth | Configuration, health, ports, environment, labels, mounts, and networks. | Overview, live stats, logs, inspect/copy, and operational actions. | **Partial.** Compare detail fields on real containers and fill high-value metadata gaps. |
| Statistics | Live CPU, memory, network, and I/O presentation. | Live statistics and charts. | **Parity.** |
| Logs | Search/filter, pause, timestamps, retention, ANSI rendering, copy/share/export. | Live logs and ANSI handling are present, but the iOS-level copy/share/export workflow was not identified. | **Partial.** Add select/copy/share/export and verify cancellation/reconnect behavior. |
| Terminal | Interactive terminal with special keys, copy, and clear. | Interactive terminal exists. | **Partial to parity.** Device-test IME, lifecycle, special-key, and reconnect behavior. |
| Ask AI from resource context | Resource-aware entry to the on-device assistant. | No AI assistant. | **Android gap**, but strategic and optional rather than core container parity. |

### Projects and Compose workflows

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Project list and lifecycle | Active/archived projects, create, deploy/redeploy, start/stop/restart, logs, archive/delete. | Active/archived projects, create from blank/template, lifecycle streams, logs, archive/delete. | **Parity** for broad lifecycle coverage. |
| Project creation | Compose and `.env` input, templates, variable-resolution support. | Blank/template creation with Compose and `.env` input. | **Parity** for initial creation. |
| Existing project files | File tree, Compose/`.env` editor, save, create, rename, move, and delete. GitOps/archived projects are read-only where appropriate. | Existing-project Compose is effectively read-only; there is no complete file workspace. | **Major Android gap.** This is the highest-value feature-depth gap for users managing projects from mobile. |
| Variable resolution | Resolution preview plus resolved YAML in the editing workflow. | Preview exists, but it is not part of a full editable existing-project workspace. | **Partial.** Fold it into the file editor rather than building another isolated preview. |
| Persistent deployment progress | Operation store survives sheet dismissal; floating progress pill, activity IDs, reconnect/cancel, and background grace. | Streaming action screens exist, but no equivalent process-resilient or app-wide operation presentation was found. | **Android gap.** Add application-owned operation state and an ongoing notification where appropriate. |
| Project logs | Searchable/shareable operational logs integrated with deployment state. | Project logs exist. | **Partial.** Align the useful log operations with container logs. |

The pinned Kotlin SDK is ready for the project workspace: `ProjectsService` exposes Compose,
file-list/read, and project-update operations, while `UpdateProject.fileChanges` has typed
`create_file`, `create_folder`, `update_file`, `rename`, `move`, and `delete` changes. This is
therefore Android UI/state work, with serialization and live-server contract tests required before
release. Do not add application-local HTTP calls or duplicate DTOs.

### Images, updates, and supply-chain data

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Image inventory and lifecycle | List/detail, pull, streamed tar upload through `UploadImageView`, delete/prune, inspect/config/layers, and update workflows. | Filtered inventory, streamed pull, tar upload, remove/prune, inspect/config/layers, and update flows. | **Parity.** |
| Vulnerability scanning | Scan, filter, ignore, and inspect vulnerabilities. Some DTOs are app-local raw REST because of an iOS SDK mismatch. | Scan/filter/ignore and aggregate/detail vulnerability flows are present through the Kotlin stack. | **Parity/Android strength.** Keep DTOs in the SDK and verify unknown values defensively. |
| Image attestations | Attestation list/filter/detail and statement copy. | No attestation UI was identified; the pinned Kotlin SDK exposes attestation operations. | **Android UI gap.** Add the workflow using SDK types and confirm payload behavior against the target server. |
| Image updates | Per-image and fleet update flows. | Per-image, update overview, updater, and fleet-update flows. | **Parity/Android strength.** Android has substantial explicit updater behavior. |

### Volumes, networks, and ports

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Volumes | Create/remove/prune, detail, browser, backups, restore/delete/download. | Create/remove/prune, detail, browser, backups, restore/delete/download. | **Parity.** |
| Network management | List/create/delete/detail plus a topology view. The current topology is a list rather than a graph. | List/create/delete/detail plus topology presented as nodes/list. | **Parity with a shared gap.** A graph is a future product enhancement, not an Android parity defect. |
| Network summary accuracy | Some internal/container-count values are stubbed in the iOS app. | Android data should be compared with server responses rather than copied from iOS summaries. | **Validate.** Do not treat known iOS stubs as a target. |
| Ports | Read-only port inventory. | Read-only port inventory. | **Parity.** |

### Activities, events, jobs, Git, and Swarm

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Activities | v2 activity stream, filtering, cancellation/clearing, environment context. | All-environment v2 live stream, filtering, cancel, and clear. | **Parity.** The pinned Kotlin SDK includes activity error/heartbeat support; validate event behavior against the target server. |
| Events | Event inventory and details. | Event inventory and details. | **Parity.** |
| Live event updates | Event presentation refreshes as server events arrive. | The Android screen loads paginated snapshots; no live polling or event stream refresh was identified. | **Partial.** Add lifecycle-aware polling or a server-supported stream, with visible refresh/error state. |
| Jobs | Job inventory and actions/details. | Job surfaces are present. | **Parity at screen level; validate** live lifecycle operations. |
| Git repositories and GitOps | Repository and GitOps management. | Typed Git repository and GitOps screens are present. | **Parity** in broad coverage. |
| Dynamic/generic resources | Generic resource presentation supports some server-driven expansion. | More domains are represented by typed screens; no equivalent general dynamic-resource UI was identified. | **Partial.** Prefer typed daily-use experiences; add generic fallback only if it materially improves forward compatibility. |
| Swarm | Placeholder. | `ui/screens/swarm/SwarmScreen.kt` is a placeholder. | **Shared gap.** Do not prioritize as an Android parity issue until the product defines the workflow. |

### Administration, RBAC, and server settings

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| User administration | User list/detail and account administration. | User list/detail and role-related management. | **Parity** in broad coverage; this does not replace an end-user profile screen. |
| API keys | API key management. | API key management. | **Parity.** |
| Roles and OIDC mappings | Role/RBAC and OIDC mapping administration. | Roles/RBAC and OIDC mapping administration. | **Parity** at screen level. |
| Notification providers and webhooks | Provider-specific notification configuration and webhooks. | Notification and webhook configuration. | **Parity** in broad coverage; compare provider-specific validation. |
| Authentication/system/build/upgrade | Server authentication settings, system information/settings, builds, and upgrade. | Authentication, system, build, and upgrade surfaces. | **Parity** in broad coverage. |
| Admin destinations as swappable tabs | Destinations retain their expected drill-down behavior. | Users, Notifications, System, and Roles use empty drill-down callbacks when selected as primary tabs in `nav/MainTabView.kt`; they work through Settings. | **Android defect.** Reuse one route owner or pass functional callbacks in both entry contexts. |
| Documentation/support links | iOS repository links are appropriate to the app. | App Settings GitHub/issue links point to the iOS repository. | **Android defect.** Point source and issue links to the Android repository or a deliberate cross-project destination. |

### Streaming, caching, offline behavior, and background work

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Read caching | Server/user/environment/path-scoped disk cache with stale-while-revalidate, request coalescing, LRU limits, and seven-day expiry. | Preferences and pins persist, but no general response cache/database was found. | **Major Android gap.** Start with safe read-only snapshots for dashboard and resource lists. |
| Offline behavior | Last-known snapshots can render; mutations still require connectivity and are not queued. | Network failure generally leaves each screen to its own loading/error path. No offline mutation queue exists. | **Partial/shared boundary.** Match stale reads; do not queue destructive mutations without a separate design. |
| Stream ownership | Reconnect behavior plus application-owned deployment state and bounded background grace. | Dashboard, stats, activities, logs, terminal, project actions, and image progress stream. Ownership is screen/store dependent. | **Partial.** Standardize retry, environment changes, and cancellation. |
| Activity Center failure recovery | Activity work can recover after transient stream/list failures. | The Activity Center has live and paginated behavior, but a clear user retry path after terminal failure was not identified. | **Partial/defect.** Add retry and test per-environment partial failure without discarding healthy results. |
| Coroutine/task cancellation | Structured task ownership with Swift concurrency. | Coroutines are used broadly, but `CancellationException` handling is uneven. | **Android hardening gap.** Audit broad catches and always rethrow cancellation. |
| Background execution | Limited background grace for active deployment operations and native Live Activity integration. | No WorkManager/background service or operation notification was found. | **Android gap.** Use an ongoing notification/foreground service only for user-initiated work that truly must outlive the screen; use WorkManager for eligible deferred work. |

Caching must be scoped by server, user, environment, and request identity. Authentication changes,
server changes, environment deletion, and destructive mutations need explicit invalidation. A cache
should never make a failed mutation appear successful.

### Platform-native integration

These rows compare user outcomes, not identical APIs.

| User outcome | iOS mechanism | Appropriate Android direction | Status |
| --- | --- | --- | --- |
| At-a-glance status | Home-screen widgets for status, environments, and updates; App Group snapshots. | Glance widgets backed by explicitly scoped, sanitized snapshots. | **Android gap.** |
| Persistent operation progress | Live Activities and Dynamic Island for deployments. | Ongoing notification, foreground service when justified, and in-app operation surface. | **Android gap.** |
| Voice/automation entry | App Intents, Siri, and Shortcuts for open/start/stop/restart and entity queries. | App shortcuts, deep links, and optionally App Actions where support and value justify them. | **Android gap.** |
| Quick launch | Home-screen quick actions. | Static/dynamic app shortcuts. | **Android gap.** |
| Responsive larger-screen UI | Optional sidebar. | Adaptive navigation rail/drawer and list-detail layouts. | **Android gap.** |
| Share/export | Native log sharing/export. | Android Sharesheet and Storage Access Framework/MediaStore as appropriate. | **Partial.** Android already uses MediaStore/download and share primitives in some flows, but not consistently for logs. |
| File input/output | Native pickers and share sheets. | Android file picker, MediaStore downloads, clipboard, share, and autofill are already used. | **Parity/Android strength.** |
| Alternate application icon | Supported. | Launcher alias approach is possible but launcher-dependent. | **Optional platform difference**, not a parity priority. |
| On-device AI | Apple Foundation Models on supported iOS 26 hardware, with streaming, read tools, and staged confirmed mutations. | If pursued, define provider, privacy boundary, device/server capability, tool permissions, and mandatory confirmation independently. | **Strategic Android gap**, not a literal port. |

No Android notification, widget, shortcut, share-in, resource app-link, QR setup, or biometric-lock
system was identified at the baseline. These should not be delivered as one monolithic “native
features” project; each needs a clear user scenario and data-security review.

### Persistence, security, and backup

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Tokens | Shared Keychain with migration and widget access controls. | Encrypted token storage through the Android SDK layer. | **Parity** for secure application storage; review backup/extraction behavior. |
| Preferences | App settings, navigation, cache policy, and account context persist. | DataStore persists server, accent, environment, and navigation tabs; SharedPreferences stores pins. Theme mode does not persist. | **Partial.** Consolidate ownership when adding theme/cache/operation state. |
| Response data | Bounded disk cache. | No equivalent response store. | **Android gap.** |
| Android backup policy | Not applicable. | `app/src/main/AndroidManifest.xml` sets `android:allowBackup="true"` while `res/xml/data_extraction_rules.xml` retains template TODO guidance. | **Android defect/risk.** Explicitly exclude tokens and sensitive cached/server data; validate both legacy and current backup rules. |

### Quality, testing, CI, and distribution

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Unit tests | Approximately 19 XCTest methods across five files, focused mainly on utilities. | 19 JVM test files and roughly 84 test methods, with useful coverage of navigation, dashboard/updater logic, URL handling, and ANSI parsing. | **Android strength.** |
| UI/instrumented tests | No meaningful UI test suite identified. | Only the template/example instrumentation test was identified. | **Shared gap.** Add a small navigation/auth/destructive-confirmation suite before attempting broad UI automation. |
| Integration/network contract tests | No broad suite identified. | No broad end-to-end contract suite identified. | **Shared gap.** SDK serialization/service tests should carry most wire-contract coverage. |
| CI | No repository CI workflow was identified in the inspected iOS baseline. | CI uses JDK 21/API 35 and runs unit tests/build, with optional signed tag release support. | **Android strength.** |
| Static quality/security gates | No comprehensive suite identified. | No lint, detekt, ktlint, instrumentation, or security scan gate was identified in CI. | **Android gap.** Add targeted gates incrementally; do not create a noisy all-at-once migration. |
| Release maturity | Version 0.5.4/build 260710 and more distribution-oriented product surfaces. App Store status was not confirmed. | Version 0.1.0; minification disabled; repository messaging still warns that the app is not intended for devices. | **Android maturity gap.** Define alpha/beta support criteria before production claims. |
| Release-note integrity | Notes correspond to iOS releases and platform behavior. | `ui/screens/whatsnew/ReleaseNotes.kt` begins at 0.2.1 while the app reports 0.1.0 and contains copied iOS-specific claims. | **Android release-hygiene defect.** Replace with Android-verified notes and enforce version ordering. |

## SDK/API prerequisites versus Android-only work

The repository dependency direction remains:

`Arcane server contract -> libarcane-kotlin -> Android`

Do not close a parity gap by adding app-local endpoints, streaming parsers, auth handling, or DTO
copies. Inspect the Arcane Go types/handlers first, then implement missing Kotlin SDK support with
tests, then build the Android UI.

### Likely Android-only work

These items appear to have sufficient application or SDK foundations and are primarily Android
composition, persistence, or platform work:

- decide whether to expose the currently unreachable environment list/detail/test surface, and
  repair admin-tab drill-down callbacks;
- persist and apply Light/Dark/Auto appearance;
- correct Android source/issue links;
- add an end-user profile route using the SDK's existing user update, password-change, and avatar
  retrieval/display support;
- add log copy/share/export;
- add adaptive navigation and authenticated resource deep links;
- add widgets, shortcuts, and ongoing-operation notifications;
- add response caching around existing read services;
- fix all complete-fleet callers to page through `EnvironmentsService` explicitly;
- implement the project-file workspace using the pinned typed SDK operations;
- harden coroutine cancellation and stream ownership;
- define Android backup/extraction rules.

### Validate SDK coverage before estimating

These need an explicit SDK/server capability check at the pinned revisions:

- exact activity stream error, heartbeat, and forward-compatible event handling;
- dynamic/generic resource descriptors, if a generic fallback UI is desired;
- server-version fallbacks for dashboard and fleet updates.

The pinned Kotlin SDK already exposes user update/password and avatar retrieval, image attestation
operations, explicit environment pagination queries, container pause/kill, typed project-file
changes, and activity/stream APIs. Profile, attestation, container-action, and project-workspace
gaps should therefore begin as Android UI/state work, while still receiving focused serialization
and contract tests against the target server version.

No current SDK blocker was identified for these top gaps. The pinned SDK revision, the sibling
composite-build revision, and the target server version must nevertheless be recorded together
because active SDK development can change that conclusion.

### Server or product-definition prerequisites

- Swarm remains a placeholder in both clients and needs a defined product/API workflow.
- A true network topology graph is not present in either app and should be specified as a shared
  enhancement.
- Multi-server profiles require an explicit credential, cache, deep-link, and active-operation
  model.
- AI assistance requires a separate product/security design, especially for mutations.

## Android strengths to preserve

- Broad typed coverage across operational and administrative domains.
- Strong update/updater and fleet-update surfaces.
- Kotlin SDK boundary avoids the app-local raw DTO workaround used by some iOS vulnerability
  flows.
- More JVM unit tests and an active build/test CI baseline than the inspected iOS repository.
- Existing Android-native file picker, MediaStore download, clipboard, share, and autofill
  integrations.
- Central ownership of client, authentication, active environment, and capabilities through
  `ArcaneClientManager`.
- A practical mobile list/detail Compose architecture rather than a wholesale attempt to copy the
  iOS presentation.

Parity work should extend these strengths. It should not replace typed Android screens with generic
views, create a second client/cache owner, or reproduce Apple-specific UI metaphors.

## Prioritized roadmap

### P0: Correctness and reachable functionality

1. Make change-server safe: clear the prior client, current user, capabilities, cookies, and active
   environment, and store credentials/tokens under a normalized server identity.
2. Repair the empty callbacks for Users, Notifications, System, and Roles when used as swappable
   main tabs.
3. Decide whether to expose Android's existing environment list/detail/test surface; do not label
   it full CRUD without a defined shared workflow.
4. Persist Light/Dark/Auto and apply it at the application theme root.
5. Correct source, documentation, and issue links that point to the iOS repository.
6. Fix silent 20-environment truncation in dashboard, updates, all-environment image updates, and
   environment management; add multi-page tests.
7. Replace copied iOS release notes with Android-specific, version-consistent notes, then add
   version-gated automatic presentation.
8. Define backup/data-extraction exclusions for tokens, server data, future caches, and operation
   state.
9. Audit broad coroutine exception catches and rethrow cancellation.

### P1: Complete high-frequency operational workflows

1. Build the existing-project file workspace: file tree, Compose/`.env` editing, save/create,
   rename/move/delete, resolution preview, and resolved YAML.
2. Add the signed-in user's profile/account workflow, separate from administrator user management.
3. Complete log search/copy/share/export and lifecycle consistency across container/project logs.
4. Add image attestation list/detail/filter/copy using the existing SDK support.
5. Fill missing container lifecycle/detail actions that the server and SDK support.
6. Add application-owned long-running operation state with reconnect/cancel and an in-app progress
   surface.
7. Add lifecycle-aware event refresh and an explicit Activity Center retry path.

### P2: Resilience and Android-native continuity

1. Add scoped, bounded stale-read caching for dashboard and high-value resource lists.
2. Standardize stream retry, cancellation, selected-environment changes, and stale-state markers.
3. Add an ongoing notification for user-initiated deployments or updates that outlive a screen;
   use foreground execution only when Android policy requires it.
4. Add authenticated resource deep links and dynamic shortcuts.
5. Add adaptive navigation and list-detail layouts for tablets/foldables.
6. Add one or two privacy-reviewed Glance widgets using sanitized snapshots.

### P3: Product expansion

1. Evaluate a multi-server profile model.
2. Establish a localization path and move existing text incrementally to resources.
3. Evaluate AI assistance as a platform-neutral product capability with read-only tools first and
   explicit confirmation for mutations.
4. Add broader UI/integration testing and incremental lint/static-analysis gates.
5. Revisit Swarm and topology visualization only after shared product requirements exist.

## Acceptance criteria for parity work

For each roadmap item:

- pin the Android, Kotlin SDK, and server revisions used;
- state whether the work is Android-only, SDK-plus-Android, or requires an upstream Arcane change;
- test unknown/optional wire values and older-server behavior where applicable;
- preserve unrelated local changes and keep cross-repository commits independently reviewable;
- run SDK tests before Android tests when the SDK changes;
- report unit/build/device/live-server validation separately;
- update the relevant matrix row and baseline SHA when the work lands.

The standard local checks are:

```text
# libarcane-kotlin, when changed
./gradlew :arcane-core:test :arcane-android:assembleRelease

# android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Use JDK 21. A successful JVM build does not replace emulator/device verification for navigation,
authentication, streaming, background execution, file handling, or destructive operations.

## Maintaining this document

This report belongs in `docs/` because it is durable product and implementation research.
`AGENTS.md` should remain concise operational guidance for contributors and agents; it is not the
only project-persistence mechanism and should not become a product backlog.

When refreshing this report:

1. update all pinned baseline revisions before changing conclusions;
2. compare source and reachable navigation, not file names alone;
3. distinguish missing functionality from intentional platform analogues;
4. move completed items to **Parity** and retain any follow-up validation;
5. identify whether an apparent Android gap is actually shared by iOS;
6. avoid treating known iOS workarounds or stubs as the desired architecture.
