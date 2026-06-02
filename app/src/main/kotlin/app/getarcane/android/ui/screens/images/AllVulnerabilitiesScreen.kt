package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.vulnerability.EnvironmentVulnerabilitySummary
import app.getarcane.sdk.models.vulnerability.VulnerabilitySeverity
import app.getarcane.sdk.models.vulnerability.VulnerabilityWithImage
import kotlinx.coroutines.launch

private const val ALL_PAGE_SIZE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllVulnerabilitiesScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var summary by remember { mutableStateOf<EnvironmentVulnerabilitySummary?>(null) }
    val items = remember { mutableStateListOf<VulnerabilityWithImage>() }
    val imageOptions = remember { mutableStateListOf<String>() }
    val selectedSeverities = remember { mutableStateListOf<VulnerabilitySeverity>() }
    var selectedImage by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    val filterCount = selectedSeverities.size + (if (selectedImage == null) 0 else 1)

    suspend fun loadItems() {
        if (client == null) return
        isLoading = true
        try {
            val sevParam = selectedSeverities.takeIf { it.isNotEmpty() }?.joinToString(",") { it.wire }
            val response = client.vulnerabilities.listAll(
                envId = envId,
                query = SearchPaginationSort(start = page * ALL_PAGE_SIZE, limit = ALL_PAGE_SIZE),
                severity = sevParam,
                imageName = selectedImage,
            )
            items.addAll(response.data)
            hasMore = response.data.size == ALL_PAGE_SIZE
        } catch (e: Throwable) {
            errorMessage = friendlyErrorMessage(e)
        } finally {
            isLoading = false
        }
    }

    suspend fun reload() {
        page = 0
        items.clear()
        loadItems()
    }

    LaunchedEffect(reloadTick) {
        if (client == null) return@LaunchedEffect
        summary = runCatching { client.vulnerabilities.environmentSummary(envId = envId) }.getOrNull()
        runCatching {
            val sevParam = selectedSeverities.takeIf { it.isNotEmpty() }?.joinToString(",") { it.wire }
            client.vulnerabilities.imageOptions(envId = envId, severity = sevParam)
        }.getOrNull()?.let { opts -> imageOptions.clear(); imageOptions.addAll(opts) }
        reload()
        refreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Vulnerabilities") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            "Filters",
                            tint = if (filterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reloadTick++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    DetailSection("Environment Summary") { SummaryCard(summary) }
                }

                if (items.isNotEmpty()) {
                    item { Text("FINDINGS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(items.size, key = { idx -> "${items[idx].imageId}|${items[idx].vulnerabilityId}|${items[idx].pkgName}#$idx" }) { idx ->
                        val item = items[idx]
                        var showDetail by remember(item, idx) { mutableStateOf(false) }
                        VulnerabilityWithImageRow(item) { showDetail = true }
                        if (showDetail) {
                            VulnerabilityWithImageDetailDialog(item) { showDetail = false }
                        }
                    }
                    if (hasMore) {
                        item {
                            TextButton(onClick = { page += 1; scope.launch { loadItems() } }, modifier = Modifier.fillMaxWidth()) {
                                Text("Load More")
                            }
                        }
                    }
                } else if (!isLoading) {
                    item {
                        ContentUnavailable(
                            "No vulnerabilities",
                            Icons.Filled.FilterList,
                            "Either no images have been scanned, or all findings have been filtered out.",
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        AllVulnFilterSheet(
            selectedSeverities = selectedSeverities,
            imageOptions = imageOptions,
            selectedImage = selectedImage,
            onSelectImage = { selectedImage = it },
            onToggleSeverity = { sev, on -> if (on) selectedSeverities.add(sev) else selectedSeverities.remove(sev) },
            onReset = { selectedSeverities.clear(); selectedImage = null },
            onDone = { showFilterSheet = false; reloadTick++ },
            onDismiss = { showFilterSheet = false },
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

@Composable
private fun SummaryCard(summary: EnvironmentVulnerabilitySummary?) {
    if (summary == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp))
            Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("Images", "${summary.totalImages}", MaterialTheme.colorScheme.onSurfaceVariant)
            Metric("Scanned", "${summary.scannedImages}", MaterialTheme.colorScheme.primary)
            summary.summary?.let { s ->
                Metric("Total CVEs", "${s.total}", if (s.total > 0) ArcaneOrange else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        summary.summary?.let { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SeverityPill("Critical", s.critical, severityColor(VulnerabilitySeverity.CRITICAL), Modifier.weight(1f))
                SeverityPill("High", s.high, severityColor(VulnerabilitySeverity.HIGH), Modifier.weight(1f))
                SeverityPill("Med", s.medium, severityColor(VulnerabilitySeverity.MEDIUM), Modifier.weight(1f))
                SeverityPill("Low", s.low, severityColor(VulnerabilitySeverity.LOW), Modifier.weight(1f))
                SeverityPill("?", s.unknown, severityColor(VulnerabilitySeverity.UNKNOWN), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VulnerabilityWithImageRow(item: VulnerabilityWithImage, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeverityBadge(item.severity)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.vulnerabilityId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(item.imageName, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.pkgName + (item.installedVersion.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.cvss?.preferredScore?.let { Text(String.format("%.1f", it), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun VulnerabilityWithImageDetailDialog(record: VulnerabilityWithImage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.vulnerabilityId, fontFamily = FontFamily.Monospace) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Image", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        LabeledRow("Name", record.imageName)
                        MonoRow(record.imageId, maxLines = 2)
                        Spacer(Modifier.size(4.dp))
                        LabeledRow("Severity", severityDisplayLabel(record.severity))
                        LabeledRow("Package", record.pkgName)
                        if (record.installedVersion.isNotEmpty()) LabeledRow("Installed", record.installedVersion)
                        record.fixedVersion?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Fixed in", it) }
                        record.cvss?.preferredScore?.let { LabeledRow("CVSS", String.format("%.1f", it)) }
                        record.publishedDate?.let { LabeledRow("Published", formatImageDate(it)) }
                        record.lastModifiedDate?.let { LabeledRow("Modified", formatImageDate(it)) }
                    }
                }
                record.title?.takeIf { it.isNotEmpty() }?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) } }
                record.description?.takeIf { it.isNotEmpty() }?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
                record.references?.takeIf { it.isNotEmpty() }?.let { refs ->
                    item {
                        Text("References", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Column { refs.forEach { MonoRow(it, maxLines = 2) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllVulnFilterSheet(
    selectedSeverities: List<VulnerabilitySeverity>,
    imageOptions: List<String>,
    selectedImage: String?,
    onSelectImage: (String?) -> Unit,
    onToggleSeverity: (VulnerabilitySeverity, Boolean) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onReset) { Text("Reset") }
                    TextButton(onClick = onDone) { Text("Done") }
                }
            }
            item { Text("SEVERITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(VulnerabilitySeverity.entries.size) { i ->
                val sev = VulnerabilitySeverity.entries[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeverityBadge(sev)
                    Spacer(Modifier.size(8.dp))
                    Text(severityDisplayLabel(sev), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = selectedSeverities.contains(sev), onCheckedChange = { onToggleSeverity(sev, it) })
                }
            }
            if (imageOptions.isNotEmpty()) {
                item { Text("IMAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    Row(Modifier.fillMaxWidth().selectable(selected = selectedImage == null, onClick = { onSelectImage(null) }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = selectedImage == null, onClick = { onSelectImage(null) })
                        Text("All", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(imageOptions.size) { i ->
                    val name = imageOptions[i]
                    Row(Modifier.fillMaxWidth().selectable(selected = selectedImage == name, onClick = { onSelectImage(name) }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = selectedImage == name, onClick = { onSelectImage(name) })
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
