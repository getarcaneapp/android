package app.getarcane.android.nav

/** Pure selection guard for the authenticated tab shell. */
internal object MainTabSelection {
    const val SETTINGS_ID = "settings"

    fun restore(
        storedTabId: String?,
        visibleTabs: List<AppTab>,
        isAdmin: Boolean,
        supportsV2: Boolean,
    ): String = normalize(
        selectedTabId = storedTabId ?: AppTab.Dashboard.id,
        visibleTabs = visibleTabs,
        isAdmin = isAdmin,
        supportsV2 = supportsV2,
    )

    fun normalize(
        selectedTabId: String,
        visibleTabs: List<AppTab>,
        isAdmin: Boolean,
        supportsV2: Boolean,
    ): String = if (isSelectable(selectedTabId, isAdmin, supportsV2)) {
        selectedTabId
    } else {
        visibleTabs.firstOrNull()?.id ?: AppTab.Dashboard.id
    }

    fun shouldPopToRootOnTap(selectedTabId: String, tappedTabId: String): Boolean =
        selectedTabId == tappedTabId

    fun bottomBarSelectedTabId(
        selectedTabId: String,
        hostedResourceTabId: String?,
        visibleTabs: List<AppTab>,
    ): String {
        val hostedTab = hostedResourceTabId?.let(AppTab::byId)
        return if (hostedTab != null && hostedTab in visibleTabs) {
            hostedTab.id
        } else {
            selectedTabId
        }
    }

    private fun isSelectable(
        tabId: String,
        isAdmin: Boolean,
        supportsV2: Boolean,
    ): Boolean {
        if (tabId == SETTINGS_ID) return true
        val tab = AppTab.byId(tabId) ?: return false
        return (!tab.requiresAdmin || isAdmin) && (!tab.requiresV2 || supportsV2)
    }
}
