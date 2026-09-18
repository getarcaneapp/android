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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.LabeledToggle
import app.getarcane.sdk.models.container.ContainerCommitRequest
import app.getarcane.sdk.models.container.ContainerGenerateComposeRequest
import app.getarcane.sdk.models.project.CreateProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContainerCommitScreen(containerId: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val session = manager.authenticatedClientScope()
    val environmentId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    var repository by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("latest") }
    var comment by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var noPause by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    fun commit() {
        if (repository.isBlank()) {
            error = "Repository is required so the committed image can be found later."
            return
        }
        val captured = session ?: return
        scope.launch {
            busy = true
            error = null
            try {
                val committed = captured.client.containers.commit(
                    environmentId,
                    containerId,
                    ContainerCommitRequest(
                        repository = repository.trim(),
                        tag = tag.trim().ifBlank { null },
                        comment = comment.ifBlank { null },
                        author = author.ifBlank { null },
                        noPause = noPause,
                    ),
                )
                if (!manager.isCurrent(captured)) return@launch
                result = committed.id
                manager.invalidateReadCache(environmentId, ReadResource.IMAGES)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                if (manager.isCurrent(captured)) error = friendlyErrorMessage(exception)
            } finally {
                if (manager.isCurrent(captured)) busy = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Commit Container") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Create an image from this container in ${manager.activeEnvironmentName}. Arcane pauses the container during commit unless you opt out.")
            OutlinedTextField(repository, { repository = it }, label = { Text("Repository") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tag, { tag = it }, label = { Text("Tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(comment, { comment = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LabeledToggle("Do not pause container", noPause, { noPause = it })
            error?.let { FormErrorRow(it) }
            result?.let { Text("Created image $it") }
            Button(onClick = ::commit, enabled = !busy && result == null, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator() else Text("Commit to Image")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContainerComposeScreen(
    containerId: String,
    onBack: () -> Unit,
    onProjectCreated: (String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val session = manager.authenticatedClientScope()
    val environmentId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    var composeContent by remember { mutableStateOf<String?>(null) }
    var projectName by remember(containerId) { mutableStateOf("container-${containerId.take(8)}") }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session, environmentId.rawValue, containerId) {
        val captured = session ?: return@LaunchedEffect
        loading = true
        try {
            val generated = captured.client.containers.generateCompose(
                environmentId,
                ContainerGenerateComposeRequest(listOf(containerId)),
            )
            if (manager.isCurrent(captured)) composeContent = generated.composeContent
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            if (manager.isCurrent(captured)) error = friendlyErrorMessage(exception)
        } finally {
            if (manager.isCurrent(captured)) loading = false
        }
    }

    fun createProject() {
        val captured = session ?: return
        val content = composeContent ?: return
        if (projectName.isBlank()) {
            error = "Project name is required."
            return
        }
        scope.launch {
            creating = true
            error = null
            try {
                val project = captured.client.projects.create(
                    environmentId,
                    CreateProject(name = projectName.trim(), composeContent = content),
                    useWorkspaceContract = manager.supportsProjectWorkspaceContract,
                )
                if (!manager.isCurrent(captured)) return@launch
                manager.invalidateReadCache(environmentId, ReadResource.PROJECTS, ReadResource.CONTAINERS)
                onProjectCreated(project.id)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                if (manager.isCurrent(captured)) error = friendlyErrorMessage(exception)
            } finally {
                if (manager.isCurrent(captured)) creating = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Convert to Compose") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Review the server-generated Compose configuration, then create a project in ${manager.activeEnvironmentName}.")
            OutlinedTextField(projectName, { projectName = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = composeContent.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Generated compose.yaml") },
                minLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { FormErrorRow(it) }
            Button(onClick = ::createProject, enabled = !creating && composeContent != null, modifier = Modifier.fillMaxWidth()) {
                if (creating) CircularProgressIndicator() else Text("Create & Open Project")
            }
        }
    }
}
