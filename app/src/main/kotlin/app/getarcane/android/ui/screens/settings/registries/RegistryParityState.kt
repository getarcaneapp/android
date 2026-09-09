package app.getarcane.android.ui.screens.settings.registries

import app.getarcane.sdk.models.containerregistry.ContainerRegistry
import app.getarcane.sdk.models.containerregistry.CreateContainerRegistry
import app.getarcane.sdk.models.containerregistry.UpdateContainerRegistry
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.template.Template
import app.getarcane.sdk.models.template.TemplateSourceFilter
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.hasPermission
import java.net.URI
import java.util.Locale

internal data class TemplateIdentity(
    val source: String,
    val registry: String,
    val id: String,
) {
    val stableKey: String = listOf(source, registry, id).joinToString("|") { "${it.length}:$it" }
}

internal fun Template.identity(): TemplateIdentity = TemplateIdentity(
    source = if (isRemote) "remote" else "local",
    registry = registryId?.trim().orEmpty()
        .ifEmpty { registry?.id?.trim().orEmpty() }
        .ifEmpty { registry?.name?.trim().orEmpty() }
        .ifEmpty { "unregistered" },
    id = id,
)

internal enum class TemplateSourceSelection(val sdkValue: TemplateSourceFilter) {
    ALL(TemplateSourceFilter.ALL),
    LOCAL(TemplateSourceFilter.LOCAL),
    REMOTE(TemplateSourceFilter.REMOTE),
}

internal fun filterTemplates(
    templates: List<Template>,
    query: String,
    source: TemplateSourceSelection,
): List<Template> {
    val needle = query.trim()
    return templates.filter { template ->
        val sourceMatches = when (source) {
            TemplateSourceSelection.ALL -> true
            TemplateSourceSelection.LOCAL -> !template.isRemote
            TemplateSourceSelection.REMOTE -> template.isRemote
        }
        val searchMatches = needle.isEmpty() || listOfNotNull(
            template.name,
            template.description,
            template.registry?.name,
            template.metadata?.author,
            template.metadata?.version,
            template.metadata?.tags?.joinToString(" "),
        ).any { it.contains(needle, ignoreCase = true) }
        sourceMatches && searchMatches
    }
}

internal data class TemplateGroup(
    val key: String,
    val title: String,
    val templates: List<Template>,
)

internal fun groupTemplates(templates: List<Template>): List<TemplateGroup> = templates
    .groupBy { template ->
        val configuredRegistry = template.registry
        when {
            !template.isRemote -> "local" to "Local"
            configuredRegistry != null -> {
                val registryKey = template.registryId?.trim().orEmpty()
                    .ifEmpty { configuredRegistry.id.trim() }
                    .ifEmpty { configuredRegistry.name.trim().lowercase(Locale.ROOT) }
                "configured:$registryKey" to "Configured · ${configuredRegistry.name.trim().ifEmpty { "Registry" }}"
            }
            else -> "remote" to "Remote"
        }
    }
    .map { (identity, values) ->
        TemplateGroup(
            key = identity.first,
            title = identity.second,
            templates = values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
        )
    }
    .sortedWith(compareBy<TemplateGroup> { groupOrder(it.key) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })

private fun groupOrder(key: String): Int = when {
    key == "local" -> 0
    key.startsWith("configured:") -> 1
    else -> 2
}

internal sealed interface TemplateImportState {
    data object Idle : TemplateImportState
    data class Importing(val selected: TemplateIdentity) : TemplateImportState
    data class Failed(val selected: TemplateIdentity, val message: String) : TemplateImportState
    data class Imported(val selected: TemplateIdentity) : TemplateImportState
}

internal fun beginTemplateImport(template: Template): TemplateImportState =
    TemplateImportState.Importing(template.identity())

internal fun failTemplateImport(state: TemplateImportState, message: String): TemplateImportState =
    when (state) {
        is TemplateImportState.Importing -> TemplateImportState.Failed(state.selected, message)
        is TemplateImportState.Failed -> state.copy(message = message)
        is TemplateImportState.Imported -> TemplateImportState.Failed(state.selected, message)
        TemplateImportState.Idle -> TemplateImportState.Idle
    }

internal fun completeTemplateImport(imported: Template): TemplateImportState =
    TemplateImportState.Imported(imported.identity())

internal data class TemplatePermissionPolicy(
    val canList: Boolean,
    val canRead: Boolean,
    val canImport: Boolean,
    val canDeploy: Boolean,
    val canCreateRegistry: Boolean,
    val canUpdateRegistry: Boolean,
    val canDeleteRegistry: Boolean,
)

internal fun templatePermissionPolicy(user: User?, environmentId: String): TemplatePermissionPolicy =
    TemplatePermissionPolicy(
        canList = user?.hasPermission(Permission.Templates.LIST) == true,
        canRead = user?.hasPermission(Permission.Templates.READ) == true,
        canImport = user?.hasPermission(Permission.Templates.READ) == true,
        canDeploy = user?.hasPermission(Permission.Projects.CREATE, environmentId) == true &&
            user.hasPermission(Permission.Projects.DEPLOY, environmentId),
        canCreateRegistry = user?.hasPermission(Permission.Templates.CREATE) == true,
        canUpdateRegistry = user?.hasPermission(Permission.Templates.UPDATE) == true,
        canDeleteRegistry = user?.hasPermission(Permission.Templates.DELETE) == true,
    )

internal data class RegistryPermissionPolicy(
    val canList: Boolean,
    val canRead: Boolean,
    val canCreate: Boolean,
    val canUpdate: Boolean,
    val canDelete: Boolean,
)

internal fun registryPermissionPolicy(user: User?): RegistryPermissionPolicy = RegistryPermissionPolicy(
    canList = user?.hasPermission(Permission.Registries.LIST) == true,
    canRead = user?.hasPermission(Permission.Registries.READ) == true,
    canCreate = user?.hasPermission(Permission.Registries.CREATE) == true,
    canUpdate = user?.hasPermission(Permission.Registries.UPDATE) == true,
    canDelete = user?.hasPermission(Permission.Registries.DELETE) == true,
)

internal data class ContainerRegistryDisplay(
    val registry: ContainerRegistry,
    val title: String,
    val subtitle: String,
    val stableIdentity: String,
)

internal fun containerRegistryDisplays(registries: List<ContainerRegistry>): List<ContainerRegistryDisplay> {
    val bases = registries.associateWith(::registryBaseDisplayName)
    val duplicateCounts = bases.values.groupingBy { it.lowercase(Locale.ROOT) }.eachCount()
    return registries.map { registry ->
        val base = bases.getValue(registry)
        val url = registry.url.trim()
        val title = if (duplicateCounts[base.lowercase(Locale.ROOT)] == 1) {
            base
        } else {
            val sameUrlCount = registries.count {
                bases.getValue(it).equals(base, ignoreCase = true) && it.url.trim().equals(url, ignoreCase = true)
            }
            if (url.isNotEmpty() && sameUrlCount == 1) "$base · $url" else "$base · ${registry.id.take(8)}"
        }
        ContainerRegistryDisplay(
            registry = registry,
            title = title,
            subtitle = when {
                url.isNotEmpty() && !title.contains(url, ignoreCase = true) -> url
                registry.registryType.equals("ecr", ignoreCase = true) -> "AWS ECR"
                else -> "Generic"
            },
            stableIdentity = listOf(base, url, registry.id).joinToString("|") { "${it.length}:$it" },
        )
    }
}

private fun registryBaseDisplayName(registry: ContainerRegistry): String =
    registry.description?.trim()?.takeIf(String::isNotEmpty)
        ?: registryProviderName(registry)
        ?: registry.url.trim().takeIf(String::isNotEmpty)
        ?: registry.id

private fun registryProviderName(registry: ContainerRegistry): String? {
    if (registry.registryType.equals("ecr", ignoreCase = true)) return "AWS ECR"
    val normalized = registry.url.trim().let { if ("://" in it) it else "https://$it" }
    val host = runCatching { URI(normalized).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return null
    return when (host.removePrefix("www.")) {
        "docker.io", "index.docker.io", "registry-1.docker.io" -> "Docker Hub"
        "ghcr.io" -> "GitHub Container Registry"
        "quay.io" -> "Quay"
        "registry.gitlab.com" -> "GitLab Container Registry"
        else -> null
    }
}

internal data class ContainerRegistryFormValues(
    val url: String,
    val username: String,
    val token: String,
    val description: String,
    val enabled: Boolean,
    val insecure: Boolean,
    val registryType: String,
    val awsAccessKeyId: String,
    val awsSecretAccessKey: String,
    val awsRegion: String,
    val repositoryNames: List<String>,
)

internal fun buildCreateRegistryRequest(
    values: ContainerRegistryFormValues,
    supportsRepositoryNames: Boolean,
): CreateContainerRegistry {
    val isAws = values.registryType == "ecr"
    return CreateContainerRegistry(
        url = values.url.trim(),
        username = if (isAws) null else values.username.trim().nullIfBlank(),
        token = if (isAws) null else values.token.nullIfBlank(),
        description = values.description.trim().nullIfBlank(),
        insecure = values.insecure,
        enabled = values.enabled,
        registryType = if (isAws) "ecr" else "generic",
        awsAccessKeyId = if (isAws) values.awsAccessKeyId.trim().nullIfBlank() else null,
        awsSecretAccessKey = if (isAws) values.awsSecretAccessKey.nullIfBlank() else null,
        awsRegion = if (isAws) values.awsRegion.trim().nullIfBlank() else null,
        repositoryNames = if (supportsRepositoryNames) normalizeRepositoryNames(values.repositoryNames) else null,
    )
}

internal fun buildUpdateRegistryRequest(
    values: ContainerRegistryFormValues,
    original: ContainerRegistry,
    supportsRepositoryNames: Boolean,
): UpdateContainerRegistry {
    val isAws = original.registryType.equals("ecr", ignoreCase = true)
    return UpdateContainerRegistry(
        url = values.url.trim(),
        username = if (isAws) null else values.username.trim().nullIfBlank(),
        token = if (isAws) null else values.token.nullIfBlank(),
        description = values.description.trim().nullIfBlank(),
        insecure = values.insecure,
        enabled = values.enabled,
        // Arcane does not allow changing provider type. Preserve even unknown
        // future values instead of coercing them through the two-option UI.
        registryType = original.registryType,
        awsAccessKeyId = if (isAws) values.awsAccessKeyId.trim().nullIfBlank() else null,
        awsSecretAccessKey = if (isAws) values.awsSecretAccessKey.nullIfBlank() else null,
        awsRegion = if (isAws) values.awsRegion.trim().nullIfBlank() else null,
        repositoryNames = if (supportsRepositoryNames) normalizeRepositoryNames(values.repositoryNames) else null,
    )
}

private fun normalizeRepositoryNames(values: List<String>): List<String> = values
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
