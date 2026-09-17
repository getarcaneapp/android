package app.getarcane.android.ui.screens.settings.system

import app.getarcane.sdk.serialization.ArcaneJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSettingsContractTest {
    @Test
    fun currentCategoryInventoryUsesAcceptedPullPoliciesAndConditionalFields() {
        val fields = systemSettingsCategories.flatMap(SettingsCategoryDef::fields)
        val pullPolicy = fields.single { it.key == "defaultDeployPullPolicy" }.type as SettingFieldType.Select
        assertEquals(listOf("missing", "always", "never"), pullPolicy.options)
        assertFalse("build" in pullPolicy.options)
        assertEquals(
            SettingFieldCondition("pruneImageMode", "olderThan"),
            fields.single { it.key == "pruneImageUntil" }.visibleWhen,
        )
        assertNull(fields.find { it.key == "scheduledPruneContainers" })
        assertTrue(fields.any { it.key == "templatesDirectory" })
        assertTrue(fields.any { it.key == "trivyConfig" })
    }

    @Test
    fun validationChecksRangesAndSelectValues() {
        val fields = systemSettingsCategories.flatMap(SettingsCategoryDef::fields)
        val errors = validateSettingChanges(
            fields,
            mapOf(
                "defaultDeployPullPolicy" to "build",
                "activityHistoryRetentionDays" to "3651",
                "trivyConcurrentScanContainers" to "zero",
            ),
        )
        assertEquals(3, errors.size)
    }

    @Test
    fun updateBuilderPreservesUnknownLoadedFieldsBySendingOnlyKnownChanges() {
        val update = updateSettingsFrom(
            mapOf(
                "templatesDirectory" to "/srv/templates",
                "unknownFutureSetting" to "preserve-on-server",
            ),
        )
        val encoded = ArcaneJson.default.encodeToString(update)
        assertTrue("templatesDirectory" in encoded)
        assertFalse("unknownFutureSetting" in encoded)
        assertFalse("projectsDirectory" in encoded)
    }
}
