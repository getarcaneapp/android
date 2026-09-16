# Resilient Reads foundation

This is the implementation record for PAR-301, PAR-302, and PAR-305. The source comparison was
refreshed on 2026-09-15 before coding:

- Android `ed03850f3c378cefd2824aacc64dba1f896bd03f`
- iOS `8d13fdb5cd61a62b1d666e9e982a2670d86086c3`
- libarcane-kotlin `af6fa681d1c4c1e1af8a26a774f69a193df02680`
- Arcane `9e5bfea2f213a63f11c83f63f77e3c8499f23aba`

A publication-time fetch confirmed that Android, iOS, and SDK `origin/main` remained at those exact
pins. Arcane `origin/main` had advanced to `02208f5cd24e028dfb0e2221305febbf3ee58837`
with S3 volume-backup discovery only; its changed handlers/types do not touch the authentication,
environment, Dashboard, list, routing, or operation contracts used here, so the reviewed and live
Arcane pin above remains the batch authority.

The iOS `ResponseCache`, cached-fetch helpers, widget snapshot publisher/store, quick-action router,
and app routing were compared directly. Android preserves the iOS outcomes—bounded stale fallback,
small current-state projection, and authenticated shortcuts—using Android lifecycle, navigation,
backup, and shortcut conventions. The stronger opaque snapshot binding and typed route validator are
intentional Android security improvements required by the parity tasks.

## Ownership and data flow

`ArcaneClientManager` remains the only authenticated-client owner. The new components are stores or
validators and cannot instantiate a client:

```text
ArcaneClientManager / SDK reads / connected streams
           |                         |
           | sanitized list data     | authoritative live UI state
           v                         v
ResilientReadCache ------------> screen stale/fresh state
           |
           | bounded one-way at-a-glance projection
           v
StatusSnapshotStore ----> future widgets/system surfaces (not implemented here)

OperationStore terminal success ----> scoped read-cache invalidation
AuthenticatedRoute + current manager ----> validated navigation
fresh authorized environment list ----> reviewed dynamic shortcuts
```

The durable operation ledger, response cache, and snapshot are separate owners and separate files.
Operation requests, payloads, logs, results, or credentials never enter the read cache or snapshot.
Cache/snapshot state never drives or replays an operation. The only operation-to-cache edge is
one-way invalidation after authoritative successful completion.

## PAR-301 cache

`ResilientReadCache` is restricted to the enumerated resources `dashboard`, `environments`,
`containers`, `projects`, `images`, `volumes`, and `networks`. It is not a generic HTTP cache and
accepts SDK-model serializers plus typed SDK loaders from the screens. There is no REST, DTO,
authentication, cookie, stream, or client wrapper.

Every key contains:

```text
schemaVersion
SHA-256(canonical server origin)
SHA-256(credential origin)
SHA-256(authenticated account/user ID)
SHA-256(sorted roles, environment permissions, capabilities, and feature support)
environment ID or the explicit fleet identity
closed resource kind
bounded request identity/query
```

The persisted version-1 envelope is `{schemaVersion, key, storedAtEpochMs, payload}`. Only explicit
sanitizers can prepare SDK list models for persistence. They remove environment API URLs, API keys,
and edge session identifiers; container command, labels, mounts, port/host/network details; project paths,
Compose and environment contents, included files, runtime services and Git URL; arbitrary image,
volume, and network metadata; volume mountpoints; and reverse resource references. Dashboard cache
data contains sanitized environments plus derived counts only.

Credentials, tokens, cookies, authorization headers, raw logs, operation data, request bodies,
secrets, passkeys, MFA data, API keys, registry credentials, notification-provider data, streams,
and mutations are not admissible cache resources. Detail endpoints are intentionally not cached in
this foundation.

### Bounds, time, and storage

| Resource | Revalidate after | Maximum stale age |
| --- | ---: | ---: |
| Dashboard | 60 seconds | 24 hours |
| Environments | 5 minutes | 7 days |
| Containers | 30 seconds | 24 hours |
| Projects | 5 minutes | 3 days |
| Images | 5 minutes | 3 days |
| Volumes | 5 minutes | 3 days |
| Networks | 5 minutes | 3 days |

Memory is access-ordered LRU bounded to 64 entries and 4 MiB. Disk is access-ordered by touched
mtime and bounded to 256 entries and 24 MiB; one envelope may not exceed 2 MiB. The cache lives at
`cacheDir/arcane_read_cache_v1`, because the data is disposable, may be reclaimed by Android, and
must never be backed up or transferred. Writes use a same-directory temporary file, file-descriptor
sync, and atomic rename. Invalid, corrupt, oversized, mismatched, expired, and unknown-schema files
are deleted as misses. Version 1 has no predecessor migration; future schemas require an explicit
decoder/migration or fail closed.

The first cache value is always `Stale`, even inside its revalidation interval. Network success is
`Fresh`; refresh failure retains an explicitly stale value with a closed user-facing error. A cache
hit is never labelled live. Equal scoped requests coalesce onto one network load. Per-key request
sequence and global invalidation generations fence late network results, so an older refresh or a
result completing after invalidation cannot overwrite newer state.

Connected Dashboard streams remain the authoritative source of live stream totals. Cached REST
state is only the marked fallback and never owns or competes with the stream. Successful direct
mutations and successful durable operations invalidate only the affected resource kinds in the
bound environment; Dashboard/fleet aggregates are also invalidated. Other environments remain.
Authorization failures immediately evict the attempted key. A changed permission/capability hash,
account, credential origin, or server makes old entries unreachable before asynchronous deletion.
Logout and Change Server synchronously end presentation of the prior scope and schedule complete
cache removal. Settings exposes cache size and an explicit Clear Cache confirmation.

To support a process restart while the server is unreachable, a separate strict seven-day
`cacheDir/arcane_offline_read_session_v1/session-v1.json` descriptor records only the opaque cache
scope, capability mode/feature flags, and bounded `read`/`list` permissions. It stores no URL,
username, account ID, token, wildcard, role, or mutation permission. Offline restoration also
requires the still-bound canonical credential origin in the excluded preference store; an origin
mismatch, logout, Change Server, corruption, expiry, or authoritative 401/403 deletes the
descriptor and cache. The temporary offline user is read-only. A reconnect first revalidates the
authoritative user/capability context, then changes scope before any network response can be cached.

## PAR-302 snapshot

`StatusSnapshotStore` is a credential-free, strict 64 KiB projection at
`noBackupFilesDir/arcane_status_snapshots/status-v1.json`. Persistent no-backup storage is deliberate:
future widget processes need process-death survival, while server-derived status must never enter
cloud backup or device transfer. The store has no SDK or manager dependency and cannot own a client.

Schema version 1 contains only:

```text
schemaVersion, sourceVersion, generatedAtEpochMs, sourceUpdatedAtEpochMs
freshness = fresh | stale | error | signed_out
errorCode = none | network_unavailable | server_error | unknown
scopeId = SHA-256(server, credential origin, account, permission context) or null
activeEnvironmentKey = SHA-256(scopeId, environment ID) or null
bounded aggregate counts
up to 10 {opaque environment key, 128-byte display name, online flag, bounded counts}
```

It contains no raw server URL/origin, username, account or environment ID, credential, cookie,
secret, log, resource identity/detail, mutation capability, or SDK object. The source-version and
generation/freshness fields prevent a consumer from presenting projected data as live.

Writes use same-directory temporary files, descriptor sync, and atomic rename. Counts, strings,
rows, schema, and file size are validated. Corrupt, oversized, old, or unknown schemas fail closed
and are removed. An active-scope authorization fence and generated-at ordering reject delayed
concurrent writers. Logout, Change Server,
account change, credential-origin change, and permission-scope change synchronously replace the
file with a signed-out snapshot; a scope mismatch found after process death does the same before
returning data. Offline refreshes publish stale/error metadata from the approved cache state.
Glance and widget presentation are intentionally outside this batch.

## PAR-305 routes and shortcuts

All external navigation uses one immutable `AuthenticatedRoute` model. Version-1 wire form is:

```text
arcane-mobile://route/v1/<server-sha256|current>/<destination>/<hex-utf8-env|->/<hex-utf8-resource|->
```

The 4 KiB total and 512-byte argument bounds are checked before decoding. Scheme, authority,
version, segment count, lowercase hex, UTF-8, argument shape, query, fragment, user info, and port
are strict. Incoming Intents are untrusted. `current` is accepted only for static non-resource
Dashboard, Containers, Projects, Activities, and Operations roots. Resource and dynamic-environment
routes always carry the canonical server-origin hash, preventing a same-ID resource from another
server from opening.

Resolution happens after authentication and validates server binding, current user, RBAC permission,
environment existence/enabled state, server capabilities, supported destination, and typed SDK
resource existence. Signed-out routes remain pending through login. Wrong-server, unauthorized,
disabled/deleted environment, deleted resource, malformed, oversized, unsupported, and temporarily
unverifiable routes fail with a bounded explanation and a safe current stack; they never guess.
Cold and warm Intents share the same coordinator. Valid routes select the environment, construct the
appropriate top-level stack, and then open a detail/operation overlay so Back returns to the expected
root. Existing operation-notification content Intents now use this same route format; the internal
cancel broadcast remains a separately validated non-exported action.

The manifest publishes exactly three static read-only shortcuts: Dashboard, Containers, Projects.
Dynamic shortcuts are limited to three fresh, enabled environments for which the current user has
view permission. They use opaque shortcut IDs and server-bound typed routes. Cached/stale reads,
permission loss, logout, server/account changes, and failed session validation remove them
immediately. No mutation shortcut exists. Future widgets can encode the same route model.

## Security and test gates

The backup audit classifies both locations and automated policy tests pin the cache to `cacheDir`,
the snapshot to `noBackupFilesDir`, and the sole backup allowlist to tab preferences. Focused tests
cover explicit stale/fresh state, TTL expiry, corruption/old schema, LRU/size bounds, request
coalescing, environment and permission isolation, authorization eviction, invalidation races,
snapshot restart/corruption/schema/size/bounds/concurrent writers/session fencing, route codec and
malformed bounds, login continuation, static shortcut policy, and backup placement.

The final `./gradlew :app:testDebugUnitTest :app:assembleDebug` baseline passed with 354 tests and
zero failures, errors, or skips. Focused cache, sanitizer, migration, coalescing, invalidation, race,
permission-scope, snapshot, route, shortcut, restoration/process-death, and backup-policy tests
passed; `git diff --check` and packaged manifest/shortcut inspection passed. No SDK source changed.

The real debug APK 0.1.0 (260901) was exercised on Android 11/API 30 using the existing
`arcane_test_api30` AVD against disposable local Arcane 2.10.2
(`sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`). The complete offline,
reconnect, corruption/schema/bounds/clear, scoped mutation and durable-operation invalidation,
permission/account/session, snapshot, external-route/back-stack, and shortcut publication/removal
evidence is recorded sequentially in the canonical PAR-301, PAR-302, and PAR-305 entries. All
disposable users/resources, APK installation, cache/snapshot evidence, shortcuts, screenshots,
certificate/trust artifacts, and the temporary LXD AVD clone were removed. The original API 30 AVD,
`arcane-e2e`, and existing environments were preserved. An API 35 AVD was not created. Intentional
remaining exclusions are cached detail/arbitrary endpoints, resource dynamic shortcuts, Glance/widget
presentation, and all mutations/streams/secrets; these belong to later separately reviewed work.
