package app.getarcane.android.nav

/**
 * Root-level Android Back decisions for the authenticated tab shell.
 *
 * Nested NavHosts, dialogs, sheets, and dropdowns should consume Back before this root fallback.
 * This coordinator only decides what to do once the selected top-level destination is already at
 * its root. Keeping the decision pure makes the Android Activity fallback behavior testable without
 * coupling tests to Compose's BackHandler dispatcher.
 */
internal object MainBackNavigation {
    enum class Action {
        SwitchToDashboard,
        LetActivityHandle,
    }

    fun resolve(selectedTabId: String): Action =
        if (selectedTabId == AppTab.Dashboard.id) {
            Action.LetActivityHandle
        } else {
            Action.SwitchToDashboard
        }
}
