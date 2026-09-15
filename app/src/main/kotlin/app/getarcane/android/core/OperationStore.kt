package app.getarcane.android.core

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.activity.Activity
import app.getarcane.sdk.models.activity.ActivityDetail
import app.getarcane.sdk.models.activity.ActivityMessageLevel
import app.getarcane.sdk.models.activity.ActivityStatus
import app.getarcane.sdk.models.activity.ActivityType
import app.getarcane.sdk.models.image.ImagePullOptions
import app.getarcane.sdk.models.project.DeployOptions
import app.getarcane.sdk.models.project.PullProgressEvent
import app.getarcane.sdk.models.system.EnvironmentUpdateJob
import app.getarcane.sdk.models.system.EnvironmentUpdateJobStatus
import app.getarcane.sdk.models.system.EnvironmentUpdateResultStatus
import app.getarcane.sdk.models.updater.UpdaterResult
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.min

private const val SUCCESS_RETENTION_MILLIS = 24L * 60 * 60 * 1_000
private const val UNCERTAIN_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1_000
private const val MAX_LINES = 400
private const val LINE_TRIM_COUNT = 40
private const val MAX_LINE_BYTES = 2_048
private const val MAX_PHASES = 64
private const val MAX_PHASE_BYTES = 512
private const val MAX_PULL_LAYERS = 2_000
private const val ACTIVITY_DETAIL_LIMIT = 200
private const val ACTIVITY_MESSAGE_ID_LIMIT = 1_000
private const val RECOVERY_FAILURE_BUDGET_MILLIS = 15L * 60 * 1_000

internal fun appendBoundedOperationLine(
    lines: List<OperationLine>,
    line: OperationLine,
): List<OperationLine> {
    val next = lines + line
    return if (next.size > MAX_LINES) next.drop(LINE_TRIM_COUNT) else next
}

internal fun appendBoundedOperationPhase(phases: List<String>, phase: String): List<String> =
    if (phase in phases || phases.size >= MAX_PHASES) phases else phases + phase

internal fun retainOperations(input: List<OperationRecord>, timestamp: Long): List<OperationRecord> {
    val kept = input.filter { record ->
        if (!record.isTerminalLike) return@filter true
        val terminalAt = record.terminalAtEpochMs ?: record.updatedAtEpochMs
        val retention = if (record.state in setOf(OperationState.UNKNOWN, OperationState.INTERRUPTED)) {
            UNCERTAIN_RETENTION_MILLIS
        } else {
            SUCCESS_RETENTION_MILLIS
        }
        timestamp - terminalAt <= retention
    }
    val active = kept.filter(OperationRecord::isActive)
    val terminal = kept.filter(OperationRecord::isTerminalLike)
        .sortedByDescending(OperationRecord::updatedAtEpochMs)
        .take(MAX_TERMINAL_OPERATION_ROWS)
    return (active + terminal).sortedBy(OperationRecord::createdAtEpochMs).takeLast(MAX_OPERATION_ROWS)
}

internal data class OperationBinding(
    val serverHash: String,
    val accountHash: String,
    val credentialHash: String,
)

internal fun OperationRecord.matchesBinding(binding: OperationBinding): Boolean =
    serverBindingHash == binding.serverHash &&
        accountBindingHash == binding.accountHash &&
        credentialOriginHash == binding.credentialHash

internal fun canCancelOperation(
    record: OperationRecord,
    hasCancelPermission: Boolean,
    currentBinding: OperationBinding?,
): Boolean = record.isActive &&
    record.recoveryMode == OperationRecoveryMode.ACTIVITY &&
    hasCancelPermission && currentBinding?.let { record.matchesBinding(it) } == true

internal fun operationConflicts(
    existing: OperationRecord,
    kind: OperationKind,
    targetType: OperationTargetType,
    targetId: String?,
    duplicateDigest: String,
): Boolean {
    if (existing.duplicateKeyDigest == duplicateDigest) return true
    if (targetType != OperationTargetType.PROJECT || existing.targetType != OperationTargetType.PROJECT) return false
    if (targetId == null || existing.opaqueTargetId != targetId) return false
    return existing.kind in PROJECT_DEPLOY_FAMILY || kind in PROJECT_DEPLOY_FAMILY
}

internal fun makeRoomForOperation(input: List<OperationRecord>): List<OperationRecord> {
    if (input.size < MAX_OPERATION_ROWS) return input
    val oldestTerminal = input.indexOfFirst(OperationRecord::isTerminalLike)
    return if (oldestTerminal < 0) input else input.filterIndexed { index, _ -> index != oldestTerminal }
}

internal fun restoredActivityTransportIds(input: List<OperationRecord>): Set<String> =
    input.filter { it.isActive && it.recoveryMode == OperationRecoveryMode.ACTIVITY }
        .mapTo(mutableSetOf(), OperationRecord::operationId)

internal fun isUnsupportedFleetError(error: Throwable): Boolean =
    error == ArcaneError.NotFound || error is ArcaneError.Decoding

private val PROJECT_DEPLOY_FAMILY = setOf(OperationKind.PROJECT_DEPLOY, OperationKind.PROJECT_REDEPLOY)

private sealed interface OperationCommand {
    data class Project(val options: DeployOptions?) : OperationCommand
    data class ImagePull(val options: ImagePullOptions) : OperationCommand
    data object ContainerRedeploy : OperationCommand
    data object Updater : OperationCommand
    data object FleetUpdate : OperationCommand
}

/**
 * The sole app-process owner of user-initiated long-running operations.
 *
 * Screens only submit immutable commands and render [operations]. Server execution is correlated
 * through the operation UUID carried as Arcane's activity batch ID; persisted descriptors are never
 * replayed after process death.
 */
class OperationStore internal constructor(
    context: Context,
    private val manager: ArcaneClientManager,
    private val persistence: OperationPersistence = DataStoreOperationPersistence(context),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceMutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val jobs = mutableMapOf<String, Job>()
    private val transportInvoked = mutableSetOf<String>()
    private val pullLayers = mutableMapOf<String, MutableMap<String, Pair<Long, Long>>>()
    private val activityMessageIds = mutableMapOf<String, LinkedHashSet<String>>()
    private var persistenceWritable = true
    private var nextActivityOpenRequestId = 0L
    private val notificationProjector = OperationNotificationProjector(context.applicationContext, manager)

    var operations by mutableStateOf<List<OperationRecord>>(emptyList()); private set
    var isCenterOpen by mutableStateOf(false); private set
    var selectedOperationId by mutableStateOf<String?>(null); private set
    var unavailableMessage by mutableStateOf<String?>(null); private set
    var activityOpenRequest by mutableStateOf<ActivityOpenRequest?>(null); private set
    var notificationPermissionRequestGeneration by mutableIntStateOf(0); private set

    init {
        scope.launch {
            when (val read = persistence.load()) {
                is OperationLedgerRead.Current -> operations = retain(read.operations)
                OperationLedgerRead.Corrupt -> {
                    operations = emptyList()
                    persistenceWritable = true
                }
                OperationLedgerRead.FutureSchema -> {
                    operations = emptyList()
                    persistenceWritable = false
                }
            }
            loaded.complete(Unit)
            if (manager.authStatus == AuthStatus.AUTHENTICATED) {
                reconcileAuthenticatedSession()
            } else {
                notificationProjector.project(emptyList())
            }
        }
    }

    fun onAuthenticated() {
        scope.launch {
            loaded.await()
            reconcileAuthenticatedSession()
        }
    }

    fun onSessionEnding() {
        val endingBinding = currentBinding() ?: return
        scope.launch {
            loaded.await()
            jobs.values.forEach(Job::cancel)
            jobs.clear()
            transportInvoked.clear()
            pullLayers.clear()
            activityMessageIds.clear()
            selectedOperationId = null
            isCenterOpen = false
            if (!persistenceWritable) {
                persistence.clear()
                persistenceWritable = true
                operations = emptyList()
            } else {
                operations = operations.filterNot { it.matchesBinding(endingBinding) }
                persistCurrent()
            }
            projectNotifications()
        }
    }

    fun startProject(
        kind: OperationKind,
        environmentId: EnvironmentId,
        environmentName: String,
        projectId: String,
        projectName: String,
        options: DeployOptions? = null,
    ): OperationStartResult {
        require(kind in PROJECT_KINDS)
        return start(
            kind = kind,
            environmentId = environmentId,
            environmentName = environmentName,
            targetType = OperationTargetType.PROJECT,
            targetId = projectId,
            targetName = projectName,
            duplicateTarget = projectId,
            command = OperationCommand.Project(options),
        )
    }

    fun startImagePull(
        environmentId: EnvironmentId,
        environmentName: String,
        imageReference: String,
        options: ImagePullOptions,
    ): OperationStartResult = start(
        kind = OperationKind.IMAGE_PULL,
        environmentId = environmentId,
        environmentName = environmentName,
        targetType = OperationTargetType.IMAGE,
        targetId = null,
        targetName = imageReference,
        duplicateTarget = imageReference,
        command = OperationCommand.ImagePull(options),
    )

    fun startContainerRedeploy(
        environmentId: EnvironmentId,
        environmentName: String,
        containerId: String,
        containerName: String,
    ): OperationStartResult = start(
        kind = OperationKind.CONTAINER_REDEPLOY,
        environmentId = environmentId,
        environmentName = environmentName,
        targetType = OperationTargetType.CONTAINER,
        targetId = containerId,
        targetName = containerName,
        duplicateTarget = containerId,
        command = OperationCommand.ContainerRedeploy,
    )

    fun startUpdater(
        environmentId: EnvironmentId,
        environmentName: String,
    ): OperationStartResult = start(
        kind = OperationKind.UPDATER_RUN,
        environmentId = environmentId,
        environmentName = environmentName,
        targetType = OperationTargetType.ENVIRONMENT,
        targetId = null,
        targetName = environmentName,
        duplicateTarget = environmentId.rawValue,
        command = OperationCommand.Updater,
    )

    fun startFleetUpdate(): OperationStartResult = start(
        kind = OperationKind.FLEET_UPDATE,
        environmentId = EnvironmentId.LOCAL_DOCKER,
        environmentName = "All environments",
        targetType = OperationTargetType.FLEET,
        targetId = null,
        targetName = "all environments",
        duplicateTarget = "fleet",
        command = OperationCommand.FleetUpdate,
    )

    private fun start(
        kind: OperationKind,
        environmentId: EnvironmentId,
        environmentName: String,
        targetType: OperationTargetType,
        targetId: String?,
        targetName: String,
        duplicateTarget: String,
        command: OperationCommand,
    ): OperationStartResult {
        if (!loaded.isCompleted) return OperationStartResult.Rejected("Operation history is still loading.")
        if (!persistenceWritable) {
            return OperationStartResult.Rejected("Operation history was written by a newer app version.")
        }
        val session = manager.authenticatedClientScope()
            ?: return OperationStartResult.Rejected("Sign in before starting an operation.")
        val binding = bindingFor(session)
        val duplicateDigest = duplicateDigest(binding, kind, environmentId.rawValue, duplicateTarget)
        operations.firstOrNull { existing ->
            existing.isActive && operationConflicts(existing, kind, targetType, targetId, duplicateDigest)
        }?.let { existing ->
            openOperation(existing.operationId)
            return OperationStartResult.Duplicate(existing.operationId)
        }

        operations = retain(operations)
        if (operations.size >= MAX_OPERATION_ROWS && operations.all(OperationRecord::isActive)) {
            return OperationStartResult.Rejected("Too many operations are already active.")
        }
        if (operations.size >= MAX_OPERATION_ROWS) {
            operations = makeRoomForOperation(operations)
        }

        val operationId = UUID.randomUUID().toString()
        val timestamp = now()
        val activitySupported = manager.capabilities.supportsActivities
        val record = OperationRecord(
            operationId = operationId,
            kind = kind,
            state = OperationState.STARTING,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            serverBindingHash = binding.serverHash,
            accountBindingHash = binding.accountHash,
            credentialOriginHash = binding.credentialHash,
            environmentId = environmentId.rawValue,
            targetType = targetType,
            opaqueTargetId = targetId,
            duplicateKeyDigest = duplicateDigest,
            activityBatchId = operationId,
            recoveryMode = when {
                kind == OperationKind.FLEET_UPDATE -> OperationRecoveryMode.FLEET_JOB
                activitySupported -> OperationRecoveryMode.ACTIVITY
                else -> OperationRecoveryMode.NONE
            },
            presentationCode = OperationPresentationCode.STARTING,
            environmentName = boundedUtf8(environmentName, 256),
            targetName = boundedUtf8(targetName, 256),
        )
        operations = operations + record
        projectNotifications()
        if (Build.VERSION.SDK_INT >= 33 && notificationPermissionRequestGeneration == 0) {
            notificationPermissionRequestGeneration = 1
        }

        val runner = scope.launch {
            persistCurrent()
            if (operations.none { it.operationId == operationId }) return@launch
            transportInvoked += operationId
            runOperation(record, command, session)
        }
        jobs[operationId] = runner
        runner.invokeOnCompletion {
            scope.launch {
                if (jobs[operationId] === runner) jobs.remove(operationId)
            }
        }
        return OperationStartResult.Started(operationId)
    }

    private suspend fun runOperation(
        initial: OperationRecord,
        command: OperationCommand,
        session: AuthenticatedClientScope,
    ) {
        try {
            when (command) {
                is OperationCommand.Project -> runProject(initial, command, session)
                is OperationCommand.ImagePull -> runImagePull(initial, command, session)
                OperationCommand.ContainerRedeploy -> runContainerRedeploy(initial, session)
                OperationCommand.Updater -> runUpdater(initial, session)
                OperationCommand.FleetUpdate -> runFleetUpdate(initial, session)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!isCurrent(initial.operationId, session)) return
            if (command == OperationCommand.FleetUpdate && isUnsupportedFleetError(error)) {
                finish(
                    initial.operationId,
                    OperationState.FAILURE,
                    OperationPresentationCode.FAILED,
                    "This Arcane server doesn't support fleet updates. Update the server first.",
                )
            } else if (initial.recoveryMode == OperationRecoveryMode.ACTIVITY) {
                reconcileActivity(initial.operationId, session.client, announceReconnect = true)
            } else if (initial.recoveryMode == OperationRecoveryMode.FLEET_JOB) {
                finish(initial.operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
            } else {
                finish(initial.operationId, OperationState.FAILURE, OperationPresentationCode.FAILED, friendlyErrorMessage(error))
            }
        }
    }

    private suspend fun runProject(
        initial: OperationRecord,
        command: OperationCommand.Project,
        session: AuthenticatedClientScope,
    ) {
        val envId = EnvironmentId(initial.environmentId)
        val projectId = requireNotNull(initial.opaqueTargetId)
        val flow = when (initial.kind) {
            OperationKind.PROJECT_DEPLOY -> session.client.projects.deployStream(
                envId, projectId, command.options, initial.activityBatchId,
            )
            OperationKind.PROJECT_REDEPLOY -> session.client.projects.redeployStream(
                envId, projectId, command.options, initial.activityBatchId,
            )
            OperationKind.PROJECT_PULL -> session.client.projects.pullImagesStream(
                envId = envId, projectId = projectId, activityBatchId = initial.activityBatchId,
            )
            OperationKind.PROJECT_BUILD -> session.client.projects.buildStream(
                envId = envId, projectId = projectId, activityBatchId = initial.activityBatchId,
            )
            else -> error("Unsupported project operation")
        }
        collectProgress(initial, session, flow)
    }

    private suspend fun runImagePull(
        initial: OperationRecord,
        command: OperationCommand.ImagePull,
        session: AuthenticatedClientScope,
    ) {
        collectProgress(
            initial,
            session,
            session.client.images.pullStream(
                envId = EnvironmentId(initial.environmentId),
                options = command.options,
                activityBatchId = initial.activityBatchId,
            ),
        )
    }

    private suspend fun collectProgress(
        initial: OperationRecord,
        session: AuthenticatedClientScope,
        flow: kotlinx.coroutines.flow.Flow<PullProgressEvent>,
    ) {
        var receivedDone = false
        flow.takeWhile { event ->
            if (!isCurrent(initial.operationId, session)) return@takeWhile false
            event.activityId?.trim()?.takeIf(String::isNotEmpty)?.let { activityId ->
                update(initial.operationId) {
                    val queued = event.status?.equals("queued", ignoreCase = true) == true
                    it.copy(
                        state = if (queued) OperationState.QUEUED else OperationState.RUNNING,
                        presentationCode = if (queued) OperationPresentationCode.WAITING else OperationPresentationCode.WORKING,
                        serverActivityId = activityId,
                    )
                }
            }
            event.error?.trim()?.takeIf(String::isNotEmpty)?.let { message ->
                appendLine(initial.operationId, message, isError = true)
                finish(initial.operationId, OperationState.FAILURE, OperationPresentationCode.FAILED, message)
                return@takeWhile false
            }
            true
        }.collect { event ->
            displayProgressLine(event)?.let { appendLine(initial.operationId, it, isError = false) }
            event.phase?.takeIf(String::isNotBlank)?.let { updatePhase(initial.operationId, it) }
                ?: event.status?.takeIf(String::isNotBlank)?.let { updatePhase(initial.operationId, it) }
            updatePullProgress(initial.operationId, event)
            if (event.done == true) receivedDone = true
        }
        val current = operation(initial.operationId) ?: return
        if (current.isTerminalLike) return
        when {
            receivedDone -> finish(initial.operationId, OperationState.SUCCESS, OperationPresentationCode.COMPLETE)
            current.recoveryMode == OperationRecoveryMode.NONE ->
                finish(initial.operationId, OperationState.SUCCESS, OperationPresentationCode.COMPLETE)
            else -> reconcileActivity(initial.operationId, session.client, announceReconnect = true)
        }
    }

    private suspend fun runContainerRedeploy(initial: OperationRecord, session: AuthenticatedClientScope) {
        update(initial.operationId) { it.copy(state = OperationState.RUNNING, presentationCode = OperationPresentationCode.WORKING) }
        val details = session.client.containers.redeploy(
            envId = EnvironmentId(initial.environmentId),
            id = requireNotNull(initial.opaqueTargetId),
            activityBatchId = initial.activityBatchId,
        )
        update(initial.operationId) { it.copy(serverActivityId = details.activityId ?: it.serverActivityId) }
        finish(initial.operationId, OperationState.SUCCESS, OperationPresentationCode.COMPLETE)
    }

    private suspend fun runUpdater(initial: OperationRecord, session: AuthenticatedClientScope) = coroutineScope {
        update(initial.operationId) { it.copy(state = OperationState.RUNNING, presentationCode = OperationPresentationCode.WORKING) }
        val resolver = if (initial.recoveryMode == OperationRecoveryMode.ACTIVITY) launch {
            delay(400)
            resolveActivity(initial.operationId, session.client)?.let { activity ->
                update(initial.operationId) { it.copy(serverActivityId = activity.id) }
            }
        } else null
        val poller = launch {
            while (true) {
                delay(1_500)
                runSuspendCatching { session.client.updater.status(EnvironmentId(initial.environmentId)) }
                    .getOrNull()?.let { status ->
                        val count = status.updatingContainers + status.updatingProjects
                        if (count > 0) updatePhase(initial.operationId, "Updating $count resources")
                    }
            }
        }
        try {
            val result = session.client.updater.run(
                envId = EnvironmentId(initial.environmentId),
                activityBatchId = initial.activityBatchId,
            )
            update(initial.operationId) { it.copy(serverActivityId = result.activityId ?: it.serverActivityId) }
            appendUpdaterResult(initial.operationId, result)
            if (updaterSucceeded(result)) {
                finish(initial.operationId, OperationState.SUCCESS, OperationPresentationCode.COMPLETE)
            } else {
                finish(
                    initial.operationId,
                    OperationState.FAILURE,
                    OperationPresentationCode.COMPLETED_WITH_ISSUES,
                    "Updater completed with issues.",
                )
            }
        } finally {
            resolver?.cancel()
            poller.cancel()
        }
    }

    private suspend fun runFleetUpdate(initial: OperationRecord, session: AuthenticatedClientScope) {
        update(initial.operationId) { it.copy(state = OperationState.RUNNING, presentationCode = OperationPresentationCode.WORKING) }
        val job = session.client.system.triggerUpdateAll(EnvironmentId.LOCAL_DOCKER)
        update(initial.operationId) { it.copy(fleetJobId = job.id) }
        followFleetJob(initial.operationId, session.client, job)
    }

    private suspend fun followFleetJob(operationId: String, client: ArcaneClient, initial: EnvironmentUpdateJob) {
        var job = initial
        var failureStartedAt: Long? = null
        var failureCount = 0
        while (true) {
            appendFleetResults(operationId, job)
            if (job.isTerminal) {
                val failed = job.results.orEmpty().count {
                    it.status == EnvironmentUpdateResultStatus.FAILED ||
                        it.status == EnvironmentUpdateResultStatus.SKIPPED_OFFLINE
                }
                if (job.status == EnvironmentUpdateJobStatus.COMPLETED && failed == 0) {
                    finish(operationId, OperationState.SUCCESS, OperationPresentationCode.COMPLETE)
                } else {
                    finish(
                        operationId,
                        OperationState.FAILURE,
                        if (failed > 0 && failed < job.results.orEmpty().size) {
                            OperationPresentationCode.COMPLETED_WITH_ISSUES
                        } else {
                            OperationPresentationCode.FAILED
                        },
                        if (failed > 0) "Fleet update completed with issues." else "Fleet update failed.",
                    )
                }
                return
            }
            delay(3_000)
            try {
                val next = client.system.updateAllStatus(EnvironmentId.LOCAL_DOCKER)
                failureStartedAt = null
                failureCount = 0
                if (next.id != operation(operationId)?.fleetJobId) {
                    finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
                    return
                }
                job = next
                if (!job.isTerminal) {
                    update(operationId) {
                        it.copy(state = OperationState.RUNNING, presentationCode = OperationPresentationCode.WORKING)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error == ArcaneError.Forbidden || error == ArcaneError.Unauthorized || error == ArcaneError.NotFound) {
                    finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
                    return
                }
                failureCount++
                val started = failureStartedAt ?: now().also { failureStartedAt = it }
                update(operationId) {
                    it.copy(state = OperationState.RECONNECTING, presentationCode = OperationPresentationCode.RECONNECTING)
                }
                if (now() - started >= RECOVERY_FAILURE_BUDGET_MILLIS) {
                    finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
                    return
                }
                delay(min(30_000L, 1_000L * (1L shl min(failureCount, 5))))
            }
        }
    }

    fun cancel(operationId: String) {
        scope.launch { cancelAndWait(operationId) }
    }

    internal suspend fun cancelAfterAuthentication(operationId: String) {
        loaded.await()
        val pending = operation(operationId) ?: return
        if (!pending.isActive || pending.recoveryMode != OperationRecoveryMode.ACTIVITY) return
        if (pending.state != OperationState.CANCEL_REQUESTED) {
            update(operationId) {
                it.copy(state = OperationState.CANCEL_REQUESTED, presentationCode = OperationPresentationCode.CANCELLING)
            }
        }
        while (manager.authStatus == AuthStatus.AUTHENTICATING) delay(100)
        if (manager.authenticatedClientScope() == null) {
            finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
            return
        }
        cancelAndWait(operationId)
    }

    internal fun continueCancellationAfterAuthentication(operationId: String) {
        scope.launch { cancelAfterAuthentication(operationId) }
    }

    suspend fun cancelAndWait(operationId: String) {
        loaded.await()
        val record = operation(operationId) ?: return
        if (!record.isActive) return
        if (!canCancel(record)) return
        val session = manager.authenticatedClientScope() ?: return
        if (!record.matchesBinding(bindingFor(session))) return

        if (operationId !in transportInvoked) {
            jobs.remove(operationId)?.cancel()
            finish(operationId, OperationState.CANCELLED, OperationPresentationCode.CANCELLED)
            return
        }
        if (record.state != OperationState.CANCEL_REQUESTED) {
            update(operationId) {
                it.copy(state = OperationState.CANCEL_REQUESTED, presentationCode = OperationPresentationCode.CANCELLING)
            }
        }
        jobs.remove(operationId)?.cancel()
        val activity = record.serverActivityId?.let { id ->
            runSuspendCatching {
                session.client.activities.detail(EnvironmentId(record.environmentId), id, limit = 1).activity
            }.getOrNull()
        } ?: resolveActivity(operationId, session.client)
        if (activity == null) {
            finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
            return
        }
        update(operationId) { it.copy(serverActivityId = activity.id) }
        try {
            session.client.activities.cancel(
                envId = EnvironmentId(record.environmentId),
                activityId = activity.id,
                requestedBy = manager.currentUser?.displayName ?: manager.currentUser?.username,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // The Activity may have landed between detail and cancel. Reconciliation is authoritative.
        }
        followActivity(operationId, session.client, announceReconnect = false)
    }

    fun retry(operationId: String) {
        val record = operation(operationId) ?: return
        if (record.state !in setOf(OperationState.UNKNOWN, OperationState.INTERRUPTED)) return
        val session = manager.authenticatedClientScope() ?: return
        if (!record.matchesBinding(bindingFor(session))) return
        jobs[operationId] = scope.launch {
            when (record.recoveryMode) {
                OperationRecoveryMode.ACTIVITY -> reconcileActivity(operationId, session.client, true)
                OperationRecoveryMode.FLEET_JOB -> reconcileFleet(operationId, session.client)
                OperationRecoveryMode.NONE -> Unit
            }
        }
    }

    fun dismiss(operationId: String) {
        val record = operation(operationId) ?: return
        if (!record.isTerminalLike) return
        scope.launch {
            update(operationId) { it.copy(state = OperationState.CLEARED) }
            operations = operations.filterNot { it.operationId == operationId }
            if (selectedOperationId == operationId) selectedOperationId = null
            persistCurrent()
            projectNotifications()
        }
    }

    fun openCenter(message: String? = null) {
        unavailableMessage = message
        selectedOperationId = null
        isCenterOpen = true
    }

    fun openOperation(operationId: String) {
        scope.launch {
            loaded.await()
            // A notification can cold-start the process before token restoration has finished.
            // Resolve the immutable binding only after that one authentication transition settles,
            // otherwise a valid exact-context tap can be mistaken for stale foreign state.
            while (manager.authStatus == AuthStatus.AUTHENTICATING) delay(100)
            val record = operation(operationId)
            val binding = currentBinding()
            if (record == null || binding == null || !record.matchesBinding(binding)) {
                openCenter("That operation is no longer available.")
            } else {
                unavailableMessage = null
                selectedOperationId = operationId
                isCenterOpen = true
            }
        }
    }

    fun closeCenter() {
        isCenterOpen = false
        selectedOperationId = null
        unavailableMessage = null
    }

    fun refreshNotificationProjection() {
        projectNotifications()
    }

    fun showOperationList() {
        selectedOperationId = null
        unavailableMessage = null
    }

    fun openActivityCenter(operationId: String) {
        val record = operation(operationId) ?: return
        val activityId = record.serverActivityId ?: return
        activityOpenRequest = ActivityOpenRequest(++nextActivityOpenRequestId, activityId, record.environmentId)
        closeCenter()
    }

    fun consumeActivityOpenRequest(requestId: Long) {
        if (activityOpenRequest?.requestId == requestId) activityOpenRequest = null
    }

    fun canCancel(record: OperationRecord): Boolean = canCancelOperation(
        record = record,
        hasCancelPermission = manager.currentUser?.hasPermission("activities:cancel", record.environmentId) ?: false,
        currentBinding = currentBinding(),
    )

    private suspend fun reconcileAuthenticatedSession() {
        val session = manager.authenticatedClientScope() ?: return
        val binding = bindingFor(session)
        if (!persistenceWritable) return
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        operations = retain(operations.filter { it.matchesBinding(binding) })
        // A restored active Activity may already own a running server mutation. Treat transport as
        // invoked conservatively so cancellation can never become a local-only success verdict.
        transportInvoked += restoredActivityTransportIds(operations)
        val environmentNames = operations.map(OperationRecord::environmentId).distinct().associateWith { id ->
            if (id == EnvironmentId.LOCAL_DOCKER.rawValue) {
                "Local Docker"
            } else {
                runSuspendCatching { session.client.environments.get(EnvironmentId(id)).name }.getOrNull()
                    ?: "Remote environment"
            }
        }
        operations = operations.map { it.copy(environmentName = environmentNames.getValue(it.environmentId)) }
        persistCurrent()
        projectNotifications()
        operations.filter(OperationRecord::isActive).forEach { record ->
            jobs[record.operationId] = scope.launch {
                when (record.recoveryMode) {
                    OperationRecoveryMode.ACTIVITY -> reconcileActivity(record.operationId, session.client, false)
                    OperationRecoveryMode.FLEET_JOB -> reconcileFleet(record.operationId, session.client)
                    OperationRecoveryMode.NONE -> finish(
                        record.operationId,
                        OperationState.INTERRUPTED,
                        OperationPresentationCode.INTERRUPTED,
                    )
                }
            }
        }
    }

    private suspend fun reconcileActivity(operationId: String, client: ArcaneClient, announceReconnect: Boolean) {
        val record = operation(operationId) ?: return
        update(operationId) {
            it.copy(state = OperationState.RECONNECTING, presentationCode = OperationPresentationCode.RECONNECTING)
        }
        val activity = record.serverActivityId?.let { id ->
            runSuspendCatching {
                client.activities.detail(EnvironmentId(record.environmentId), id, ACTIVITY_DETAIL_LIMIT).activity
            }.getOrNull()
        } ?: resolveActivity(operationId, client)
        if (activity == null) {
            finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
            return
        }
        update(operationId) { it.copy(serverActivityId = activity.id) }
        followActivity(operationId, client, announceReconnect)
    }

    private suspend fun resolveActivity(operationId: String, client: ArcaneClient): Activity? {
        val record = operation(operationId) ?: return null
        val expectedType = record.kind.activityType ?: return null
        repeat(6) { attempt ->
            try {
                val matches = client.activities.listPaginated(
                    envId = EnvironmentId(record.environmentId),
                    limit = 200,
                    type = expectedType,
                ).data.filter { activity ->
                    activity.batchId == record.activityBatchId &&
                        activity.type == expectedType &&
                        (record.opaqueTargetId == null || activity.resourceId == record.opaqueTargetId)
                }
                if (matches.size == 1) return matches.single()
                if (matches.size > 1) return null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Retry only the exact batch correlation lookup.
            }
            delay(400L * (attempt + 1))
        }
        return null
    }

    private suspend fun followActivity(operationId: String, client: ArcaneClient, announceReconnect: Boolean) {
        val started = now()
        var failureCount = 0
        if (announceReconnect) appendLine(operationId, "Following the server activity…", false)
        while (now() - started < RECOVERY_FAILURE_BUDGET_MILLIS) {
            val record = operation(operationId) ?: return
            val activityId = record.serverActivityId ?: return
            try {
                val detail = client.activities.detail(
                    envId = EnvironmentId(record.environmentId),
                    activityId = activityId,
                    limit = ACTIVITY_DETAIL_LIMIT,
                )
                failureCount = 0
                applyActivityDetail(operationId, detail)
                when (detail.activity.status) {
                    ActivityStatus.SUCCESS -> {
                        finish(operationId, OperationState.SUCCESS, OperationPresentationCode.COMPLETE)
                        return
                    }
                    ActivityStatus.FAILED -> {
                        finish(
                            operationId,
                            OperationState.FAILURE,
                            OperationPresentationCode.FAILED,
                            detail.activity.error,
                        )
                        return
                    }
                    ActivityStatus.CANCELLED -> {
                        finish(operationId, OperationState.CANCELLED, OperationPresentationCode.CANCELLED)
                        return
                    }
                    ActivityStatus.QUEUED -> update(operationId) {
                        it.copy(state = OperationState.QUEUED, presentationCode = OperationPresentationCode.WAITING)
                    }
                    ActivityStatus.RUNNING -> update(operationId) {
                        it.copy(
                            state = if (it.state == OperationState.CANCEL_REQUESTED) it.state else OperationState.RUNNING,
                            presentationCode = if (it.state == OperationState.CANCEL_REQUESTED) {
                                OperationPresentationCode.CANCELLING
                            } else {
                                OperationPresentationCode.WORKING
                            },
                        )
                    }
                    ActivityStatus.UNKNOWN -> Unit
                }
                delay(2_000)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (
                    error == ArcaneError.Forbidden ||
                    error == ArcaneError.Unauthorized ||
                    error == ArcaneError.NotFound
                ) {
                    finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
                    return
                }
                failureCount++
                update(operationId) {
                    it.copy(state = OperationState.RECONNECTING, presentationCode = OperationPresentationCode.RECONNECTING)
                }
                delay(min(30_000L, 1_000L * (1L shl min(failureCount, 5))))
            }
        }
        finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
    }

    private suspend fun reconcileFleet(operationId: String, client: ArcaneClient) {
        val record = operation(operationId) ?: return
        val expectedJobId = record.fleetJobId
        if (expectedJobId == null) {
            finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
            return
        }
        val current = runSuspendCatching {
            client.system.updateAllStatus(EnvironmentId.LOCAL_DOCKER)
        }.getOrNull()
        if (current == null || current.id != expectedJobId) {
            finish(operationId, OperationState.UNKNOWN, OperationPresentationCode.OUTCOME_UNKNOWN)
            return
        }
        followFleetJob(operationId, client, current)
    }

    private fun applyActivityDetail(operationId: String, detail: ActivityDetail) {
        val seen = activityMessageIds.getOrPut(operationId) { linkedSetOf() }
        detail.messages.sortedBy { it.createdAt }.takeLast(ACTIVITY_DETAIL_LIMIT).forEach { message ->
            if (seen.add(message.id)) {
                appendLine(operationId, message.message, message.level == ActivityMessageLevel.ERROR)
            }
        }
        while (seen.size > ACTIVITY_MESSAGE_ID_LIMIT) seen.remove(seen.first())
        detail.activity.step.takeIf(String::isNotBlank)?.let { updatePhase(operationId, it) }
        detail.activity.progress?.let { progress ->
            update(operationId) { current ->
                current.copy(progressPercent = maxOf(current.progressPercent ?: 0, progress.coerceIn(0, 100)))
            }
        }
    }

    private fun appendUpdaterResult(operationId: String, result: UpdaterResult) {
        result.items.forEach { item ->
            val label = item.resourceName ?: item.resourceId
            appendLine(
                operationId,
                "$label: ${item.error ?: item.status}",
                item.error?.isNotBlank() == true,
            )
        }
    }

    private fun appendFleetResults(operationId: String, job: EnvironmentUpdateJob) {
        job.results.orEmpty().forEach { result ->
            appendLine(operationId, "${result.environmentName}: ${result.status.wire}", result.status == EnvironmentUpdateResultStatus.FAILED)
        }
    }

    private fun updaterSucceeded(result: UpdaterResult): Boolean =
        result.failed == 0 && result.items.none { it.error?.isNotBlank() == true }

    private fun displayProgressLine(event: PullProgressEvent): String? =
        event.log ?: event.stream ?: buildList {
            event.status?.takeIf(String::isNotBlank)?.let(::add)
            event.progress?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" ").takeIf(String::isNotBlank)

    private fun updatePullProgress(operationId: String, event: PullProgressEvent) {
        val layerId = event.id ?: return
        val detail = event.progressDetail ?: return
        val total = detail.total?.takeIf { it > 0 } ?: return
        val layers = pullLayers.getOrPut(operationId) { mutableMapOf() }
        if (layerId !in layers && layers.size >= MAX_PULL_LAYERS) return
        layers[layerId] = (detail.current ?: 0L).coerceIn(0L, total) to total
        var currentSum = 0L
        var totalSum = 0L
        layers.values.forEach { (current, layerTotal) ->
            currentSum = saturatingAdd(currentSum, current)
            totalSum = saturatingAdd(totalSum, layerTotal)
        }
        if (totalSum <= 0) return
        val percent = ((currentSum.toDouble() / totalSum.toDouble()) * 100).toInt().coerceIn(0, 100)
        update(operationId) { current -> current.copy(progressPercent = maxOf(current.progressPercent ?: 0, percent)) }
    }

    private fun appendLine(operationId: String, text: String, isError: Boolean) {
        val bounded = boundedUtf8(text, MAX_LINE_BYTES)
        update(operationId, persist = false) { record ->
            record.copy(lines = appendBoundedOperationLine(record.lines, OperationLine(bounded, isError)))
        }
    }

    private fun updatePhase(operationId: String, phase: String) {
        val bounded = boundedUtf8(phase, MAX_PHASE_BYTES)
        update(operationId, persist = false) { record ->
            record.copy(phases = appendBoundedOperationPhase(record.phases, bounded), detailMessage = bounded)
        }
    }

    private fun finish(
        operationId: String,
        state: OperationState,
        code: OperationPresentationCode,
        detailMessage: String? = null,
    ) {
        pullLayers.remove(operationId)
        activityMessageIds.remove(operationId)
        update(operationId) { record ->
            record.copy(
                state = state,
                presentationCode = code,
                progressPercent = if (state == OperationState.SUCCESS) 100 else record.progressPercent,
                terminalAtEpochMs = now(),
                detailMessage = detailMessage?.let { boundedUtf8(it, MAX_LINE_BYTES) },
            )
        }
        operations = retain(operations)
        projectNotifications()
    }

    private fun update(
        operationId: String,
        persist: Boolean = true,
        transform: (OperationRecord) -> OperationRecord,
    ) {
        val index = operations.indexOfFirst { it.operationId == operationId }
        if (index < 0) return
        val current = operations[index]
        val transformed = transform(current)
        if (!OperationStateMachine.canTransition(current.state, transformed.state)) return
        val updated = transformed.copy(updatedAtEpochMs = now())
        operations = operations.toMutableList().also { it[index] = updated }
        projectNotifications()
        if (persist) scope.launch { persistCurrent() }
    }

    private suspend fun persistCurrent() {
        if (!persistenceWritable) return
        persistenceMutex.withLock {
            val snapshot = withContext(Dispatchers.Main.immediate) { retain(operations) }
            persistence.save(snapshot)
        }
    }

    private fun projectNotifications() {
        notificationProjector.project(operations)
    }

    private fun retain(input: List<OperationRecord>): List<OperationRecord> = retainOperations(input, now())

    private fun isCurrent(operationId: String, session: AuthenticatedClientScope): Boolean =
        operation(operationId) != null && manager.isCurrent(session)

    private fun operation(operationId: String): OperationRecord? =
        operations.firstOrNull { it.operationId == operationId }

    private fun currentBinding(): OperationBinding? =
        manager.authenticatedClientScope()?.let(::bindingFor)

    private fun bindingFor(session: AuthenticatedClientScope): OperationBinding {
        val server = sha256(session.serverIdentity)
        return OperationBinding(
            serverHash = server,
            accountHash = sha256("${session.serverIdentity}\u0000${session.userId}"),
            credentialHash = sha256("credential\u0000${session.serverIdentity}"),
        )
    }

    private fun duplicateDigest(
        binding: OperationBinding,
        kind: OperationKind,
        environmentId: String,
        target: String,
    ): String = sha256(
        listOf(binding.serverHash, binding.accountHash, duplicateFamily(kind), kind.name, environmentId, target)
            .joinToString("\u0000"),
    )

    private fun duplicateFamily(kind: OperationKind): String = when (kind) {
        OperationKind.PROJECT_DEPLOY, OperationKind.PROJECT_REDEPLOY -> "project_deploy"
        else -> kind.name
    }

    private val OperationKind.activityType: ActivityType?
        get() = when (this) {
            OperationKind.PROJECT_DEPLOY -> ActivityType.PROJECT_DEPLOY
            OperationKind.PROJECT_REDEPLOY -> ActivityType.PROJECT_REDEPLOY
            OperationKind.PROJECT_PULL -> ActivityType.PROJECT_PULL
            OperationKind.PROJECT_BUILD -> ActivityType.PROJECT_BUILD
            OperationKind.IMAGE_PULL -> ActivityType.IMAGE_PULL
            OperationKind.CONTAINER_REDEPLOY -> ActivityType.CONTAINER_REDEPLOY
            OperationKind.UPDATER_RUN -> ActivityType.AUTO_UPDATE
            OperationKind.FLEET_UPDATE, OperationKind.UNKNOWN -> null
        }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right > 0 && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun boundedUtf8(value: String, maximumBytes: Int): String {
        val bytes = value.encodeToByteArray()
        if (bytes.size <= maximumBytes) return value
        return bytes.copyOf(maximumBytes).decodeToString().trimEnd('\uFFFD')
    }

    private companion object {
        val PROJECT_KINDS = setOf(
            OperationKind.PROJECT_DEPLOY,
            OperationKind.PROJECT_REDEPLOY,
            OperationKind.PROJECT_PULL,
            OperationKind.PROJECT_BUILD,
        )
    }
}

val LocalOperationStore = androidx.compose.runtime.staticCompositionLocalOf<OperationStore> {
    error("OperationStore not provided")
}
