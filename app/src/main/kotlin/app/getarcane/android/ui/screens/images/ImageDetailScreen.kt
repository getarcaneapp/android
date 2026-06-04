package app.getarcane.android.ui.screens.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.image.ImageDetailConfig
import app.getarcane.sdk.models.image.ImageDetailSummary
import app.getarcane.sdk.models.imageupdate.ImageUpdateResponse
import app.getarcane.sdk.models.vulnerability.VulnerabilityScanSummary
import app.getarcane.sdk.models.vulnerability.VulnerabilityScannerStatus
import kotlinx.coroutines.launch

private fun ImageDetailSummary.displayName(): String =
    repoTags.firstOrNull { it.isNotBlank() && it != "<none>:<none>" }
        ?: id.removePrefix("sha256:").take(12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenVulnerabilities: (imageId: String, displayName: String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<ImageDetailSummary>>(Loadable.Loading) }
    var updateInfo by remember { mutableStateOf<ImageUpdateResponse?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var vulnSummary by remember { mutableStateOf<VulnerabilityScanSummary?>(null) }
    var scannerStatus by remember { mutableStateOf<VulnerabilityScannerStatus?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (client == null) return@LaunchedEffect
        state = try {
            Loadable.Success(client.images.inspect(envId = envId, id = id))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        // Vulnerability summary + scanner status (best-effort).
        scannerStatus =
            runCatching { client.vulnerabilities.scannerStatus(envId = envId) }.getOrNull()
        vulnSummary = runCatching {
            client.vulnerabilities.scanSummary(
                envId = envId,
                imageId = id
            )
        }.getOrNull()
    }

    fun checkForUpdate() {
        if (client == null) return
        isCheckingUpdate = true
        scope.launch {
            try {
                updateInfo = client.images.checkUpdateByIDPost(envId = envId, imageId = id)
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun removeImage() {
        if (client == null) return
        scope.launch {
            try {
                client.images.remove(envId = envId, id = id)
                onBack()
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        confirmDelete = true
                    }) { Icon(Icons.Filled.Delete, "Delete", tint = ArcaneRed) }
                },
            )
        },
    ) { padding ->
        Box(Modifier
            .fillMaxSize()
            .padding(padding)) {
            when (val s = state) {
                is Loadable.Loading -> SkeletonListLoadingView()
                is Loadable.Error -> ContentUnavailable("Error", Icons.Filled.Warning, s.message)
                is Loadable.Success -> {
                    val d = s.value
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { ImageHeader(d) }

                        item {
                            DetailSection("Details") {
                                LabeledRow("Created", formatIsoDate(d.created))
                                LabeledRow("Architecture", d.architecture)
                                LabeledRow("OS", d.os)
                                LabeledRow("Size", formatBytes(d.size))
                                if (d.author.isNotBlank()) LabeledRow("Author", d.author)
                            }
                        }

                        if (d.repoTags.isNotEmpty()) {
                            item {
                                DetailSection("Tags") {
                                    d.repoTags.forEach { MonoRow(it) }
                                    UpdateCheckRow(
                                        info = updateInfo,
                                        isChecking = isCheckingUpdate,
                                        onCheck = { checkForUpdate() },
                                    )
                                }
                            }
                        }

                        if (d.repoDigests.isNotEmpty()) {
                            item {
                                DetailSection("Digests") {
                                    d.repoDigests.forEach { MonoRow(it, maxLines = 2) }
                                }
                            }
                        }

                        item { ImageConfigSection(d.config) }

                        item {
                            VulnerabilitiesSection(
                                summary = vulnSummary,
                                scannerStatus = scannerStatus,
                                onOpen = { onOpenVulnerabilities(d.id, d.displayName()) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove Image") },
            text = { Text("This will remove the image from the host.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; removeImage() }) {
                    Text(
                        "Remove"
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun ImageHeader(d: ImageDetailSummary) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(ArcanePurple.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Layers, null, tint = ArcanePurple, modifier = Modifier.size(28.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                d.displayName(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                d.id,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(d.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UpdateCheckRow(info: ImageUpdateResponse?, isChecking: Boolean, onCheck: () -> Unit) {
    when {
        isChecking -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Checking for updates…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(Modifier.size(18.dp))
            }
        }

        info != null && !info.error.isNullOrEmpty() ->
            Row(
                Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Warning, null, tint = ArcaneRed, modifier = Modifier.size(16.dp))
                Text(info.error!!, style = MaterialTheme.typography.labelMedium, color = ArcaneRed)
            }

        info != null && info.hasUpdate ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowCircleUp,
                    null,
                    tint = ArcaneOrange,
                    modifier = Modifier.size(18.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Update available",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val latest = info.latestVersion
                    if (!latest.isNullOrEmpty() && info.currentVersion.isNotEmpty() && latest != info.currentVersion) {
                        Text(
                            "${info.currentVersion} → $latest",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onCheck) { Text("Recheck") }
            }

        info != null ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    tint = ArcaneGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text("Up to date", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCheck) { Text("Recheck") }
            }

        else ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCheck)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.SystemUpdateAlt,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Check for updates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
    }
}

@Composable
private fun ImageConfigSection(config: ImageDetailConfig) {
    DetailSection("Image Config") {
        config.cmd?.takeIf { it.isNotEmpty() }?.let { LabeledRow("CMD", it.joinToString(" ")) }
        config.entrypoint?.takeIf { it.isNotEmpty() }
            ?.let { LabeledRow("Entrypoint", it.joinToString(" ")) }
        config.workingDir?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Working Dir", it) }
        config.user?.takeIf { it.isNotEmpty() }?.let { LabeledRow("User", it) }
        config.env?.takeIf { it.isNotEmpty() }?.let { env ->
            ExpandableRow("Env Vars (${env.size})") {
                env.forEach { MonoRow(it, maxLines = 2) }
            }
        }
        config.labels?.takeIf { it.isNotEmpty() }?.let { labels ->
            ExpandableRow("Labels (${labels.size})") {
                labels.entries.sortedBy { it.key }.forEach { (k, v) -> LabeledRow(k, v) }
            }
        }
    }
}

@Composable
private fun ExpandableRow(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.padding(start = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) { content() }
        }
    }
}

@Composable
private fun VulnerabilitiesSection(
    summary: VulnerabilityScanSummary?,
    scannerStatus: VulnerabilityScannerStatus?,
    onOpen: () -> Unit,
) {
    DetailSection("Vulnerabilities") {
        when {
            summary != null ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpen)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SeveritySummaryRow(
                            summary = summary.summary,
                            scanTime = summary.scanTime.toString(),
                            status = summary.status.label(),
                            error = summary.error,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            scannerStatus?.available == false ->
                Row(
                    Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Security,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Scanner unavailable on host",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            else ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpen)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Security,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Not scanned yet — open to scan",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        }
    }
}
