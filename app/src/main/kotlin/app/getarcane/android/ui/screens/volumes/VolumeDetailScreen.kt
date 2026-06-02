package app.getarcane.android.ui.screens.volumes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.sdk.models.volume.Volume
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeDetailScreen(
    name: String,
    onBack: () -> Unit,
    onBrowse: (String) -> Unit,
    onBackups: (String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<Volume>>(Loadable.Loading) }
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name, refreshKey) {
        if (client == null) return@LaunchedEffect
        state = try {
            Loadable.Success(client.volumes.inspect(envId = envId, name = name))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        sizeBytes = runCatching {
            client.volumes.sizes(envId = envId).firstOrNull { it.name == name }?.size ?: 0L
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    val v = s.value
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(
                                    Modifier.size(56.dp).background(ArcaneOrange.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Storage, null, tint = ArcaneOrange, modifier = Modifier.size(28.dp)) }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(v.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Driver: ${v.driver}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            SectionCard("Details") {
                                DetailRow("Driver", v.driver)
                                DetailRow("Scope", v.scope.replaceFirstChar { it.uppercase() })
                                if (v.mountpoint.isNotEmpty()) DetailRow("Mount Point", v.mountpoint)
                                if (v.createdAt.isNotEmpty()) DetailRow("Created", v.createdAt)
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Size", style = MaterialTheme.typography.bodyMedium)
                                    when {
                                        sizeBytes != null -> Text(formatBytes(sizeBytes!!), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        else -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                                HorizontalDivider()
                                NavRow("Browse Files") { onBrowse(v.name) }
                                NavRow("Backups") { onBackups(v.name) }
                            }
                        }
                        if (v.labels.isNotEmpty()) {
                            item {
                                SectionCard("Labels") {
                                    v.labels.toSortedMap().forEach { (k, value) -> DetailRow(k, value) }
                                }
                            }
                        }
                        if (v.options.isNotEmpty()) {
                            item {
                                SectionCard("Options") {
                                    v.options.toSortedMap().forEach { (k, value) -> DetailRow(k, value) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Volume") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (client != null) scope.launch {
                        runCatching { client.volumes.remove(envId = envId, name = name) }
                            .onSuccess { onBack() }
                            .onFailure { errorMessage = friendlyErrorMessage(it) }
                    }
                }) { Text("Delete") }
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
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) { content() }
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
