# iOS-to-Android gap analysis

Last reviewed: 2026-09-11

This document compares Arcane's iOS application with the Android application to guide Android
product planning. It is a source-analysis snapshot, not a promise that Android will reproduce every
iOS implementation detail.

## Comparison baseline

The analysis is pinned to these product revisions:

| Component | Revision | Notes |
| --- | --- | --- |
| iOS | [`6088fcc0ef04dc906ce74e9129dffa96894a6da5`](https://github.com/getarcaneapp/ios/tree/6088fcc0ef04dc906ce74e9129dffa96894a6da5) | Current `origin/main`; mobile behavior authority for this refresh |
| iOS resolved Swift SDK | [`facc40e20e32b7d6600b004fd744a214bbd2a166`](https://github.com/getarcaneapp/libarcane-swift/tree/facc40e20e32b7d6600b004fd744a214bbd2a166) | Current `origin/main`; compared for account, passkey/MFA, variables, and upgrade contracts |
| Android | [`75fde394f61ee8838f201f25b42a3e83af146513`](https://github.com/getarcaneapp/android/tree/75fde394f61ee8838f201f25b42a3e83af146513) | Current `origin/main` and Image Insights branch base |
| Kotlin SDK | [`275e7a533bd5f68063d3e275012041d0f846e251`](https://github.com/getarcaneapp/libarcane-kotlin/tree/275e7a533bd5f68063d3e275012041d0f846e251) | Image History PR #9 head; based on current `origin/main` `b21faefd091de53fa30e6b5b910c66f49ec8076c` |
| Arcane | [`5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105`](https://github.com/getarcaneapp/arcane/tree/5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105) | Current `origin/main` wire-contract authority; live compatibility exercised on 2.10.2 tag `670ee2b` |

This refresh revalidated Image Insights against current iOS, both SDKs, Arcane handlers/types,
Android `origin/main`, and the canonical backlog. It also reconciles the completed Projects
Workspace and Accounts and Administration batches.

Parity status also reflects the locally validated PAR-001 session-restoration hardening and PAR-002
server-session scoping layered on the pinned Android base. Their exact implementation and validation
evidence is maintained in the canonical task list.

### Method and limitations

Most rows remain a static source comparison. The completed Projects Workspace and Accounts and
Administration rows additionally have API 30 live evidence against disposable Arcane 2.10.2, and
the Android backup-policy row has API 35 cloud and device-transfer restore evidence. Provider-
dependent WebAuthn enrollment/login still requires a server with a completing ceremony.
Items outside those batches that depend on runtime permissions, signing, background execution, or
distribution still require targeted runtime validation.

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

The largest remaining difference is depth and continuity, not the count of resource screens. iOS
still has disk-backed stale-while-revalidate caching, adaptive tablet navigation, persistent deployment progress, several native entry
points, activity-start feedback, and interactive network topology. Android now covers profile,
project-file workspace, deploy options, template discovery, registry identity, passkeys/MFA,
scoped global variables, rich log continuity, container lifecycle actions, and Image Insights
through typed SDK contracts.

The most urgent Android work is smaller than those strategic gaps. PAR-002 closes the change-server
state and credential-scoping defect; PAR-005 closes the unreachable admin-navigation paths; and
PAR-006 gives App Settings deliberate Android/project links plus Android-owned, version-filtered
release notes. PAR-009 now persists Light/Dark/Auto and accent choices at the app theme root, while
PAR-007 deliberately excludes that server-adjacent preference file from backup. The old
recommendation to expose a separate
environment list is no longer a parity blocker: iOS 0.7.0 deliberately makes the dashboard its
single fleet destination, which is compatible with Android's dashboard-plus-detail outcome.

The recommended sequence is:

1. Add persistent long-running operation state and resilient cached reads.
2. Add Android-native equivalents for adaptive navigation, widgets, shortcuts, deep links, and
   ongoing-operation notifications.
3. Consider optional product expansion such as multi-server profiles only after the operational
   foundation is reliable.

## Detailed capability matrix

### Application shell, navigation, and presentation

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Application architecture | SwiftUI state machine with setup, authentication, login, and authenticated states; service/store ownership around the SDK. | Single `ComponentActivity`, Compose auth router, central `ArcaneClientManager`, and screen-local stores. See `app/src/main/kotlin/app/getarcane/android/MainActivity.kt`, `ui/ArcaneApp.kt`, and `core/ArcaneClientManager.kt`. | **Parity** for the core application lifecycle. Preserve one client/auth owner. |
| Configurable primary tabs | Four user-swappable resource tabs plus Settings, with tab state and independent navigation. | Four user-swappable resource tabs plus fixed Settings; selected tabs persist through `nav/NavTabsStore.kt`. | **Parity** in basic configuration. |
| Adaptive large-screen navigation | Compact navigation and an optional regular-width sidebar/drawer. | Bottom navigation only; no tablet-adaptive rail/sidebar strategy was found. | **Android gap.** Add a `NavigationSuiteScaffold`-style adaptive shell or equivalent after navigation defects are fixed. |
| Per-tab navigation continuity | Independent navigation stacks, environment-aware rebuild, deep-link restoration. | Android rebuilds the selected tab's content and loses that tab's nested stack when switching tabs. | **Android gap.** Preserve independent stacks across tab switches; separately validate configuration-change and process-recreation restoration. |
| Deep links and external entry points | Deep links can select tab/environment/container/project; quick actions and widgets use them. | OIDC callback handling exists, but no comparable authenticated resource deep-link system was found. | **Android gap.** Define stable internal routes before widgets and shortcuts. |
| Release notes | Version-aware release notes display automatically when appropriate. | A manually reachable What's New surface shows Android-owned notes at or below the installed version and identifies only an exact installed-version match. Android does not automatically present new-version notes. | **Partial.** The release data and version boundary are safe; add automatic presentation only from the exact installed-version mapping. |
| Appearance | Accent, sidebar preference, alternate icons, material compatibility, motion-aware polish. | Accent and Light/Dark/Auto persist through app-owned preferences and drive the application theme root. They intentionally reset to defaults after reinstall/restore because server and environment identity shares the same protected preference file. | **Partial.** Core theme persistence is complete; iOS retains additional icon, sidebar, and motion-aware appearance options. |
| Localization | English-only; future-language intent is visible. | Most user-visible text is hard-coded; only minimal string resources exist. | **Shared gap**, with higher Android remediation cost. New work should use resources without coupling a feature to a full rewrite. |
| Accessibility and interaction polish | Haptics, toasts, custom confirmations, reduce-motion handling, skeletons, tips, and review prompts. | Standard Compose semantics and confirmations exist, but no comparable coordinated polish layer was identified. | **Partial.** Audit accessibility, motion, haptics, and destructive confirmations as cross-cutting work. |

### Server setup, authentication, and account

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Server setup | Server URL setup with DNS/bootstrap retry and local-server allowances. | URL normalization and server setup exist. | **Parity** for the primary outcome; compare error recovery during live testing. |
| Password authentication | Password login, secure persisted credentials/tokens, session restore, and logout. | Password login, encrypted token storage, restoration, and logout via `ArcaneClientManager`. | **Parity.** |
| OIDC | Uses `ASWebAuthenticationSession` and public provider information. | Current and legacy OIDC callback/deep-link handling. | **Parity** at the product level; device-test provider variants. |
| Passkeys and MFA | Passkey sign-in, passkey enrollment/rename/delete, password or passkey step-up, MFA policy, and recovery on supported Arcane servers. | Typed Kotlin contracts and a server-origin mobile bridge integrate Credential Manager with capability-gated login, account enrollment/rename/delete, step-up, MFA policy, and recovery. | **Parity** for supported-server outcomes. API 30 verified capability/status, password step-up, and safe browser cancellation; a completing WebAuthn provider remains a release-environment integration check. |
| Demo mode | Demo provisioning and session behavior. | Demo provisioning, heartbeat, and countdown. | **Parity**, with Android exposing explicit heartbeat/countdown behavior. |
| User profile | View/update display name and email, change password, avatar/Gravatar handling, sign out, and change server. | A signed-in Account route provides the same outcomes, remains distinct from administrator user management, and refreshes shared current-user state after mutation. | **Parity.** Validation, password policy failures, re-login, sign-out, and change-server passed live API 30 testing. |
| Multiple server profiles | No complete multi-profile manager was identified; change-server flow exists. | One active server is persisted. PAR-002 canonicalizes its origin, scopes tokens and process caches, rotates client/session ownership, and durably clears the saved server and credential binding before exposing setup. | **Shared profile gap; single-server switching is hardened.** Keep multi-profile work deferred until cache, operation, and route identity are equally scoped. |
| Biometric application lock | No core capability identified. | No core capability identified. | **Shared gap**, not required for iOS parity. Consider separately if threat modeling supports it. |

### Dashboard and environment management

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Fleet dashboard | Fleet totals, server cards, stats/sparklines, needs-attention groups, failed activities, pinned resources/actions, update-all, and card actions for environment sync/system/upgrade/prune. | Fleet totals/cards, stats/sparklines, needs attention, failed activities, pins/actions, update-all, prune/detail, and authoritative per-environment upgrade gating are present; a comparable sync shortcut is absent. | **Partial.** Upgrade parity is complete; assess the remaining sync shortcut independently. |
| Live dashboard updates | v2 stream with legacy fallback and bounded concurrent stats streams. | Dashboard streaming with reconnect behavior and resource statistics streams. | **Partial.** Validate fallback/version behavior and connection limits under many environments. |
| Environment selection | Active environment selection and environment-aware navigation. | Active environment selection, detail/test, persistence, and client rebuild. | **Parity** for selection. |
| Environment management | The dashboard is the single fleet destination and opens environment details/actions; it does not claim full CRUD. | Dashboard cards open environment details and selection; a separate list/detail/test surface exists but is not a primary route or full CRUD. | **Parity** for the current read/select/detail outcome. Exposing the extra Android list is a product choice, not a parity prerequisite. |
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

### Projects and Compose workflows

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Project list and lifecycle | Active/archived projects, create, deploy/redeploy, start/stop/restart, logs, archive/delete. | Active/archived projects, create from blank/template, lifecycle streams, logs, archive/delete. | **Parity** for broad lifecycle coverage. |
| Project creation | Compose and `.env` input, templates, variable-resolution support. | Blank/template creation with Compose and `.env` input. | **Parity** for initial creation. |
| Deploy options | Per-project deploy supports pull-policy and force-recreate choices and remembers them by server/environment/project. | Android exposes typed pull-policy/force-recreate choices and scopes preferences by server, account, environment, and project. | **Parity.** Persistence and live deployment passed in PAR-113. |
| Existing project files | File tree, Compose/`.env` editor, save, create, rename, move, and delete. GitOps/archived projects are read-only where appropriate. | Android provides the complete typed workspace with dirty/conflict recovery and archived/GitOps restrictions. | **Parity.** Mutations and conflict/rebase behavior passed in PAR-103. |
| Variable resolution | Resolution preview plus resolved YAML in the editing workflow. | Variable entry, preview, and resolved YAML are integrated with the editable workspace. | **Parity.** |
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
| Image attestations | Attestation list/filter/detail and statement copy. | **Complete on PAR-105 review branch.** Image detail exposes a scoped list/filter/detail flow with complete labeled copy/export, defensive states, and no trust implication. | Outcome parity complete; retain the Android-native destination/bottom-sheet presentation. |
| Image layer history | Image detail shows Docker layer history with command, size, date, and tags. | **Complete on PAR-112 review branches.** Kotlin PR #9 adds the typed route; Android renders environment/digest-scoped Docker layer history separately from image-build/updater history. | Outcome parity complete, pending SDK-before-Android PR merge ordering. |
| Image updates | Per-image and fleet update flows. | Per-image, update overview, updater, and fleet-update flows. | **Parity/Android strength.** Android has substantial explicit updater behavior. |

### Volumes, networks, and ports

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Volumes | Create/remove/prune, detail, browser, backups, restore/delete/download. | Create/remove/prune, detail, browser, backups, restore/delete/download. | **Parity.** |
| Network management | List/create/delete/detail plus an interactive network-to-container diagram. | List/create/delete/detail plus topology presented as grouped rows. | **Partial/Android gap.** Preserve the readable list as an accessibility/fallback mode while adding a bounded interactive graph for useful topology parity. |
| Network summary accuracy | Some internal/container-count values are stubbed in the iOS app. | Android data should be compared with server responses rather than copied from iOS summaries. | **Validate.** Do not treat known iOS stubs as a target. |
| Ports | Read-only port inventory. | Read-only port inventory. | **Parity.** |

### Activities, events, jobs, Git, and Swarm

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Activities | v2 activity stream, filtering, cancellation/clearing, environment context. | All-environment v2 live stream, filtering, cancel, and clear. | **Parity.** The pinned Kotlin SDK includes activity error/heartbeat support; validate event behavior against the target server. |
| Activity-start feedback | User-configurable toasts surface user/system activity and open the app-wide Activity Center. | Activity Center and failed-count badge exist, but no configurable app-wide activity-start surface was found. | **Partial.** Treat this as a projection of the future operation/activity store, with bounded noise and permission-safe environment context. |
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
| Global variables | v2 global variables support create/edit/delete, secret values, all/specific-environment scoping, sync status, and explicit sync. | Typed Kotlin contracts back permission-gated list/search/create/edit/delete/sync flows with all/selected scope and protected secret handling. | **Parity.** Live coverage included partial sync across 26 environments and an unauthorized account. |
| Template discovery | Search, source filtering, metadata, preview, remote download, deploy, and registry management. | Search/source filters, metadata, preview, remote import, variable resolution, failure recovery, and deployment are implemented. | **Parity.** Completed and live-tested in PAR-114. |
| Container registry names | Registries expose a user-facing identity in addition to URL and credentials. | Current Arcane has no independent `name` field; Android derives a stable provider/description/URL identity and preserves encrypted credentials on update. | **Parity for the actual wire contract.** Completed and live-tested in PAR-115. |
| Authentication/system/build/upgrade | Server authentication settings, system information/settings, builds, and upgrade. | Authentication, system, build, and upgrade surfaces include typed per-environment capability gating. | **Parity** in broad coverage. |
| Admin/config destinations as swappable tabs | Administration/configuration destinations are not bottom-tab replacement choices. | Android centralizes bottom-tab eligibility and excludes Users, Notifications, System, Roles, and other configuration destinations under merged PR #5. Their drill-down flows remain available through Settings. | **Parity.** Verify Settings-owned drill-down behavior rather than unsupported primary-admin-tab behavior. |
| Documentation/support links | iOS repository links are appropriate to the app. | App Settings centralizes deliberate Android source/issues, Arcane documentation/privacy, and Discord support destinations. | **Parity.** Focused mapping tests prevent regression to iOS repository links. |

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
| AI assistant | Removed in iOS 0.7.0. | No AI assistant. | **No current parity gap.** Any future assistant is an independent product/security project. |

No Android notification, widget, shortcut, share-in, resource app-link, QR setup, or biometric-lock
system was identified at the baseline. These should not be delivered as one monolithic “native
features” project; each needs a clear user scenario and data-security review.

### Persistence, security, and backup

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Tokens | Shared Keychain with migration and widget access controls. | Encrypted token storage through the Android SDK layer; the token ciphertext file is excluded from backup and device transfer. | **Parity** for secure application storage and Android backup isolation. |
| Preferences | App settings, navigation, cache policy, and account context persist. | DataStore persists server, appearance, environment, and navigation tabs; SharedPreferences stores pins and server-scoped project deploy defaults. | **Partial.** Consolidate ownership when adding cache/operation state. |
| Response data | Bounded disk cache. | No equivalent response store. | **Android gap.** |
| Android backup policy | Not applicable. | A deny-by-default allowlist restores only local tab customization. Credentials, server/account identity, server-derived data, caches, and operation state are excluded from legacy cloud backup, Android 12+ cloud backup, and device transfer. API 35 cloud and D2D uninstall/reinstall tests restored only the tab DataStore. | **Android-native policy complete.** Keep the formats aligned and classify every new persistence location before inclusion. |

### Quality, testing, CI, and distribution

| Capability | iOS baseline | Android baseline | Status and action |
| --- | --- | --- | --- |
| Unit tests | 29 XCTest methods across six files, still focused mainly on utilities and post-0.6 pagination/security regressions. | 44 JVM test files and 261 test methods, with useful coverage of authentication restoration, server credential/cache scoping, navigation, dashboard/updater logic, URL handling, ANSI parsing, container completeness, and backup policy. | **Android strength.** |
| UI/instrumented tests | No meaningful UI test suite identified. | Only the template/example instrumentation test was identified. | **Shared gap.** Add a small navigation/auth/destructive-confirmation suite before attempting broad UI automation. |
| Integration/network contract tests | No broad suite identified. | No broad end-to-end contract suite identified. | **Shared gap.** SDK serialization/service tests should carry most wire-contract coverage. |
| CI | No repository CI workflow was identified in the inspected iOS baseline. | CI uses JDK 21/API 35 and runs unit tests/build, with optional signed tag release support. | **Android strength.** |
| Static quality/security gates | No comprehensive suite identified. | No lint, detekt, ktlint, instrumentation, or security scan gate was identified in CI. | **Android gap.** Add targeted gates incrementally; do not create a noisy all-at-once migration. |
| Release maturity | Version 0.7.0 and more distribution-oriented product surfaces. App Store status was not confirmed. | Version 0.1.0; minification disabled; repository messaging still warns that the app is not intended for devices. | **Android maturity gap.** Define alpha/beta support criteria before production claims. |
| Release-note integrity | Notes correspond to iOS releases and platform behavior. | Android `0.1.0` maps to conservative Android-owned notes; semantic ordering filters out future versions, and build code `260901` advances past the `260602` public alpha artifacts. | **Parity.** Keep every future note aligned to `versionName` and increment `versionCode` for every distributed artifact. |

## SDK/API prerequisites versus Android-only work

The repository dependency direction remains:

`Arcane server contract -> libarcane-kotlin -> Android`

Do not close a parity gap by adding app-local endpoints, streaming parsers, auth handling, or DTO
copies. Inspect the Arcane Go types/handlers first, then implement missing Kotlin SDK support with
tests, then build the Android UI.

### Likely Android-only work

These items appear to have sufficient application or SDK foundations and are primarily Android
composition, persistence, or platform work:

- add adaptive navigation and authenticated resource deep links;
- add widgets, shortcuts, and ongoing-operation notifications;
- add response caching around existing read services;
- add version-gated automatic What's New presentation;

### Validate SDK coverage before estimating

These need an explicit SDK/server capability check at the pinned revisions:

- exact activity stream error, heartbeat, and forward-compatible event handling;
- dynamic/generic resource descriptors, if a generic fallback UI is desired;
- server-version fallbacks for dashboard and fleet updates.

The pinned Kotlin SDK exposes image-attestation and per-image Docker layer-history operations,
explicit environment pagination queries, container pause/kill, typed project-file changes, and
activity/stream APIs. Image Insights is complete on PAR-105/PAR-112 review branches; remaining
container-action gaps are Android UI/state work. Profile and project-workspace outcomes are complete,
and complete-fleet pagination plus coroutine ownership were addressed in PAR-004 and PAR-008.

The Accounts and Administration batch adds the previously missing typed passkey/MFA and current
global-variable contracts to Kotlin before Android consumes them. Per-image layer history was added
in Kotlin SDK PR #9 before Android consumed it. The pinned SDK revision, sibling composite-build
revision, and target server version remain recorded together because active SDK development can
change those conclusions.

### Server or product-definition prerequisites

- Swarm remains a placeholder in both clients and needs a defined product/API workflow.
- Multi-server profiles require an explicit credential, cache, deep-link, and active-operation
  model.
- AI assistance is not present in either current mobile client; any future work needs a separate
  product/security design.

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

PAR-002 completed the change-server foundation: prior client/user/capability/environment state is
invalidated and credentials are scoped to a normalized server identity. PAR-007 then established
the deny-by-default backup boundary. Remaining P0 work is:

1. Add version-gated automatic What's New presentation using PAR-006's exact installed-version
   mapping.

### P1: Complete high-frequency operational workflows

1. Add application-owned long-running operation state with reconnect/cancel and an in-app progress
   surface.

### P2: Resilience and Android-native continuity

1. Add scoped, bounded stale-read caching for dashboard and high-value resource lists.
2. Standardize stream retry, cancellation, selected-environment changes, and stale-state markers.
3. Add an ongoing notification for user-initiated deployments or updates that outlive a screen;
   use foreground execution only when Android policy requires it.
4. Add authenticated resource deep links and dynamic shortcuts.
5. Add adaptive navigation and list-detail layouts for tablets/foldables.
6. Add one or two privacy-reviewed Glance widgets using sanitized snapshots.
7. Add an interactive, bounded network topology with a readable list fallback.

### P3: Product expansion

1. Evaluate a multi-server profile model.
2. Establish a localization path and move existing text incrementally to resources.
3. Add broader UI/integration testing and incremental lint/static-analysis gates.
4. Revisit Swarm only after shared product requirements exist.

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
