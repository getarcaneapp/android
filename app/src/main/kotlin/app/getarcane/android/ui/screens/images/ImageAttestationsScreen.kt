package app.getarcane.android.ui.screens.images

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.image.ImageAttestation
import app.getarcane.sdk.models.image.ImageAttestationList
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageAttestationsScreen(
    identity: ImageInsightIdentity,
    onBack: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    var state by remember(identity.requestKey) {
        mutableStateOf<ImageInsightUiState<ImageAttestationList>>(ImageInsightUiState.Loading)
    }
    var selectedPredicateType by remember(identity.requestKey) { mutableStateOf<String?>(null) }
    var selectedAttestation by remember(identity.requestKey) { mutableStateOf<ImageAttestation?>(null) }
    var refreshRevision by remember(identity.requestKey) { mutableIntStateOf(0) }
    var refreshing by remember(identity.requestKey) { mutableStateOf(false) }

    val currentUserId = manager.currentUser?.id.orEmpty()
    val activeEnvironmentId = manager.activeEnvironmentId.rawValue
    val current = identity.isCurrent(
        manager.serverSessionIdentity,
        currentUserId,
        activeEnvironmentId,
    )
    val canRead = manager.currentUser?.hasPermission(
        Permission.Images.READ,
        identity.environmentId,
    ) == true

    LaunchedEffect(
        identity.requestKey,
        manager.client,
        currentUserId,
        manager.serverSessionIdentity,
        activeEnvironmentId,
        canRead,
        refreshRevision,
    ) {
        if (!current) {
            selectedAttestation = null
            state = ImageInsightUiState.StaleSelection
            refreshing = false
            return@LaunchedEffect
        }
        if (!canRead) {
            selectedAttestation = null
            state = ImageInsightUiState.Failed(
                ImageInsightFailure(
                    ImageInsightFailureKind.Unauthorized,
                    "Your account does not have image-read access for ${identity.environmentName}.",
                ),
            )
            refreshing = false
            return@LaunchedEffect
        }
        val captured = manager.authenticatedClientScope()
        if (captured == null) {
            selectedAttestation = null
            state = ImageInsightUiState.StaleSelection
            refreshing = false
            return@LaunchedEffect
        }
        if (!refreshing) state = ImageInsightUiState.Loading
        val result = loadImageInsightResult(
            isCurrent = {
                manager.isCurrent(captured) && identity.isCurrent(
                    manager.serverSessionIdentity,
                    manager.currentUser?.id.orEmpty(),
                    manager.activeEnvironmentId.rawValue,
                )
            },
            load = {
                captured.client.images.attestations(
                    envId = EnvironmentId(identity.environmentId),
                    id = identity.imageId,
                )
            },
        )
        state = when (result) {
            is ImageInsightLoadResult.Success -> {
                selectedAttestation = selectedAttestation?.let {
                    selectAttestation(result.value.attestations, it)
                }
                ImageInsightUiState.Content(result.value)
            }
            is ImageInsightLoadResult.Failure -> ImageInsightUiState.Failed(result.failure)
            null -> ImageInsightUiState.StaleSelection
        }
        refreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attestations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ImageInsightScopeHeader(identity, "Attached in-toto statements")
            Box(Modifier.fillMaxSize()) {
                when (val currentState = state) {
                    ImageInsightUiState.Loading -> SkeletonListLoadingView(rows = 3)
                    ImageInsightUiState.StaleSelection -> ContentUnavailable(
                        "Image selection changed",
                        Icons.Filled.Warning,
                        "${identity.imageDisplayName} is tied to ${identity.environmentName}. Go back and select the image again.",
                        "Back",
                        onBack,
                    )
                    is ImageInsightUiState.Failed -> ImageInsightFailureContent(
                        currentState.failure,
                        onRetry = {
                            refreshing = true
                            refreshRevision++
                        },
                    )
                    is ImageInsightUiState.Content -> {
                        val result = currentState.value
                        val filtered = filterAttestations(result.attestations, selectedPredicateType)
                        if (result.attestations.isEmpty()) {
                            ContentUnavailable(
                                "No Attestations",
                                Icons.Filled.Security,
                                "This image has no attached in-toto attestations. Absence or presence does not establish image trust.",
                                "Refresh",
                            ) {
                                refreshing = true
                                refreshRevision++
                            }
                        } else {
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = {
                                    refreshing = true
                                    refreshRevision++
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    AttestationTrustNotice()
                                    PredicateFilters(
                                        predicateTypes = predicateTypeOptions(result.attestations),
                                        selected = selectedPredicateType,
                                        onSelected = { selectedPredicateType = it },
                                    )
                                    if (filtered.isEmpty()) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            ),
                                        ) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(Icons.Filled.FilterList, null)
                                                    Text(
                                                        "No Matching Attestations",
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                }
                                                Text(
                                                    "No attached statement matches ${predicateTypeLabel(selectedPredicateType.orEmpty())}.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                TextButton(onClick = { selectedPredicateType = null }) {
                                                    Text("Show All Types")
                                                }
                                            }
                                        }
                                    } else {
                                        filtered.forEach { attestation ->
                                            AttestationRow(attestation) {
                                                selectedAttestation = attestation
                                            }
                                        }
                                        result.subjectDigest.takeIf(String::isNotBlank)?.let { digest ->
                                            Text(
                                                "Subject digest: $digest",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedAttestation?.let { attestation ->
        ImageAttestationDetailSheet(
            identity = identity,
            attestation = attestation,
            onDismiss = { selectedAttestation = null },
        )
    }
}

@Composable
private fun AttestationTrustNotice() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, null, modifier = Modifier.size(20.dp))
            Text(
                "Attached does not mean trusted or cryptographically verified. Review the issuer and verification evidence separately.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PredicateFilters(
    predicateTypes: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelected(null) }, label = { Text("All Types") })
        predicateTypes.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(predicateTypeLabel(type)) },
            )
        }
        if (selected != null && selected !in predicateTypes) {
            FilterChip(
                selected = true,
                onClick = { onSelected(null) },
                label = { Text(predicateTypeLabel(selected)) },
            )
        }
    }
}

@Composable
private fun AttestationRow(attestation: ImageAttestation, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Security, null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    predicateTypeLabel(attestation.predicateType),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    attestation.predicateType.ifBlank { "Predicate type not supplied" },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${attestation.platform?.ifBlank { null } ?: "Platform unspecified"} · ${formatBytes(attestation.size)} · ${attestation.subject.orEmpty().size} subject(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageAttestationDetailSheet(
    identity: ImageInsightIdentity,
    attestation: ImageAttestation,
    onDismiss: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectionKey = attestationSelectionKey(identity, attestation)
    var statementState by remember(selectionKey) {
        mutableStateOf<ImageInsightUiState<String>?>(null)
    }
    var operationMessage by remember(selectionKey) { mutableStateOf<String?>(null) }
    var pendingExport by remember(selectionKey) { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val statement = pendingExport
        pendingExport = null
        if (uri == null || statement == null) return@rememberLauncherForActivityResult
        operationMessage = runCatching {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri))
            output.bufferedWriter().use { it.write(statement) }
            "Raw in-toto statement exported."
        }.getOrElse { "Export failed: ${friendlyErrorMessage(it)}" }
    }

    fun loadStatement() {
        val captured = manager.authenticatedClientScope()
        if (captured == null || !identity.isCurrent(
                manager.serverSessionIdentity,
                manager.currentUser?.id.orEmpty(),
                manager.activeEnvironmentId.rawValue,
            )
        ) {
            statementState = ImageInsightUiState.StaleSelection
            return
        }
        statementState = ImageInsightUiState.Loading
        operationMessage = null
        scope.launch {
            val result = loadImageInsightResult(
                isCurrent = {
                    manager.isCurrent(captured) && identity.isCurrent(
                        manager.serverSessionIdentity,
                        manager.currentUser?.id.orEmpty(),
                        manager.activeEnvironmentId.rawValue,
                    )
                },
                load = {
                    val response = captured.client.images.attestations(
                        envId = EnvironmentId(identity.environmentId),
                        id = identity.imageId,
                        platform = attestation.platform,
                        predicateType = attestation.predicateType,
                        includeStatement = true,
                    )
                    val matched = selectAttestation(response.attestations, attestation)
                        ?: throw AttestationSelectionMissingException()
                    val statement = matched.statement ?: throw MissingAttestationStatementException()
                    rawStatementJson(statement)
                },
            )
            statementState = when (result) {
                is ImageInsightLoadResult.Success -> ImageInsightUiState.Content(result.value)
                is ImageInsightLoadResult.Failure -> ImageInsightUiState.Failed(result.failure)
                null -> ImageInsightUiState.StaleSelection
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Attestation", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
            }
            Text(
                "Raw attached metadata — not a trust or verification result.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AttestationField("Predicate type", attestation.predicateType.ifBlank { "Unknown" }, mono = true)
            AttestationField("Platform", attestation.platform?.ifBlank { null } ?: "Unspecified")
            AttestationField("Size", if (attestation.size < 0) "Unknown" else formatBytes(attestation.size))
            AttestationField("Media type", attestation.mediaType.ifBlank { "Unknown" }, mono = true)
            AttestationField("Artifact type", attestation.artifactType?.ifBlank { null } ?: "Not supplied", mono = true)
            AttestationField("Statement type", attestation.statementType?.ifBlank { null } ?: "Not supplied", mono = true)
            AttestationField("Attestation digest", attestation.digest.ifBlank { "Not supplied" }, mono = true)

            if (attestation.subject.isNullOrEmpty()) {
                AttestationField("Subjects", "No subjects supplied")
            } else {
                Text("Subjects", style = MaterialTheme.typography.titleMedium)
                attestation.subject.orEmpty().forEach { subject ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            SelectionContainer {
                                Text(
                                    subject.name.ifBlank { "Unnamed subject" },
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            if (subject.digest.isEmpty()) {
                                Text("No subject digests supplied", style = MaterialTheme.typography.bodySmall)
                            } else {
                                subject.digest.toSortedMap().forEach { (algorithm, value) ->
                                    SelectionContainer {
                                        Text(
                                            "$algorithm:$value",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Raw in-toto statement", style = MaterialTheme.typography.titleMedium)
            when (val statement = statementState) {
                null -> Button(onClick = ::loadStatement) { Text("Load Raw Statement") }
                ImageInsightUiState.Loading -> SkeletonListLoadingView(rows = 2)
                ImageInsightUiState.StaleSelection -> Text(
                    "The image, environment, server, or account changed. Close this detail and select the image again.",
                    color = MaterialTheme.colorScheme.error,
                )
                is ImageInsightUiState.Failed -> {
                    val icon = if (statement.failure.kind == ImageInsightFailureKind.Unauthorized) {
                        Icons.Filled.Lock
                    } else {
                        Icons.Filled.Warning
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.error)
                        Text(statement.failure.message, color = MaterialTheme.colorScheme.error)
                    }
                    if (statement.failure.kind != ImageInsightFailureKind.Unauthorized) {
                        TextButton(onClick = ::loadStatement) { Text("Try Again") }
                    }
                }
                is ImageInsightUiState.Content -> {
                    val preview = rawStatementPreview(statement.value)
                    SelectionContainer {
                        Text(
                            preview.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 18,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (preview.truncated) {
                        Text(
                            "Preview limited to $RAW_STATEMENT_PREVIEW_LIMIT characters. Copy or export includes the complete raw statement.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Raw in-toto statement for ${identity.imageDisplayName}",
                                    statement.value,
                                ),
                            )
                            operationMessage = "Complete raw in-toto statement copied."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, null)
                        Text("  Copy Complete Raw Statement")
                    }
                    Button(
                        onClick = {
                            pendingExport = statement.value
                            exportLauncher.launch(rawStatementFilename(attestation))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FileDownload, null)
                        Text("  Export Complete Raw Statement")
                    }
                }
            }
            operationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun AttestationField(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}
