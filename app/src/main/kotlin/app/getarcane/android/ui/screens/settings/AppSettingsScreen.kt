package app.getarcane.android.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneYellow
import androidx.core.net.toUri
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.formatBytes
import app.getarcane.android.ui.theme.ArcaneRed
import kotlinx.coroutines.launch

/**
 * App-level settings: appearance entry plus an About section of external links, What's New, and
 * version/build. Port of iOS `AppSettingsView`, including explicit size and clearing controls for
 * the bounded resilient-read cache. The cache remains separate from image loading caches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onWhatsNew: () -> Unit,
) {
    val context = LocalContext.current
    val manager = LocalArcaneManager.current
    val scope = rememberCoroutineScope()
    val appVersion = remember { installedAppVersion() }
    val versionName = appVersion.name
    val versionCode = appVersion.code.toString()
    var cacheBytes by remember { mutableStateOf(0L) }
    var confirmClearCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { cacheBytes = manager.readCache.diskBytes() }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    fun shareUrl(url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader("Application")
            SettingsRow(
                title = "Appearance",
                icon = Icons.Filled.Brush,
                iconColor = ArcanePink,
                onClick = onAppearance,
                trailing = { ChevronTrailing() },
            )

            SettingsSectionHeader("Storage")
            SettingsRow(
                title = "Cached data",
                subtitle = "Stale read fallback; excluded from backup and device transfer",
                icon = Icons.Filled.Storage,
                iconColor = ArcaneBlue,
                trailing = {
                    Text(
                        formatBytes(cacheBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingsRow(
                title = "Clear Cache",
                icon = Icons.Filled.DeleteForever,
                iconColor = ArcaneRed,
                titleColor = ArcaneRed,
                onClick = { confirmClearCache = true },
            )

            SettingsSectionHeader("About")
            SettingsRow(
                title = "Documentation",
                icon = Icons.Filled.Language,
                iconColor = ArcaneBlue,
                onClick = { openUrl(AppLinks.DOCUMENTATION) },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Share Arcane",
                icon = Icons.Filled.Share,
                iconColor = ArcaneBlue,
                onClick = { shareUrl(AppLinks.PROJECT_HOME) },
            )
            SettingsRow(
                title = "Join the Discord",
                icon = Icons.AutoMirrored.Filled.Chat,
                iconColor = ArcaneIndigo,
                onClick = { openUrl(AppLinks.COMMUNITY_SUPPORT) },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Contribute on GitHub",
                icon = Icons.Filled.Code,
                iconColor = ArcanePurple,
                onClick = { openUrl(AppLinks.ANDROID_SOURCE) },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Report an Issue",
                icon = Icons.Filled.ReportProblem,
                iconColor = ArcaneOrange,
                onClick = { openUrl(AppLinks.ANDROID_ISSUES) },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Privacy Policy",
                icon = Icons.Filled.PanTool,
                iconColor = ArcaneGray,
                onClick = { openUrl(AppLinks.PRIVACY_POLICY) },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "What's New",
                icon = Icons.Filled.AutoAwesome,
                iconColor = ArcaneYellow,
                onClick = onWhatsNew,
                trailing = { ChevronTrailing() },
            )
            SettingsRow(
                title = "Version",
                icon = Icons.Filled.Info,
                iconColor = ArcaneGray,
                trailing = {
                    Text(
                        versionName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingsRow(
                title = "Build",
                icon = Icons.Filled.Build,
                iconColor = ArcaneGray,
                trailing = {
                    Text(
                        versionCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                },
            )
        }
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Clear cached data?") },
            text = { Text("Offline Dashboard and resource-list data will be removed. You can reload it while connected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCache = false
                    scope.launch {
                        manager.readCache.clear()
                        cacheBytes = manager.readCache.diskBytes()
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearCache = false }) { Text("Cancel") } },
        )
    }
}
