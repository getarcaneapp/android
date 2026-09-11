package app.getarcane.android.ui.screens.containers

internal enum class ContainerDetailAction(
    val permission: String,
    val title: String,
) {
    Start("containers:start", "Start"),
    Stop("containers:stop", "Stop"),
    Restart("containers:restart", "Restart"),
    Pause("containers:pause", "Pause"),
    Unpause("containers:pause", "Unpause"),
    Kill("containers:kill", "Kill"),
    Redeploy("containers:redeploy", "Redeploy"),
    Delete("containers:delete", "Delete"),
    Inspect("containers:read", "Inspect"),
    Logs("containers:logs", "Logs"),
    Terminal("containers:exec", "Terminal"),
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

internal fun ContainerDetailAction.confirmationMessage(resourceName: String, environmentName: String): String? =
    when (this) {
        ContainerDetailAction.Stop ->
            "Stop “$resourceName” in $environmentName? The container can be started again."
        ContainerDetailAction.Restart ->
            "Restart “$resourceName” in $environmentName? Active connections may be interrupted."
        ContainerDetailAction.Pause ->
            "Pause “$resourceName” in $environmentName? Its processes will stop running until unpaused."
        ContainerDetailAction.Kill ->
            "Force kill “$resourceName” in $environmentName with SIGKILL? The process cannot shut down cleanly."
        ContainerDetailAction.Redeploy ->
            "Redeploy “$resourceName” in $environmentName? Arcane will pull and recreate the container."
        ContainerDetailAction.Delete ->
            "Permanently delete “$resourceName” from $environmentName? Arcane will force removal if needed; this cannot be undone."
        else -> null
    }

internal val ContainerDetailAction.successMessage: String
    get() = when (this) {
        ContainerDetailAction.Start -> "Container started."
        ContainerDetailAction.Stop -> "Container stopped."
        ContainerDetailAction.Restart -> "Container restarted."
        ContainerDetailAction.Pause -> "Container paused."
        ContainerDetailAction.Unpause -> "Container unpaused."
        ContainerDetailAction.Kill -> "Container killed."
        ContainerDetailAction.Redeploy -> "Container redeployed."
        ContainerDetailAction.Delete -> "Container deleted."
        ContainerDetailAction.Inspect,
        ContainerDetailAction.Logs,
        ContainerDetailAction.Terminal,
        -> ""
    }
