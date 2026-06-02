package app.getarcane.android.ui.screens.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.highlightEnv
import app.getarcane.android.ui.components.highlightYaml
import app.getarcane.sdk.models.project.CreateProject
import app.getarcane.sdk.models.template.Template
import kotlinx.coroutines.launch

private const val DEFAULT_COMPOSE = "services:\n  app:\n    image: \n    ports:\n      - \"8080:80\"\n"

/**
 * Create a Compose project, optionally seeded from a template.
 * Top section: Project Name + Template.
 * Bottom section: segmented compose.yml / .env editor with syntax highlighting —
 * mirrors [ComposeFileScreen]'s layout exactly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    prefilledName: String? = null,
    prefilledCompose: String? = null,
    prefilledEnv: String? = null,
    templateLabel: String? = null,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val isPrefilled = prefilledCompose != null

    var name by remember { mutableStateOf(prefilledName.orEmpty()) }
    var compose by remember { mutableStateOf(prefilledCompose ?: DEFAULT_COMPOSE) }
    var env by remember { mutableStateOf(prefilledEnv.orEmpty()) }
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var selectedLabel by remember { mutableStateOf(templateLabel ?: "Blank") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showVariables by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) } // 0 = compose.yml, 1 = .env

    LaunchedEffect(Unit) {
        if (!isPrefilled && client != null) {
            templates = runCatching { client.templates.listAll() }.getOrDefault(emptyList())
        }
    }

    fun applyTemplate(template: Template?) {
        templateMenu = false
        if (template == null) { selectedLabel = "Blank"; return }
        selectedLabel = template.name
        scope.launch {
            runCatching {
                val content = client?.templates?.getContent(template.id) ?: return@launch
                compose = content.content
                env = content.envContent
                if (name.isBlank()) name = slugify(content.template.name)
            }.onFailure { error = friendlyErrorMessage(it) }
        }
    }

    fun create() {
        val c = client ?: return
        scope.launch {
            loading = true; error = null
            try {
                c.projects.create(
                    envId = manager.activeEnvironmentId,
                    request = CreateProject(
                        name = name.trim(),
                        composeContent = compose,
                        envContent = env.ifBlank { null },
                    ),
                )
                onSuccess()
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }

    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    // Rebuild only when the active tab changes.
    val syntaxTransform = remember(tab) {
        VisualTransformation { text ->
            val highlighted = if (tab == 0) highlightYaml(text.text) else highlightEnv(text.text)
            TransformedText(highlighted, OffsetMapping.Identity)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Project", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") }
                },
                actions = {
                    TextButton(onClick = { create() }, enabled = name.isNotBlank() && !loading) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 0 && compose.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { showVariables = true },
                    icon = { Icon(Icons.Filled.DataObject, null) },
                    text = { Text("Variables") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Error banner
            error?.let { msg ->
                item { ErrorBanner(msg) }
            }

            // Project Name
            item {
                DetailSection("Project Name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("my-app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Template
            item {
                DetailSection("Template") {
                    if (isPrefilled) {
                        Text(
                            "${templateLabel ?: "Template"} · Loaded",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        Box {
                            OutlinedButton(
                                onClick = { templateMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(selectedLabel, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = templateMenu,
                                onDismissRequest = { templateMenu = false },
                            ) {
                                DropdownMenuItem(text = { Text("Blank") }, onClick = { applyTemplate(null) })
                                templates.forEach { t ->
                                    DropdownMenuItem(text = { Text(t.name) }, onClick = { applyTemplate(t) })
                                }
                            }
                        }
                    }
                }
            }

            // Segmented tab + syntax-highlighted editable code area (mirrors ComposeFileScreen).
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 80.dp), // room for the FAB
                ) {
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
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

                    val currentText = if (tab == 0) compose else env
                    val onTextChange: (String) -> Unit =
                        if (tab == 0) ({ compose = it }) else ({ env = it })

                    BasicTextField(
                        value = currentText,
                        onValueChange = onTextChange,
                        textStyle = mono.copy(color = onSurface),
                        visualTransformation = syntaxTransform,
                        cursorBrush = SolidColor(primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp),
                    )
                }
            }
        }
    }

    if (showVariables) {
        Dialog(
            onDismissRequest = { showVariables = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            RenderComposeView(
                initialCompose = compose,
                initialEnv = env,
                onApply = { resolved -> compose = resolved; showVariables = false },
                onCancel = { showVariables = false },
            )
        }
    }
}

// Reuses the same section header pattern as ProjectDetailScreen.
@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        content()
    }
}

private fun slugify(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), "-")
