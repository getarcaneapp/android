package app.getarcane.android.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTabTest {
    @Test
    fun canPinToBottomBarMatchesPrimaryContentTabs() {
        val pinnable = AppTab.entries.filter { it.canPinToBottomBar }.toSet()

        assertEquals(
            setOf(
                AppTab.Dashboard,
                AppTab.Containers,
                AppTab.Images,
                AppTab.Projects,
                AppTab.Volumes,
                AppTab.Networks,
                AppTab.Ports,
                AppTab.Updates,
                AppTab.Activities,
                AppTab.Events,
                AppTab.Swarm,
            ),
            pinnable,
        )
    }

    @Test
    fun adminAndConfigTabsCannotBePinnedToBottomBar() {
        val nonPinnable = setOf(
            AppTab.GitRepositories,
            AppTab.GitOps,
            AppTab.ContainerRegistries,
            AppTab.TemplateRegistries,
            AppTab.Builds,
            AppTab.Jobs,
            AppTab.Users,
            AppTab.ApiKeys,
            AppTab.Notifications,
            AppTab.Webhooks,
            AppTab.SystemSettings,
            AppTab.Authentication,
            AppTab.Roles,
            AppTab.OidcRoleMappings,
        )

        nonPinnable.forEach { tab ->
            assertFalse("${tab.id} should stay in Settings, not the bottom bar", tab.canPinToBottomBar)
        }
    }

    @Test
    fun bottomBarAvailabilityAlsoAppliesPermissionAndServerGates() {
        assertTrue(AppTab.Events.isAvailableForBottomBar(isAdmin = false, supportsV2 = false))
        assertFalse(AppTab.Swarm.isAvailableForBottomBar(isAdmin = false, supportsV2 = true))
        assertTrue(AppTab.Swarm.isAvailableForBottomBar(isAdmin = true, supportsV2 = true))
        assertFalse(AppTab.Activities.isAvailableForBottomBar(isAdmin = false, supportsV2 = false))
        assertTrue(AppTab.Activities.isAvailableForBottomBar(isAdmin = false, supportsV2 = true))
        assertFalse(AppTab.Users.isAvailableForBottomBar(isAdmin = true, supportsV2 = true))
    }

    @Test
    fun normalizedPinnedBottomTabsDropsNonPinnableTabsAndBackfillsDefaults() {
        val normalized = normalizedPinnedBottomTabs(
            pinned = listOf(AppTab.Dashboard, AppTab.Users, AppTab.Images, AppTab.Swarm),
        )

        assertEquals(
            listOf(AppTab.Dashboard, AppTab.Images, AppTab.Swarm, AppTab.Containers),
            normalized,
        )
    }

    @Test
    fun normalizedPinnedBottomTabsDropsDuplicates() {
        val normalized = normalizedPinnedBottomTabs(
            pinned = listOf(AppTab.Dashboard, AppTab.Images, AppTab.Images, AppTab.Projects),
        )

        assertEquals(
            listOf(AppTab.Dashboard, AppTab.Images, AppTab.Projects, AppTab.Containers),
            normalized,
        )
    }

    @Test
    fun normalizedPinnedBottomTabsPreservesTransientlyUnavailablePins() {
        val normalized = normalizedPinnedBottomTabs(
            pinned = listOf(AppTab.Dashboard, AppTab.Activities, AppTab.Swarm, AppTab.Images),
        )

        assertEquals(
            listOf(AppTab.Dashboard, AppTab.Activities, AppTab.Swarm, AppTab.Images),
            normalized,
        )
    }

    @Test
    fun visibleBottomTabsHidesUnavailablePinsWithoutMutatingNormalizedPins() {
        val pinned = listOf(AppTab.Dashboard, AppTab.Activities, AppTab.Swarm, AppTab.Images)
        val visible = visibleBottomTabs(
            pinned = pinned,
            isAdmin = false,
            supportsV2 = false,
        )

        assertEquals(
            listOf(AppTab.Dashboard, AppTab.Images, AppTab.Containers, AppTab.Projects),
            visible,
        )
        assertEquals(
            listOf(AppTab.Dashboard, AppTab.Activities, AppTab.Swarm, AppTab.Images),
            pinned,
        )
    }
}
