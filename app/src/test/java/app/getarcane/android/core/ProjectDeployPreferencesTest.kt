package app.getarcane.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.getarcane.sdk.models.project.DeployPullPolicy

class ProjectDeployPreferencesTest {
    @Test
    fun `invalid or missing persisted values use safe defaults`() {
        val storage = FakeStorage(mutableMapOf())
        val preferences = ProjectDeployPreferences(storage)
        val scope = scope()

        assertEquals(ProjectDeployPreferenceValues(), preferences.load(scope))

        storage.values["${scope.storageKey}.pullPolicy"] = "future-policy"
        storage.values["${scope.storageKey}.forceRecreate"] = "not-a-boolean"
        assertEquals(ProjectDeployPullPolicy.MISSING, preferences.load(scope).pullPolicy)
        assertFalse(preferences.load(scope).forceRecreate)
    }

    @Test
    fun `saved values round trip in the exact scope`() {
        val preferences = ProjectDeployPreferences(FakeStorage(mutableMapOf()))
        val scope = scope()
        val expected = ProjectDeployPreferenceValues(ProjectDeployPullPolicy.ALWAYS, forceRecreate = true)

        preferences.save(scope, expected)

        assertEquals(expected, preferences.load(scope))
    }

    @Test
    fun `preferences cannot cross server user environment or project`() {
        val preferences = ProjectDeployPreferences(FakeStorage(mutableMapOf()))
        val selected = ProjectDeployPreferenceValues(ProjectDeployPullPolicy.NEVER, forceRecreate = true)
        val original = scope()
        preferences.save(original, selected)

        val otherScopes = listOf(
            original.copy(normalizedServer = "https://other.example:443"),
            original.copy(userId = "user-2"),
            original.copy(environmentId = "env-2"),
            original.copy(projectId = "project-2"),
        )

        otherScopes.forEach { candidate ->
            assertNotEquals(original.storageKey, candidate.storageKey)
            assertEquals(ProjectDeployPreferenceValues(), preferences.load(candidate))
        }
        assertEquals(selected, preferences.load(original))
    }

    @Test
    fun `force recreation is persisted only when explicitly saved`() {
        val storage = FakeStorage(mutableMapOf())
        val preferences = ProjectDeployPreferences(storage)
        val scope = scope()

        assertFalse(preferences.load(scope).forceRecreate)
        preferences.save(scope, ProjectDeployPreferenceValues(forceRecreate = true))
        assertTrue(preferences.load(scope).forceRecreate)
    }

    @Test
    fun `preference values map exactly to typed SDK options`() {
        val options = ProjectDeployPreferenceValues(
            pullPolicy = ProjectDeployPullPolicy.NEVER,
            forceRecreate = true,
        ).toSdkDeployOptions()

        assertEquals(DeployPullPolicy.NEVER, options.pullPolicy)
        assertTrue(options.forceRecreate == true)
    }

    private fun scope() = ProjectDeployPreferenceScope(
        normalizedServer = "https://arcane.example:443",
        userId = "user-1",
        environmentId = "env-1",
        projectId = "project-1",
    )
}

private class FakeStorage(
    val values: MutableMap<String, Any>,
) : ProjectDeployPreferenceStorage {
    override fun getString(key: String): String? = values[key] as? String
    override fun getBoolean(key: String): Boolean? = values[key] as? Boolean
    override fun put(values: Map<String, Any>) {
        this.values.putAll(values)
    }
}
