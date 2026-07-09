package app.getarcane.android.nav

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.navTabsDataStore: DataStore<Preferences> by preferencesDataStore(name = "arcane_tabs")
internal val lastSelectedMainTabKey = stringPreferencesKey("last_selected")

/** Persists the 4 swappable bottom-nav tabs (Settings is always the 5th). Port of iOS `NavTabsStore`. */
class NavTabsStore(context: Context) {
    private val store = context.applicationContext.navTabsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val key = stringPreferencesKey("pinned")

    var pinned by mutableStateOf(AppTab.defaults); private set

    init {
        scope.launch {
            val saved = store.data.map { it[key] }.first()
            val tabs = saved?.split(",")?.mapNotNull { AppTab.byId(it) }
            if (tabs != null && tabs.size == SLOTS) pinned = tabs
        }
    }

    /** The [SLOTS] tabs to show, filtered by current availability and padded with defaults. */
    fun visibleTabs(isAdmin: Boolean, supportsV2: Boolean): List<AppTab> {
        val normalized = normalizedPinnedBottomTabs(pinned)
        if (normalized != pinned) {
            pinned = normalized
            persist(normalized)
        }
        return visibleBottomTabs(normalized, isAdmin, supportsV2)
    }

    fun swap(slot: Int, replacement: AppTab) {
        val updated = pinned.toMutableList()
        if (slot in updated.indices) {
            updated[slot] = replacement
            pinned = updated
            persist(updated)
        }
    }

    fun resetToDefaults() {
        pinned = AppTab.defaults
        scope.launch { store.edit { it.remove(key) } }
    }

    private fun persist(tabs: List<AppTab>) {
        scope.launch { store.edit { it[key] = tabs.joinToString(",") { tab -> tab.id } } }
    }

    companion object {
        const val SLOTS = 4
    }
}

/** Persists the last selected top-level tab so app relaunch restores where the user left off. */
class MainTabSelectionStore internal constructor(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    constructor(context: Context) : this(
        context.applicationContext.navTabsDataStore,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    var hasLoaded by mutableStateOf(false); private set
    var selectedTabId by mutableStateOf<String?>(null); private set

    init {
        scope.launch {
            try {
                selectedTabId = store.data.map { it[lastSelectedMainTabKey] }.first()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            } finally {
                hasLoaded = true
            }
        }
    }

    fun select(tabId: String) {
        if (selectedTabId == tabId) return
        selectedTabId = tabId
        scope.launch { store.edit { it[lastSelectedMainTabKey] = tabId } }
    }

    suspend fun clear() {
        selectedTabId = null
        store.edit { it.remove(lastSelectedMainTabKey) }
    }
}

internal fun normalizedPinnedBottomTabs(
    pinned: List<AppTab>,
): List<AppTab> {
    val result = pinned
        .filter { it.canPinToBottomBar }
        .distinct()
        .toMutableList()

    for (fallback in AppTab.defaults) {
        if (result.size >= NavTabsStore.SLOTS) break
        if (fallback !in result && fallback.canPinToBottomBar) result.add(fallback)
    }

    return result.take(NavTabsStore.SLOTS)
}

internal fun visibleBottomTabs(
    pinned: List<AppTab>,
    isAdmin: Boolean,
    supportsV2: Boolean,
): List<AppTab> {
    fun allowed(tab: AppTab) = tab.isAvailableForBottomBar(isAdmin, supportsV2)
    val result = pinned
        .filter(::allowed)
        .toMutableList()

    for (fallback in AppTab.defaults) {
        if (result.size >= NavTabsStore.SLOTS) break
        if (fallback !in result && allowed(fallback)) result.add(fallback)
    }

    return result.take(NavTabsStore.SLOTS)
}
