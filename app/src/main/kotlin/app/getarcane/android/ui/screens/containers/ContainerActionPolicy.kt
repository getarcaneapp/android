package app.getarcane.android.ui.screens.containers

import androidx.annotation.StringRes
import app.getarcane.android.R

internal enum class ContainerDetailAction(
    val permission: String,
    @get:StringRes val titleRes: Int,
    @get:StringRes val successMessageRes: Int? = null,
    @get:StringRes val confirmationMessageRes: Int? = null,
) {
    Start("containers:start", R.string.container_action_start, R.string.container_started),
    Stop("containers:stop", R.string.container_action_stop, R.string.container_stopped, R.string.confirm_container_stop_message),
    Restart("containers:restart", R.string.container_action_restart, R.string.container_restarted, R.string.confirm_container_restart_message),
    Pause("containers:pause", R.string.container_action_pause, R.string.container_paused, R.string.confirm_container_pause_message),
    Unpause("containers:pause", R.string.container_action_unpause, R.string.container_unpaused),
    Kill("containers:kill", R.string.container_action_kill, R.string.container_killed, R.string.confirm_container_kill_message),
    Redeploy("containers:redeploy", R.string.container_action_redeploy, R.string.container_redeployed, R.string.confirm_container_redeploy_message),
    Delete("containers:delete", R.string.container_action_delete, R.string.container_deleted, R.string.confirm_container_delete_message),
    Inspect("containers:read", R.string.container_action_inspect),
    Logs("containers:logs", R.string.container_action_logs),
    Terminal("containers:exec", R.string.container_action_terminal),
}

internal fun availableContainerActions(
    isRunning: Boolean,
    isPaused: Boolean,
    permissions: Set<String>,
    supportsPauseKill: Boolean,
): Set<ContainerDetailAction> = buildSet {
    fun allows(action: ContainerDetailAction): Boolean =
        "*" in permissions || action.permission in permissions

    ContainerDetailAction.entries.filterTo(this) { action ->
        if (!allows(action)) return@filterTo false
        when (action) {
            ContainerDetailAction.Start -> !isRunning && !isPaused
            ContainerDetailAction.Stop,
            ContainerDetailAction.Restart,
            -> isRunning && !isPaused
            ContainerDetailAction.Pause -> isRunning && !isPaused && supportsPauseKill
            ContainerDetailAction.Unpause -> isPaused && supportsPauseKill
            ContainerDetailAction.Kill -> isRunning && supportsPauseKill
            ContainerDetailAction.Terminal -> isRunning && !isPaused
            ContainerDetailAction.Redeploy,
            ContainerDetailAction.Delete,
            ContainerDetailAction.Inspect,
            ContainerDetailAction.Logs,
            -> true
        }
    }
}
