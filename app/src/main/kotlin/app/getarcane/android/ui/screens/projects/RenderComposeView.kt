package app.getarcane.android.ui.screens.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getarcane.android.ui.components.ContentUnavailable

/**
 * Resolves `${VAR}` placeholders in a compose file. Mirrors iOS `RenderComposeView`: scans for
 * variables (with inline `${VAR:-default}` and `.env` defaults), lets the user fill values, then
 * previews / applies the substituted YAML.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderComposeView(
    initialCompose: String,
    initialEnv: String,
    onApply: (String) -> Unit,
    onCancel: () -> Unit,
) {
    // (key, default) discovered in order of first appearance.
    val scan = remember(initialCompose, initialEnv) { scanForVariables(initialCompose, initialEnv) }
    val values = remember { mutableStateMapOf<String, String>().apply { scan.forEach { put(it.name, it.default) } } }
    var showPreview by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showPreview) "Resolved YAML" else "Resolve Variables") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = {
                    if (scan.isNotEmpty()) {
                        if (showPreview) {
                            TextButton(onClick = { showPreview = false }) { Text("Edit") }
                        } else {
                            TextButton(onClick = { showPreview = true }) { Text("Preview") }
                        }
                        TextButton(onClick = { onApply(substitute(initialCompose, scan, values)) }) { Text("Use Resolved") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                scan.isEmpty() -> ContentUnavailable(
                    "No Variables",
                    Icons.Filled.CheckCircle,
                    "This compose file has no \${VAR} placeholders to resolve.",
                )
                showPreview -> {
                    Text(
                        substitute(initialCompose, scan, values),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        item {
                            Text(
                                "Fill values for the placeholders found in this compose file. Empty values fall back to the default (if any).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        items(scan, key = { it.name }) { variable ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                androidx.compose.foundation.layout.Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(variable.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    if (variable.default.isNotEmpty()) {
                                        Text(
                                            "default: ${variable.default}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = values[variable.name] ?: "",
                                    onValueChange = { values[variable.name] = it },
                                    placeholder = { Text(variable.default.ifEmpty { "value" }) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class ComposeVariable(val name: String, val default: String)

private val VAR_PATTERN =
    Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::?[-?]([^}]*))?\}""")

/** Scan for `${VAR}` placeholders, capturing inline and `.env` defaults. iOS `scanForVariables`. */
internal fun scanForVariables(compose: String, env: String): List<ComposeVariable> {
    val envDefaults = parseEnv(env)
    val order = LinkedHashMap<String, String>()
    for (match in VAR_PATTERN.findAll(compose)) {
        val name = match.groupValues[1]
        if (name.isEmpty()) continue
        val inlineDefault = match.groups[2]?.value
        val existing = order[name]
        val resolved = when {
            !existing.isNullOrEmpty() -> existing
            !inlineDefault.isNullOrEmpty() -> inlineDefault
            else -> envDefaults[name] ?: ""
        }
        order[name] = resolved
    }
    return order.map { ComposeVariable(it.key, it.value) }
}

/** Substitute each placeholder with its filled value (or default). iOS `substitute(in:)`. */
internal fun substitute(source: String, variables: List<ComposeVariable>, values: Map<String, String>): String {
    val defaults = variables.associate { it.name to it.default }
    return VAR_PATTERN.replace(source) { match ->
        val name = match.groupValues[1]
        if (name.isEmpty()) return@replace match.value
        val filled = values[name]
        if (!filled.isNullOrEmpty()) filled else defaults[name] ?: ""
    }
}

private fun parseEnv(contents: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (raw in contents.split('\n', '\r')) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val eq = line.indexOf('=')
        if (eq < 0) continue
        val key = line.substring(0, eq).trim()
        var value = line.substring(eq + 1).trim()
        if (value.length >= 2 &&
            ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))
        ) {
            value = value.substring(1, value.length - 1)
        }
        if (key.isNotEmpty()) result[key] = value
    }
    return result
}
