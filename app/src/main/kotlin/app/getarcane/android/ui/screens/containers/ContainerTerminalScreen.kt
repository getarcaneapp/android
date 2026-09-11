package app.getarcane.android.ui.screens.containers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.AnsiSanitizer
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.runSuspendCatching
import app.getarcane.android.ui.components.BannerSeverity
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission
import app.getarcane.sdk.streaming.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val shells = listOf("/bin/sh", "/bin/bash", "/bin/zsh", "/bin/ash")
private const val INPUT_CHARACTER_BUDGET = 16_384

// Control sequences (kept as \u escapes so no raw control bytes live in source).
private const val ESC = "\u001b"
private const val CTRL_C = "\u0003"
private const val CTRL_D = "\u0004"

/**
 * Interactive container terminal over a WebSocket exec session. Output appended to a monospaced
 * scrollback; input bar + shell shortcut keys (Tab/Esc/Ctrl+C/Ctrl+D/arrows). Port of iOS
 * `ContainerTerminalView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerTerminalScreen(id: String, title: String, onClose: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val outputBuffer = remember { TerminalOutputBuffer() }
    val canExec = manager.currentUser?.hasPermission(Permission.Containers.EXEC, envId.rawValue) == true
    val currentUserId = manager.currentUser?.id.orEmpty()

    var output by remember { mutableStateOf(TerminalOutputSnapshot()) }
    var input by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var shell by remember { mutableStateOf("/bin/sh") }
    var menuOpen by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    var outputClient by remember { mutableStateOf(client) }
    var outputUserId by remember { mutableStateOf(currentUserId) }
    var outputEnvironmentId by remember { mutableStateOf(envId.rawValue) }
    var outputContainerId by remember { mutableStateOf(id) }

    val scrollState = rememberScrollState()

    suspend fun closeSession(closingSession: TerminalSession? = session) {
        if (closingSession != null) {
            withContext(NonCancellable) {
                runSuspendCatching { closingSession.close() }
            }
        }
        if (session === closingSession) {
            session = null
            isConnected = false
            isConnecting = false
        }
    }

    LaunchedEffect(client, currentUserId, envId.rawValue, id, shell, retryKey, canExec) {
        val activeClient = client
        if (outputClient !== activeClient ||
            outputUserId != currentUserId ||
            outputEnvironmentId != envId.rawValue ||
            outputContainerId != id ||
            !canExec
        ) {
            output = outputBuffer.clear()
            input = ""
            outputClient = activeClient
            outputUserId = currentUserId
            outputEnvironmentId = envId.rawValue
            outputContainerId = id
        }
        if (!canExec) {
            connectError = "You don't have permission to open a terminal for this container in ${manager.activeEnvironmentName}."
            return@LaunchedEffect
        }
        if (activeClient == null) {
            connectError = "Not connected to a server."
            return@LaunchedEffect
        }
        isConnecting = true
        connectError = null
        var ownedSession: TerminalSession? = null
        try {
            val activeSession = activeClient.containers.exec(envId = envId, id = id, shell = shell)
            ownedSession = activeSession
            session = activeSession
            isConnected = true
            isConnecting = false
            activeSession.output.collect { chunk ->
                val raw = chunk.decodeToString()
                // Auto-reply to cursor-position requests (DSR ESC[6n).
                if (raw.contains(ESC + "[6n")) {
                    runSuspendCatching { activeSession.send(ESC + "[1;1R") }
                }
                val stripped = AnsiSanitizer.strip(raw)
                output = outputBuffer.append(stripped)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            connectError = if (isConnected) {
                "Disconnected: ${friendlyErrorMessage(e)}"
            } else {
                friendlyErrorMessage(e)
            }
        } finally {
            closeSession(ownedSession)
        }
    }

    fun send(text: String) {
        val activeSession = session ?: return
        if (!isConnected) return
        scope.launch {
            try {
                activeSession.send(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (session === activeSession) {
                    connectError = "Send failed: ${friendlyErrorMessage(e)}"
                }
            }
        }
    }

    fun sendShortcut(text: String) {
        send(text)
    }

    fun sendInput() {
        if (input.isEmpty()) return
        val payload = input + "\n"
        send(payload)
        input = ""
    }

    LaunchedEffect(output) { scrollState.animateScrollTo(scrollState.maxValue) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = { scope.launch { closeSession(); onClose() } }) { Text("Close") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.Terminal, "Options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text(
                                "Shell",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                            )
                            shells.forEach { sh ->
                                DropdownMenuItem(
                                    text = { Text(sh, fontFamily = FontFamily.Monospace) },
                                    onClick = { shell = sh; menuOpen = false },
                                    enabled = !isConnected && !isConnecting,
                                    leadingIcon = { RadioButton(selected = shell == sh, onClick = null) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Copy All Retained") },
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("$title terminal output", output.text))
                                    menuOpen = false
                                },
                                enabled = output.text.isNotEmpty(),
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Output") },
                                onClick = { output = outputBuffer.clear(); menuOpen = false },
                                leadingIcon = { Icon(Icons.Filled.Clear, null) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            connectError?.let {
                ErrorBanner(
                    it,
                    severity = BannerSeverity.Warning,
                    onRetry = { retryKey++ },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            if (output.discardedCharacters > 0) {
                Text(
                    "Showing the latest retained output; ${output.discardedCharacters} earlier characters were discarded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState),
            ) {
                SelectionContainer {
                    Text(
                        text = output.text.ifEmpty {
                            when {
                                isConnecting -> "Connecting to $shell…"
                                isConnected -> "Connected to $shell. Waiting for output…"
                                else -> "No terminal output retained."
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }

            HorizontalDivider()

            InputBar(
                input = input,
                onInputChange = { input = it },
                isConnected = isConnected,
                onSend = { sendInput() },
                onShortcut = { sendShortcut(it) },
            )
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    isConnected: Boolean,
    onSend: () -> Unit,
    onShortcut: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShortcutButton("Tab", isConnected) { onShortcut("\t") }
                ShortcutButton("Esc", isConnected) { onShortcut(ESC) }
                ShortcutButton("Ctrl+C", isConnected) { onShortcut(CTRL_C) }
                ShortcutButton("Ctrl+D", isConnected) { onShortcut(CTRL_D) }
                ShortcutButton("↑", isConnected) { onShortcut(ESC + "[A") }
                ShortcutButton("↓", isConnected) { onShortcut(ESC + "[B") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = input,
                    onValueChange = { onInputChange(it.take(INPUT_CHARACTER_BUDGET)) },
                    placeholder = { Text("command", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSend, enabled = isConnected && input.isNotEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "Send",
                        tint = if (isConnected && input.isNotEmpty()) ArcaneBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
