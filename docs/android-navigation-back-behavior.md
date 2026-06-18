# Android navigation and Back behavior

Implementation note for Arcane Android system navigation, especially the hardware/software Back button. This is based on the current app structure: `ArcaneApp` routes by auth state, `MainTabView` owns top-level bottom tabs, and most resource/settings areas own nested `NavHost` stacks.

## Global rules

- The Android Back button must always resolve the most local transient UI first: open dropdown/menu -> modal sheet/dialog -> nested screen stack -> top-level tab fallback -> app background/exit.
- Toolbar Up/Close buttons and Android Back should be equivalent for the same destination. If a screen has `onBack`, `onClose`, or `onCancel`, system Back should call the same logical path.
- Resource tabs should never become app-exit dead ends. If a resource list/detail is reachable from Dashboard or Settings, Back should return to the previous in-app context before the Activity is allowed to finish.
- Destructive or long-running operations must not be triggered by Back. Back only cancels/dismisses UI or returns to prior destinations; it must not confirm stop/restart/delete/prune/deploy actions.
- In-progress async operations may continue only when the operation is already committed server-side. Closing a progress/log screen should leave the operation running and return to the previous screen; copy should make that clear when needed.

## Top-level authenticated shell (`MainTabView`)

- Top-level tabs: Dashboard, the four user-configurable visible tabs, and Settings.
- Back from a nested screen inside the selected tab pops that tab's nested stack first.
- Back from the root of any non-Dashboard top-level tab should switch to Dashboard instead of immediately exiting the app.
- Back from Dashboard root should background/finish the Activity according to normal Android behavior. Do not show a custom "press again to exit" prompt unless user testing shows accidental exits are common.
- Back should preserve the selected tab and each tab's nested stack while the app remains alive. Switching tabs should not clear another tab's stack unless the user logs out, changes server, or permissions make the destination unavailable.
- If the currently selected tab becomes unavailable after a capability/admin/permission change, fall back to the first visible tab or Dashboard and clear invalid nested destinations.

## Nested resource stacks

Apply the same stack rule to each tab-specific `NavHost`: Back pops one route at a time until the tab root list. Toolbar Up should do the same.

- Containers: `list -> detail -> logs | terminal | inspect`. Back from logs/terminal/inspect returns to container detail; Back from detail returns to container list; Back from list follows the top-level rule.
- Projects: `list -> detail -> logs | compose | streaming action`, plus `create`, `archived`, and `templates`. Back from logs/compose/streaming returns to project detail; Back from create/archived/templates returns to project list. If a streaming action is still running, Back leaves the action screen only after an explicit dismiss/cancel affordance or a confirmation that the command continues in the background.
- Images: `list -> detail -> image vulnerabilities`, plus list-level `updates` and `all vulnerabilities`. Back from child screens returns to the previous image context, then list.
- Volumes: `list -> detail -> browser | backups`. Back from browser/backups returns to volume detail; Back from detail returns to list.
- Networks: `list -> detail -> topology`. Back from topology returns to network detail; Back from detail returns to list.
- Ports, Events, Jobs, Activities, Updates, GitOps, Git Repositories, Swarm, Environments: use list/root -> detail/subscreen semantics where present. If a destination is currently a single-screen tab, Back at root follows the top-level rule.

## Settings hierarchy

- Settings root behaves as a top-level tab root. Back from Settings root switches to Dashboard.
- Back from Settings child routes (`App Settings`, `Appearance`, `What's New`, Users detail/roles, API Keys, RBAC roles, notification provider forms, system category, upgrade, registries, authentication, builds, etc.) pops to the previous Settings route.
- Settings routes that embed resource tabs (for example opening Containers from Settings) must still respect that embedded tab's own nested stack first, then return to Settings root, then top-level fallback.
- Logout confirmation: Back dismisses the confirmation dialog and does not log out. Actual logout only happens through the explicit Sign Out confirmation action.

## Modals, sheets, menus, dialogs

- Back dismisses the topmost modal/sheet/dialog/menu first and does not also pop the underlying navigation stack.
- `TabSwapSheet`: Back dismisses the sheet without changing tabs; selecting a replacement applies the swap and closes the sheet.
- Dashboard activity dialog: Back closes the dialog and returns to Dashboard.
- Prune / cleanup full-screen dialog: Back should dismiss only when not submitting. While submitting, disable Back or require explicit cancellation if cancellation is supported.
- API key creation and new-key display dialogs: Back from create dismisses the form using the same logic as Cancel; Back from the one-time key display closes it only after the user has a visible copy/acknowledge path.
- Dropdown menus in forms and settings close on Back before the containing form receives Back.

## Forms and unsaved changes

Use a common dirty-state policy for create/edit screens and full-screen dialogs.

- If the form is clean, Back is equivalent to Cancel/Close and returns to the previous route.
- If the form is dirty and not submitted, Back opens a discard confirmation: Stay / Discard changes. Discard pops/dismisses; Stay leaves the user on the form.
- If validation errors are showing, Back follows the same dirty-state rule; errors do not trap the user.
- If submit is in progress, Back should be disabled or ask whether to leave while the request continues, depending on whether the API call can be canceled safely. Never silently lose an in-flight create/update without clear feedback.
- Screens requiring this policy include project creation, role create/edit, notification provider forms, API key creation, user creation/edit/role assignment, system/build/authentication settings, registry forms, Git repository forms, image pull/upload/prune sheets, rename container sheets, and any future Compose/edit screens.

## Authentication, setup, and onboarding

- `AUTHENTICATING` loading state: Back may finish/background the Activity; do not mutate auth state.
- Login/setup server URL step: Back from the first screen backgrounds/finishes the app. It should not clear the typed server URL unless the Activity is destroyed normally.
- Login password form or OIDC step after server discovery: Back returns to the prior server URL/setup step and preserves entered server URL. If username/password fields are dirty, show the discard confirmation before clearing them.
- OIDC browser/custom tab: system Back follows browser/custom-tab behavior first. Returning to Arcane after cancellation should leave the app on Login with a cancellable error state, not close the app unexpectedly.
- After successful login, clear auth/onboarding back history so Back from Dashboard does not return to Login.
- Logout clears authenticated nested stacks and returns to Login/Setup. Back from Login after logout exits/backgrounds; it must not reopen authenticated screens.

## Environment changes and state invalidation

- Switching the active environment should preserve the selected top-level tab when the tab remains valid, but reset environment-scoped nested stacks to their list/root destinations if the current detail id may no longer exist.
- Non-environment-scoped Settings/App Settings stacks may remain intact across environment changes unless they depend on the old environment.
- If a server capability or permission change removes the current route, replace it with the nearest valid parent route and avoid leaving Back to expose unauthorized or unavailable UI.

## App exit/background behavior

- Root Dashboard: allow the Activity to finish/background via default Android Back.
- Root non-Dashboard tab: Back goes to Dashboard first.
- Auth root/login first step: Back finishes/backgrounds the Activity.
- Never call logout, clear credentials, stop polling permanently, or cancel server-side operations solely because the Activity backgrounds from Back.

## Implementation checklist

- Add a root navigation/back coordinator rather than relying only on independent nested `NavHost`s. It should know selected top-level tab, each tab's route depth, and whether transient UI or dirty forms are active.
- Add `BackHandler` only at the smallest owner that can handle the event; do not let multiple handlers fire for one Back press.
- Ensure every explicit toolbar Up/Close/Cancel path has a matching system Back path.
- Add tests for: nested resource Back pop, non-Dashboard root -> Dashboard, Dashboard root -> Activity finish/default, modal/sheet dismissal before route pop, dirty-form discard confirmation, login step Back, and logout clearing authenticated history.
- Prefer pure state/navigation tests for route/back decisions plus focused Compose tests for dialog/sheet/form Back handling.
