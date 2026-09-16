package app.getarcane.android

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.pressKey
import app.getarcane.android.core.AuthStatus
import app.getarcane.android.core.OperationKind
import app.getarcane.android.core.OperationRecord
import app.getarcane.android.core.OperationRecoveryMode
import app.getarcane.android.core.OperationState
import app.getarcane.android.core.OperationTargetType
import app.getarcane.android.ui.AuthRouteContent
import app.getarcane.android.ui.components.DestructiveConfirmationDialog
import app.getarcane.android.ui.operations.OperationDetail
import app.getarcane.android.ui.screens.environments.EnvironmentRow
import app.getarcane.android.ui.theme.ArcaneTheme
import app.getarcane.sdk.models.environment.Environment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReleaseReadinessUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authenticationRoutingDoesNotShowLoginWhileRestoring() {
        composeRule.setContent {
            var status by mutableStateOf(AuthStatus.AUTHENTICATING)
            ArcaneTheme {
                AuthRouteContent(
                    authStatus = status,
                    authenticatingContent = {
                        TextButton(onClick = { status = AuthStatus.AUTHENTICATED }) {
                            Text("Complete restoration")
                        }
                    },
                    loginContent = { Text("Login", Modifier.testTag("route")) },
                    authenticatedContent = { Text("Dashboard", Modifier.testTag("route")) },
                )
            }
        }

        composeRule.onNodeWithText("Login").assertDoesNotExist()
        composeRule.onNodeWithText("Complete restoration").performClick()
        composeRule.onNodeWithTag("route").assertTextEquals("Dashboard")

    }

    @Test
    fun destructiveConfirmationRequiresExplicitActionAndBackOnlyDismisses() {
        var confirmations = 0
        var dismissals = 0
        composeRule.setContent {
            ArcaneTheme {
                DestructiveConfirmationDialog(
                    title = "Delete test container?",
                    message = "This action cannot be undone.",
                    confirmLabel = "Delete",
                    onConfirm = { confirmations++ },
                    onDismiss = { dismissals++ },
                )
            }
        }

        composeRule.onNodeWithText("Delete test container?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete test container?").performKeyInput { pressKey(Key.Back) }
        composeRule.runOnIdle {
            assertEquals(0, confirmations)
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun environmentSelectionIsExposedAndTargetsTheChosenEnvironment() {
        var selectedId by mutableStateOf("local")
        val remote = Environment(
            id = "remote",
            name = "Remote lab",
            apiUrl = "https://example.invalid",
            status = "online",
        )
        composeRule.setContent {
            ArcaneTheme {
                EnvironmentRow(
                    env = remote,
                    isActive = selectedId == remote.id,
                    onClick = { selectedId = remote.id },
                    onSetActive = { selectedId = remote.id },
                )
            }
        }

        composeRule.onNodeWithText("Remote lab").assertHasClickAction().performClick()
        composeRule.onNodeWithText("Remote lab").assertIsSelected()
        composeRule.runOnIdle { assertEquals("remote", selectedId) }
    }

    @Test
    fun representativeOperationShowsTruthfulStateAndAction() {
        var cancels = 0
        val record = OperationRecord(
            operationId = "11111111-1111-4111-8111-111111111111",
            kind = OperationKind.PROJECT_DEPLOY,
            state = OperationState.RUNNING,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
            serverBindingHash = "server",
            accountBindingHash = "account",
            credentialOriginHash = "credential",
            environmentId = "remote",
            targetType = OperationTargetType.PROJECT,
            opaqueTargetId = "project-id",
            duplicateKeyDigest = "duplicate",
            activityBatchId = "11111111-1111-4111-8111-111111111111",
            recoveryMode = OperationRecoveryMode.ACTIVITY,
            serverActivityId = "activity-id",
            progressPercent = 40,
            targetName = "demo project",
        )
        composeRule.setContent {
            ArcaneTheme {
                OperationDetail(
                    record = record,
                    onBack = {},
                    onCancel = { cancels++ },
                    onRetry = {},
                    onDismiss = {},
                    onOpenActivity = {},
                    canCancel = true,
                )
            }
        }

        composeRule.onNodeWithText("Running").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(1, cancels) }
    }
}
