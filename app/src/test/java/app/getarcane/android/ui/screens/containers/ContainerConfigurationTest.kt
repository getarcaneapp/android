package app.getarcane.android.ui.screens.containers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerConfigurationTest {
    private val draft = ContainerConfigurationDraft(
        name = "worker",
        image = "busybox:1.37",
        command = "sh\n-c\necho ready",
        environment = "TOKEN=secret\nMODE=test",
        labels = "team=mobile",
        binds = "data:/data",
        ports = "80/tcp=127.0.0.1|8080",
        networks = "bridge",
        restartPolicy = "unless-stopped",
        memoryMb = "128",
        cpus = "0.5",
    )

    @Test
    fun createPayloadMapsSupportedConfiguration() {
        val payload = buildContainerConfigurationPayload(ContainerConfigurationMode.CREATE, draft)
        assertTrue(payload.errors.isEmpty())
        val create = requireNotNull(payload.create)
        assertEquals("worker", create.name)
        assertEquals(listOf("TOKEN=secret", "MODE=test"), create.environment)
        assertEquals("8080", create.hostConfig?.portBindings?.get("80/tcp")?.single()?.hostPort)
        assertEquals(128L * 1024L * 1024L, create.hostConfig?.memory)
        assertEquals(500_000_000L, create.hostConfig?.nanoCpus)
    }

    @Test
    fun editPayloadUsesRecreateContractWithoutRenameOrUnownedSections() {
        val edit = requireNotNull(
            buildContainerConfigurationPayload(ContainerConfigurationMode.EDIT, draft).edit,
        )
        assertNull(edit.name)
        assertNull(edit.healthcheck)
        assertNull(edit.credentials)
        assertNull(edit.hostConfig?.mounts)
        assertNull(edit.hostConfig?.capAdd)
    }

    @Test
    fun invalidConfigurationFailsBeforeNetworkMutation() {
        val payload = buildContainerConfigurationPayload(
            ContainerConfigurationMode.CREATE,
            draft.copy(name = "", image = "", labels = "broken", ports = "broken", cpus = "many"),
        )
        assertFalse(payload.errors.isEmpty())
        assertNull(payload.create)
        assertTrue(payload.errors.any { "Name" in it })
        assertTrue(payload.errors.any { "port" in it })
    }

    @Test
    fun numericOverflowFailsBeforeNetworkMutation() {
        val payload = buildContainerConfigurationPayload(
            ContainerConfigurationMode.CREATE,
            draft.copy(memoryMb = Long.MAX_VALUE.toString(), cpus = "Infinity"),
        )

        assertNull(payload.create)
        assertTrue(payload.errors.any { "Memory" in it })
        assertTrue(payload.errors.any { "CPUs" in it })
    }
}
