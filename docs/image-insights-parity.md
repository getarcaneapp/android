# Image Insights parity evidence

This records the PAR-105 image-attestation and PAR-112 Docker image-layer-history contract,
product decisions, implementation, and validation completed on 2026-09-11.

## Compared revisions

- Android: `75fde394f61ee8838f201f25b42a3e83af146513` (`origin/main` and branch base), branch
  `parity/image-insights`
- iOS: `6088fcc0ef04dc906ce74e9129dffa96894a6da5`
- libarcane-swift: `facc40e20e32b7d6600b004fd744a214bbd2a166`
- libarcane-kotlin: `275e7a533bd5f68063d3e275012041d0f846e251`, branch
  `parity/image-layer-history-contract`, PR #9
- Arcane: `5df09ed475c4bac4ab6f54e1b9bdde1ac1c55105`; live current target Arcane 2.10.2
  image digest `sha256:62d8001c3568e03acf66b53d4bdd97fcca59ae9e43f1561d8f720f38b738ffbc`

All repositories were fetched before implementation. The handed-off Android, iOS, Kotlin SDK, and
Arcane pins matched current `origin/main`; the Kotlin pin above is the resulting feature commit.
Current iOS is the mobile outcome authority. Arcane handlers/types are the wire authority, and the
Swift SDK was inspected where it clarified selection and statement-fetch semantics.

## Contract and presentation inventory

| Concern | Contract and Android outcome |
| --- | --- |
| Navigation | Image detail has separate **Attestations** and **Docker layer history** rows. Each opens an Android-native destination and Back returns to the same image detail. |
| Identity | Requests and rendered headers carry normalized server/user session, environment ID/name, and immutable image ID/digest. Route arguments are encoded. Results publish only while that identity remains current. |
| Permission | Both Arcane handlers require `images:read`. Android keeps the destinations visible for discoverability and renders a scoped Permission Required state when access is denied. |
| Compatibility | Arcane 2.2 and later expose both routes. A route-level 404 is rendered as Not Supported; 401/403 as Permission Required; decode failures as malformed data; other failures retain Retry. No app-local version guess is used. |
| Attestation summary | Existing SDK fields are used directly: image reference, subject digest, registry platform, descriptor digest/media/artifact/predicate/statement types, descriptor platform, size, and optional subjects/statement. Unknown values are shown, not rejected. |
| Attestation filter | Predicate type is the stable filter key. Known types receive concise labels; unknown URIs get a useful derived label while the complete value remains in detail. A disappeared selection produces an explicit filtered-empty state. |
| Attestation detail | Selection matches immutable descriptor digest plus predicate type and platform. Full metadata and optional subjects are shown. The raw in-toto statement is fetched on demand, explicitly labeled unverified, rendered with a bounded 32 KiB preview, and copied/exported in full. |
| Layer history | `GET /environments/{id}/images/{name}/history` returns Docker history entries with optional layer ID, created Unix seconds, command, tags, size, and comment. Null, absent, and future fields are defensive; missing IDs display as metadata-only layers. |
| Ordering/format | Server order is preserved (Docker newest-first). Android uses existing byte/date formatters, monospace commands, visible tags/comments, and exact image/environment context. It never calls this Arcane image-build or updater history. |
| Lifecycle | Structured cancellation stops abandoned loads. Refresh replaces the owned job; stale success and failure are rejected after image, environment, server, or account change. No raw Android HTTP client or duplicate DTO was added. |
| Native differences | iOS uses tabs and native share surfaces. Android uses detail rows, Compose destinations/bottom sheet, clipboard, and Storage Access Framework. These are presentation differences; fields and outcomes remain equivalent. |

An attestation's existence is not evidence that it is trusted or cryptographically verified. Android
states this before raw statement actions.

## Automated verification

The SDK focused contract test passed two tests. The required SDK command
`./gradlew :arcane-core:test :arcane-android:assembleRelease` passed 104 tests with zero failures or
errors and one skipped (103 passed), and release assembly. SDK PR #9 GitHub CI passed.

The Android focused Image Insights model suite passed nine tests. The Android gate
`./gradlew -Parcane.remoteSdk :app:testDebugUnitTest :app:assembleDebug` passed 303 tests with zero
failures, errors, or skips, and debug assembly. The flag intentionally exercises the remote source
dependency because SDK PR #9 is not merged; the Android branch explicitly resolves that SDK source
branch and must return to `main` after the SDK merges. Tests cover decoding in the
SDK and, on Android, identity scoping, route round trips, filters, exact selection, stale-result
rejection, cancellation and failure classification, formatting/order, and bounded-preview/full-copy
behavior. `git diff --check` and an independent complete-diff review both passed with no additional
findings after the image/environment-specific delete-confirmation wording was corrected.

## Live AVD and server evidence

The actual debug application ran on `arcane_test_api30` (Android API 30) in the disposable
`arcane-e2e` LXD environment against Docker 29.1.3 and Arcane 2.10.2. An older Arcane
2.1.0 instance, digest `sha256:56ed6dbe71f65eb560ddb2a78ca804ba618c6196d4e982eda2a517db6d2d44ea`, proved unsupported-server
behavior.

Deterministic disposable fixtures included:

- a seven-entry multi-layer image with fixed January 2024 timestamps, recognizable PAR-112
  commands, 12/16 KiB layers, tags, comments, and metadata-only entries;
- a genuine zero-history `FROM scratch` image;
- an unattested image;
- SPDX and SLSA provenance attestations on one immutable image;
- an unknown future predicate with no subjects;
- a malformed 43-byte statement and a temporarily unreachable registry reference;
- a restricted user whose role had image listing but not `images:read`; and
- an unreachable environment used during an in-flight request ownership check.

On-device checks covered detail-to-destination navigation and Back, identity headers, layer order
and formatting, pull refresh, empty history, predicate filtering and details, an unattested empty
state, registry-error Retry recovery, unknown/no-subject presentation, malformed-statement recovery,
permission denial, v2.1.0 unsupported routes, force-stop/reopen, and repeated navigation. Switching
away during a delayed request canceled it; changing to the unreachable environment showed no prior
image content, and switching back restored only current local data. The 110,330-byte pretty raw SPDX
statement remained responsive with a bounded preview, while clipboard and Storage Access Framework
export received all 110,330 bytes with explicit raw/unverified labeling.

The filtered-empty transition and arbitrary stale success/failure races are deterministic model
tests because forcing them through the server would require mutating an attestation index while the
screen is open. Generic network error classification is also covered by tests; live registry failure,
malformed data, authorization, and unsupported routes exercised distinct recovery paths.

## Cleanup evidence

The app, screenshots, UI dumps, exported statements, debug trust/cleartext manifest, temporary AVD
clone, emulator, current/old Arcane test containers, registry, buildx builder, fixture images, network,
restricted user/role, test certificates, Docker trust files, APKs, and temporary SDK/AVD LXD devices
were removed. The original Arcane 2.10.2 container CA bundle and service were restored. Final checks
reported zero fixture containers, images, networks, devices, users, roles, and test CA subjects, with
the restored server reporting v2.10.2. No production security behavior or generated artifact is part
of the branch.
