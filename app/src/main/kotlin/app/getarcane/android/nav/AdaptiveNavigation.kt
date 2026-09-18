package app.getarcane.android.nav

/** Canonical Android adaptive width classes used by the authenticated shell and list-detail UI. */
internal enum class AdaptiveWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal object AdaptiveNavigation {
    const val MEDIUM_MIN_WIDTH_DP = 600
    const val EXPANDED_MIN_WIDTH_DP = 840

    fun widthClass(widthDp: Int): AdaptiveWidthClass = when {
        widthDp >= EXPANDED_MIN_WIDTH_DP -> AdaptiveWidthClass.EXPANDED
        widthDp >= MEDIUM_MIN_WIDTH_DP -> AdaptiveWidthClass.MEDIUM
        else -> AdaptiveWidthClass.COMPACT
    }

    fun availableTabs(
        isAdmin: Boolean,
        supportsV2: Boolean,
        canReadVariables: Boolean,
        canAccess: ((AppTab) -> Boolean)? = null,
    ): List<AppTab> = AppTab.entries.filter { tab ->
        canAccess?.invoke(tab) ?: (
            (!tab.requiresAdmin || isAdmin) &&
                (!tab.requiresV2 || supportsV2) &&
                (tab != AppTab.Variables || canReadVariables)
            )
    }

    /** Non-pinnable destinations retain the complete nested flows already owned by Settings. */
    fun usesSettingsHost(tab: AppTab): Boolean = !tab.canPinToBottomBar
}
