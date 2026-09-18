package app.getarcane.android.ui.screens.containers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.ReadResource
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ProtectSensitiveWindow
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.LabeledToggle
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import app.getarcane.sdk.models.container.ContainerCreate
import app.getarcane.sdk.models.container.ContainerEdit
import app.getarcane.sdk.models.container.ContainerEditConfig
import app.getarcane.sdk.models.container.ContainerRestartPolicyCreate
import app.getarcane.sdk.models.container.EndpointSettingsCreate
import app.getarcane.sdk.models.container.HostConfigCreate
import app.getarcane.sdk.models.container.HostConfigEdit
import app.getarcane.sdk.models.container.NetworkingConfigCreate
import app.getarcane.sdk.models.container.PortBindingCreate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class ContainerConfigurationMode { CREATE, EDIT }

internal data class ContainerConfigurationDraft(
    val name: String = "",
    val image: String = "",
    val command: String = "",
    val entrypoint: String = "",
    val workingDirectory: String = "",
    val user: String = "",
    val environment: String = "",
    val labels: String = "",
    val binds: String = "",
    val ports: String = "",
    val networks: String = "",
    val networkSettings: Map<String, EndpointSettingsCreate> = emptyMap(),
    val restartPolicy: String = "no",
    val privileged: Boolean = false,
    val autoRemove: Boolean = false,
    val readOnlyRootFilesystem: Boolean = false,
    val memoryMb: String = "0",
    val cpus: String = "0",
)

internal data class ContainerConfigurationPayload(
    val create: ContainerCreate? = null,
    val edit: ContainerEdit? = null,
    val errors: List<String> = emptyList(),
)

internal fun buildContainerConfigurationPayload(
    mode: ContainerConfigurationMode,
    draft: ContainerConfigurationDraft,
): ContainerConfigurationPayload {
    val errors = mutableListOf<String>()
    if (mode == ContainerConfigurationMode.CREATE && draft.name.isBlank()) errors += "Name is required."
    if (draft.image.isBlank()) errors += "Image is required."
    val labels = parseAssignments(draft.labels, "label", errors)
    val portBindings = parsePortBindings(draft.ports, errors)
    val memoryMb = draft.memoryMb.ifBlank { "0" }.toLongOrNull()
    if (memoryMb == null || memoryMb < 0 || memoryMb > Long.MAX_VALUE / (1024L * 1024L)) {
        errors += "Memory must be a non-negative whole number within the supported range."
    }
    val cpus = draft.cpus.ifBlank { "0" }.toDoubleOrNull()
    if (cpus == null || !cpus.isFinite() || cpus < 0 || cpus > Long.MAX_VALUE / 1_000_000_000.0) {
        errors += "CPUs must be a non-negative number within the supported range."
    }
    if (errors.isNotEmpty()) return ContainerConfigurationPayload(errors = errors)

    val commands = draft.command.nonBlankLines()
    val entrypoint = draft.entrypoint.nonBlankLines()
    val environment = draft.environment.nonBlankLines()
    val binds = draft.binds.nonBlankLines()
    val networkNames = draft.networks.nonBlankLines().distinct()
    val networking = NetworkingConfigCreate(
        endpointsConfig = networkNames.associateWith { draft.networkSettings[it] ?: EndpointSettingsCreate() },
    )
    val restart = ContainerRestartPolicyCreate(name = draft.restartPolicy)
    val memory = requireNotNull(memoryMb) * 1024L * 1024L
    val nanoCpus = (requireNotNull(cpus) * 1_000_000_000L).toLong()

    return when (mode) {
        ContainerConfigurationMode.CREATE -> ContainerConfigurationPayload(
            create = ContainerCreate(
                name = draft.name.trim(),
                image = draft.image.trim(),
                command = commands,
                entrypoint = entrypoint,
                workingDir = draft.workingDirectory,
                user = draft.user,
                environment = environment,
                labels = labels,
                hostConfig = HostConfigCreate(
                    binds = binds,
                    portBindings = portBindings,
                    restartPolicy = restart,
                    privileged = draft.privileged,
                    autoRemove = draft.autoRemove,
                    readonlyRootfs = draft.readOnlyRootFilesystem,
                    memory = memory,
                    nanoCpus = nanoCpus,
                ),
                networkingConfig = networking,
            ),
        )
        ContainerConfigurationMode.EDIT -> ContainerConfigurationPayload(
            edit = ContainerEdit(
                image = draft.image.trim(),
                workingDir = draft.workingDirectory,
                user = draft.user,
                command = commands,
                entrypoint = entrypoint,
                environment = environment,
                labels = labels,
                hostConfig = HostConfigEdit(
                    binds = binds,
                    portBindings = portBindings,
                    restartPolicy = restart,
                    privileged = draft.privileged,
                    autoRemove = draft.autoRemove,
                    readonlyRootfs = draft.readOnlyRootFilesystem,
                    memory = memory,
                    nanoCpus = nanoCpus,
                ),
                networkingConfig = networking,
            ),
        )
    }
}

private fun String.nonBlankLines(): List<String> = lines().map(String::trim).filter(String::isNotEmpty)

private fun parseAssignments(raw: String, kind: String, errors: MutableList<String>): Map<String, String> =
    buildMap {
        raw.nonBlankLines().forEachIndexed { index, line ->
            val key = line.substringBefore('=', missingDelimiterValue = "").trim()
            if (key.isBlank() || '=' !in line) {
                errors += "Invalid $kind on line ${index + 1}; use NAME=VALUE."
            } else {
                put(key, line.substringAfter('='))
            }
        }
    }

private fun parsePortBindings(raw: String, errors: MutableList<String>): Map<String, List<PortBindingCreate>> =
    buildMap {
        raw.nonBlankLines().forEachIndexed { index, line ->
            val containerPort = line.substringBefore('=', missingDelimiterValue = "").trim()
            val host = line.substringAfter('=', missingDelimiterValue = "").trim()
            if (containerPort.isBlank() || host.isBlank()) {
                errors += "Invalid port on line ${index + 1}; use 80/tcp=8080 or 80/tcp=127.0.0.1|8080."
            } else {
                val normalized = if ('/' in containerPort) containerPort else "$containerPort/tcp"
                val parts = host.split('|', limit = 2)
                put(
                    normalized,
                    listOf(
                        PortBindingCreate(
                            hostIp = parts.takeIf { it.size == 2 }?.first()?.ifBlank { null },
                            hostPort = parts.last(),
                        ),
                    ),
                )
            }
        }
    }

private fun ContainerEditConfig.toDraft(): ContainerConfigurationDraft = ContainerConfigurationDraft(
    name = name,
    image = image,
    command = command.orEmpty().joinToString("\n"),
    entrypoint = entrypoint.orEmpty().joinToString("\n"),
    workingDirectory = workingDir.orEmpty(),
    user = user.orEmpty(),
    environment = environment.orEmpty().joinToString("\n"),
    labels = labels.orEmpty().entries.joinToString("\n") { "${it.key}=${it.value}" },
    binds = hostConfig.binds.orEmpty().joinToString("\n"),
    ports = hostConfig.portBindings.orEmpty().flatMap { (containerPort, bindings) ->
        bindings.map { binding ->
            "$containerPort=${binding.hostIp?.takeIf(String::isNotBlank)?.let { "$it|" }.orEmpty()}${binding.hostPort.orEmpty()}"
        }
    }.joinToString("\n"),
    networks = networks.orEmpty().keys.joinToString("\n"),
    networkSettings = networks.orEmpty(),
    restartPolicy = hostConfig.restartPolicy?.name ?: "no",
    privileged = hostConfig.privileged == true,
    autoRemove = hostConfig.autoRemove == true,
    readOnlyRootFilesystem = hostConfig.readonlyRootfs == true,
    memoryMb = ((hostConfig.memory ?: 0L) / (1024L * 1024L)).toString(),
    cpus = ((hostConfig.nanoCpus ?: 0L) / 1_000_000_000.0).toString(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContainerConfigurationScreen(
    mode: ContainerConfigurationMode,
    containerId: String? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val environmentId = manager.activeEnvironmentId
    val environmentName = manager.activeEnvironmentName
    val session = manager.authenticatedClientScope()
    val scope = rememberCoroutineScope()
    var draft by remember(containerId, environmentId.rawValue) { mutableStateOf(ContainerConfigurationDraft()) }
    var loading by remember { mutableStateOf(mode == ContainerConfigurationMode.EDIT) }
    var saving by remember { mutableStateOf(false) }
    var editDisabledReason by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingPayload by remember { mutableStateOf<ContainerEdit?>(null) }
    ProtectSensitiveWindow()

    LaunchedEffect(client, containerId, environmentId.rawValue) {
        if (mode != ContainerConfigurationMode.EDIT) return@LaunchedEffect
        val id = containerId ?: return@LaunchedEffect
        val captured = session ?: return@LaunchedEffect
        loading = true
        try {
            val config = captured.client.containers.editConfig(environmentId, id)
            if (!manager.isCurrent(captured)) return@LaunchedEffect
            if (config.editDisabled == true || config.isCompose == true) {
                editDisabledReason = "This container is managed by Compose${config.composeProject?.let { " project $it" }.orEmpty()}. Edit the project instead."
            }
            draft = config.toDraft()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            error = friendlyErrorMessage(exception)
        } finally {
            loading = false
        }
    }

    fun submit(edit: ContainerEdit? = null) {
        val captured = session ?: return
        scope.launch {
            saving = true
            error = null
            try {
                val id = when (mode) {
                    ContainerConfigurationMode.CREATE -> {
                        val payload = buildContainerConfigurationPayload(mode, draft)
                        if (payload.errors.isNotEmpty()) {
                            error = payload.errors.joinToString("\n")
                            return@launch
                        }
                        captured.client.containers.create(environmentId, requireNotNull(payload.create)).id
                    }
                    ContainerConfigurationMode.EDIT -> captured.client.containers.edit(
                        environmentId,
                        requireNotNull(containerId),
                        requireNotNull(edit),
                    ).id
                }
                if (!manager.isCurrent(captured)) return@launch
                manager.invalidateReadCache(environmentId, ReadResource.CONTAINERS)
                onSaved(id)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                if (manager.isCurrent(captured)) error = friendlyErrorMessage(exception)
            } finally {
                if (manager.isCurrent(captured)) saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == ContainerConfigurationMode.CREATE) "Create Container" else "Edit Container") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            editDisabledReason?.let { FormErrorRow(it) }
            error?.let { FormErrorRow(it) }
            Text(
                "Target: $environmentName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsSectionHeader("Identity & image")
            FormField("Name", draft.name, mode == ContainerConfigurationMode.CREATE) { draft = draft.copy(name = it) }
            FormField("Image", draft.image) { draft = draft.copy(image = it) }
            FormField("Working directory", draft.workingDirectory) { draft = draft.copy(workingDirectory = it) }
            FormField("User", draft.user) { draft = draft.copy(user = it) }
            SettingsSectionHeader("Process")
            MultiLineField("Command · one argument per line", draft.command) { draft = draft.copy(command = it) }
            MultiLineField("Entrypoint · one argument per line", draft.entrypoint) { draft = draft.copy(entrypoint = it) }
            SettingsSectionHeader("Environment & labels")
            MultiLineField("Environment · NAME=VALUE", draft.environment) { draft = draft.copy(environment = it) }
            MultiLineField("Labels · NAME=VALUE", draft.labels) { draft = draft.copy(labels = it) }
            SettingsSectionHeader("Host")
            MultiLineField("Binds · source:target[:mode]", draft.binds) { draft = draft.copy(binds = it) }
            MultiLineField("Ports · 80/tcp=8080", draft.ports) { draft = draft.copy(ports = it) }
            MultiLineField("Networks · one name per line", draft.networks) { draft = draft.copy(networks = it) }
            LabeledPicker("Restart policy", draft.restartPolicy, listOf("no", "always", "unless-stopped", "on-failure"), { it }, { draft = draft.copy(restartPolicy = it) })
            LabeledToggle("Privileged", draft.privileged, { draft = draft.copy(privileged = it) })
            LabeledToggle("Auto remove", draft.autoRemove, { draft = draft.copy(autoRemove = it) })
            LabeledToggle("Read-only root filesystem", draft.readOnlyRootFilesystem, { draft = draft.copy(readOnlyRootFilesystem = it) })
            FormField("Memory (MB)", draft.memoryMb) { draft = draft.copy(memoryMb = it) }
            FormField("CPUs", draft.cpus) { draft = draft.copy(cpus = it) }
            Button(
                onClick = {
                    val payload = buildContainerConfigurationPayload(mode, draft)
                    if (payload.errors.isNotEmpty()) error = payload.errors.joinToString("\n")
                    else if (mode == ContainerConfigurationMode.EDIT) pendingPayload = payload.edit
                    else submit()
                },
                enabled = !saving && editDisabledReason == null,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (saving) CircularProgressIndicator() else Text(if (mode == ContainerConfigurationMode.CREATE) "Create Container" else "Review & Recreate")
            }
        }
    }

    pendingPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingPayload = null },
            title = { Text("Recreate ${draft.name}?") },
            text = { Text("Arcane will stop and replace this container in $environmentName. Its ID may change and the workload may be briefly unavailable. Unedited configuration remains preserved by the server.") },
            confirmButton = { TextButton(onClick = { pendingPayload = null; submit(payload) }) { Text("Recreate") } },
            dismissButton = { TextButton(onClick = { pendingPayload = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FormField(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun MultiLineField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = 3,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
