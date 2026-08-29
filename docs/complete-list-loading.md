# Complete-list loading

Arcane list endpoints default to a finite page, usually 20 items. Android callers that present a
complete catalog or calculate a fleet-wide result must not rely on that default or on an arbitrary
large limit.

At the Android, Kotlin SDK, and Arcane revisions recorded in PAR-004, Arcane documents `limit=-1`
as its finite show-all request. `CompleteListLoader` centralizes that request shape and validates the
single response before publishing it. It preserves server order, de-duplicates stable identities,
accepts totals that count either raw rows or unique identities, and rejects unsuccessful or
count-mismatched responses atomically. Loaders do not catch cancellation.

Offset traversal is deliberately not the fallback. Several operational collections can change
during a load and do not expose a unique supported sort key, so multiple offset requests could skip
or duplicate resources while still appearing complete. A caller must remain explicitly paged when
the endpoint does not support show-all semantics.

## Checked caller inventory

| Classification | Callers | Result |
| --- | --- | --- |
| Fixed: environments | Environment list; dashboard overview fallback; Updates; all-environment image updates; Activity Center fan-out; OIDC role-mapping environment selector; user role-assignment environment selector | All use the shared show-all request and identity/count validation. |
| Fixed: dashboard | Pinned containers, projects, and volumes; fleet volume/update totals; failed-activity badge and attention count; legacy fleet totals | Pins load complete resource collections. Fleet totals fail atomically instead of displaying partial sums. The update total uses action items from each supported per-environment dashboard snapshot (impacted standalone containers plus projects), not the raw image-update count. Failed activities use the filtered pagination total rather than the size of a bounded page. |
| Fixed: updates and images | Image list; per-environment image updates; all-environment image-update buckets | Every local filter and update-ref lookup receives the complete image collection. |
| Fixed: operational resources | Networks; volumes; volume backups; ports | Each non-paged list uses show-all loading with stable identity de-duplication and count validation. |
| Fixed: administration | API keys; users; roles; container registries; webhook container/project selectors | Arbitrary 100/500-item caps were removed. |
| Fixed: GitOps | Git repositories; per-environment GitOps syncs | Arbitrary 100-item caps were removed. |
| Intentionally paged | Active projects; archived projects; events; all-vulnerability results; per-image vulnerability results; Activity Center history; updater history | These screens expose load-more behavior or grow their requested window and do not claim that the current page is a complete total. |
| Intentionally bounded | Activity detail messages (500); Activity Center stream seed (one page); updater-run history probe (5); dashboard failed-activity page (1) | These calls support a detail tail, live seed, or identity/count probe. Displayed complete counts come from pagination metadata, not page length. |
| Endpoint-defined complete | Jobs; webhooks; notification settings; OIDC mappings; template `listAll`; template registries | These SDK operations are non-paginated or use a dedicated all-results endpoint. |

When adding a list caller, classify it in this inventory. A complete caller should use
`loadCompletePaginatedCollection`, or adapt a custom SDK envelope through `CompleteListResponse` and
`loadCompleteCollection`. An intentionally paged or bounded caller must avoid copy or counters that
imply the loaded slice is the full collection.
