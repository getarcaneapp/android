# Accessibility and localization foundation

Arcane Android treats accessibility information as operational state, not decoration. Resource,
environment, and server names remain user/server data; UI instructions, action labels, state words,
and accessibility descriptions are translatable resources.

## String-resource conventions

- Use a stable, surface-first name such as `auth_sign_in`, `nav_dashboard`,
  `confirm_delete_container_title`, or `operation_status_running`. Shared actions use the
  `action_` prefix and accessibility-only descriptions use `a11y_`.
- Put complete sentences or complete grammatical phrases in one resource. Do not concatenate a
  translated label with punctuation, counts, state words, or another translated fragment.
- Use positional `%1$s`/`%2$d` arguments for formatted text and escape literal percent signs. Add an
  XML translator comment when an argument's meaning is not obvious.
- Use `<plurals>` whenever grammar changes with quantity. Counts exposed only as a visual badge must
  still have a complete singular/plural TalkBack description.
- Keep protocol tokens, image names, usernames, resource names, hostnames, versions, IDs, and
  server-provided error details as data arguments. Do not mark them translatable and do not alter
  their case with the current locale.
- Content descriptions explain purpose or state, not icon appearance. Decorative icons use `null`.
  A visible label and its duplicate icon description must not create two TalkBack stops.
- New user-visible text is resource-backed. A focused change may leave unrelated existing English
  text alone, but must not add to that debt.

The first PAR-401 slice covers authentication, top-level/adaptive navigation and customization,
container/project destructive confirmations, durable-operation status/actions/counts, shared retry,
and accessibility-critical loading/state text. Debug builds generate Android's `en-XA` accented and
`ar-XB` bidirectional pseudolocales. The release artifact does not advertise pseudolocales.

Exercise the migrated flows with:

```sh
adb shell setprop persist.sys.locale en-XA
adb shell settings put system font_scale 2.0
adb reboot
```

Repeat with `ar-XB`, then restore the original locale and `font_scale=1.0`. Verify long expansion,
argument ordering, bidirectional isolation of user/server data, mirrored layout and Back/navigation
placement, dialogs/sheets, and that critical actions remain reachable by scrolling. Locale changes on
modern test images may instead be made through system Settings; the evidence record must state which
method and image were used.

## Accessibility implementation rules

- Every mutation has a descriptive visible label and a minimum 48 dp interaction target. Destructive
  actions include the resource and environment when available, require an explicit confirmation, and
  never use Back/outside dismissal as confirmation.
- Selected navigation/environment state exposes selected or state-description semantics, not color
  alone. Status chips and resource rows retain text or shape/icon differences in light and dark mode.
- Screen/section titles are headings where doing so improves navigation. Lists preserve visual reading
  order; trailing controls are reached after their row identity. Dialog focus remains inside the
  dialog until it is dismissed.
- Loading and determinate progress expose progress semantics. Authentication restoration does not
  announce or flash the login route. Error, stale/offline, operation transition, and completion text
  use polite live regions where repeated interruption would be harmful.
- Material components provide keyboard/D-pad focus by default. Custom canvas/topology content must
  have real focusable semantic nodes and a direct grouped-list fallback; drawn edges are silent.
- Compose animation observes the platform animator-duration setting. Continuous shimmer, pulse,
  progress, and topology feedback must remain understandable at animation scale 0 and must not be the
  only indication of activity or state.
- Haptics are reserved for useful acknowledgement such as an intentional long press. They honor
  platform/user settings and always accompany a visible or spoken result.

## Manual audit matrix

For each representative compact and expanded configuration, check TalkBack swipe exploration,
keyboard Tab/Shift-Tab/Enter/Back or D-pad, touch targets, focus after navigation, 100% and 200% font,
light/dark/automatic theme, portrait/landscape, and animation scale 1x/0x.

| Surface | Required observations |
| --- | --- |
| Authentication | Server/username/password fields have stable labels and appropriate keyboard/security behavior; errors are announced; loading/restoration never exposes the wrong route; browser/MFA return and Back remain predictable. |
| Adaptive navigation | Compact bar, medium rail/More sheet, expanded drawer, tab replacement/reset, selected state, reselect-to-root, and Back order are labeled and keyboard reachable. |
| Dashboard | Fleet totals, Needs Attention, failed-activity badge, pinned rows/actions, environment cards, stale/offline banners, Update All, and widget-authenticated routes do not rely on color or ambiguous unlabeled icons. |
| Containers | List/detail focus, status, stats progress, lifecycle actions, logs/terminal navigation, destructive confirmation, and operation completion announce truthful resource/environment context. |
| Projects and operations | Deploy/build/pull/down/delete confirmation, operation progress/reconnect/failure/completion, cancel/retry/dismiss, Activity navigation, and rotation/process recovery retain one understandable state. |
| Settings and environments | Settings hierarchy, switches, dialogs, credential-sensitive fields, environment selection/actions, sheets, account/admin restrictions, and Back do not trap or reorder focus. |
| Widgets and authenticated routes | Widget state is textual, sizes do not clip critical status, signed-out/stale/unavailable routes are fenced, and launcher/shortcut/notification entry returns through authenticated navigation. |
| Topology | Nodes expose name/type/status/connection count, selection is non-color-only, pan/zoom does not strand focus, details are reachable, and the grouped-list fallback is immediately available. |

Automated Compose tests assert representative route state, selected/button roles, destructive-dialog
behavior, adaptive navigation interaction, and operation status/action semantics. These checks catch
regressions in the semantics tree, but they do not replace spoken-output, traversal, contrast,
font-scale, reduced-motion, switch-access, or real keyboard testing.

## Evidence record

The release-readiness PR must append the actual AVD/device/API, locale, theme, font scale, input
method, TalkBack version, animator scale, passed surfaces, limitations, and any sanitized retained
assets. Do not mark PAR-401 or PAR-402 complete merely because the automated suite passes.
