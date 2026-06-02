package app.getarcane.android.ui.screens.projects

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
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.highlightEnv
import app.getarcane.android.ui.components.highlightYaml

/**
 * Read-only viewer for a project's `compose.yml` / `.env`, with a "Resolve Variables" action that
 * opens [RenderComposeView]. Port of iOS `ComposeFileView` (presented read-only on Android).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeFileScreen(
    projectId: String,
    projectName: String,
    onBack: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    var state by remember { mutableStateOf<Loadable<Pair<String, String>>>(Loadable.Loading) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) } // 0 = compose.yml, 1 = .env
    var resolved by remember { mutableStateOf<String?>(null) }
    var showRender by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, refreshKey) {
        if (client == null) return@LaunchedEffect
        state = try {
            val details = client.projects.compose(envId = envId, projectId = projectId)
            Loadable.Success((resolved ?: details.composeContent.orEmpty()) to details.envContent.orEmpty())
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Project Files", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            val compose = (state as? Loadable.Success)?.value?.first
            if (tab == 0 && !compose.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showRender = true },
                    icon = { Icon(Icons.Filled.DataObject, null) },
                    text = { Text("Variables") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    val (compose, env) = s.value
                    Column(Modifier.fillMaxSize()) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SegmentedButton(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("compose.yml") }
                            SegmentedButton(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text(".env") }
                        }
                        val text = if (tab == 0) compose else env
                        if (text.isEmpty()) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(
                                    if (tab == 0) "No compose file." else "No .env file.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = if (tab == 0) highlightYaml(text) else highlightEnv(text),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRender) {
        val s = state as? Loadable.Success
        RenderComposeView(
            initialCompose = s?.value?.first.orEmpty(),
            initialEnv = s?.value?.second.orEmpty(),
            onApply = { result ->
                resolved = result
                refreshKey++ // reload so the viewer reflects the resolved compose
                showRender = false
            },
            onCancel = { showRender = false },
        )
    }
}
