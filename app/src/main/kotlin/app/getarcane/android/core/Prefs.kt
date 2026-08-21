package app.getarcane.android.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "arcane_prefs")

/** App preferences (server URL, accent, active environment). Mirrors iOS UserDefaults usage. */
class Prefs(context: Context) {
    private val store = context.applicationContext.dataStore

    val serverUrl: Flow<String?> = store.data.map { it[SERVER_URL] }
    val accentHex: Flow<String?> = store.data.map { it[ACCENT_HEX] }
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
