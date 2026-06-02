package app.getarcane.android.ui.screens.ports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.sdk.models.port.PortMapping

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortDetailScreen(portId: String, onBack: () -> Unit) {
    val port = PortStore.get(portId)
    val title = port?.containerName ?: "Port"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (port == null) {
                ContentUnavailable("Port Unavailable", Icons.AutoMirrored.Filled.ArrowBack, "This port mapping is no longer available.", "Back", onBack)
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Section("Mapping") {
                            if (port.hostPort != null) {
                                MonoRow("Host", hostString(port))
                                Row2("Host Port", port.hostPort.toString())
                            } else {
                                Row2("Exposure", "Internal only")
                            }
                            Row2("Container Port", port.containerPort.toString())
                            Row2("Protocol", port.protocolName.uppercase())
                            Row2("Published", if (port.isPublished) "Yes" else "No")
                        }
                    }
                    item {
                        Section("Container") {
                            Row2("Name", port.containerName)
                            MonoRow("ID", port.containerId)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) { content() }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

@Composable
private fun MonoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.6f))
    }
}
