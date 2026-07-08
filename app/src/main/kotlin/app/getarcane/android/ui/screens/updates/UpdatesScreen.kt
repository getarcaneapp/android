package app.getarcane.android.ui.screens.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.nav.PopToRootOnSignal
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.environment.Environment

/**
 * Updates tab with its own nested back stack. Mirrors the iOS `UpdatesView`:
 * image-update results plus actions to run the updater or view history for a picked environment.
 */
@Composable
fun UpdatesScreen(popToRootSignal: Int = 0) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val nav = rememberNavController()
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "updates")
    var environments by remember { mutableStateOf<List<Environment>>(emptyList()) }
    var pickerMode by remember { mutableStateOf<PickerMode?>(null) }

    LaunchedEffect(client) {
        if (client == null) return@LaunchedEffect
        environments = runCatching { client.environments.list().data }.getOrDefault(emptyList())
    }

    fun launch(mode: PickerMode) {
        when (environments.size) {
            0 -> Unit
            1 -> nav.navigate(mode.route(environments.first()))
            else -> pickerMode = mode
        }
    }

    NavHost(navController = nav, startDestination = "updates") {
        composable("updates") {
            ImageUpdatesScreen(
                onRunUpdater = { launch(PickerMode.RunUpdater) },
                onHistory = { launch(PickerMode.History) },
                actionsEnabled = environments.isNotEmpty(),
            )
        }
        composable("run/{envId}/{envName}") { entry ->
            val envId = entry.arguments?.getString("envId").orEmpty().decodeArg()
            val envName = entry.arguments?.getString("envName").orEmpty().decodeArg()
            UpdaterRunScreen(
                onBack = { nav.popBackStack() },
                environmentId = EnvironmentId(envId),
                environmentName = envName,
            )
        }
        composable("history/{envId}/{envName}") { entry ->
            val envId = entry.arguments?.getString("envId").orEmpty().decodeArg()
            val envName = entry.arguments?.getString("envName").orEmpty().decodeArg()
            UpdaterHistoryScreen(
                onBack = { nav.popBackStack() },
                environmentId = EnvironmentId(envId),
                environmentName = envName,
            )
        }
    }

    pickerMode?.let { mode ->
        EnvironmentPickerDialog(
            environments = environments,
            mode = mode,
            onPick = { env ->
                pickerMode = null
                nav.navigate(mode.route(env))
            },
            onDismiss = { pickerMode = null },
        )
    }
}

private enum class PickerMode(val title: String, private val routePrefix: String) {
    RunUpdater("Run Updater", "run"),
    History("Updater History", "history"),
    ;

    fun route(env: Environment): String = "$routePrefix/${env.id.encodeArg()}/${(env.name ?: env.id).encodeArg()}"
}

@Composable
private fun EnvironmentPickerDialog(
    environments: List<Environment>,
    mode: PickerMode,
    onPick: (Environment) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    mode.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn {
                    items(environments, key = { it.id }) { env ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(env) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Filled.Circle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(env.name ?: env.id, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    env.apiUrl.ifEmpty { env.id },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusBadge(status = env.status)
                        }
                        HorizontalDivider(Modifier.padding(start = 20.dp))
                    }
                }
                Text(
                    "Pick an environment to ${if (mode == PickerMode.RunUpdater) "run the updater on" else "view history for"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun String.encodeArg(): String = java.net.URLEncoder.encode(this, "UTF-8")

private fun String.decodeArg(): String =
    runCatching { java.net.URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
