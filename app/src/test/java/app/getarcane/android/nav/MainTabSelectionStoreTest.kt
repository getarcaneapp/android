package app.getarcane.android.nav

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainTabSelectionStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun clearRemovesStoredSelectionWithoutClearingPinnedTabs() = runBlocking {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temp.root, "arcane_tabs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        val pinnedKey = stringPreferencesKey("pinned")
        val pinnedTabs = listOf(AppTab.Images, AppTab.Projects, AppTab.Volumes, AppTab.Networks)
            .joinToString(",") { it.id }

        dataStore.edit {
            it[lastSelectedMainTabKey] = AppTab.Images.id
            it[pinnedKey] = pinnedTabs
        }

        MainTabSelectionStore(dataStore, this).clear()

        val saved = dataStore.data.first()
        assertNull(saved[lastSelectedMainTabKey])
        assertEquals(pinnedTabs, saved[pinnedKey])

        dataStoreScope.cancel()
    }
}
