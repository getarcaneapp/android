package app.getarcane.android.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "arcane_prefs")
internal val themeModePreferenceKey = stringPreferencesKey("theme_mode")

enum class AppThemeMode(val persistedValue: String) {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto"),
    ;

    fun resolvesToDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        AUTO -> systemInDarkTheme
    }

    companion object {
        fun fromPersistedValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: AUTO
    }
}

/** App preferences (server URL, appearance, active environment). Mirrors iOS UserDefaults usage. */
class Prefs internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    val serverUrl: Flow<String?> = store.data.map { it[SERVER_URL] }
    val accentHex: Flow<String?> = store.data.map { it[ACCENT_HEX] }
    val themeMode: Flow<AppThemeMode> = store.data.map {
        AppThemeMode.fromPersistedValue(it[themeModePreferenceKey])
    }
    val activeEnvId: Flow<String?> = store.data.map { it[ACTIVE_ENV_ID] }
    val activeEnvName: Flow<String?> = store.data.map { it[ACTIVE_ENV_NAME] }
    val credentialOrigin: Flow<String?> = store.data.map { it[CREDENTIAL_ORIGIN] }

    suspend fun setServerUrl(value: String) = store.edit {
        if (it[SERVER_URL] != value) {
            it.remove(ACTIVE_ENV_ID)
            it.remove(ACTIVE_ENV_NAME)
        }
        it[SERVER_URL] = value
    }.let {}
    suspend fun setAccentHex(value: String) = store.edit { it[ACCENT_HEX] = value }.let {}
    suspend fun setThemeMode(value: AppThemeMode) = store.edit {
        it[themeModePreferenceKey] = value.persistedValue
    }.let {}
    suspend fun setActiveEnv(id: String, name: String) = store.edit {
        it[ACTIVE_ENV_ID] = id
        it[ACTIVE_ENV_NAME] = name
    }.let {}

    suspend fun setCredentialOrigin(origin: String) = store.edit {
        it[CREDENTIAL_ORIGIN] = origin
    }.let {}

    suspend fun clearCredentialOrigin(origin: String) = store.edit {
        if (it[CREDENTIAL_ORIGIN] == origin) it.remove(CREDENTIAL_ORIGIN)
    }.let {}

    suspend fun clearServerState(expectedServerUrl: String, expectedCredentialOrigin: String?) = store.edit {
        if (it[SERVER_URL] == expectedServerUrl) {
            it.remove(SERVER_URL)
            it.remove(ACTIVE_ENV_ID)
            it.remove(ACTIVE_ENV_NAME)
        }
        if (expectedCredentialOrigin != null && it[CREDENTIAL_ORIGIN] == expectedCredentialOrigin) {
            it.remove(CREDENTIAL_ORIGIN)
        }
    }.let {}

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val ACCENT_HEX = stringPreferencesKey("accent_hex")
        private val ACTIVE_ENV_ID = stringPreferencesKey("active_env_id")
        private val ACTIVE_ENV_NAME = stringPreferencesKey("active_env_name")
        private val CREDENTIAL_ORIGIN = stringPreferencesKey("credential_origin")
    }
}

/**
 * App-owned appearance state. Its scope outlives the Appearance screen, so leaving that screen
 * cannot cancel a pending preference write. State flows also retain the last persisted selection
 * instead of briefly resetting a recreated screen to its default.
 */
class AppearancePreferences internal constructor(
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    private val mutableThemeMode = MutableStateFlow(AppThemeMode.AUTO)
    val themeMode: StateFlow<AppThemeMode> = mutableThemeMode.asStateFlow()

    private val mutableAccentHex = MutableStateFlow("")
    val accentHex: StateFlow<String> = mutableAccentHex.asStateFlow()

    private var themeWriteJob: Job? = null
    private var accentWriteJob: Job? = null

    init {
        scope.launch {
            prefs.themeMode.collect { mutableThemeMode.value = it }
        }
        scope.launch {
            prefs.accentHex.collect { mutableAccentHex.value = it.orEmpty() }
        }
    }

    fun setThemeMode(value: AppThemeMode) {
        mutableThemeMode.value = value
        themeWriteJob?.cancel()
        themeWriteJob = scope.launch {
            try {
                prefs.setThemeMode(value)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (mutableThemeMode.value == value) {
                    mutableThemeMode.value = prefs.themeMode.first()
                }
            }
        }
    }

    fun setAccentHex(value: String) {
        mutableAccentHex.value = value
        accentWriteJob?.cancel()
        accentWriteJob = scope.launch {
            try {
                prefs.setAccentHex(value)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (mutableAccentHex.value == value) {
                    mutableAccentHex.value = prefs.accentHex.first().orEmpty()
                }
            }
        }
    }
}

val LocalAppearancePreferences = staticCompositionLocalOf<AppearancePreferences> {
    error("AppearancePreferences not provided")
}
