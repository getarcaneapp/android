package app.getarcane.android.ui.screens.settings.rbac

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.sdk.models.role.PermissionAction
import app.getarcane.sdk.models.role.PermissionResource
import app.getarcane.sdk.models.role.PermissionResourceScope
import app.getarcane.sdk.models.role.PermissionsManifest
import app.getarcane.android.ui.screens.settings.permissionResourceColor
import app.getarcane.android.ui.screens.settings.permissionResourceIcon

/**
 * Emits the hierarchical permission picker into a surrounding [LazyListScope]. Each resource is a
 * disclosure header with a select-all control; expanded resources show one toggle per action. When
 * [search] is non-empty, matching resources auto-expand. Mirrors iOS `PermissionPickerView`.
 */
fun LazyListScope.permissionPicker(
    manifest: PermissionsManifest,
    selected: Set<String>,
    onToggle: (permission: String, isOn: Boolean) -> Unit,
    onToggleResource: (actions: List<PermissionAction>, selectAll: Boolean) -> Unit,
    expanded: Set<String>,
    onExpandChange: (key: String, expanded: Boolean) -> Unit,
    isReadOnly: Boolean,
    search: String,
) {
    val q = search.lowercase()
    val filtered: List<PermissionResource> = if (search.isEmpty()) {
        manifest.resources
    } else {
        manifest.resources.mapNotNull { resource ->
            if (resource.label.lowercase().contains(q) || resource.key.lowercase().contains(q)) {
                resource
            } else {
                val acts = resource.actions.filter {
                    it.label.lowercase().contains(q) ||
                        it.permission.lowercase().contains(q) ||
                        (it.description ?: "").lowercase().contains(q)
                }
                if (acts.isEmpty()) null else resource.copy(actions = acts)
            }
        }
    }

    filtered.forEach { resource ->
        val isExpanded = expanded.contains(resource.key) || search.isNotEmpty()
        item(key = "perm-res-${resource.key}") {
            ResourceHeader(
                resource = resource,
                selected = selected,
                isExpanded = isExpanded,
                isReadOnly = isReadOnly,
                onExpandToggle = { onExpandChange(resource.key, !isExpanded) },
                onSelectAll = { selectAll -> onToggleResource(resource.actions, selectAll) },
            )
        }
        if (isExpanded) {
            items(resource.actions, key = { "perm-act-${it.permission}" }) { action ->
                ActionToggle(action, selected.contains(action.permission), isReadOnly) { isOn ->
                    onToggle(action.permission, isOn)
                }
            }
        }
    }
}

@Composable
private fun ResourceHeader(
    resource: PermissionResource,
    selected: Set<String>,
    isExpanded: Boolean,
    isReadOnly: Boolean,
    onExpandToggle: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
) {
    val total = resource.actions.size
    val selectedCount = resource.actions.count { selected.contains(it.permission) }
    val allSelected = selectedCount == total && total > 0
    val partial = selectedCount > 0 && !allSelected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            permissionResourceIcon(resource.key),
            contentDescription = null,
            tint = permissionResourceColor(resource.key),
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(resource.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$selectedCount/$total selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (resource.scopeKind == PermissionResourceScope.GLOBAL) {
                    Text(
                        "· Global",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
        IconButton(onClick = { onSelectAll(!allSelected) }, enabled = !isReadOnly) {
            Icon(
                when {
                    allSelected -> Icons.Filled.CheckBox
                    partial -> Icons.Filled.IndeterminateCheckBox
                    else -> Icons.Filled.CheckBoxOutlineBlank
                },
                contentDescription = if (allSelected) "Deselect all in ${resource.label}" else "Select all in ${resource.label}",
                tint = if (allSelected || partial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionToggle(action: PermissionAction, isOn: Boolean, isReadOnly: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(action.label.ifEmpty { action.key }, style = MaterialTheme.typography.bodyLarge)
            action.description?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                action.permission,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Switch(checked = isOn, onCheckedChange = onToggle, enabled = !isReadOnly)
    }
}
