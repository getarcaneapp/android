package app.getarcane.android.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.getarcane.android.ui.theme.ArcaneTheme
import org.junit.Rule
import org.junit.Test

class AdaptiveNavigationShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingAnotherTabThenDashboardSwitchesBack() {
        composeRule.setContent {
            var selectedTabId by remember { mutableStateOf(AppTab.Dashboard.id) }
            ArcaneTheme {
                AdaptiveNavigationShell(
                    pinnedTabs = listOf(
                        AppTab.Dashboard,
                        AppTab.Containers,
                        AppTab.Volumes,
                        AppTab.Projects,
                    ),
                    availableTabs = AppTab.entries,
                    selectedTabId = selectedTabId,
                    snackbarHostState = remember { SnackbarHostState() },
                    onSelect = { selectedTabId = it },
                    onCompactLongClick = {},
                ) {
                    Text(selectedTabId, Modifier.testTag("result"))
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Volumes", useUnmergedTree = true)
            .performTouchInput { click() }
        composeRule.onNodeWithTag("result").assertTextEquals("volumes")

        composeRule
            .onNodeWithContentDescription("Dashboard", useUnmergedTree = true)
            .performTouchInput { click() }
        composeRule.onNodeWithTag("result").assertTextEquals("dashboard")
    }

    @Test
    fun longPressCustomizesWithoutSelectingDashboard() {
        composeRule.setContent {
            var selectedTabId by remember { mutableStateOf(AppTab.Volumes.id) }
            var customizedTabId by remember { mutableStateOf("none") }
            ArcaneTheme {
                AdaptiveNavigationShell(
                    pinnedTabs = listOf(
                        AppTab.Dashboard,
                        AppTab.Containers,
                        AppTab.Volumes,
                        AppTab.Projects,
                    ),
                    availableTabs = AppTab.entries,
                    selectedTabId = selectedTabId,
                    snackbarHostState = remember { SnackbarHostState() },
                    onSelect = { selectedTabId = it },
                    onCompactLongClick = { customizedTabId = it.id },
                ) {
                    Text(
                        text = "$selectedTabId:$customizedTabId",
                        modifier = Modifier.testTag("result"),
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Dashboard", useUnmergedTree = true)
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("result").assertTextEquals("volumes:dashboard")
    }
}
