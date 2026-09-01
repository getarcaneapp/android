package app.getarcane.android.ui.screens.settings.registries

import app.getarcane.sdk.models.containerregistry.ContainerRegistry
import app.getarcane.sdk.models.meta.TemplateMetadata
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.template.Template
import app.getarcane.sdk.models.template.TemplateRegistry
import app.getarcane.sdk.models.user.User
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryParityStateTest {
    @Test
    fun templateFilteringUsesTypedSourceAndMetadataWithoutCollidingIdentities() {
        val first = template(
            id = "shared",
            name = "Database",
            remote = true,
            registry = templateRegistry("one", "Community"),
            metadata = TemplateMetadata(author = "Arcane", tags = listOf("storage")),
        )
        val second = template(
            id = "shared",
            name = "Database",
            remote = true,
            registry = templateRegistry("two", "Private"),
        )
        val local = template(id = "shared", name = "Web", remote = false)

        assertNotEquals(first.identity().stableKey, second.identity().stableKey)
        assertNotEquals(first.identity().stableKey, local.identity().stableKey)
        assertEquals(listOf(first), filterTemplates(listOf(first, second, local), "storage", TemplateSourceSelection.REMOTE))
        assertEquals(listOf(local), filterTemplates(listOf(first, second, local), "", TemplateSourceSelection.LOCAL))
        assertEquals(
            listOf("Local", "Configured · Community", "Configured · Private"),
            groupTemplates(listOf(second, local, first)).map { it.title },
        )
    }

    @Test
    fun failedTemplateImportRetainsExactSelectedIdentity() {
        val remote = template(
            id = "same",
            name = "Remote",
            remote = true,
            registry = templateRegistry("community", "Community"),
        )

        val failed = failTemplateImport(beginTemplateImport(remote), "network down")

        assertTrue(failed is TemplateImportState.Failed)
        failed as TemplateImportState.Failed
        assertEquals(remote.identity(), failed.selected)
        assertEquals("network down", failed.message)

        val imported = remote.copy(id = "local-copy", isRemote = false, registryId = null, registry = null)
        assertEquals(imported.identity(), (completeTemplateImport(imported) as TemplateImportState.Imported).selected)
    }

    @Test
    fun permissionPoliciesKeepGlobalAndEnvironmentActionsSeparate() {
        val user = User(
            id = "user",
            username = "operator",
            permissionsByEnv = mapOf(
                User.GLOBAL_PERMISSIONS_KEY to listOf(
                    Permission.Templates.LIST,
                    Permission.Templates.READ,
                    Permission.Registries.LIST,
                    Permission.Registries.READ,
                    Permission.Registries.UPDATE,
                ),
                "env-1" to listOf(Permission.Projects.CREATE),
            ),
        )

        val templatePolicy = templatePermissionPolicy(user, "env-1")
        assertTrue(templatePolicy.canList)
        assertTrue(templatePolicy.canRead)
        assertFalse(templatePolicy.canDeploy)
        assertTrue(templatePolicy.canImport)
        assertFalse(templatePolicy.canCreateRegistry)

        val deployPolicy = templatePermissionPolicy(
            user.copy(
                permissionsByEnv = user.permissionsByEnv.orEmpty() +
                    ("env-1" to listOf(Permission.Projects.CREATE, Permission.Projects.DEPLOY)),
            ),
            "env-1",
        )
        assertTrue(deployPolicy.canDeploy)

        val registryPolicy = registryPermissionPolicy(user)
        assertTrue(registryPolicy.canList)
        assertTrue(registryPolicy.canRead)
        assertTrue(registryPolicy.canUpdate)
        assertFalse(registryPolicy.canCreate)
        assertFalse(registryPolicy.canDelete)
    }

    @Test
    fun registryDisplayUsesProviderFallbackAndDisambiguatesDuplicates() {
        val first = registry(id = "registry-one", url = "docker.io", description = null)
        val second = registry(id = "registry-two", url = "https://index.docker.io", description = null)
        val custom = registry(id = "registry-three", url = "", description = "  Team Cache  ")
        val fallback = registry(id = "registry-four", url = "", description = "")
        val namedProvider = registry(id = "registry-five", url = "docker.io/team", description = "  Team Docker  ")

        val displays = containerRegistryDisplays(listOf(first, second, custom, fallback, namedProvider))

        assertEquals("Docker Hub · docker.io", displays[0].title)
        assertEquals("Docker Hub · https://index.docker.io", displays[1].title)
        assertEquals("Team Cache", displays[2].title)
        assertEquals("registry-four", displays[3].title)
        assertEquals("Team Docker", displays[4].title)
        assertEquals(5, displays.map { it.stableIdentity }.toSet().size)
    }

    @Test
    fun registryPayloadOmitsBlankSecretsAndHiddenProviderFields() {
        val generic = values(
            registryType = "generic",
            token = "   ",
            awsAccessKeyId = "hidden-key",
            awsSecretAccessKey = "hidden-secret",
            awsRegion = "hidden-region",
            repositoryNames = listOf(" org/app ", "", "org/app", "org/worker"),
        )
        val genericOriginal = registry(
            id = "generic",
            url = "registry.example.com",
            description = "Registry",
        )
        val genericUpdate = buildUpdateRegistryRequest(generic, genericOriginal, supportsRepositoryNames = true)

        assertNull(genericUpdate.token)
        assertNull(genericUpdate.awsAccessKeyId)
        assertNull(genericUpdate.awsSecretAccessKey)
        assertNull(genericUpdate.awsRegion)
        assertEquals(listOf("org/app", "org/worker"), genericUpdate.repositoryNames)

        val ecr = values(
            registryType = "ecr",
            username = "hidden-user",
            token = "hidden-token",
            awsSecretAccessKey = "",
        )
        val ecrCreate = buildCreateRegistryRequest(ecr, supportsRepositoryNames = false)

        assertNull(ecrCreate.username)
        assertNull(ecrCreate.token)
        assertNull(ecrCreate.awsSecretAccessKey)
        assertNull(ecrCreate.repositoryNames)
        assertEquals("ecr", ecrCreate.registryType)
    }

    @Test
    fun registryUpdatePreservesUnknownImmutableTypeAndOmitsUnrelatedFields() {
        val original = registry(
            id = "future",
            url = "registry.example.com",
            description = "Future",
            registryType = "future-provider",
        )
        val update = buildUpdateRegistryRequest(
            values(registryType = "generic", awsAccessKeyId = "hidden", awsSecretAccessKey = "hidden", awsRegion = "hidden"),
            original,
            supportsRepositoryNames = false,
        )

        assertEquals("future-provider", update.registryType)
        assertNull(update.awsAccessKeyId)
        assertNull(update.awsSecretAccessKey)
        assertNull(update.awsRegion)
    }

    private fun template(
        id: String,
        name: String,
        remote: Boolean,
        registry: TemplateRegistry? = null,
        metadata: TemplateMetadata? = null,
    ) = Template(
        id = id,
        name = name,
        description = "$name description",
        content = "services: {}",
        isCustom = !remote,
        isRemote = remote,
        registryId = registry?.id,
        registry = registry,
        metadata = metadata,
    )

    private fun templateRegistry(id: String, name: String) = TemplateRegistry(
        id = id,
        name = name,
        description = "",
        url = "https://example.com/$id.json",
        enabled = true,
    )

    private fun registry(
        id: String,
        url: String,
        description: String?,
        registryType: String = "generic",
    ) = ContainerRegistry(
        id = id,
        url = url,
        username = "",
        description = description,
        insecure = false,
        enabled = true,
        registryType = registryType,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun values(
        registryType: String,
        username: String = "user",
        token: String = "token",
        awsAccessKeyId: String = "key",
        awsSecretAccessKey: String = "secret",
        awsRegion: String = "us-east-1",
        repositoryNames: List<String> = emptyList(),
    ) = ContainerRegistryFormValues(
        url = "registry.example.com",
        username = username,
        token = token,
        description = "Registry",
        enabled = true,
        insecure = false,
        registryType = registryType,
        awsAccessKeyId = awsAccessKeyId,
        awsSecretAccessKey = awsSecretAccessKey,
        awsRegion = awsRegion,
        repositoryNames = repositoryNames,
    )
}
