# Current Android app state from screenshots

Date: 2026-06-16
Branch: `feat/android-continuation`

These notes capture observed behavior from user-provided emulator/device screenshots so future work continues from what already runs instead of re-discovering the app state.

## Overall state

The Android app is launching and appears connected to a real Arcane server. It is rendering operational data across multiple top-level resource areas, not just static placeholder screens.

Visible bottom navigation entries:

- Dashboard
- Containers
- Images
- Projects
- Settings

## Containers

### Container detail: Stats tab

Observed screen: container detail for a long container name truncated as `automation-homea...`.

Visible behavior:

- Detail top bar with back navigation, truncated container title, quick-action icons, and overflow menu.
- Segmented tabs: Overview, Stats, Logs.
- Stats tab selected.
- Metric cards render live-looking values:
  - CPU around `1.1%`.
  - Memory around `842.9 MB` and `2.6%`.
  - Network `0 B/s`.
  - Block I/O with read/write rates.
- CPU and memory charts are visible.
- Bottom navigation keeps Containers selected.

Implications:

- Container detail routing is functional.
- Stats/metrics data is wired enough to render useful operational telemetry.
- Long names should keep getting mobile-native handling; current truncation is acceptable but may need a detail/title affordance later.

### Container detail: Logs tab

Observed behavior:

- Logs tab shows real Home Assistant-style log output in a monospace/log viewer.
- There is a floating `Live` pill/button with a play icon.
- Log entries include raw ANSI escape sequences such as `[32m`, `[31m`, and `[0m`.

Follow-up issue:

- Strip ANSI escape sequences or render them as styled spans before displaying logs. Raw terminal color codes make the log viewer harder to read.

## Images

Observed Images screen:

- Top-level title: `Images`.
- Top actions include overflow, pull/download, and delete/trash-style actions.
- Search field: `Search images`.
- Section label: `USED`.
- Image rows render image names, purple circular icons, green status indicators, and `IN USE` status labels.
- Examples visible:
  - `1activegeek/airconnect:latest`
  - `couchdb:3`
  - `freikin/dawarich:latest`
  - `ghcr.io/getarcaneapp/arcane:latest`
  - `ghcr.io/jessielw/reclaimerr:latest`
  - `ghcr.io/krelltunez/lastglance:latest`
  - `ghcr.io/maziggy/bambuddy:latest`
  - `ghcr.io/music-assistant/server:...`
  - `homeassistant/home-assistant:...`
- Bottom navigation keeps Images selected.

Implications:

- Image inventory is API-backed enough to display real entries and status.
- Search and top-level actions are present.
- Future work should preserve the compact list/status style and add careful confirmations for destructive image actions.

## Projects

Observed Projects screen:

- Top-level title: `Projects`.
- Top actions include document/file, plus/add, and overflow menu icons.
- Search field: `Search projects`.
- Section label: `ACTIVE`.
- Project rows render orange icons and green `RUNNING` status badges.
- Examples visible:
  - `ai`
  - `automation`
  - `Backup`
  - `docker`
  - `media`
  - `monitoring`
  - `networking`
  - `personal`
  - `utilities`
- Bottom navigation keeps Projects selected.

Implications:

- Project list data is functional and visually coherent.
- The status badge pattern is readable and should be reused for other operational lists.
- Project actions should continue to use safe confirmation flows before exposing high-impact operations.

## Product direction reinforced by screenshots

- The app is already well beyond a blank MVP: it has working resource navigation and real operational data.
- Immediate continuation should focus on making the existing surfaces reliable and safe rather than rebuilding them.
- High-value fixes from current screenshots:
  1. Ensure Android Studio module/run configuration works from a clean checkout.
  2. Strip or render ANSI color codes in logs.
  3. Verify container stats/logs behavior on slow/offline/server-error paths.
  4. Preserve mobile-native list/detail patterns for Images and Projects.
  5. Keep all dangerous actions behind clear environment/resource-specific confirmations.
