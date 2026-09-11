package app.getarcane.android.core

import app.getarcane.sdk.models.version.VersionInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFeatureSupportTest {
    @Test
    fun pauseKillGateStartsAtArcane220() {
        assertFalse(version("2.1.9").supportsContainerReliabilityActions())
        assertTrue(version("2.2.0").supportsContainerReliabilityActions())
        assertTrue(version("v2.10.2+build.1").supportsContainerReliabilityActions())
    }

    @Test
    fun pauseKillGateRejectsUntrustedVersionShapes() {
        assertFalse(version("dev", semver = false).supportsContainerReliabilityActions())
        assertFalse(version("2.2", semver = true).supportsContainerReliabilityActions())
        assertFalse(version("1.99.0", semver = true).supportsContainerReliabilityActions())
    }

    private fun version(value: String, semver: Boolean = true) = VersionInfo(
        currentVersion = value,
        revision = "revision",
        shortRevision = "revision",
        goVersion = "go1.25",
        displayVersion = value,
        isSemverVersion = semver,
        updateAvailable = false,
    )
}
