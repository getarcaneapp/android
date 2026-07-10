package app.getarcane.android.ui.screens.settings.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.screens.settings.ConfirmDialog
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.system.UpgradeCheckResult
import app.getarcane.sdk.models.user.isAdmin
import kotlinx.coroutines.launch

private sealed interface UpgradePhase {
    data object Checking : UpgradePhase
    data class Ready(val result: UpgradeCheckResult) : UpgradePhase
    data class CheckFailed(val message: String) : UpgradePhase
    data object Triggering : UpgradePhase
    data class Triggered(val message: String) : UpgradePhase
    data class TriggerFailed(val message: String) : UpgradePhase
}

/** Self-upgrade check + trigger flow. Port of iOS `SystemUpgradeView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemUpgradeScreen(
    environmentId: EnvironmentId? = null,
    onBack: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = environmentId ?: manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    val isAdmin = manager.currentUser?.isAdmin ?: false

    var phase by remember { mutableStateOf<UpgradePhase>(UpgradePhase.Checking) }
    var showConfirm by remember { mutableStateOf(false) }

    fun check() {
        val c = client ?: run { phase = UpgradePhase.CheckFailed("Not connected"); return }
        scope.launch {
            phase = UpgradePhase.Checking
            phase = try {
                UpgradePhase.Ready(c.system.checkUpgrade(envId))
            } catch (e: Throwable) {
                UpgradePhase.CheckFailed(friendlyErrorMessage(e))
            }
        }
    }

    fun trigger() {
        val c = client ?: run { phase = UpgradePhase.TriggerFailed("Not connected"); return }
        scope.launch {
            phase = UpgradePhase.Triggering
            phase = try {
                c.system.triggerUpgrade(envId)
                UpgradePhase.Triggered("Upgrade initiated. Arcane will restart shortly.")
            } catch (e: Throwable) {
                UpgradePhase.TriggerFailed(friendlyErrorMessage(e))
            }
        }
    }

    LaunchedEffect(envId, isAdmin) { if (isAdmin) check() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upgrade Arcane") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isAdmin && phase is UpgradePhase.Ready) {
                        IconButton(onClick = { check() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!isAdmin) {
                ContentUnavailable("Admins Only", Icons.Filled.Lock, "Upgrading Arcane requires an administrator account.")
                return@Column
            }
            when (val p = phase) {
                is UpgradePhase.Checking -> LoadingCard()
                is UpgradePhase.Ready -> ReadyContent(p.result, onUpgradeClick = { showConfirm = true })
                is UpgradePhase.CheckFailed -> ContentUnavailable("Couldn't Check for Upgrade", Icons.Filled.Warning, p.message)
                is UpgradePhase.Triggering -> TriggeringCard()
                is UpgradePhase.Triggered -> TriggeredCard(p.message)
                is UpgradePhase.TriggerFailed -> {
                    ContentUnavailable("Upgrade Failed", Icons.Filled.Warning, p.message)
                    OutlinedButton(onClick = { check() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, null)
                        Text("  Check Again")
                    }
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = "Upgrade Arcane?",
            message = "Arcane will restart. The mobile app may briefly lose connection.",
            confirmLabel = "Upgrade",
            onConfirm = { trigger() },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun LoadingCard() {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text("Checking for upgrade…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyContent(result: UpgradeCheckResult, onUpgradeClick: () -> Unit) {
    HeroCard(
        tint = if (result.canUpgrade) ArcaneBlue else ArcaneGray,
        icon = if (result.canUpgrade) Icons.Filled.ArrowCircleUp else Icons.Filled.Lock,
        title = if (result.canUpgrade) "Upgrade Available" else "Upgrade Unavailable",
        subtitle = result.message,
    )
    if (result.canUpgrade) {
        InfoCard(
            Icons.Filled.Info, ArcaneBlue, "What happens next",
            "A new Arcane container will be created from the latest image and replace this one. The mobile app will briefly lose connection while it restarts.",
        )
        Button(
            onClick = onUpgradeClick,
            colors = ButtonDefaults.buttonColors(containerColor = ArcaneRed),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.ArrowCircleUp, null)
            Text("  Upgrade Arcane")
        }
    } else {
        InfoCard(
            Icons.Filled.Warning, ArcaneOrange, "Self-upgrade is not supported here",
            "Arcane can only self-upgrade when running in a Docker container with access to the Docker socket. Update Arcane from your deployment instead.",
        )
    }
}

@Composable
private fun TriggeringCard() {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(96.dp).background(ArcaneBlue.copy(alpha = 0.18f), CircleShape), Alignment.Center) {
            CircularProgressIndicator()
        }
        Text("Starting upgrade…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Asking Arcane to restart", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TriggeredCard(message: String) {
    HeroCard(ArcaneGreen, Icons.Filled.CheckCircle, "Upgrade Initiated", message)
    InfoCard(
        Icons.Filled.Info, ArcaneBlue, "Reconnecting shortly",
        "A new Arcane container is starting. The mobile app may briefly lose connection — pull to refresh once it's back.",
    )
    CircularProgressIndicator()
}

@Composable
private fun HeroCard(tint: Color, icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(96.dp).background(tint.copy(alpha = 0.18f), CircleShape), Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoCard(icon: ImageVector, tint: Color, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
