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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/**
 * App-level settings: appearance entry plus an About section of external links, What's New, and
 * version/build. Port of iOS `AppSettingsView` (the iOS-only cache-size/clear-cache controls are
 * omitted since the Android client has no exposed response/image cache to size or clear).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onWhatsNew: () -> Unit,
) {
    val context = LocalContext.current
    val pkg = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = pkg?.versionName ?: "1.0"
    @Suppress("DEPRECATION")
    val versionCode = pkg?.versionCode?.toString() ?: "—"

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

            SettingsSectionHeader("About")
            SettingsRow(
                title = "Documentation",
                icon = Icons.Filled.Language,
                iconColor = ArcaneBlue,
                onClick = { openUrl("https://getarcane.app") },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Share Arcane",
                icon = Icons.Filled.Share,
                iconColor = ArcaneBlue,
                onClick = { shareUrl("https://getarcane.app") },
            )
            SettingsRow(
                title = "Join the Discord",
                icon = Icons.AutoMirrored.Filled.Chat,
                iconColor = ArcaneIndigo,
                onClick = { openUrl("https://discord.gg/WyXYpdyV3Z") },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Contribute on GitHub",
                icon = Icons.Filled.Code,
                iconColor = ArcanePurple,
                onClick = { openUrl("https://github.com/getarcaneapp/ios") },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Report an Issue",
                icon = Icons.Filled.ReportProblem,
                iconColor = ArcaneOrange,
                onClick = { openUrl("https://github.com/getarcaneapp/ios/issues") },
                trailing = { ExternalLinkTrailing() },
            )
            SettingsRow(
                title = "Privacy Policy",
                icon = Icons.Filled.PanTool,
                iconColor = ArcaneGray,
                onClick = { openUrl("https://getarcane.app/privacy") },
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
}
