package app.getarcane.android.ui.screens.settings.system

import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.system.UpgradeCheckResult
import app.getarcane.sdk.models.system.TriggerUpgradeResult
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.version.VersionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeAvailabilityTest {
    @Test
    fun `upgrade confirmation identifies the selected environment`() {
        assertEquals(
            UpgradeConfirmationText(
                "Upgrade Arcane on Remote Lab?",
                "Remote Lab will restart its Arcane service. The mobile app may briefly lose connection.",
            ),
            upgradeConfirmationText("Remote Lab"),
        )
    }

    @Test
    fun `trigger result distinguishes restart from already current`() {
        assertEquals(
            UpgradeTriggerOutcome.Restarting("Restarting"),
            mapUpgradeTriggerResult(TriggerUpgradeResult("Restarting", upToDate = false)),
        )
        assertEquals(
            UpgradeTriggerOutcome.UpToDate("Already current"),
            mapUpgradeTriggerResult(TriggerUpgradeResult("Already current", upToDate = true)),
        )
    }

    @Test
    fun `authorization is evaluated independently for each environment`() = runBlocking {
        val user = userWithPermissions("env-a")

        val allowed = resolve(user, "env-a")
        val denied = resolve(user, "env-b")

        assertTrue(allowed is UpgradeAvailability.Ready)
        assertTrue(denied is UpgradeAvailability.Unauthorized)
        denied as UpgradeAvailability.Unauthorized
        assertEquals(setOf(Permission.System.READ, Permission.System.UPGRADE), denied.missingPermissions)
    }

    @Test
    fun `server before typed upgrade contract is explicit and is not checked`() = runBlocking {
        var checked = false

        val result = resolveUpgradeAvailability(
            user = userWithPermissions("env-a"),
            environmentId = "env-a",
            loadVersion = { version("1.9.9", updateAvailable = true) },
            checkUpgrade = { checked = true; upgradeResult() },
        )

        assertTrue(result is UpgradeAvailability.OlderServer)
        assertFalse(checked)
    }

    @Test
    fun `missing version endpoint is an older server`() = runBlocking {
        val result = resolveUpgradeAvailability(
            user = userWithPermissions("env-a"),
            environmentId = "env-a",
            loadVersion = { throw ArcaneError.NotFound },
            checkUpgrade = { upgradeResult() },
        )

        assertTrue(result is UpgradeAvailability.OlderServer)
    }

    @Test
    fun `version two upgrade contract remains eligible for authoritative check`() = runBlocking {
        val result = resolveUpgradeAvailability(
            user = userWithPermissions("env-a"),
            environmentId = "env-a",
            loadVersion = { version("2.0.0", updateAvailable = true) },
            checkUpgrade = { upgradeResult() },
        )

        assertTrue(result is UpgradeAvailability.Ready)
    }

    @Test
    fun `current version avoids upgrade check`() = runBlocking {
        var checked = false
        val result = resolveUpgradeAvailability(
            user = userWithPermissions("env-a"),
            environmentId = "env-a",
            loadVersion = { version("2.10.2", updateAvailable = false) },
            checkUpgrade = { checked = true; upgradeResult() },
        )

        assertTrue(result is UpgradeAvailability.Unavailable)
        assertFalse(checked)
    }

    @Test
    fun `typed check distinguishes unsupported and server error`() = runBlocking {
        val unsupported = resolve(checkResult = upgradeResult(canUpgrade = false, message = "No Docker socket"))
        val serverError = resolve(checkResult = upgradeResult(canUpgrade = false, error = true, message = "Probe failed"))

        assertTrue(unsupported is UpgradeAvailability.Unsupported)
        assertTrue(serverError is UpgradeAvailability.Error)
        assertEquals("Probe failed", (serverError as UpgradeAvailability.Error).message)
    }

    @Test
    fun `not found typed check is unsupported rather than eligible`() = runBlocking {
        val result = resolveUpgradeAvailability(
            user = userWithPermissions("env-a"),
            environmentId = "env-a",
            loadVersion = { version("2.10.2", updateAvailable = true) },
            checkUpgrade = { throw ArcaneError.NotFound },
        )

        assertTrue(result is UpgradeAvailability.Unsupported)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates`(): Unit = runBlocking {
        resolveUpgradeAvailability(
            user = userWithPermissions("env-a"),
            environmentId = "env-a",
            loadVersion = { version("2.10.2", updateAvailable = true) },
            checkUpgrade = { throw CancellationException("cancelled") },
        )
    }

    private suspend fun resolve(
        user: User = userWithPermissions("env-a"),
        environmentId: String = "env-a",
        checkResult: UpgradeCheckResult = upgradeResult(),
    ): UpgradeAvailability = resolveUpgradeAvailability(
        user = user,
        environmentId = environmentId,
        loadVersion = { version("2.10.2", updateAvailable = true) },
        checkUpgrade = { checkResult },
    )

    private fun userWithPermissions(environmentId: String) = User(
        id = "user-1",
        username = "operator",
        permissionsByEnv = mapOf(
            environmentId to listOf(Permission.System.READ, Permission.System.UPGRADE),
        ),
    )

    private fun upgradeResult(
        canUpgrade: Boolean = true,
        error: Boolean = false,
        message: String = "System can be upgraded",
    ) = UpgradeCheckResult(canUpgrade = canUpgrade, error = error, message = message)

    private fun version(current: String, updateAvailable: Boolean) = VersionInfo(
        currentVersion = current,
        currentTag = "v$current",
        revision = "revision",
        shortRevision = "revision",
        goVersion = "go1.25",
        displayVersion = current,
        isSemverVersion = true,
        newestVersion = "2.10.3",
        updateAvailable = updateAvailable,
    )
}
