# Android-native presentation batch

This document records the PAR-303, PAR-304, and PAR-505 design boundary. The implementation was
prepared against Android `0f353eaaa53eff7d9c7720ec2e131db96ba59253`, iOS
`8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
`b29695d547b78389ed7230b35cd133f7046b4b52`, and Arcane
`9fa57c867b1085a7142d7e755f87ddd9318b1a1d`. SDK PR #11 is included in that SDK revision and makes
unknown or missing topology node types decode as `UNKNOWN`. No Android-local endpoint, wire DTO, or
second client was introduced.

## PAR-304 adaptive presentation

### Navigation inventory

Arcane remains a single-activity Compose app with one top-level selection and one nested
`NavController` per composed tab. The inventory at the source pin is:

- four user-configurable primary slots plus Settings on compact phones;
- Dashboard, Projects, registries, Git repositories, and GitOps management destinations;
- Containers, Images, Builds, Updates, Networks, Ports, Volumes, Jobs, and Activities resources;
- Swarm plus Events, Variables, Users, API Keys, Notifications, Webhooks, Authentication, Roles,
  OIDC role mappings, and System Settings, gated by capability, administrator status, and Variables
  read permission;
- Dashboard-hosted details, nested list/detail/action routes, authenticated routes, three static
  shortcuts, dynamic environment shortcuts, and operation notification routes.

The previous shell assumed a compact bottom bar at every width. It did not preserve a tab's nested
composition when another tab was selected, and an external container/project route temporarily
hosted its detail under Dashboard.

### Width behavior and ownership

The app uses stable Android window-width breakpoints: compact below 600 dp, medium from 600 through
839 dp, and expanded at 840 dp or wider.

- Compact preserves the existing configurable four-slot bottom navigation, long-press replacement,
  and Settings item.
- Medium uses a navigation rail for the configured slots, a permission-filtered More sheet for all
  destinations, and Settings.
- Expanded uses a permanent 300 dp grouped drawer exposing all destinations the current account and
  server may access, plus Settings.

Destinations that already own a top-level resource stack continue to use that stack. Administrative
and other non-pinnable destinations launched from More/the drawer are delivered to their existing
route inside the Settings `NavHost`; this preserves user/role/provider/category detail navigation
instead of duplicating those routes in the adaptive shell.

`MainTabSelection`, `NavTabsStore`, the existing tab `NavController`s, and the existing Settings
`NavHost` remain authoritative.
There is no second navigation framework or selection store. A saveable state holder keys global tabs
by tab ID and environment-scoped tabs by tab ID plus environment ID. It preserves independent nested
state through tab changes, width changes, rotation, folding changes, and process recreation, while
an environment change removes only the prior environment's scoped state.

Containers and Projects are the high-value list/detail targets: they have dense lists, frequently
opened details, and deeper actions that benefit from keeping context visible. Once the adaptive
shell leaves at least 600 dp of content width, a fixed 360 dp list sits beside the existing detail
`NavHost`; narrower content stays one-pane. This permits list/detail on wider medium windows as well
as expanded layouts without crowding narrower foldable postures. Selection navigates with
`launchSingleTop` after returning to the list root, so selecting again cannot stack duplicate details
and compact Back still returns to the list. Networks was not
converted because topology needs the available workspace; Volumes, Images, Activities, settings,
and operational forms retain their deliberate single-pane layouts rather than receiving a blanket
redesign.

Authenticated container/project routes validate server, permission, enabled environment, and
resource existence first, synchronously select the exact environment, select the owning tab, and
deliver one saveable detail request to that tab's existing controller. Static/dynamic shortcuts and
operation notifications continue through the same authenticated-route coordinator. A route never
creates a parallel detail store.

## PAR-303 Fleet Status widget

One focused Glance widget presents privacy-reviewed fleet outcomes from the PAR-302 snapshot only.
It never receives `ArcaneClientManager`, constructs `ArcaneClient`, accesses a credential/token,
performs a request, schedules periodic work, or exposes a mutation. Refresh means calling Glance to
render the latest app-published file. Material snapshot changes, logout, server/account/scope
changes, and signed-out replacement request a render; timestamp-only rewrites do not spend launcher
refresh budget.

Snapshot schema 2 adds only the one-way canonical server hash required to construct a server-bound
Dashboard route. It retains the 64 KiB limit, ten-row limit, atomic replacement, active-scope fence,
monotonic generation fence, opaque environment keys, bounded names/counts, and strict fail-closed
decoding. An external consumer may read but cannot activate a writer scope. Authenticated snapshots
without a valid scope and server binding are rejected before write. Schema 1 data at the same path
fails closed or is atomically replaced by the app.

The widget exposes container, image, update, online-environment, and bounded display-name summaries.
It does not expose a URL, username, raw account/environment/resource ID, credential, secret, log,
operation data, or action control. Small (120 x 100 dp), medium (240 x 120 dp), and large
(300 x 200 dp) responsive layouts progressively add aggregate and environment context. Fresh,
stale, unavailable, signed-out, and unconfigured states use text as well as color. Whenever Glance
renders, a nominally fresh Dashboard snapshot older than 60 seconds becomes stale and one older than
24 hours becomes unavailable, matching the approved Dashboard cache policy without scheduling a
timer.

The whole authenticated widget opens the aggregate Dashboard through the existing server-bound
route. Individual environment rows are intentionally not tap targets: the reviewed snapshot stores
only one-way environment keys, so inventing reversible IDs or persisting raw IDs would weaken the
privacy boundary. Wrong-server, unauthorized, signed-out, or stale app routes therefore use the
existing safe route resolution. The receiver is non-exported, accepts only the platform widget
update action, and declares a zero periodic update interval. No Glance state definition or widget
configuration persistence is used.

## PAR-505 topology normalization and diagram

Arcane's typed `/networks/topology` response is the sole source of nodes, relationships, labels,
metadata, addresses, and counts. Android does not infer links from container or network lists and
does not implement the known iOS summary placeholders.

The server caps topology at 500 nodes and 2,000 edges, which also matches current iOS input limits.
Android retains those hard input bounds, then uses a lower interactive threshold of 80 supported
nodes and 240 valid edges. The lower threshold is deliberate: Compose renders accessible node cards
and their semantics, not only a bitmap, so it bounds composition, hit testing, focus navigation, and
layout work. IDs are limited to 512 UTF-8 bytes and display/metadata strings to 256 bytes.

Normalization is deterministic and fail-closed:

- first valid node/edge ID wins; duplicate nodes, duplicate edge IDs, and semantic duplicate edges
  are counted and omitted;
- blank or oversized IDs, missing endpoints, self links, reversed links, cycles outside the typed
  network-to-container relation, and links involving unknown types are omitted;
- blank labels receive a typed fallback; unknown nodes and orphan containers remain available in
  the grouped list;
- over-limit input is truncated at the hard bound and always forces the list fallback;
- canonical sorted nodes and edges produce a graph identity used to fence selection.

The interactive diagram is a deterministic grouped bipartite layout. Networks occupy the left
column. A container shared by several networks is laid out once under its first sorted network while
every authoritative edge remains drawn. Isolated containers remain visible. There is no force
simulation, no invented relationship, and no unbounded iteration. Zoom is clamped to 0.35 through
1.75, pan is clamped around bounded content, and explicit Fit and Reset controls are provided.

Every node is a focusable button with a type/name/status/connection-count description, selected
semantics, a non-color border state, and a large details sheet exposing typed metadata, addresses,
and connected nodes. Drawn edges are removed from the semantics tree to avoid meaningless TalkBack
stops. The direct grouped-list switch remains available for accessibility and preference; it is
forced for unsupported, malformed-only, truncated, or interactively excessive graphs. Refresh
errors label retained data as the last in-memory topology rather than live. Captured authenticated
scope and environment checks discard refresh races, and selection survives only while environment
and canonical graph identity remain valid.

## Automated and live validation (2026-09-16)

The final JVM baseline contains 377 tests. The 63-test focused presentation run covers adaptive
breakpoints and authorized destinations; existing selection, restoration, Back, route, and shortcut
rules; snapshot atomicity, strict schema/privacy/bounds, session fences, unconfigured/sign-out
transitions, and widget model/routing/provider policy; plus topology normalization, deterministic
maximum/over-limit layouts, duplicate/missing/reversed/cyclic relationships, selection identity,
and viewport bounds. `:app:assembleDebug`, manifest/resource merge, APK inspection, backup-policy
tests, and `git diff --check` also pass.

Post-publication device feedback identified two compact-phone regressions before review: nested
click handling could swallow a tab tap on an OEM input stack, while the Dashboard handler retained
its first-composition selection and could therefore acknowledge a later tap without leaving another
tab. Optimistic cache emissions also briefly flashed the cached-data warning before a successful
revalidation. Compact tabs now give Material `NavigationBarItem` sole ownership of ordinary taps;
a non-consuming pointer observer and an accessibility long-click action retain customization without
adding a nested click target. The Dashboard handler reads the latest normalized selection and shares
the same root transition as system Back. All resilient list families keep the warning hidden during
optimistic cache use and reveal it only after live revalidation fails. Focused JVM tests cover the
selection rule and both warning phases.

The follow-up was reproduced and verified in the authenticated debug APK on a disposable clone of
the existing Android 11/API 30 AVD against the retained Arcane 2.10.2 instance. Before the fix,
instrumented logs proved the Dashboard click arrived while its handler still read `dashboard` with
Containers visibly selected. After the fix, physical taps returned from Containers, Images,
Projects, and Settings to Dashboard; reinstall plus process restart preserved authentication and
also returned from restored Containers. The two Compose device tests passed for the exact
Dashboard-to-Volumes-to-Dashboard sequence and for long-press customization without accidental
selection.

Live validation used three disposable Google APIs API 35 x86_64 AVDs against Arcane 2.10.2,
container image ID `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`:

- phone: 1080 x 2400 compact portrait/landscape, tab replacement and independent nested state,
  container/project detail and Back, rotation/process recreation, valid cold/warm authenticated
  routes, static shortcuts, widget lifecycle/state transitions, and device restart;
- foldable: 2208 x 1840 at 420 dpi (841 dp expanded), half-open posture, 1080 x 2092 compact fold,
  and a 1600 x 1840 medium resize; the selected container detail survived every transition;
- tablet: 2560 x 1600 at 320 dpi expanded landscape and 1600 x 2560 (800 dp) medium portrait;
  Containers and Projects retained list/detail selection through rotation and process recreation.

At 200% font scale the navigation labels, list/detail content, topology switch, controls, nodes, and
details remained reachable without misleading clipping. TalkBack was enabled on the tablet; the
diagram and grouped-list nodes exported one labeled, selected, focusable button each, decorative
edges had no semantics, and keyboard/D-pad Tab plus Enter reached and opened node details. Real
touch injection exercised bounded pan, pinch zoom, Fit, Reset, selection, and the direct list
fallback.

The live topology fixture contained six Docker networks and eight containers: one isolated network,
one container shared by three networks, two containers shared by two networks, isolated nodes, and
18 typed nodes/13 typed edges in the server response. Docker inspection independently confirmed
network membership counts of 2, 3, 3, 2, 2, and 0 before the app run. Empty, moderate, refreshed,
rotated, environment-changed, and offline/error-retained presentations were exercised; synthetic
tests provide the destructive/malformed hard-bound cases that the live server cannot safely emit.

The Fleet Status widget was added, resized across its responsive sizes, removed, reinstalled, and
re-added from the Pixel launcher. Live states included fresh (9/9 running), a 35-minute stale
snapshot, unavailable during a stopped Arcane container, signed out, unconfigured after state clear,
process death, cold/warm tap delivery, app-data clear, and launcher/device restart. A normal app
refresh changed the widget without a widget-owned request; logout and Change Server invalidated the
old projection, and the final unconfigured boundary deleted it and re-rendered even when no file
remained. App-private inspection found only the reviewed schema-2 snapshot keys and Glance launcher
bookkeeping; source/APK inspection found the existing single `ArcaneClient` construction site and no
widget SDK/client/network/mutation path. Because this fleet widget exposes only one aggregate,
server-bound Dashboard action, deleted/disabled environment resource routes do not exist; wrong
server, permission, signed-out, and stale delivery continue through the existing authenticated-route
resolver rather than a widget-specific route store.

Cleanup uninstalled the test APK (removing the widget/provider binding), deleted all three disposable
AVDs, six networks, eight containers, screenshots, mounted SDK/AVD/APK paths, emulator-only packages,
and temporary debug certificate trust. The retained `arcane-e2e` health endpoint returned 200 after
cleanup. The existing API 30 AVD, Arcane container, users, projects, and environments were preserved.

## Persistence and intentional exclusions

Adaptive tab IDs and the last selected top-level destination continue in the existing backed-up UI
customization DataStore. Nested tab stacks and topology selection/viewport use saveable instance
state only. The widget snapshot remains under `noBackupFilesDir`; there is no app-defined widget
configuration, Glance state, graph file, screenshot, database, or SharedPreferences/DataStore.
Glance's internal app-widget-manager DataStore is implementation-owned launcher bookkeeping and is
excluded by the unchanged deny-by-default backup allowlist.

Intentional exclusions are a second widget, per-environment widget actions, periodic/background
network refresh, mutation controls, arbitrary screen redesign, a second navigation layer, topology
force simulation, server relationship inference, and iOS summary placeholders.
