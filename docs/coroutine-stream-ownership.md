# Coroutine and stream ownership

This is the durable PAR-008 audit record for long-lived Android work. Its scope is stores, live
streams, and screen operations that continue long enough to cross a server, environment, or screen
change. Ordinary request/response actions remain owned by their calling Compose scope and are not
additional stream owners.

## Ownership inventory

| Operation | Owner | Replacement and cancellation rule |
| --- | --- | --- |
| Authentication, capability, and environment session work | `ArcaneClientManager` session job and scope | A new server/session advances the client generation and cancels the prior session work. |
| Dashboard aggregate stream | One `DashboardStreamStore` stream job | Stop, reconnect, or client replacement invalidates the generation and cancels the old job before starting another. |
| Dashboard per-environment snapshot fallback | One store-owned job per newly discovered environment, plus caller-owned explicit refresh children | Environment removal and store/client shutdown cancel store-owned work. Request tokens reject older concurrent responses and any response invalidated by a stream event, environment removal, or client replacement. |
| Dashboard live system stats | `LaunchedEffect(client, enabled environments, refresh, lifecycle restart)` | A key or lifecycle change cancels the entire structured child set before replacement. |
| Activity Center loading | Latest calling job, registered by `ActivityCenterStore` | A newer load or client replacement cancels the prior load; generation checks prevent late bucket publication. Canceled paging restores the previous requested limit. |
| Activity Center live events | One store-owned job per enabled environment | Reload reconciles and replaces the set. Client change, capability loss, or screen disposal cancels every prior job; job identity prevents an old `finally` block from removing its replacement. |
| Container logs and stats, project logs, and project streaming actions | A target-keyed `LaunchedEffect` | Client, environment, resource, or action changes cancel the old collector and clear target-specific output before starting the replacement. |
| Container terminal | One target/shell/retry-keyed `LaunchedEffect` | The effect owns both the terminal session and output collection. Its exact session is closed in `NonCancellable`; an old cleanup cannot close or reset a replacement session. |
| Image pull and upload streams | One remembered job in the sheet's composition scope | The action disables duplicate starts. Sheet disposal, client replacement, or environment change cancels the current job. |
| Updater run and status polling | One target-keyed `LaunchedEffect` with one structured request child | Leaving or changing the target cancels request and probes; cancellation is never mapped to a run outcome or polling failure. |
| Demo heartbeat | One `DemoService` job in the manager-owned session scope | Starting a heartbeat first cancels the previous job; ending or replacing the session stops it. |

## Cancellation and stale-result rules

- A broad catch around suspending work must rethrow `CancellationException`. Best-effort suspending
  requests use `runSuspendCatching`; ordinary `runCatching` remains appropriate only for
  non-suspending parsing, platform calls, and cleanup that cannot consume coroutine cancellation.
- A Compose stream key includes the client identity, environment identity, and resource/action
  identity that determines the server request.
- Store-owned work uses generation, request-token, or exact-job identity checks before publishing
  state. Cancellation is the primary stop mechanism; identity checks are the final defense against
  transports that complete after cancellation.
- Cleanup acts on the resource captured by the owner. It must not read a newer shared session/job
  and accidentally close or remove that replacement.
- Stream output is bounded by the screen's existing retention limit and cleared when its target
  changes so content from one environment cannot appear under another.

## Regression coverage

- `CoroutineFailuresTest` proves the suspending result wrapper preserves success and ordinary
  failures while rethrowing the original cancellation.
- `DashboardStreamStoreTest` covers canceled refresh, concurrent refresh ordering, reconnect with
  one live owner, environment removal, client replacement, cancellation of store-owned snapshot
  jobs, and rejection of stale snapshots.
- `CompleteListLoaderTest` retains paging cancellation coverage used by Activity Center's complete
  environment discovery and the shared pagination boundary.
- `UpdaterRunScreenTest` proves cancellation cannot become an updater request outcome.
