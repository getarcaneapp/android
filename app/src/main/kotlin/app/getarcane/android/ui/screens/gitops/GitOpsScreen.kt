package app.getarcane.android.ui.screens.gitops

import androidx.compose.runtime.Composable

/**
 * GitOps tab. Mirrors the iOS `GitOpsSyncsView` (env-scoped GitOps sync configurations): a list with
 * per-row "Sync Now" / "Delete" actions, a create sheet, and a details view. Scoped to the active
 * environment via `client.gitops` + `manager.activeEnvironmentId`.
 */
@Composable
fun GitOpsScreen() {
    GitOpsSyncsScreen()
}
