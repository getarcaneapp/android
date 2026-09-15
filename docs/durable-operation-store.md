# Durable operation store

This record is the implementation gate for PAR-201, PAR-202, and PAR-203. It was prepared against
Android `0e65cbebbf924e7553952f751c32debdbf5b2b57`, iOS
`6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
`70086644c63624340f9827a056c19edf8ca7e05f`, and Arcane
`5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105` after fetching every repository on 2026-09-11.

The compared iOS implementation is `DeploymentActivityStore.swift`. It is useful authority for
operation presentation, direct Activity-ID capture, server cancellation, and reattachment after a
dropped stream. Android deliberately replaces iOS's heuristic fallback lookup with Arcane's exact
batch-ID correlation and goes further where PAR-201 requires
durability and multiple-operation support: iOS keeps one in-memory operation, while Android keeps a
small persisted recovery ledger owned at application scope.

Status: **reviewed and approved for implementation**. The independent gate review completed on
2026-09-11 with no remaining blocking or high findings.

## Authority and inventory

Current Arcane v2 creates a durable Activity before project and image stream work, writes an initial
NDJSON `{type:"activity", activityId:...}` frame, runs the work from an app-owned server context,
persists bounded Activity output, and ends successful streams with `{done:true}`. The Activity API
supports list, detail, multiplexed updates, and server-side cancellation of queued/running work.
Container redeploy and updater calls return their Activity ID in the final response. Before sending
every Activity-backed request, Android also supplies its operation UUID as
`X-Arcane-Batch-Id`; Arcane persists that value as `Activity.batchId` and forwards it across remote
environment proxies. This is the only permitted pre-response correlation key. Fleet update is not
an Activity: Arcane persists an
`EnvironmentUpdateJob`, returns its job ID, and exposes the latest job at
`system/upgrade/all/status`. It has no cancellation endpoint.

The approved initial inventory is:

| Kind | Android entry point | Execution contract | Recovery/cancel contract |
| --- | --- | --- | --- |
| Project deploy | project detail, pinned Dashboard project, and template-created project | NDJSON stream | direct Activity ID; exact batch-ID fallback; detail/stream reattach; server cancel |
| Project redeploy | project detail | NDJSON stream | direct Activity ID; detail/stream reattach; server cancel |
| Project image pull | project detail | NDJSON stream | direct Activity ID; detail/stream reattach; server cancel |
| Project build | project detail | NDJSON stream | direct Activity ID; detail/stream reattach; server cancel |
| Image pull | Images pull sheet | NDJSON stream | direct Activity ID; detail/stream reattach; server cancel |
| Container redeploy | container detail | request/response | response or exact batch-ID lookup; detail reattach; server cancel once resolved |
| Updater run / container update | Updates for one environment | request plus status projection | response or exact batch-ID `auto_update` lookup; detail reattach; server cancel once resolved |
| Fleet update | Dashboard update-all | request plus job polling | persisted fleet job ID/latest-job match; status polling; no cancel |

Quick container actions, destructive deletes, update checks, image upload, image build (which has no
current Android initiation surface), and unrelated reads are outside this batch. Adding a new kind
requires an explicit adapter, recovery classification, duplicate key, permission rule, navigation
route, and tests; it must not bypass the store.

The existing Android container-update entry point is the environment updater; Android has no direct
single-container `updateContainer` UI at the pinned revision, so this batch migrates the applicable
aggregate flow rather than inventing another product surface. Its server Activity represents all
container/project updates in that run. Container redeploy remains its separate detail action.

The Kotlin SDK must expose the server fields Android needs. In particular the shared project/image
progress event needs `type`, `activityId`, `done`, `phase`, and `log`; `Activity` needs `batchId`;
`ContainerDetails` and `UpdaterResult` need `activityId`; and every in-scope typed mutation accepts a
validated request-scoped `activityBatchId` sent as `X-Arcane-Batch-Id`. Android uses the already
typed SDK fleet-update models and
`SystemService` methods instead of its current local REST calls and duplicate DTOs. No Android REST,
DTO, NDJSON, token, or polling transport is introduced.

## Single owner and lifecycle

`ArcaneApplication` constructs exactly one `ArcaneClientManager` and one `OperationStore` for the
application process. `MainActivity`, screens, notification projection, and notification intents all
refer to those objects. Moving `ArcaneClientManager` out of the Activity is safe because it already
retains only `applicationContext`; it also makes its session generation and client lifetime agree
with the operation owner across rotation and Activity recreation.

`OperationStore` owns a `SupervisorJob`/application coroutine scope, the operation map, each runner,
recovery followers, cancellation jobs, persistence writes, and notification projection. A failed
operation does not cancel siblings. Every caught `CancellationException` is rethrown unless the
store itself is deliberately translating its own runner shutdown into an explicit state transition.
Screens submit immutable commands and render state; leaving a screen, switching tabs, rotation, or
backgrounding never cancels a runner. A screen, Activity Center, notification, receiver, worker, or
service may not create another operation map or own execution.

The OS may stop the process. That is not treated as local execution failure and Android does not try
to keep the process immortal. The ledger is restored after authentication on the next process start
and active descriptors are reconciled as described below.

## Identity and immutable binding

Each operation has a random UUID `operationId` generated before submission. Its immutable binding
contains:

- `kind` and schema version;
- normalized server-origin hash (never the raw server URL);
- account/user-ID hash plus the credential-origin hash used by the authenticated client;
- source environment ID (display names are resolved in memory);
- target type and an optional stable opaque project/container ID (display names are resolved in
  memory); image pull persists no raw target because its target is the sensitive image reference;
- a digest of the operation-specific duplicate key;
- creation time and submission time;
- optional server Activity ID, or fleet job ID;
- the correlation batch ID (equal to `operationId`) for Activity-backed work; and
- a recovery mode (`activity`, `fleet_job`, or `none`).

The live runner additionally captures the exact `AuthenticatedClientScope`/client generation. Every
mutation of presentation state, persistence, notification state, and completion callback checks the
operation ID and binding against that captured scope. A newly selected server, credential origin,
account, environment, or same-named target can therefore never receive a stale result. Environment
selection is not part of validity—the operation remains visible and bound to its original
environment when the user merely selects another environment.

Raw passwords, tokens, cookies, authorization headers, step-up grants, registry credentials,
compose/project content, request bodies, server URLs, and client objects are never persisted.
Deploy options and image-pull credentials live only long enough to submit the request. Recovery uses
the Activity/job descriptor, never a replayable mutation request.

## State machine

The persisted states and allowed transitions are:

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `queued` | server Activity reports queued | running, reconnecting, cancel-requested, success, cancelled, failure, interrupted, unknown |
| `starting` | descriptor is durable but no server acknowledgement is captured | queued, running, reconnecting, cancel-requested, success, failure, interrupted, unknown |
| `running` | request or server Activity is active | reconnecting, cancel-requested, success, failure, cancelled, interrupted, unknown |
| `reconnecting` | local transport is unavailable while a recoverable server descriptor is followed | queued, running, cancel-requested, success, failure, cancelled, interrupted, unknown |
| `cancel-requested` | one idempotent user request is in flight or accepted | cancelled, success, failure, reconnecting, interrupted, unknown |
| `success` | authoritative per-kind success predicate is satisfied | cleared |
| `failure` | authoritative operation/server failure | cleared |
| `cancelled` | server Activity reports cancelled, or submission was cancelled before transport invocation began | cleared |
| `interrupted` | process/app upgrade ended work that has no durable server reattachment contract | unknown, cleared |
| `unknown` | the server outcome cannot be established; success is explicitly not implied | reconnecting, success, failure, cancelled, cleared |
| `cleared` | transient tombstone used to cancel jobs/notifications before deleting the row | removed |

Unknown wire statuses map to `unknown`, never success. A clean v2 stream EOF without `done:true` is
not success; it enters `reconnecting` and follows the Activity. A legacy stream without Activities
may treat a documented clean EOF as success only in the same live process. After process death,
legacy `starting`/`queued`/`running` work becomes `interrupted`, because replaying the request could
duplicate a mutation and there is no authoritative record to inspect.

Terminal predicates are adapter-specific. Activity `success`/`failed`/`cancelled` is authoritative
for Activity-backed recovery. A live stream succeeds only on `done:true` (or documented legacy clean
EOF); any explicit error fails. Container redeploy requires a successful typed response. An updater
response succeeds only when `failed == 0` and no item has an error; otherwise it is `failure` with
the closed code `completed_with_issues`. Fleet update succeeds only when the exact job is
`completed` and no result is `failed` or `skipped_offline`; `failed` job status or all actionable
environment results failing is full `failure`, while any failed/offline subset is `failure` with
`completed_with_issues`. The UI may explain full versus partial failure from current in-memory typed
results, but neither is labelled success.

A cancellation tap is idempotent. The first tap changes the state to `cancel-requested`; later taps
observe that state and do not issue another request. If the Activity ID is not yet known, the store
looks up an Activity only by exact environment plus batch ID, verifies the expected kind (and target
when Arcane supplies one), and cancels only that exact match. It never cancels a type/target/time
heuristic. HTTP 409
because the Activity landed concurrently triggers a detail refresh rather than fabricating
cancelled. Unsupported/request-only kinds expose no cancel action.

The local state becomes `cancelled` without a server verdict only when cancellation happens before
the transport invocation begins. Once request submission begins, inability to resolve an exact
Activity produces `unknown` (or `interrupted` during legacy restore), never assumed cancellation.

## Submission and concurrency

The store accepts multiple distinct operations. Arcane remains authoritative for its per-environment
queue/concurrency limits. Android does not serialize independent work merely to keep one foreground
card simple.

The persisted duplicate key is a SHA-256 digest of binding, mutual-exclusion family, kind,
environment, and canonical target; the concatenated input is never stored. A second non-terminal
exact key is rejected and opens the existing operation. Different targets and environments may run concurrently.
Project deploy and redeploy share a mutual-exclusion family for the same project; project pull/build
are separate kinds but are rejected while deploy/redeploy of that project is active. One updater run
per environment and one fleet update per server/account are allowed. Terminal entries never block a
new submission. The server may additionally queue or reject conflicting work; Android reflects its
authoritative result.

`starting` is persisted before network submission. This prevents a crash between the user's tap and
the request from disappearing, while recovery still avoids replay. The store bounds itself to 32
non-cleared rows. If that bound is reached, it first removes expired terminal rows, then the oldest
terminal-like rows; it never evicts active work to accept a new submission. If all 32 rows are
active, submission is rejected with an in-app explanation and no request is sent.

## Progress, phases, and logs

In memory, each operation keeps at most 400 presentation lines, each UTF-8 bounded to 2 KiB; trimming
removes the oldest 40 at a time. It keeps at most 64 distinct 512-byte phases and at most 2,000
pull-layer counters. Byte sums saturate and progress is clamped to 0–100 and monotonic. Activity
detail requests ask for no more than 200 messages and de-duplicate by message ID.

Raw log lines are not persisted. They can contain registry/image references, project paths, daemon
output, or accidental secrets. Persistence contains only numeric progress and closed-vocabulary
presentation codes (`waiting`, `starting`, `working`, `reconnecting`, `cancelling`, `complete`,
`completed_with_issues`, `failed`, `cancelled`, `interrupted`, `outcome_unknown`). It never persists
raw throwable text, Activity error/latest-message/step, stream phase/status/log, image references,
project/container names, environment names, or server URLs. Target and environment labels are
resolved from current authenticated server data or shown generically after restore. Restored detail
can refill a bounded in-memory view from the server Activity. Notification text is generated from kind, generic
state, progress, and a bounded environment label; it never uses logs, server URLs, image references,
project content, raw target IDs, or error bodies.

## Persistence, migrations, and retention

The ledger is `files/datastore/arcane_operations.preferences_pb`, with a single serialized document
written transactionally by DataStore:

```text
schemaVersion: 1
operations[]:
  operationId, kind, state, createdAtEpochMs, updatedAtEpochMs
  serverBindingHash, accountBindingHash, credentialOriginHash
  environmentId, targetType, opaqueTargetId?, duplicateKeyDigest, activityBatchId
  recoveryMode, serverActivityId?, fleetJobId?
  presentationCode?, progressPercent?, terminalAtEpochMs?
```

All decoded strings and collection sizes are bounded before use. Corrupt documents are quarantined
in memory as no rows and overwritten on the next safe write; corruption never triggers request
replay. Migration is an explicit pure `vN -> vN+1` chain with fixture tests. Unknown newer schemas
are read as no actionable operations and retained untouched until a compatible app runs. On logout,
Change Server, account/credential-origin invalidation, or explicit local-history clearing, an
unreadable newer ledger is deleted wholesale because it cannot be scoped safely and must never
resurrect presentation later. Version 1
has no predecessor file, so rollout migration is empty; existing screen-owned in-memory work cannot
exist across installing the new build and is not guessed into the store.

Success, failure, and cancelled rows are retained for 24 hours; unknown and interrupted rows are
retained for 7 days so users can inspect the uncertainty, while all terminal-like states share a
maximum of 20 rows. Expired/excess rows are cleared on startup or the next mutation. Users may
dismiss any terminal-like row immediately. Active rows are
never aged into success. If an Activity has disappeared from bounded server history, recovery becomes
`unknown`. `cleared` cancels local jobs, cancels the notification, persists removal, and does not
delete the authoritative Activity Center history.

Reattachment/detail polling uses exponential backoff capped at 30 seconds and a 15-minute continuous
failure budget per process session. Exhaustion changes `reconnecting` or `cancel-requested` to
`unknown` with a generic code; a later explicit Retry starts a fresh exact reconciliation budget.

The new DataStore is intentionally excluded from legacy cloud backup, Android 12+ cloud backup, and
device transfer by the existing single-entry allowlist. The backup audit and `BackupPolicyTest`
must name it as a protected location. Operation state must not follow a user to another device.

## Recovery and invalidation boundaries

Successful terminal completion projects only a scoped invalidation into `ResilientReadCache` for
the operation's bound environment and affected resource kinds. The operation ledger remains the
sole owner of operation recovery and status: no descriptor, request, payload, log, error body, or
result is copied into the cache or status snapshot, and neither resilient-read store may replay or
control an operation. This one-way edge was added by the PAR-301/PAR-302 foundation.

After the manager restores an authenticated session, the store computes the current binding hashes
and considers only matching rows actionable:

- Activity-backed rows with an Activity ID call typed SDK detail immediately, then follow the
  multiplexed stream with bounded detail polling as a fallback. Activity status/step/progress wins.
- Activity-backed rows without an ID list by the captured environment and require exactly one
  Activity whose `batchId` equals the operation UUID, then verify the expected Activity type and any
  available target. No match or a conflicting match becomes `unknown`; older servers without batch
  correlation are never searched heuristically and cannot expose cancellation before a direct ID.
- Fleet rows compare the stored job ID with the typed latest-job response and poll only an exact ID.
  A missing/mismatched job becomes `unknown`.
- `none` recovery changes every active state—including queued, running, reconnecting, and
  cancel-requested—to `interrupted` during restore.

Logout and Change Server cancel local runners/followers, clear all presentation rows and
notifications for the ending binding, and never send server cancellation implicitly. An explicit
user cancel is a separate destructive action. Logging back into the same account after logout does
not resurrect locally cleared rows; Activity Center still shows server history.

An account change or credential-origin change hides and clears the prior binding. Permission loss
stops following, removes cancel actions, and marks active rows `unknown` with a permission-safe
summary; it does not claim server failure. Environment deletion makes exact Activity lookup
impossible and similarly yields `unknown`. A server that lacks Activities uses live stream/request
behavior but records `recoveryMode=none`; after process death it is `interrupted`. Unsupported kinds
fail before submission and do not create a misleading running row.

Changing the selected environment or tab does not invalidate an operation. Every row, card,
notification, cancellation call, Activity Center link, and result callback continues to use the
captured environment ID and the currently resolved display label.

App upgrade recovery runs the same schema migration and reconciliation. It never reconstructs a
mutation command from persisted state and therefore cannot duplicate an operation after upgrade.

## Activity Center relationship

The operation store answers “what did this Android user just start and what presentation/recovery
state belongs to it?” Activity Center answers “what work did this server record?” Activity Center
remains authoritative history and may contain scheduled/system/other-client work that the local
store never owns. The operation store links to an Activity ID but does not copy or clear Activity
history. Dismissing a local terminal row leaves the Activity untouched.

For current-server Activity-backed work, the operation detail offers **Open in Activity Center**.
The request selects the Activities tab and pushes its existing exact route
`detail/{activityId}/{environmentId}`. If permissions/session no longer allow that route, the local
detail stays visible with a safe error.

## In-app projection and navigation

The authenticated root renders one app-wide operation indicator above the bottom navigation. It
shows the number of active operations and the most recently updated state; tapping it opens the
operation center. The center lists active work first and retained terminal work second with explicit
environment context. Each operation detail supports inspect, server cancel when supported, dismiss
when terminal/unknown/interrupted, and the exact Activity Center link when an Activity ID exists.

`ArcaneApp` owns an `OperationHost` above `MainTabView`; it renders the center/detail as root-level
Compose overlays driven by the single store, not by a second NavHost/store. `MainTabView` gains an
explicit one-shot `ActivityOpenRequest(activityId, environmentId)` input. It selects the Activities
tab and passes the request into `ActivitiesTab`; that existing nested NavHost percent-encodes both
arguments and pushes its real `detail/{id}/{env}` route. A consumed request ID prevents duplicate
pushes across recomposition.

The internal exact routes are:

- operation center: `operations`;
- operation detail: `operations/{operationId}`;
- server Activity context: `MainTabView` selection handoff followed by the existing
  `ActivitiesTab` nested `detail/{encodedActivityId}/{encodedEnvironmentId}` route.

Notification content intents use an explicit, immutable `PendingIntent` to `MainActivity` with
`arcane-mobile://operations/{operationId}`. The Activity parses only this host/path and asks the
single store to open that exact local operation. `onCreate` and `onNewIntent` both pass the parsed
request to the store. Cold-start requests wait until authentication restoration completes; a
missing/cleared/wrong-binding UUID opens the operation center with a generic unavailable message and
does not reveal retained data.

Cancellation uses an explicit immutable `PendingIntent` to a non-exported
`OperationActionReceiver`, with the operation UUID in both URI data and request code. The receiver
uses `goAsync`, delegates immediately to the application-owned store, waits for matching
authentication restoration within a strict bounded timeout, revalidates binding/state/permission/
environment/Activity ID, and then calls the typed SDK cancel method. It never owns a runner, reads or
writes a separate ledger, replays a mutation, or starts an Activity, so it is not a notification
trampoline. On timeout/mismatch it leaves the operation truthful and notification tappable for
inspection. The receiver wraps its entire coroutine—authentication restoration, revalidation, and
the typed SDK cancel call—in one 8-second deadline inside the broadcast execution window. It always
calls `PendingResult.finish()` from `finally`, and a timeout causes no automatic second cancellation
attempt; the user can open detail and retry. The manifest declares only this `exported=false`
receiver; there is no service.

## Notification projection

`OperationNotificationProjector` subscribes to store state but owns no coroutine runner, request,
recovery, or persisted operation data. It creates one low-importance `arcane_operations` channel and
uses stable collision-resistant positive IDs derived from the full UUID with collision resolution
against the current map. Multiple operations remain separately visible and use an
`arcane_operations` summary group.

Running, queued, reconnecting, cancel-requested, success, failure, cancelled, interrupted, and
unknown each have distinct bounded text/icon/progress treatment. Only Activity-backed active rows
with a resolvable Activity ID and current cancel permission get a Cancel action. Terminal
notifications auto-cancel when the local row is dismissed or expires.

On API 33+, `POST_NOTIFICATIONS` is requested from the visible Activity in context when the user
starts their first operation or selects notification enablement; denial or revocation suppresses
posting without affecting execution or in-app state. The projector rechecks both runtime permission
and `NotificationManagerCompat.areNotificationsEnabled()` on every projection. API 24–32 have no
runtime notification permission, but channel/app-level disablement is still respected where the OS
exposes it.

Notifications use `VISIBILITY_PRIVATE` and a generic public version (“Arcane operation in progress”
or “Arcane operation finished”). The private notification still excludes log lines, image refs,
project/container names and IDs, server URLs, and raw errors. It may show the operation verb,
environment display name resolved only in the current process, generic state, and percent. The
action receiver never starts an Activity; content taps go directly to `MainActivity`, avoiding a
notification trampoline.

## WorkManager and foreground-service decision

Neither is required or declared for this design. The durable work is executed by Arcane, not the
Android process: current server handlers detach accepted work from the HTTP request, persist the
Activity/job, and provide reattachment. Android's responsibility is a recoverable projection. If the
OS kills the app, the already-posted notification may remain at its last bounded state and can be
stale until the next user launch/tap; it is not presented as an uninterrupted device-side monitor
and is not made indefinitely ongoing/non-dismissible. The app reconciles it on the next user
launch/tap; Android does not need to continuously execute the Docker
operation.

WorkManager would add delayed polling but cannot guarantee continuous socket work, and replaying a
mutation would be unsafe. A foreground service would misrepresent remote server work as ongoing
device work, would add Android 14 type-specific declarations, and would still be subject to Android
12 background-start limits. A `dataSync` service would additionally share Android 15's six-hour
background budget and cannot be launched from `BOOT_COMPLETED` by target-35 apps. No boot receiver,
`FOREGROUND_SERVICE`, type-specific foreground permission, or service component is added.

This decision follows Android's official foreground-service type, start-restriction, timeout, and
notification-permission guidance:

- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/timeout
- https://developer.android.com/develop/ui/compose/notifications/notification-permission

## Test and rollout strategy

Before migration of a UI entry point, tests must cover the pure transition reducer, duplicate
policy, binding/hash checks, line/phase/progress bounds, notification IDs/content/actions,
navigation parsing, and schema codec/migrations. Persistence integration tests cover atomic writes,
corruption, unknown-newer schema, process-death recovery descriptors, retention, and clearing. A
serialization fixture asserts that an image-pull reference is absent from the ledger bytes.
SDK fixtures cover initial Activity/done frames, response Activity IDs, unknown values, and fleet
models.

Implementation migrates a representative project deploy first and verifies it can outlive its
screen, then project redeploy/pull/build, image pull, container redeploy, updater, and fleet update.
Each old screen-owned runner is removed as its submission moves to the store. The app-wide surface
and notification projection are added only after all starts flow through the same owner.

Automated validation includes focused store/persistence/migration/notification/navigation/cancel/
retry/stale-state tests, the SDK baseline if changed, Android's full unit/assemble baseline,
`git diff --check`, and packaged manifest inspection proving only `POST_NOTIFICATIONS` and the
non-exported action receiver were added and no service/foreground-service permission exists.

Live validation uses the actual debug APK on API 30 and a disposable API 35 AVD against only the
disposable Arcane/Docker environment. It exercises success, explicit server failure, idempotent
cancel, navigation/tab/rotation/background continuity, process kill and Activity reattachment,
disconnect/restart, full and partial multi-environment failure, binding changes, duplicates,
concurrent operations, bounded high-volume output, notification allow/deny/revoke/update/grouping/
cancel/exact tap, relaunch, and an older unsupported server. Outcomes are corroborated with Activity
records and Docker/server state. All fixtures, secondary instances, AVDs, installed APKs, captures,
CA/proxy/trust artifacts, and test resources are removed afterward.

Rollout is schema 1 with no legacy import and no foreground component. A compatibility failure in
Activities disables reattachment/cancel but leaves the operation truthful (`interrupted`/`unknown`).
There is no feature flag that could create two owners. Rollback simply leaves an excluded DataStore
unknown to the older app; it contains no credentials or replayable request.

## Independent design review

The first independent review on 2026-09-11 blocked implementation with three blocking and six high
findings. This revision resolves them as follows:

- replaces heuristic Activity matching/cancellation with the operation UUID carried by the server's
  existing batch-ID correlation contract and adds the missing SDK requirements;
- makes restore-to-interrupted transitions explicit and forbids assumed local cancellation after
  transport invocation;
- defines a non-exported, non-owning action receiver and bounded cold-start authentication behavior;
- defines the actual root overlay and Activities nested-navigation handoff, including cold-start and
  invalid-target behavior;
- adds pinned-Dashboard deploy to inventory and documents the current aggregate container-update UI;
- defines per-kind full/partial terminal predicates;
- replaces persisted raw/bounded presentation text with a closed vocabulary, removes persisted
  display names, and digests duplicate keys;
- defines unknown/interrupted retention, reconnect exhaustion, and full-active-ledger rejection;
- deletes unreadable newer-schema data on security invalidation; and
- makes stale post-process-death notification behavior explicit.

The second review found one remaining high issue: image pull's “target ID” is itself the sensitive
image reference, so persisting it contradicted the sanitization rule. The schema now permits only
opaque project/container IDs and stores no image-pull target; its correlation and duplicate digest
remain sufficient. The review also requested a testable receiver deadline, which is now eight
seconds for the entire receiver coroutine, with unconditional `PendingResult.finish()` and no
automatic retry.

The final re-review confirmed that the complete eight-second receiver deadline resolves the last
finding. The PAR-201 design gate passed with no remaining blocking or high findings, authorizing
PAR-202 and PAR-203 implementation to begin.
