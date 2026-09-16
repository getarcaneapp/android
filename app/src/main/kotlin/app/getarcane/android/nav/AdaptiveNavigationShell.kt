package app.getarcane.android.nav

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val SETTINGS_ID = MainTabSelection.SETTINGS_ID

/** One adaptive shell around the existing tab/navigation owners; no second navigation framework. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveNavigationShell(
    pinnedTabs: List<AppTab>,
    availableTabs: List<AppTab>,
    selectedTabId: String,
    snackbarHostState: SnackbarHostState,
    onSelect: (String) -> Unit,
    onCompactLongClick: (AppTab) -> Unit,
    content: @Composable () -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        when (AdaptiveNavigation.widthClass(maxWidth.value.toInt())) {
            AdaptiveWidthClass.COMPACT -> ShellScaffold(
                snackbarHostState = snackbarHostState,
                bottomBar = {
                    NavigationBar {
                        pinnedTabs.forEach { tab ->
                            CompactNavigationItem(
                                tab = tab,
                                selected = selectedTabId == tab.id,
                                onClick = { onSelect(tab.id) },
                                onLongClick = { onCompactLongClick(tab) },
                            )
                        }
                        CompactNavigationItem(
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                            label = "Settings",
                            selected = selectedTabId == SETTINGS_ID,
                            onClick = { onSelect(SETTINGS_ID) },
                        )
                    }
                },
                content = content,
            )

            AdaptiveWidthClass.MEDIUM -> Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    pinnedTabs.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTabId == tab.id,
                            onClick = { onSelect(tab.id) },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.tabBarTitle, maxLines = 1) },
                        )
                    }
                    NavigationRailItem(
                        selected = AppTab.byId(selectedTabId)?.let { it !in pinnedTabs } == true,
                        onClick = { showMore = true },
                        icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "More destinations") },
                        label = { Text("More") },
                    )
                    NavigationRailItem(
                        selected = selectedTabId == SETTINGS_ID,
                        onClick = { onSelect(SETTINGS_ID) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ShellScaffold(snackbarHostState = snackbarHostState, content = content)
                }
            }

            AdaptiveWidthClass.EXPANDED -> PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(Modifier.width(300.dp)) {
                        Text(
                            text = "Arcane",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TabSection.entries.forEach { section ->
                                val sectionTabs = availableTabs.filter { it.section == section }
                                if (sectionTabs.isNotEmpty()) {
                                    item(key = "section-${section.name}") {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 28.dp, top = 14.dp, bottom = 4.dp),
                                        )
                                    }
                                    items(sectionTabs, key = { it.id }) { tab ->
                                        NavigationDrawerItem(
                                            label = { Text(tab.title, maxLines = 1) },
                                            selected = selectedTabId == tab.id,
                                            onClick = { onSelect(tab.id) },
                                            icon = { Icon(tab.icon, contentDescription = null) },
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        NavigationDrawerItem(
                            label = { Text("Settings") },
                            selected = selectedTabId == SETTINGS_ID,
                            onClick = { onSelect(SETTINGS_ID) },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                },
            ) {
                ShellScaffold(snackbarHostState = snackbarHostState, content = content)
            }
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Text(
                text = "All destinations",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxHeight(0.75f)) {
                TabSection.entries.forEach { section ->
                    val sectionTabs = availableTabs.filter { it.section == section }
                    if (sectionTabs.isNotEmpty()) {
                        item(key = "more-section-${section.name}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp),
                            )
                        }
                        items(sectionTabs, key = { "more-${it.id}" }) { tab ->
                            NavigationDrawerItem(
                                label = { Text(tab.title) },
                                selected = selectedTabId == tab.id,
                                onClick = {
                                    showMore = false
                                    onSelect(tab.id)
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellScaffold(
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.CompactNavigationItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            // Keep customization on the icon while the standard NavigationBarItem owns normal
            // taps. This avoids competing full-item click detectors swallowing a tab switch on
            // some OEM Compose/input combinations.
            Icon(
                tab.icon,
                contentDescription = tab.title,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            )
        },
        label = { Text(tab.tabBarTitle, maxLines = 1) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.CompactNavigationItem(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label, maxLines = 1) },
    )
}
