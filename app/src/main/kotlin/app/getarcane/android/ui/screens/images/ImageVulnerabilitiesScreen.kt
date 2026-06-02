package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.user.isAdmin
import app.getarcane.sdk.models.vulnerability.IgnoredVulnerability
import app.getarcane.sdk.models.vulnerability.Vulnerability
import app.getarcane.sdk.models.vulnerability.VulnerabilityIgnorePayload
import app.getarcane.sdk.models.vulnerability.VulnerabilityScanSummary
import app.getarcane.sdk.models.vulnerability.VulnerabilityScannerStatus
import app.getarcane.sdk.models.vulnerability.VulnerabilitySeverity
import kotlinx.coroutines.launch

private const val VULN_PAGE_SIZE = 50

private fun ignoreKey(v: Vulnerability): String = "${v.vulnerabilityId}|${v.pkgName}|${v.installedVersion}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageVulnerabilitiesScreen(
    imageId: String,
    imageDisplayName: String,
    onBack: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    val isAdmin = manager.currentUser?.isAdmin == true

    var scannerStatus by remember { mutableStateOf<VulnerabilityScannerStatus?>(null) }
    var summary by remember { mutableStateOf<VulnerabilityScanSummary?>(null) }
    val vulnerabilities = remember { mutableStateListOf<Vulnerability>() }
    val ignoredByKey = remember { mutableStateMapOf<String, IgnoredVulnerability>() }
    val selectedSeverities = remember { mutableStateListOf<VulnerabilitySeverity>() }
    var showIgnored by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var ignoreTarget by remember { mutableStateOf<Vulnerability?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    suspend fun loadVulnerabilities() {
        if (client == null) return
        isLoading = true
        try {
            val sevParam = selectedSeverities.takeIf { it.isNotEmpty() }?.joinToString(",") { it.wire }
            val response = client.vulnerabilities.listForImage(
                envId = envId,
                imageId = imageId,
                query = SearchPaginationSort(start = page * VULN_PAGE_SIZE, limit = VULN_PAGE_SIZE),
                severity = sevParam,
            )
            vulnerabilities.addAll(response.data)
            hasMore = response.data.size == VULN_PAGE_SIZE
        } catch (_: Throwable) {
            // Empty list / not yet scanned — silent (matches iOS).
        } finally {
            isLoading = false
        }
    }

    suspend fun reload() {
        page = 0
        vulnerabilities.clear()
        summary = runCatching { client?.vulnerabilities?.scanSummary(envId = envId, imageId = imageId) }.getOrNull()
        loadVulnerabilities()
    }

    LaunchedEffect(reloadTick) {
        if (client == null) return@LaunchedEffect
        scannerStatus = runCatching { client.vulnerabilities.scannerStatus(envId = envId) }
            .getOrDefault(VulnerabilityScannerStatus(available = false))
        if (scannerStatus?.available == true) reload()
        refreshing = false
    }

    fun runScan() {
        if (client == null) return
        isScanning = true
        scope.launch {
            try {
                client.vulnerabilities.scanImage(envId = envId, imageId = imageId)
                reload()
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isScanning = false
            }
        }
    }

    fun unignore(record: IgnoredVulnerability, key: String) {
        if (client == null) return
        scope.launch {
            try {
                client.vulnerabilities.unignore(envId = envId, ignoreId = record.id)
                ignoredByKey.remove(key)
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vulnerabilities") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val status = scannerStatus
        if (status != null && !status.available) {
            ContentUnavailable(
                "Scanner unavailable",
                Icons.Filled.Search,
                "Trivy is not installed or reachable on the host. Install Trivy and reload to scan images.",
                "Reload",
            ) { reloadTick++ }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reloadTick++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val filtered = vulnerabilities.filter { v ->
                showIgnored || ignoredByKey[ignoreKey(v)] == null
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                summary?.let { sum ->
                    item {
                        DetailSection("Summary") {
                            SeveritySummaryRow(
                                summary = sum.summary,
                                scanTime = sum.scanTime.toString(),
                                status = sum.status.label(),
                                error = sum.error,
                            )
                        }
                    }
                }

                item {
                    DetailSection("Filters") {
                        VulnerabilitySeverity.entries.forEach { sev ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                SeverityBadge(sev)
                                Spacer(Modifier.size(8.dp))
                                Text(severityDisplayLabel(sev), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = selectedSeverities.contains(sev),
                                    onCheckedChange = { on ->
                                        if (on) selectedSeverities.add(sev) else selectedSeverities.remove(sev)
                                        scope.launch { reload() }
                                    },
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Show Ignored", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = showIgnored, onCheckedChange = { showIgnored = it })
                        }
                    }
                }

                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !isScanning) { runScan() }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(if (isScanning) "Scanning…" else "Re-scan now", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            if (isScanning) CircularProgressIndicator(Modifier.size(18.dp))
                        }
                        Text("Re-runs Trivy against this image.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (filtered.isEmpty() && !isLoading) {
                    item {
                        ContentUnavailable(
                            "No vulnerabilities",
                            Icons.Filled.Search,
                            if (summary == null) "Run a scan to see results." else "Nothing matches the current filter.",
                        )
                    }
                } else {
                    item {
                        Text(
                            "FINDINGS (${filtered.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(filtered.size, key = { idx -> ignoreKey(filtered[idx]) + "#$idx" }) { idx ->
                        val vuln = filtered[idx]
                        val ignore = ignoredByKey[ignoreKey(vuln)]
                        var showDetail by remember(vuln, idx) { mutableStateOf(false) }
                        var rowMenu by remember(vuln, idx) { mutableStateOf(false) }
                        Box {
                            VulnerabilityRow(
                                record = vuln,
                                isIgnored = ignore != null,
                                onClick = { showDetail = true },
                                onLongClick = { if (isAdmin) rowMenu = true },
                            )
                            androidx.compose.material3.DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                                if (ignore != null) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Unignore") },
                                        onClick = { rowMenu = false; unignore(ignore, ignoreKey(vuln)) },
                                    )
                                } else {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Ignore") },
                                        onClick = { rowMenu = false; ignoreTarget = vuln },
                                    )
                                }
                            }
                        }
                        if (showDetail) {
                            VulnerabilityDetailDialog(record = vuln, ignoreInfo = ignore, onDismiss = { showDetail = false })
                        }
                    }

                    if (hasMore) {
                        item {
                            TextButton(
                                onClick = { page += 1; scope.launch { loadVulnerabilities() } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Load More") }
                        }
                    }
                }
            }
        }
    }

    ignoreTarget?.let { target ->
        IgnoreVulnerabilityDialog(
            vulnerability = target,
            imageId = imageId,
            onDismiss = { ignoreTarget = null },
            onIgnored = { record -> ignoredByKey[ignoreKey(target)] = record; ignoreTarget = null },
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VulnerabilityRow(
    record: Vulnerability,
    isIgnored: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeverityBadge(record.severity)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                record.vulnerabilityId,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (isIgnored) TextDecoration.LineThrough else TextDecoration.None,
            )
            Text(
                record.pkgName + (record.installedVersion.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.fixedVersion?.takeIf { it.isNotEmpty() }?.let {
                Text("Fixed in $it", style = MaterialTheme.typography.labelSmall, color = ArcaneGreen)
            }
        }
        if (isIgnored) {
            Text(
                "Ignored",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else {
            record.cvss?.preferredScore?.let { cvss ->
                Text(String.format("%.1f", cvss), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VulnerabilityDetailDialog(record: Vulnerability, ignoreInfo: IgnoredVulnerability?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.vulnerabilityId, fontFamily = FontFamily.Monospace) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabeledRow("Severity", severityDisplayLabel(record.severity))
                        LabeledRow("Package", record.pkgName)
                        if (record.installedVersion.isNotEmpty()) LabeledRow("Installed", record.installedVersion)
                        record.fixedVersion?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Fixed in", it) }
                        record.cvss?.preferredScore?.let { LabeledRow("CVSS", String.format("%.1f", it)) }
                        record.publishedDate?.let { LabeledRow("Published", formatImageDate(it)) }
                        record.lastModifiedDate?.let { LabeledRow("Modified", formatImageDate(it)) }
                    }
                }
                record.title?.takeIf { it.isNotEmpty() }?.let { title ->
                    item { Spacer(Modifier.size(4.dp)); Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                }
                record.description?.takeIf { it.isNotEmpty() }?.let { desc ->
                    item { Text(desc, style = MaterialTheme.typography.bodySmall) }
                }
                record.references?.takeIf { it.isNotEmpty() }?.let { refs ->
                    item {
                        Text("References", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Column { refs.forEach { MonoRow(it, maxLines = 2) } }
                    }
                }
                ignoreInfo?.let { ig ->
                    item {
                        Text("Ignored", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Column {
                            ig.reason?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Reason", it) }
                            LabeledRow("By", ig.createdBy)
                            LabeledRow("When", formatImageDate(ig.createdAt))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun IgnoreVulnerabilityDialog(
    vulnerability: Vulnerability,
    imageId: String,
    onDismiss: () -> Unit,
    onIgnored: (IgnoredVulnerability) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Ignore CVE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledRow("ID", vulnerability.vulnerabilityId, mono = true)
                LabeledRow("Package", vulnerability.pkgName)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
                errorMessage?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(20.dp))
            } else {
                TextButton(onClick = {
                    if (client == null) return@TextButton
                    isSaving = true
                    scope.launch {
                        try {
                            val result = client.vulnerabilities.ignore(
                                envId = envId,
                                payload = VulnerabilityIgnorePayload(
                                    imageId = imageId,
                                    vulnerabilityId = vulnerability.vulnerabilityId,
                                    pkgName = vulnerability.pkgName,
                                    installedVersion = vulnerability.installedVersion,
                                    reason = reason.ifEmpty { null },
                                ),
                            )
                            onIgnored(result)
                        } catch (e: Throwable) {
                            errorMessage = friendlyErrorMessage(e)
                        } finally {
                            isSaving = false
                        }
                    }
                }) { Text("Ignore") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } },
    )
}
