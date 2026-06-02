package app.getarcane.android.ui.screens.environments

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.environment.EnvironmentVersion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentDetailScreen(id: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val envId = remember(id) { EnvironmentId(id) }

    var state by remember { mutableStateOf<Loadable<Environment>>(Loadable.Loading) }
    var version by remember { mutableStateOf<EnvironmentVersion?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id, refreshKey) {
        if (client == null) return@LaunchedEffect
        state = try {
            Loadable.Success(client.environments.get(envId))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        version = runCatching { client.environments.version(envId) }.getOrNull()
    }

    val env = (state as? Loadable.Success)?.value
    val isActive = id == manager.activeEnvironmentId.rawValue

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(env?.label ?: "Environment", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    val e = s.value
                    val statusColor = if (e.isOnline) ArcaneGreen else ArcaneGray
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(
                                    Modifier.size(56.dp).background(statusColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Dns, null, tint = statusColor, modifier = Modifier.size(28.dp)) }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(e.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (e.apiUrl.isNotEmpty()) {
                                        Text(e.apiUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusBadge(e.status)
                                }
                            }
                        }

                        // Set-active / test actions, mirroring the iOS detail action toolbar.
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isActive) {
                                    Button(
                                        onClick = { manager.setActiveEnvironment(envId, e.label) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                                        Text(" Set Active")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        testing = true
                                        testResult = null
                                        if (client != null) scope.launch {
                                            val result = runCatching { client.environments.testConnection(envId) }
                                            testResult = result.fold(
                                                onSuccess = { it.message ?: it.status },
                                                onFailure = { friendlyErrorMessage(it) },
                                            )
                                            testing = false
                                        }
                                    },
                                    enabled = !testing,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.Lan, null, modifier = Modifier.size(18.dp))
                                    Text(" Test Connection")
                                }
                            }
                        }
                        testResult?.let { msg ->
                            item { Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }

                        item {
                            Section("Details") {
                                Row2("ID", e.id)
                                if (e.apiUrl.isNotEmpty()) Row2("URL", e.apiUrl)
                                Row2("Status", e.status.replaceFirstChar { it.uppercase() })
                                Row2("Type", if (e.isEdge) "Edge" else "Local")
                                Row2("Enabled", if (e.enabled) "Yes" else "No")
                                if (isActive) Row2("Active", "Yes")
                            }
                        }

                        version?.let { v ->
                            item {
                                Section("Version") {
                                    Row2("Version", v.displayVersion)
                                    if (v.shortRevision.isNotEmpty()) Row2("Revision", v.shortRevision)
                                    Row2("Go", v.goVersion)
                                    if (v.updateAvailable) {
                                        HorizontalDivider()
                                        Row2("Update Available", v.newestVersion ?: "Yes")
                                    }
                                }
                            }
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
