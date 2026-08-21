package app.getarcane.android.ui.screens.ports

import app.getarcane.sdk.models.port.PortMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortStoreTest {
    @Test
    fun `port cache cannot cross server identity`() {
        val port = port("port-a")
        PortStore.put("https://server-a.example.com:443", listOf(port))

        assertEquals(port, PortStore.get("https://server-a.example.com:443", port.id))
        assertNull(PortStore.get("https://server-b.example.com:443", port.id))
    }

    @Test
    fun `replacing server cache removes previous rows even when ids match`() {
        val serverA = port("shared", containerName = "server-a")
        val serverB = port("shared", containerName = "server-b")
        PortStore.put("https://server-a.example.com:443", listOf(serverA))

        PortStore.put("https://server-b.example.com:443", listOf(serverB))

        assertNull(PortStore.get("https://server-a.example.com:443", "shared"))
        assertEquals(serverB, PortStore.get("https://server-b.example.com:443", "shared"))
    }

    @Test
    fun `clear removes cached rows`() {
        PortStore.put("https://server-a.example.com:443", listOf(port("port-a")))

        PortStore.clear()

        assertNull(PortStore.get("https://server-a.example.com:443", "port-a"))
    }

    private fun port(id: String, containerName: String = "container"): PortMapping =
        PortMapping(
            id = id,
            containerId = "container-id",
            containerName = containerName,
            hostPort = 8080,
            containerPort = 80,
            protocolName = "tcp",
            isPublished = true,
        )
}
