# Projects workspace parity batch

This packet coordinates PAR-103, PAR-113, PAR-114, and PAR-115 as one Projects experience. It is
temporary implementation and validation evidence; durable conclusions return to the canonical
backlog before publication.

## Revision pins

Remote references were fetched on 2026-09-01 before implementation:

- Android `fbf0c02b147526e5c7b152d0df0507eb909f60bf`
- iOS `cdd05d89a1169bea50b53a12dcd00ca479233d26`
- Arcane `0c7174f1089079d79535563ac2d54b032ea6914a`
- libarcane-kotlin `7a192f3ebc1a7c623eea6a4919085fc23180add2`
- libarcane-swift `c7614fcdcef185fca4209d77bf6ab431ed07027e`

Android integration work is on `parity/projects-workspace-batch`. Kotlin SDK contract work is on
`parity/projects-workspace-contracts`. The stopped PAR-109 worktree remains untouched.

## Revalidated contract decisions

- Current Arcane project files use `/projects/{projectId}/workspace` and multipart manifests. The
  older Kotlin `/files`, `/file`, and `/includes` routes are no longer registered. Workspace file
  updates send both current content and the editor baseline so concurrent content changes produce a
  conflict instead of a silent overwrite.
- Compose, `.env`, and override content remain project-update fields. Archived projects are
  read-only. GitOps-owned files are read-only, while operator-owned overlay files retain the
  server's per-file editability. Current Arcane does not accept a revision or content baseline for
  Compose/`.env` updates, so Android explicitly identifies those two fields as last-write-wins;
  baseline-aware conflict recovery applies to workspace files.
- Kotlin follows the Swift SDK's current-route-first fallback for legacy workspace tree/file/update
  routes. Project creation has a dedicated Arcane 2.8 boundary in Android so current servers receive
  multipart and older servers retain the legacy JSON request without a retry that could duplicate a
  project; the SDK rejects modern-only fields on that legacy path instead of silently dropping them.
- Variable resolution and resolved YAML preview are mobile editor behavior; Arcane has no separate
  resolver endpoint.
- Deploy options are request-scoped. Persisted Android pull-policy and force-recreation defaults are
  isolated by normalized server, account, environment, and project and are written only after the
  user confirms the deploy-options dialog.
- Template deployment composes typed template preview/import, typed project creation, and the
  project deploy stream; there is no separate template-deploy endpoint.
- Arcane and both SDK contracts have no container-registry `name` field at the pinned revisions.
  iOS derives a display label from description and URL. Android will use a stable derived label and
  URL/ID fallback rather than inventing a wire field. Kotlin will add the real current
  `repositoryNames` registry field.

## Consolidated device and live-server matrix

Run once after the batch is automated-test clean; do not pause between PAR items.

| Area | Device/live-server scenario | Status |
| --- | --- | --- |
| Project files | Browse and edit Compose, `.env`, and text files; create/rename/move/delete a nested file and folder | Pending |
| Conflicts | Modify the same workspace file from another client, verify save preserves and rebases the Android draft; confirm Compose/`.env` show the last-write-wins warning | Pending |
| Read-only | Browse archived and GitOps projects; verify owned/protected files cannot be mutated and overlays follow server metadata | Pending |
| Variables | Preview `.env` and inline substitutions, inspect resolved YAML, then save or discard deliberately | Pending |
| Deploy | Exercise missing/always/never pull policy and force recreation; verify scoped defaults and stream failure/cancel behavior | Pending |
| Templates | Search/filter local, configured-registry, and remote templates; preview, import, create, and deploy one remote template | Pending |
| Registries | Create/edit a descriptively labeled registry, preserve an existing secret on blank credential fields, and confirm URL/ID fallback labels | Pending |
| Isolation | Switch account, server, environment, and project; confirm deploy defaults and workspace state do not leak | Pending |

## Automated validation

Completed on 2026-09-01 with JDK 21 and the local Android SDK:

- Kotlin SDK PR #5 merged to `origin/main` at
  `edb55803847521bdc775dccad6420c2885f1a514`; the Android gate was rerun against that exact merged
  revision.
- Kotlin SDK: `./gradlew :arcane-core:test :arcane-android:assembleRelease` — **BUILD SUCCESSFUL**,
  30 tasks.
- Android: `./gradlew :app:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL**, 65 tasks.
- Added 34 focused Android tests for workspace mapping/state, conflict replay and partial commits, deploy mapping,
  preference isolation and stream failures, template identity/filter/import state, permissions, and
  registry display/request preservation.
- Added nine Kotlin SDK contract tests for current and legacy workspace routing, multipart ordering
  and baselines, version-scoped project creation, typed deploy options, template source filters,
  registry repository names, and old-payload decoding.
- `git diff --check` passes in both worktrees.

The device/live-server matrix above is intentionally consolidated and remains pending: `adb
devices -l` reported no attached device, and no live Arcane test server was supplied for this batch
run.
