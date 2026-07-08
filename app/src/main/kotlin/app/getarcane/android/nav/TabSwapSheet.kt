package app.getarcane.android.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.user.isGlobalAdmin

/**
 * Bottom sheet to replace a bottom-nav slot with another tab. Port of iOS `TabSwapSheet`:
 * eligible tabs grouped by section (excluding pinned + the current tab, gated by admin/v2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwapSheet(
    current: AppTab,
    tabsStore: NavTabsStore,
    onPick: (AppTab) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val isAdmin = manager.currentUser?.isGlobalAdmin ?: false
    val supportsV2 = manager.capabilities.mode == ServerCapabilities.Mode.RBAC
    val pinnedSet = tabsStore.pinned.toSet()

    fun eligible(section: TabSection): List<AppTab> = AppTab.entries.filter { tab ->
        tab.section == section &&
            tab !in pinnedSet &&
            tab != current &&
            tab.isAvailableForBottomBar(isAdmin, supportsV2)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Replace ${current.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onReset, enabled = tabsStore.pinned != AppTab.defaults) { Text("Reset") }
            }
            TabSection.entries.forEach { section ->
                val tabs = eligible(section)
                if (tabs.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            section.title.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        tabs.chunked(3).forEach { rowTabs ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowTabs.forEach { tab ->
                                    TabTile(tab, Modifier.weight(1f)) { onPick(tab) }
                                }
                                repeat(3 - rowTabs.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun TabTile(tab: AppTab, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(42.dp).background(tab.color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(tab.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            tab.tabBarTitle,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
