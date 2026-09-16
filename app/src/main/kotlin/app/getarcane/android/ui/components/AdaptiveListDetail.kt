package app.getarcane.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Uses one existing nested navigation owner at every width. At expanded width the list remains
 * visible beside that owner's current destination; compact/medium windows render a single pane.
 */
@Composable
fun AdaptiveListDetailLayout(
    listPane: @Composable () -> Unit,
    detailPane: @Composable (expanded: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The navigation drawer/rail has already consumed part of the window. Use the remaining
        // content width rather than applying the outer shell's 840dp breakpoint a second time.
        val expanded = maxWidth >= LIST_DETAIL_MIN_CONTENT_WIDTH_DP.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(360.dp).fillMaxSize()) { listPane() }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxSize()) { detailPane(true) }
            }
        } else {
            detailPane(false)
        }
    }
}

private const val LIST_DETAIL_MIN_CONTENT_WIDTH_DP = 600

@Composable
fun ListDetailPlaceholder(resourceName: String) {
    ContentUnavailable(
        title = "Select a $resourceName",
        icon = Icons.Outlined.TouchApp,
        description = "Choose an item from the list to see its details.",
    )
}
