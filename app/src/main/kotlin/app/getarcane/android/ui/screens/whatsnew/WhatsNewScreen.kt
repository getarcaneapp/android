package app.getarcane.android.ui.screens.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Brush
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.ui.screens.settings.Pill
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneMint
import app.getarcane.android.ui.theme.ArcaneRed

/** Release-notes list. Port of iOS `WhatsNewView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
    val latestId = ReleaseNotes.latest?.version
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What's New") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(ReleaseNotes.all, key = { it.version }) { note ->
                ReleaseNoteCard(note = note, isCurrent = note.version == latestId)
            }
        }
    }
}

@Composable
private fun ReleaseNoteCard(note: ReleaseNote, isCurrent: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(note.version, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (isCurrent) {
                Pill("Current", ArcaneGreen)
            }
        }

        if (note.new.isNotEmpty()) {
            Section("NEW", Icons.Filled.AutoFixHigh, ArcaneMint, note.new)
        }
        if (note.changed.isNotEmpty()) {
            Section("CHANGED", Icons.Filled.Brush, ArcaneBlue, note.changed)
        }
        if (note.fixed.isNotEmpty()) {
            Section("FIXED", Icons.Filled.BugReport, ArcaneRed, note.fixed)
        }
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, tint: Color, bullets: List<Bullet>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.padding(end = 0.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bullets.forEach { BulletRow(it) }
        }
    }
}

@Composable
private fun BulletRow(bullet: Bullet) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                bullet.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            bullet.badge?.let { Pill(it.label, it.color) }
        }
    }
}
