package app.getarcane.android.core

import app.getarcane.sdk.models.container.ContainerHostConfig
import app.getarcane.sdk.models.container.ContainerMount
import app.getarcane.sdk.models.container.ContainerNetworkSettings
import app.getarcane.sdk.models.container.ContainerPort
import app.getarcane.sdk.models.container.ContainerSummary
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.volume.Volume
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadCacheSanitizersTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `environment cache projection removes credential and session material`() {
        val sanitized = Environment(
            id = "env",
            name = "Environment",
            apiUrl = "https://edge.invalid",
            status = "online",
            edgeSessionId = "session-secret",
            edgeAgentInstance = "agent-secret",
            apiKey = "api-key-secret",
        ).sanitizedForReadCache()

        val encoded = json.encodeToString(sanitized)
        assertFalse(encoded.contains("session-secret"))
        assertFalse(encoded.contains("agent-secret"))
        assertFalse(encoded.contains("api-key-secret"))
        assertFalse(encoded.contains("edge.invalid"))
        assertTrue(encoded.contains("Environment"))
    }

    @Test
    fun `resource projections remove commands paths content metadata and reverse references`() {
        val container = ContainerSummary(
            id = "container",
            names = listOf("name"),
            image = "image:tag",
            imageId = "image-id",
            command = "command-secret",
            created = 1,
            ports = listOf(ContainerPort(privatePort = 80, publicPort = 1234, type = "tcp")),
            labels = mapOf("label-secret" to "value-secret"),
            state = "running",
            status = "Up",
            hostConfig = ContainerHostConfig(networkMode = "host-secret"),
            networkSettings = ContainerNetworkSettings(),
            mounts = listOf(ContainerMount(type = "bind", source = "/host-secret", destination = "/data")),
        ).sanitizedForReadCache()
        val project = ProjectDetails(
            id = "project",
            name = "Project",
            path = "/project-secret",
            composeContent = "compose-secret",
            envContent = "env-secret",
            status = "running",
            createdAt = "created",
            updatedAt = "updated",
            gitRepositoryURL = "https://git-secret.invalid/repo",
        ).sanitizedForReadCache()
        val volume = Volume(
            id = "volume",
            name = "Volume",
            mountpoint = "/mount-secret",
            options = mapOf("option-secret" to "value-secret"),
            labels = mapOf("label-secret" to "value-secret"),
            containers = listOf("container-secret"),
        ).sanitizedForReadCache()

        val encoded = listOf(
            json.encodeToString(container),
            json.encodeToString(project),
            json.encodeToString(volume),
        ).joinToString()
        listOf(
            "command-secret",
            "label-secret",
            "host-secret",
            "/project-secret",
            "compose-secret",
            "env-secret",
            "git-secret",
            "/mount-secret",
            "option-secret",
            "container-secret",
        ).forEach { secret -> assertFalse(secret, encoded.contains(secret)) }
    }
}
