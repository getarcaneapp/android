package app.getarcane.android.core

import android.content.Context
import android.content.SharedPreferences
import app.getarcane.sdk.models.project.DeployOptions
import app.getarcane.sdk.models.project.DeployPullPolicy
import java.security.MessageDigest

internal enum class ProjectDeployPullPolicy(val persistedValue: String) {
    MISSING("missing"),
    ALWAYS("always"),
    NEVER("never"),
    ;

    companion object {
        fun fromPersistedValue(value: String?): ProjectDeployPullPolicy =
            entries.firstOrNull { it.persistedValue == value } ?: MISSING
    }
}

internal data class ProjectDeployPreferenceValues(
    val pullPolicy: ProjectDeployPullPolicy = ProjectDeployPullPolicy.MISSING,
    val forceRecreate: Boolean = false,
)

internal fun ProjectDeployPreferenceValues.toSdkDeployOptions(): DeployOptions =
    DeployOptions(
        pullPolicy = when (pullPolicy) {
            ProjectDeployPullPolicy.MISSING -> DeployPullPolicy.MISSING
            ProjectDeployPullPolicy.ALWAYS -> DeployPullPolicy.ALWAYS
            ProjectDeployPullPolicy.NEVER -> DeployPullPolicy.NEVER
        },
        forceRecreate = forceRecreate,
    )

internal data class ProjectDeployPreferenceScope(
    val normalizedServer: String,
    val userId: String,
    val environmentId: String,
    val projectId: String,
) {
    internal val storageKey: String
        get() = listOf(normalizedServer, userId, environmentId, projectId)
            .joinToString(separator = "\u001f")
            .sha256()
}

internal interface ProjectDeployPreferenceStorage {
    fun getString(key: String): String?
    fun getBoolean(key: String): Boolean?
    fun put(values: Map<String, Any>)
}

internal class ProjectDeployPreferences internal constructor(
    private val storage: ProjectDeployPreferenceStorage,
) {
    constructor(context: Context) : this(
        SharedPreferencesProjectDeployStorage(
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun load(scope: ProjectDeployPreferenceScope): ProjectDeployPreferenceValues {
        val prefix = scope.storageKey
        return ProjectDeployPreferenceValues(
            pullPolicy = ProjectDeployPullPolicy.fromPersistedValue(storage.getString("$prefix.pullPolicy")),
            forceRecreate = storage.getBoolean("$prefix.forceRecreate") ?: false,
        )
    }

    fun save(scope: ProjectDeployPreferenceScope, values: ProjectDeployPreferenceValues) {
        val prefix = scope.storageKey
        storage.put(
            mapOf(
                "$prefix.pullPolicy" to values.pullPolicy.persistedValue,
                "$prefix.forceRecreate" to values.forceRecreate,
            ),
        )
    }

    private companion object {
        const val FILE_NAME = "arcane_project_deploy_options"
    }
}

private class SharedPreferencesProjectDeployStorage(
    private val preferences: SharedPreferences,
) : ProjectDeployPreferenceStorage {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun getBoolean(key: String): Boolean? =
        if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    override fun put(values: Map<String, Any>) {
        preferences.edit().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }.apply()
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
