package app.getarcane.android.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.getarcane.sdk.EnvironmentId

/** Per-(kind, environment) pinned resource IDs. Port of iOS `PinnedItemsStore` (UserDefaults-backed). */
class PinnedItemsStore(context: Context) {
    enum class Kind { CONTAINER, PROJECT, VOLUME }

    private val prefs = context.applicationContext.getSharedPreferences("arcane_pinned", Context.MODE_PRIVATE)

    // Bumping this on every change establishes the Compose read-dependency so lists re-partition.
    var version by mutableIntStateOf(0)
        private set

    fun pinnedIds(kind: Kind, envId: EnvironmentId): Set<String> {
        @Suppress("UNUSED_EXPRESSION") version
        return prefs.getStringSet(key(kind, envId), emptySet())?.toSet() ?: emptySet()
    }

    fun isPinned(id: String, kind: Kind, envId: EnvironmentId): Boolean =
        pinnedIds(kind, envId).contains(id)

    fun togglePin(id: String, kind: Kind, envId: EnvironmentId) {
        val key = key(kind, envId)
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (!current.add(id)) current.remove(id)
        prefs.edit().putStringSet(key, current).apply()
        version++
    }

    fun unpin(id: String, kind: Kind, envId: EnvironmentId) {
        val key = key(kind, envId)
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (current.remove(id)) {
            prefs.edit().putStringSet(key, current).apply()
            version++
        }
    }

    private fun key(kind: Kind, envId: EnvironmentId): String =
        "arcane.pinned.${kind.name.lowercase()}.${envId.rawValue}"
}

val LocalPinnedStore = staticCompositionLocalOf<PinnedItemsStore> { error("PinnedItemsStore not provided") }
