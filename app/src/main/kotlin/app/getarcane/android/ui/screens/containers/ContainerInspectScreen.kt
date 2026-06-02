package app.getarcane.android.ui.screens.containers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.sdk.models.container.ContainerDetails
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

/**
 * Pretty-printed `inspect` JSON for a container, with a copy button + line filter. Port of iOS
 * `ContainerInspectView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerInspectScreen(id: String, onClose: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val context = LocalContext.current

    var state by remember { mutableStateOf<Loadable<String>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(id, retryKey) {
        if (client == null) return@LaunchedEffect
        state = try {
            val details: ContainerDetails = client.containers.inspect(envId = envId, id = id)
            Loadable.Success(prettyJson.encodeToString(ContainerDetails.serializer(), details))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val raw = (state as? Loadable.Success)?.value.orEmpty()
    val filtered = remember(raw, search) {
        val trimmed = search.trim()
        if (trimmed.isEmpty()) raw
        else raw.lineSequence().filter { it.contains(trimmed, ignoreCase = true) }.joinToString("\n")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inspect") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Done") } },
                actions = {
                    IconButton(
                        onClick = {
                            copyToClipboard(context, raw)
                            copied = true
                        },
                        enabled = raw.isNotEmpty(),
                    ) {
                        Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, "Copy")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading inspect…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retryKey++ }) { Text("Retry") }
                    }
                }
                is Loadable.Success -> {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Filter lines") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    ) {
                        SelectionContainer(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    filtered,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("inspect", text))
}
