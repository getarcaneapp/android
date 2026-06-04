package app.getarcane.android.ui.screens.swarm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.getarcane.android.ui.components.ContentUnavailable

/**
 * Swarm tab. Mirrors the iOS `SwarmView`, which is currently a "coming soon" placeholder while the
 * cluster/services/nodes management UI is reworked. The tab stays in the navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwarmScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Swarm") }) }) { padding ->
        Box(Modifier
            .fillMaxSize()
            .padding(padding)) {
            ContentUnavailable(
                title = "Swarm",
                icon = Icons.Filled.Layers,
                description = "Swarm management is coming soon.",
            )
        }
    }
}
