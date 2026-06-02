package app.getarcane.android.ui.screens.whatsnew

import androidx.compose.ui.graphics.Color
import app.getarcane.android.ui.theme.ArcanePurple

/** A badge shown next to a release-note bullet. Mirrors iOS `ReleaseNote.Badge`. */
enum class ReleaseBadge(val label: String, val color: Color) {
    Premium("Premium", ArcanePurple),
}

/** A single release-note bullet, with an optional badge. Mirrors iOS `ReleaseNote.Bullet`. */
data class Bullet(val text: String, val badge: ReleaseBadge? = null)

/** One app version's changelog entry. Mirrors iOS `ReleaseNote`. */
data class ReleaseNote(
    val version: String,
    val new: List<Bullet> = emptyList(),
    val changed: List<Bullet> = emptyList(),
    val fixed: List<Bullet> = emptyList(),
)

/**
 * Hardcoded changelog, ported 1:1 from the iOS app's `ReleaseNotes.all`. The first entry is the
 * latest release.
 */
object ReleaseNotes {
    val all: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "0.2.1",
            changed = listOf(
                Bullet("Server addresses entered without http:// or https:// now default to https://, and the login screen reminds you to include http:// when connecting to a local server."),
            ),
            fixed = listOf(
                Bullet("You can now sign in to a local, HTTP-only Arcane server on your network — for example http://192.168.1.50:3000. iOS was silently blocking these plain-HTTP connections, so login failed with a generic connection error."),
                Bullet("Connection problems on the login screen now explain what actually went wrong — can't reach the server, server not found, timed out, or a secure-connection issue — instead of showing a raw system message."),
                Bullet("The Dashboard's Containers and Images tiles showed '—' instead of counts on Arcane 2.0 servers. They now read live per-environment data — the same source the environment cards use — so they populate regardless of server version, and a genuine zero shows as '0' instead of a dash."),
            ),
        ),
        ReleaseNote(
            version = "0.2.0",
            new = listOf(
                Bullet("First Public Testflight Release!"),
            ),
        ),
        ReleaseNote(
            version = "0.1.9",
            changed = listOf(
                Bullet("Searching and filtering large resource lists (Containers, Images, Networks, Projects, Volumes) is snappier — results are now computed once when you stop typing or change the sort or filter, instead of re-sorting on every keystroke."),
                Bullet("The dashboard now appears as soon as your environment cards are ready instead of waiting on the cross-environment Volumes and Updates totals, which fill in their tiles a moment later."),
                Bullet("Resource icons stop loading the moment you scroll past them, so fast scrolling through long lists uses less CPU and data."),
                Bullet("Loading skeletons now use a single synchronized shimmer that stays contained to each placeholder — no glow bleed onto neighboring content — and honor the system Reduce Motion setting."),
                Bullet("Swarm management is temporarily a placeholder while the screen is reworked. The tab stays in the navigation; the cluster, services, and nodes screens will return in a future update."),
            ),
        ),
        ReleaseNote(
            version = "0.1.8",
            fixed = listOf(
                Bullet("Project compose files and env file do not show up when requested."),
            ),
        ),
        ReleaseNote(
            version = "0.1.7",
            new = listOf(
                Bullet("New Roles screen for Arcane 2.0 servers: browse built-in roles, create and edit custom roles, and pick permissions from a searchable, grouped picker. Pinnable as a bottom tab or reachable from Settings → Administration."),
                Bullet("New OIDC Role Mappings screen for Arcane 2.0 servers: map an SSO claim value to a role and an optional environment scope. Mappings declared via the OIDC_ROLE_MAPPINGS env var are shown read-only with a lock badge."),
                Bullet("New Edit Role Assignments screen on every user's detail page (Arcane 2.0 servers): see a user's assignments grouped by scope (Global, then per-environment), add new assignments with a role + scope picker, swipe to remove manual assignments. OIDC-sourced assignments are shown but can't be changed from the app."),
                Bullet("Tabs that only make sense on Arcane 2.0 (Roles, OIDC Role Mappings) are automatically hidden when you connect to an older server, and reappear when you connect to a 2.0 one."),
            ),
            changed = listOf(
                Bullet("Updated to work with Arcane 2.0's new role-based access control while still supporting older servers transparently. Existing admin users keep admin access after the server upgrade (Arcane 2.0 backfills them into the built-in Admin role)."),
                Bullet("Creating or editing a user on Arcane 2.0 servers now manages the admin role through the new role-assignment endpoint behind the scenes. The Administrator toggle still works the same way; for finer control, use the new Edit Role Assignments screen on the user's detail page."),
            ),
            fixed = listOf(
                Bullet("Permission picker now expands one resource group at a time instead of opening every group together."),
                Bullet("Permission picker search field is now the standard iOS search bar at the top of the screen instead of a custom field inside the form."),
            ),
        ),
        ReleaseNote(
            version = "0.1.6",
            changed = listOf(
                Bullet("Removed the Apprise section from Notifications. Apprise support has been dropped in Arcane 2.0."),
                Bullet("Admin badge in the Users list is now a solid indigo capsule with white text — better contrast and a less alarming color than the previous orange-on-orange."),
                Bullet("Tapping a row in any resource list (Containers, Images, Networks, Volumes, Projects) — or an environment card on the Dashboard — now zooms into the detail view instead of pushing flat from the right."),
                Bullet("Resource lists now show shimmering skeleton rows on first load instead of a centered 'Loading…' spinner, and the Dashboard's first-load skeleton has the same subtle shimmer."),
                Bullet("Paginated lists (Images, Networks, Volumes, Projects) auto-load the next page as you scroll, with a skeleton row in place while the next page fetches, instead of requiring you to tap 'Load More'."),
                Bullet("Search results in long lists settle 200 ms after you stop typing instead of re-filtering on every keystroke."),
                Bullet("Resource lists animate row reflow when you change the sort order or apply a filter."),
                Bullet("Empty states for all five resource lists now offer a primary action — Create for Networks/Volumes/Projects, Pull Image for Images, Refresh for Containers — instead of a dead end."),
                Bullet("Container detail tabs (Overview, Stats, Logs) slide between sections instead of snapping."),
                Bullet("The status dot on a running container's detail screen pulses subtly so it's clear the container is live."),
                Bullet("Start, Stop, Restart, and Redeploy buttons cross-fade smoothly into the in-flight spinner while an action runs."),
                Bullet("Dashboard counts and the CPU/Memory/Disk gauges on environment cards now roll between values instead of popping."),
                Bullet("Dashboard, Container, Project, and Environment detail screens now use iOS 26's soft scroll-edge effect so the toolbar fades naturally into the scrolling content."),
                Bullet("Logs view: the Live/Paused button icon morphs between states, pausing shows a floating 'N new' pill at the bottom that resumes live tailing and jumps to the latest line, and new log lines fade in gently instead of popping."),
                Bullet("The Arcane logo on the login screen bounces in with a spring on appear instead of just popping into place."),
                Bullet("Tab swap hint banner fades out smoothly when dismissed or when you discover the long-press feature, instead of popping."),
                Bullet("Dashboard tiles and mini-metric cards now read out as a single VoiceOver element with the metric name and value combined, instead of as a stack of separate icons and numbers."),
                Bullet("Action toolbar button labels now scale with the system Text Size setting."),
                Bullet("All new motion respects the system Reduce Motion accessibility setting."),
            ),
        ),
        ReleaseNote(
            version = "0.1.5",
            new = listOf(
                Bullet("Redesigned login screen with a refined hero, glass-effect form card, and a persistent 'Try the demo' card that's always available — no need to wipe your server config to spin up a demo."),
            ),
            changed = listOf(
                Bullet("Login screen and demo banner now use your selected accent color from Settings."),
                Bullet("Starting a demo now hides the rest of the login form so the spinner is the focus."),
                Bullet("Removed the redundant Cancel button from the Change Server flow."),
                Bullet("Removed the 'Welcome back' subtitle on the login screen for a cleaner hero."),
            ),
            fixed = listOf(
                Bullet("'End' button in the demo banner now sends you back to the login screen immediately instead of waiting on background cleanup."),
                Bullet("Server URL, Username, and Password placeholders no longer pick up URL-style link coloring."),
                Bullet("'End' button color now matches your selected accent color instead of the system default."),
                Bullet("Appearance settings swatch selection is now derived from the stored accent color, so the checkmark always matches the actual color the app is using."),
                Bullet("Tab bar labels for long titles (Container Registries, Template Registries, Git Repositories, System Settings, Authentication) now use compact names so they no longer wrap or clip."),
            ),
        ),
        ReleaseNote(
            version = "0.1.4",
            fixed = listOf(
                Bullet("Fix an issue where the ImageList logic was not parsed correctly"),
            ),
        ),
        ReleaseNote(
            version = "0.1.3",
            new = listOf(
                Bullet("New cross-environment Updates screen: per-environment summary cards with totals, per-image update rows, and a 'Recheck all images' action for each environment."),
            ),
            changed = listOf(
                Bullet("Refactored the app to use the shared Arcane Swift SDK directly for API models and services, improving consistency with the backend."),
            ),
        ),
        ReleaseNote(
            version = "0.1.2",
            new = listOf(
                Bullet("Dashboard now shows an Updates tile with the total count of pending image updates across all environments. Tap to jump into the Updates tab."),
                Bullet("New floating Liquid Glass action bar on Project, Container, Environment, and Updates detail screens — primary actions (Stop, Restart, Redeploy, etc.) live in circular glass buttons above the tab bar."),
            ),
            changed = listOf(
                Bullet("Dashboard now loads environment cards lazily as you scroll and shows at most 50 environments at a time, with a link to view the full list."),
                Bullet("Replaced the Environments overview tile with the new Updates tile — the per-environment cards below already convey online/total counts."),
                Bullet("Container detail tabs are now Overview, Stats, and Logs (replacing the Inspect tab). Inspect moved to a toolbar button; Terminal also lives in the toolbar when the container is running."),
            ),
            fixed = listOf(
                Bullet("Error messages now show human-readable text instead of raw API responses or schema URLs"),
                Bullet("Error banners wrap to multiple lines so long messages are fully readable"),
                Bullet("Validation errors point to the specific field that needs attention"),
                Bullet("Fixed a security issue where a malicious or compromised server could degrade or crash the app by returning an excessive number of environments."),
                Bullet("Fixed a security issue where a malicious or compromised server could crash the app at launch by returning duplicate keys in the public OIDC settings response."),
            ),
        ),
        ReleaseNote(
            version = "0.1.1",
            new = listOf(
                Bullet("Redesigned dashboard with per-environment summary cards showing live CPU, memory, disk, and container/image counts"),
                Bullet("Long-press an environment card to set it as the active context or jump into system details"),
                Bullet("Volume totals now aggregate across all environments on the dashboard"),
                Bullet("Skeleton loading state on first dashboard load"),
            ),
            changed = listOf(
                Bullet("Overall Design fixes between iOS 18 and iOS 26"),
            ),
            fixed = listOf(
                Bullet("Fixed a security issue where crafted icon URLs could leak authentication headers to external servers."),
            ),
        ),
        ReleaseNote(
            version = "0.1.0",
            new = listOf(
                Bullet("Initial Arcane Mobile Beta release"),
                Bullet("Customizable bottom tab bar — long-press any tab to swap"),
                Bullet("Pin containers, projects, and resources to keep them at the top"),
                Bullet("Archived projects collapse into their own section"),
                Bullet("Redesigned Updater Status and Updater History screens"),
                Bullet("Redesigned Events list with severity filtering"),
                Bullet("Ports grouped by container"),
                Bullet("Show More pagination on Events and Updater History"),
                Bullet("Reset to defaults from the tab customization sheet"),
            ),
            changed = listOf(
                Bullet("Reorganized navigation to match the web app (Management / Resources / Swarm / Administration)"),
                Bullet("Tab customization sheet now uses a tile grid and opens to full height"),
                Bullet("Smoother scrolling on the dashboard, container stats, and tab picker"),
                Bullet("Removed OIDC sign-in from the login screen"),
                Bullet("Removed Build Workspace (build settings still available to admins)"),
            ),
            fixed = listOf(
                Bullet("Container and project icons no longer crop wider artwork"),
                Bullet("Events sort newest-first"),
                Bullet("GitOps icon now renders"),
                Bullet("Reduced memory use on long image lists"),
            ),
        ),
    )

    val latest: ReleaseNote? get() = all.firstOrNull()
}
