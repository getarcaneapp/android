package app.getarcane.android.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrefsAppearanceTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun persistedValueMappingFallsBackToAuto() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromPersistedValue("light"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromPersistedValue("dark"))
        assertEquals(AppThemeMode.AUTO, AppThemeMode.fromPersistedValue("auto"))
        assertEquals(AppThemeMode.AUTO, AppThemeMode.fromPersistedValue(null))
        assertEquals(AppThemeMode.AUTO, AppThemeMode.fromPersistedValue("sepia"))
    }

    @Test
    fun themeModeResolvesAgainstSystemMode() {
        assertFalse(AppThemeMode.LIGHT.resolvesToDark(systemInDarkTheme = true))
        assertTrue(AppThemeMode.DARK.resolvesToDark(systemInDarkTheme = false))
        assertFalse(AppThemeMode.AUTO.resolvesToDark(systemInDarkTheme = false))
        assertTrue(AppThemeMode.AUTO.resolvesToDark(systemInDarkTheme = true))
    }

    @Test
    fun allThemeModesPersistWithoutChangingAccent() = runBlocking {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temp.root, "arcane_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        val prefs = Prefs(dataStore)

        prefs.setAccentHex("#AF52DE")
        AppThemeMode.entries.forEach { mode ->
            prefs.setThemeMode(mode)

            val anotherPrefsOwner = Prefs(dataStore)
            assertEquals(mode, anotherPrefsOwner.themeMode.first())
            assertEquals(mode.persistedValue, dataStore.data.first()[themeModePreferenceKey])
            assertEquals("#AF52DE", anotherPrefsOwner.accentHex.first())
        }

        dataStoreScope.cancel()
    }

    @Test
    fun invalidStoredModeFallsBackWithoutChangingAccent() = runBlocking {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temp.root, "invalid_arcane_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        val prefs = Prefs(dataStore)

        assertEquals(AppThemeMode.AUTO, prefs.themeMode.first())
        prefs.setAccentHex("#34C759")
        dataStore.edit { it[themeModePreferenceKey] = "invalid" }

        assertEquals(AppThemeMode.AUTO, prefs.themeMode.first())
        assertEquals("#34C759", prefs.accentHex.first())

        dataStoreScope.cancel()
    }

    @Test
    fun appOwnedAppearanceStatePersistsAfterSetterReturns() = runBlocking {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val appearanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temp.root, "owned_arcane_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        val appearancePreferences = AppearancePreferences(Prefs(dataStore), appearanceScope)

        appearancePreferences.setThemeMode(AppThemeMode.LIGHT)
        appearancePreferences.setAccentHex("#5856D6")

        assertEquals(AppThemeMode.LIGHT, appearancePreferences.themeMode.value)
        assertEquals("#5856D6", appearancePreferences.accentHex.value)
        assertEquals(
            AppThemeMode.LIGHT.persistedValue,
            dataStore.data.map { it[themeModePreferenceKey] }.first { it != null },
        )
        assertEquals(AppThemeMode.LIGHT, appearancePreferences.themeMode.first { it == AppThemeMode.LIGHT })
        assertEquals("#5856D6", appearancePreferences.accentHex.first { it == "#5856D6" })

        appearanceScope.cancel()
        dataStoreScope.cancel()
    }
}
