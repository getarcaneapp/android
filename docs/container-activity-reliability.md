# Container and activity reliability

This document records the durable product and contract decisions for PAR-106 through PAR-109. The
comparison was performed against Android `1fb86f8560c39f119ca63625a0ab2039bbdb9201`, iOS
`6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
`7787bff82973302062d1d0c8db4c12f09547c5b0`, and Arcane
`5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105` after fetching each repository's current `origin/main`.
The required SDK correction merged through libarcane-kotlin PR #8 as
`b21faefd091de53fa30e6b5b910c66f49ec8076c`; Android, iOS, and Arcane remained at the comparison
revisions when publication was revalidated on 2026-09-11.

## Container action inventory

Android uses only typed `libarcane-kotlin` services. Every action is filtered by the signed-in
user's exact permission (or `*`) before it is shown. Pause, unpause, and kill additionally require
Arcane 2.2.0 or newer because that is the server release where those handlers were introduced; a
remote environment is checked against its own version rather than the controller's version.

| Action | State availability | Permission | Server gate | Confirmation and result | Parity decision |
| --- | --- | --- | --- | --- | --- |
| Start | Stopped, not paused | `containers:start` | Existing container API | Starts immediately; success/error is reported and the same detail selection refreshes | Existing Android/iOS behavior retained |
| Stop | Running, not paused | `containers:stop` | Existing container API | Names the container and environment; warns it can be restarted | Existing action hardened with policy and feedback |
| Restart | Running, not paused | `containers:restart` | Existing container API | Names the container and environment; warns about interrupted connections | Existing action hardened with policy and feedback |
| Pause | Running, not paused | `containers:pause` | Arcane >= 2.2.0 | Names the container and environment; explains processes stop until unpaused | Android-native addition backed by current server/SDK support |
| Unpause | Paused | `containers:pause` | Arcane >= 2.2.0 | Immediate inverse of pause with explicit success/error feedback | iOS exposes this outcome; Android now matches it |
| Kill | Running, including paused | `containers:kill` | Arcane >= 2.2.0 | Names container/environment and explicitly says SIGKILL cannot shut down cleanly | Deliberate high-value server/SDK action; kept in overflow because it is exceptional |
| Redeploy | Any known state | `containers:redeploy` | Existing container API | Names container/environment and warns that Arcane pulls and recreates it | Existing Android/iOS behavior retained |
| Delete | Any known state | `containers:delete` | Existing container API | Names container/environment, says permanent, and discloses forced removal explicitly | Existing destructive flow retained and hardened |
| Inspect | Any known state | `containers:read` | Existing container API | Read-only; no confirmation | Existing behavior permission-gated |
| Logs | Any known state | `containers:logs` | Existing log stream | Read-only viewer; stream errors are retryable | Existing destination upgraded by PAR-107 |
| Terminal | Running, not paused | `containers:exec` | Existing exec stream | Interactive destination; connection and command errors remain visible | Existing destination upgraded by PAR-107 |

Stop, restart, pause, kill, redeploy, and delete are the actions that can interrupt work or destroy
state, so they require resource- and environment-specific confirmation. Start and unpause are
recovering transitions and remain one-tap actions. Unsupported states and permissions are omitted,
not shown as controls that fail after selection. Docker's broader signal selection and arbitrary
process execution remain intentionally omitted: iOS does not expose them as container lifecycle
outcomes and the focused mobile UI uses the typed kill and terminal contracts instead. Rename is
also intentionally omitted even though both mobile SDKs and iOS currently expose it: current Arcane
has no `/containers/{id}/rename` handler. Android does not present a control that is guaranteed to
fail, and it does not reinterpret the server's full edit-and-recreate contract as a rename operation.

## Log and terminal continuity

Container and project logs share one viewer and retention policy:

- the SDK requests timestamps and the viewer independently controls whether they are displayed;
- search covers ANSI-stripped text, service, level, and timestamp metadata;
- ANSI color is rendered in the list and stripped from copied/shared/exported plain text;
- automatic follow can be paused explicitly or by manual scrolling and reports retained arrivals;
- repeated sequence values are deduplicated; reconnect keeps the retained window, while changing
  client, environment, or resource clears it before the next stream;
- memory is capped at 5,000 lines, 300,000 retained characters, and 32,000 characters per line;
  earlier lines and shortened oversized lines are counted and shown as a bounded-window warning;
- users may select visible text, copy all retained text, use the Android Sharesheet, or export a
  UTF-8 `.log` through the Storage Access Framework; a canceled or failed destination does not
  change retained data; and
- every whole-window copy/share/export confirmation says exactly how many lines are included,
  discloses discarded/shortened content, and warns that logs can contain passwords, tokens, and
  other secrets. The app does not infer or redact arbitrary secret formats because silent partial
  redaction would create a false safety guarantee.

Terminal output is independently capped at 200,000 characters. Visible output supports normal
text selection, while the terminal menu provides **Copy all retained** and **Clear**. A discarded
character count makes the bounded scope explicit. Leaving the lifecycle owner cancels the stream;
changing environment or container replaces the owned session.

## Live Events

The current SDK and Arcane server expose paginated Events reads but no Events stream. Android
therefore polls the global Events endpoint every five seconds only while the screen is `RESUMED`
and the Live toggle is enabled. Failures retain visible data and back off to 10, 20, 40, then 60
seconds; a successful request restores the five-second interval. The newest 200 rows are retained,
merged by event ID, and sorted by timestamp descending with ID as a stable tie-breaker. Manual
refresh uses the same merge rule. Client/session identity and coroutine cancellation prevent late
results from a prior server from replacing the current state.

Events remain global because that is the existing Android destination and current typed endpoint;
the selected Docker environment is not silently substituted as a filter. The five-second visible
poll with a 60-second failure ceiling matches the server's existing remote-activity cadence while
bounding background battery and network work to zero when the route is not owned.

## Activity Center recovery

Arcane's supported activity contract is the multiplexed NDJSON endpoint
`GET /api/stream?channels=activities`; the former SDK path to a per-environment activity stream did
not exist in the current server. The SDK now decodes activity envelopes, retains connection
heartbeats, and can optionally filter activity frames by environment without filtering heartbeats.

Android owns one aggregate stream only while Activity Center is `RESUMED`. Initial environment
loads run concurrently and are stored by source, so one failed environment cannot discard healthy
activity buckets. Server environment-error frames identify the affected source and produce an
individual retry action. Retrying a source performs one bounded paginated reload for only that
environment. A connection failure or 45-second heartbeat timeout retries after 1, 2, and 4 seconds,
then exposes a terminal connection retry; three healthy heartbeats reset that retry budget.

Activity IDs are the deduplication key, snapshots replace only their source bucket, and the final
list uses the server timestamp with ID tie-breaking. A generation token plus client/environment
identity rejects late frames and reloads after server or environment changes. Full failure is
distinguished from partial failure, but both retain any last healthy data until it is refreshed or
the owning session changes.

## Validation record

The SDK baseline `./gradlew :arcane-core:test :arcane-android:assembleRelease` passed with 102 tests,
zero failures, and a release AAR before PR #8 merged. Android's CI-equivalent
`./gradlew :app:testDebugUnitTest :app:assembleDebug` passed with 294 tests, zero failures, and a
debug APK. Focused suites cover server gating, container action/confirmation policy, log retention,
deduplication, formatting and pause state, terminal retention, Events ordering/backoff, and Activity
failure/retry/heartbeat/cancellation behavior. `git diff --check` also passed.

Live validation used the actual debug APK on `arcane_test_api30` (Android 11/API 30, emulator
37.1.11.0) against
disposable Arcane 2.10.2 (image revision `670ee2b34ea7b0fb2917643229b6ce9070ee9742`)
and Docker 29.1.3/API 1.52 in LXD `arcane-e2e`. Deterministic running, stopped, paused, failing,
noisy, ANSI, project-log, terminal, event, and unreachable-environment fixtures covered:

- pause, unpause, and kill confirmations, repeated state changes, Docker before/after state, detail
  selection retention, and refreshed compatible controls;
- ANSI rendering and stripped plain-text sharing, scoped secret warnings, canceled SAF export,
  timestamps/search, manual pause with retained arrivals, explicit resume, bounded noisy input, and
  interactive terminal command/copy/clear behavior;
- a server event created at 16:38:11 UTC and fetched by the visible Events route at 16:38:12 UTC,
  with request logs confirming the bounded polling cadence; and
- healthy Local Docker activities retained beside an individually retryable unreachable environment,
  a separately identified terminal stream failure, and recovery of that stream without erasing the
  still-failed environment.

Temporary containers, project files, environment registration, test account and role assignments,
APK copies, screenshots, AVD clones, user CA, display overrides, and debug-only trust configuration
were removed. Docker and API verification reported zero `par-rel-*` containers and zero
`PAR Unreachable` registrations; the production manifest and network security configuration were
never weakened.
