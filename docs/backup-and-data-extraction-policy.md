# Android backup and data-extraction policy

Arcane Android permits Android Auto Backup and device-to-device transfer only for one app-local UI
customization file. Authentication state, server/account identity, server-derived data, and present
or future operational state are denied by default.

This policy was audited for PAR-007 against Android
`27aa01b77f10f421c7ebea5d6648b66001847cd2`, iOS
`6088fcc0ef04dc906ce74e9129dffa96894a6da5`, libarcane-kotlin
`7787bff82973302062d1d0c8db4c12f09547c5b0`, and Arcane
`6a9ff7aa64fbec74e379b5dc9622699189093d73`. No SDK or server contract change is required.

The policy was re-audited for PAR-301/PAR-302 on 2026-09-15 against Android
`ed03850f3c378cefd2824aacc64dba1f896bd03f`, iOS
`8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
`af6fa681d1c4c1e1af8a26a774f69a193df02680`, and Arcane
`9e5bfea2f213a63f11c83f63f77e3c8499f23aba`. The resilient-read cache and sanitized status
snapshot are both app-private but deliberately live outside every backed-up domain.

The policy was re-audited for PAR-303/PAR-304/PAR-505 on 2026-09-16 against Android
`0f353eaaa53eff7d9c7720ec2e131db96ba59253`, iOS
`8d13fdb5cd61a62b1d666e9e982a2670d86086c3`, libarcane-kotlin
`b29695d547b78389ed7230b35cd133f7046b4b52`, and Arcane
`9fa57c867b1085a7142d7e755f87ddd9318b1a1d`. The widget reads the existing no-backup snapshot and
adds no app-defined configuration or Glance state. Glance's own app-widget-manager bookkeeping is
an implementation-owned DataStore and remains excluded by the deny-by-default allowlist. Adaptive
nested stacks and topology selection/viewport are saved-instance state only. The allowlist remains
unchanged.

## Protected data boundary

Android backs up app-private files, databases, shared preferences, and app-specific external files
by default. Declaring any `<include>` changes that behavior to an allowlist. Arcane uses the same
single-entry allowlist in the Android 11-and-lower `backup_rules.xml`, Android 12+ cloud backup, and
Android 12+ device transfer:

These semantics follow Android's official [Auto Backup configuration
guide](https://developer.android.com/identity/data/autobackup) and [DataStore backup
guidance](https://developer.android.com/topic/libraries/architecture/datastore#backup-rules).

| Backup domain and path | Decision | Contents and rationale |
| --- | --- | --- |
| `file/datastore/arcane_tabs.preferences_pb` | Included | User-selected bottom-tab IDs and the last selected top-level tab ID. These are app-defined UI identifiers, not server/account identifiers or operation data. |
| `file/datastore/arcane_prefs.preferences_pb` | Excluded | Server URL, normalized credential-origin binding, active environment ID/name, theme, and accent share one DataStore file. Backup rules are file-granular, so the whole file is protected and appearance falls back to app defaults after restore. |
| `file/datastore/arcane_secure_tokens.preferences_pb` | Excluded | SDK-owned, origin-scoped access and refresh token ciphertext. The AES key remains in Android Keystore and is not an app backup file, but ciphertext is excluded independently rather than relying on key non-transferability. |
| `file/datastore/arcane_operations.preferences_pb` | Excluded | Durable-operation recovery descriptors and bounded presentation state, including hashed server/account bindings, environment and Activity identifiers, and terminal outcomes. The data is deliberately bound to one installation's authenticated server context and must never transfer or restore onto another device. |
| `sharedpref/arcane_pinned.xml` | Excluded | Server-derived container, project, and volume IDs keyed by environment ID. |
| `sharedpref/arcane_project_deploy_options.xml` | Excluded | Pull policy and force-recreate defaults keyed by a hash of normalized server, account, environment, and project identity. Hashing the scope does not make these server-bound operation preferences portable. |
| `sharedpref/arcane_secure_prefs.xml` | Excluded | Historical/deprecated SDK encrypted-token location, protected in case data from an older build remains installed. |
| `cache/arcane_read_cache_v1/*.arc` | Excluded | Bounded, sanitized server-derived list and Dashboard responses. Android never backs up the app cache domain; the explicit allowlist admits no cache path. Cache placement also allows ordinary OS storage reclamation without treating cached reads as durable user data. |
| `cache/arcane_offline_read_session_v1/session-v1.json` | Excluded | Opaque server/credential/account/permission binding plus bounded read-only permissions used to locate the correct cache after an offline process restart. It contains no URL, username, account ID, token, or mutation permission and is deleted on every session boundary. |
| `no_backup/arcane_status_snapshots/status-v1.json` | Excluded | Credential-free Fleet Status widget projection. Schema 2 contains bounded counts/names plus one-way server/scope/environment bindings. `noBackupFilesDir` is structurally outside Auto Backup and device transfer, and the file allowlist does not admit it. Persistent no-backup storage lets the widget survive process death while never transferring devices. |
| `files/datastore/GlanceAppWidgetManager-app.getarcane.android.preferences_pb` | Excluded | Glance-owned launcher/widget bookkeeping, not app-defined configuration or server data. The file allowlist does not admit it. |
| Every database, other private/shared-preference file, and app-specific external file | Excluded | The allowlist does not admit these domains. Future caches, snapshots, exports, or operation stores stay protected until all policy formats and tests explicitly classify them. |

The exact policy is intentional even where data is encrypted at rest. Encryption does not turn an
authentication token or server-derived record into portable user preference data.

## Persistence audit

- `Prefs` uses `files/datastore/arcane_prefs.preferences_pb` for server identity, credential-origin
  binding, environment selection, and appearance. It is excluded as one indivisible file.
- `NavTabsStore` and `MainTabSelectionStore` share the sole included file,
  `files/datastore/arcane_tabs.preferences_pb`.
- `PinnedItemsStore` uses `shared_prefs/arcane_pinned.xml`; its server resource and environment IDs
  are excluded.
- `ProjectDeployPreferences` uses `shared_prefs/arcane_project_deploy_options.xml` for pull-policy
  and force-recreate defaults scoped to a hash of normalized server, signed-in user, environment,
  and project IDs. The file is excluded because the settings are server-bound operation context.
- `AndroidSecureTokenStore` in libarcane-kotlin uses Android Keystore plus
  `files/datastore/arcane_secure_tokens.preferences_pb`; the token file is excluded. The Android app
  does not instantiate the SDK's deprecated `arcane_secure_prefs.xml` store, but the allowlist also
  excludes any residue from older integrations.
- `OperationStore` uses `files/datastore/arcane_operations.preferences_pb` for its versioned,
  bounded recovery ledger. It contains no credential, authorization header, server URL, raw image
  reference, project content, or persisted log line. The allowlist excludes the entire ledger from
  both cloud backup and device transfer; `BackupPolicyTest` names it as a protected location.
- `ResilientReadCache` uses `cache/arcane_read_cache_v1`. It stores only approved sanitized
  Dashboard/environment/container/project/image/volume/network projections, is size/age bounded,
  and is cleared on session boundaries. The cache directory is app-private and excluded from both
  backup and transfer independently of the XML allowlist.
- `OfflineReadSessionStore` uses `cache/arcane_offline_read_session_v1`. Its strict, seven-day
  descriptor contains only one-way scope hashes, feature modes, and bounded read/list permissions;
  it lets a process restarted without network select the already scoped cache while withholding
  mutation permissions. It is cleared with the cache on logout, Change Server, account/credential
  invalidation, and authoritative authentication rejection.
- `StatusSnapshotStore` uses
  `no_backup/arcane_status_snapshots/status-v1.json`. The strict, 64 KiB credential-free projection
  contains opaque server/scope/environment correlations, bounded names, and bounded counts only. It
  is synchronously
  replaced by a signed-out snapshot at logout/server/account boundaries. `noBackupFilesDir` keeps
  it out of cloud backup and device transfer while allowing it to persist across process death. The
  Fleet Status widget reads that projection directly and defines no app-specific Glance state,
  configuration, database, DataStore, or SharedPreferences. Glance's internal app-widget-manager
  DataStore is launcher bookkeeping and remains outside the backup allowlist.
- Adaptive tab navigation continues to use only the existing included
  `arcane_tabs.preferences_pb`. Per-tab nested stacks, list-detail selection, topology selection, and
  pan/zoom viewport use Android saved-instance state; no graph or adaptive-state file is created.
- `ArcaneCookieJar`, including hosted-demo session cookies, is memory-only and cleared on session,
  server, and lifecycle boundaries. Ktor has no configured persistent cookie or HTTP response store.
- Projects workspace files, unsaved Compose/`.env` edits, variable values and sync state,
  account/profile edits, passwords, passkey challenges/credentials, step-up grants, MFA recovery
  codes, and container/template registry forms are held in Compose or in-memory SDK/store state.
  None is written to DataStore, SharedPreferences, a database, or an app-private file. The passkey
  browser bridge keeps its active transaction only in memory, while Credential Manager owns any
  enrolled platform credential outside Arcane's app-private backup domains.
- Apart from the explicitly sanitized resilient-read cache and status snapshot above, loaded server
  responses, current-user/capability/environment objects, avatars, templates, activity/stream
  state, process-port lookups, and operation request/result payloads are held in memory. Login URL,
  username, password-form visibility, and navigation selection can participate
  in Android saved-instance-state recreation; passwords and other sensitive forms deliberately use
  non-saveable state. Saved-instance state is transient system state outside the Auto Backup file
  domains and is discarded when the app is uninstalled.
- Coil may use its normal memory/disk image caches. Android excludes app cache and code-cache
  directories independently, and the Arcane allowlist admits no cache path.
- The app defines no Room, SQLite, or other database. The database domains remain excluded.
- Image uploads are read from a user-selected content URI and retained only in screen memory. Volume
  backup downloads are written deliberately to the public Downloads collection through MediaStore
  (or the legacy public Downloads directory); public user files are outside the app-private backup
  dataset and are not copied into the repository.

Any new persistence API or filename must update this audit first. It remains excluded unless there
is a product reason to restore it and it contains no credential, server/account identity,
server-derived content, cache/snapshot, or operation payload. An approved addition must be made to
all three allowlists and to `BackupPolicyTest` in the same change.

## Automated validation

`BackupPolicyTest` parses the source manifest and both XML formats. It asserts that backup remains
enabled only through the two declared resources, that legacy cloud and Android 12+ cloud/device
transfer have the exact same allowlist, that no broad directory is included, and that known
protected persistence locations are absent. It also pins the read cache and offline binding to `cacheDir`, the snapshot
to `noBackupFilesDir`, and proves neither name is admitted by the allowlist. Android resource
processing and APK assembly provide schema and manifest-merge validation.

## PAR-007 validation evidence

Validation completed on 2026-09-10 with the debug APK (`versionName` `0.1.0`, `versionCode`
`260901`, target SDK 35):

- All 3 focused `BackupPolicyTest` tests passed. The CI-equivalent
  `./gradlew :app:testDebugUnitTest :app:assembleDebug` run passed all 261 tests (0 failures,
  0 errors, 0 skipped) and assembled the APK. Packaged `aapt2 dump xmltree` inspection found only
  `file/datastore/arcane_tabs.preferences_pb` in each of the compiled legacy cloud, Android 12+
  cloud, and Android 12+ device-transfer sections.
- Runtime validation used the disposable `par007_backup_api35` Google APIs emulator on Android 15
  (API 35), fingerprint
  `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys`, against the
  disposable Arcane 2.10.2 container image
  `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`. A temporary,
  emulator-trusted HTTPS reverse proxy kept the tested debug APK unchanged while connecting to the
  local HTTP-only server.
- Before each backup, the app had a server URL, authenticated session and SDK tokens, a dark custom
  appearance, a non-local environment selection, a pinned disposable project, non-default project
  deployment preferences, loaded server data, and an unsent project form. The bottom navigation had
  `Networks` in place of `Containers`, with `Projects` as the last selected top-level tab. The
  corresponding protected DataStore and SharedPreferences files were present without inspecting
  their contents.
- The encrypted local cloud transport returned `Success` for the package. After uninstall and
  reinstall, the only restored app-private file was
  `files/datastore/arcane_tabs.preferences_pb`. The app opened in setup with an empty server URL;
  no preference/token file, shared preference, cache, database, other private file, or operation
  form returned.
- The Google Play services D2D test transport was initialized separately and also returned
  `Success`. Its uninstall/reinstall result was identical: only the tab DataStore returned, and the
  app opened at blank server setup.
- After re-entering the disposable server and account in each cycle, `Networks` still replaced
  `Containers` and `Projects` was restored as the selected top-level tab. Appearance and environment
  were at defaults, the pinned section was absent, deployment options reopened as `If Missing` with
  force recreation off, and the unsent form did not return. The disposable project appeared only
  after authentication reloaded current server data.
- The original Google backup transport and test settings were restored. The disposable project,
  HTTPS proxy, emulator, AVD, APK copy, screenshots, and inspection files were removed after the
  run. No credential, backup dataset, app-private contents, or machine-specific test artifact is
  retained in the checkout.

## Safe emulator backup-and-restore check

Use a disposable emulator running Android 12 or newer, a disposable Arcane server or account, and
the debug APK. A Google Play services image is additionally required for the single-device D2D
transport test below. Never use production credentials, print preference contents, pull a backup
dataset, or save test output under the repository. The following flow follows Android's
official [backup and restore test guide](https://developer.android.com/identity/data/testingbackup),
uses the local encrypted backup transport, and uses only the ignored Gradle APK output:

1. Build and install `app/build/outputs/apk/debug/app-debug.apk`. Record the current transport from
   `adb shell bmgr list transports` so it can be restored afterward.
2. In the app, configure the disposable server and account. Set a distinctive appearance and active
   environment, pin a disposable resource, customize the bottom tabs, and select a non-default tab.
   Load server content and type—but do not submit—a harmless operation form.
3. Run:

   ```sh
   adb shell bmgr enable true
   adb shell bmgr transport com.android.localtransport/.LocalTransport
   adb shell settings put secure backup_local_transport_parameters is_encrypted=true
   adb shell bmgr backupnow app.getarcane.android
   ```

   Require `Package app.getarcane.android with result: Success` before continuing.
4. On this dedicated emulator only, uninstall the emulator user's package and reinstall from the
   existing build output so Auto Restore runs:

   ```sh
   adb shell pm uninstall --user 0 app.getarcane.android
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

5. Launch the app. It must require server setup and sign-in; no server URL, credential/session,
   active environment, theme/accent, pins, server response, or unsent operation content may return.
   Re-enter the disposable server/account and verify that the customized bottom tabs and last valid
   top-level tab are the only restored state.
6. Restore the transport recorded in step 1 and clear local transport test configuration:

   ```sh
   adb shell bmgr transport <recorded-transport-component>
   adb shell settings delete secure backup_local_transport_parameters
   ```

   Wipe the emulator when finished. Do not copy its backup data or app-private files into the
   checkout.

For Android 12+ device-transfer coverage, repeat the same assertions with a factory-reset second
emulator through the setup wizard. The official guide also documents the Google Play services D2D
test transport for a single disposable emulator: enable `backup_enable_d2d_test_mode`, select and
initialize `com.google.android.gms/.backup.migrate.service.D2dTransport`, require a successful
`bmgr backupnow`, uninstall/reinstall as above, then initialize the D2D transport again, set test
mode back to `0`, and restore the recorded transport. Wipe the emulator afterward. Cloud and D2D
results must be recorded separately because Android 12+ applies separate sections even though
Arcane intentionally gives them the same allowlist.
